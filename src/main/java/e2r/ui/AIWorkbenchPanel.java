package e2r.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;

import e2r.E2RExtension;
import e2r.ai.AiProvider;
import e2r.ai.PromptBuilder;
import e2r.core.ContextExtractor;
import e2r.core.ContextExtractor.ContextResult;
import e2r.core.DiscoveredEndpoint;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tab 2: AI Workbench Panel
 * Generate HTTP requests from endpoints using Ollama AI.
 */
public class AIWorkbenchPanel extends JPanel {
    
    private final MontoyaApi api;
    private final SettingsPanel settingsPanel;
    
    private JLabel targetLabel;
    private JRadioButton fullFileRadio;
    private JRadioButton customRangeRadio;
    private JSpinner rangeSpinner;
    private JButton generateButton;
    
    private final JTextArea contextPreview;
    private final JTextArea outputArea;
    
    private final JButton copyButton;
    private final JButton sendToRepeaterButton;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    private DiscoveredEndpoint currentEndpoint;
    private String currentContext;
    
    public AIWorkbenchPanel(MontoyaApi api, SettingsPanel settingsPanel) {
        this.api = api;
        this.settingsPanel = settingsPanel;
        
        setLayout(new BorderLayout());
        
        // Top control bar
        JPanel controlBar = createControlBar();
        add(controlBar, BorderLayout.NORTH);
        
        // Main content - horizontal split
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(500);
        
        // Left: Context preview
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Code Context to Send (Preview)"));
        
        contextPreview = new JTextArea();
        contextPreview.setFont(new Font("Consolas", Font.PLAIN, 12));
        contextPreview.setEditable(false);
        contextPreview.setBackground(new Color(40, 40, 40));
        contextPreview.setForeground(new Color(200, 200, 200));
        contextPreview.setCaretColor(Color.WHITE);
        
        JScrollPane contextScroll = new JScrollPane(contextPreview);
        leftPanel.add(contextScroll, BorderLayout.CENTER);
        
        splitPane.setLeftComponent(leftPanel);
        
        // Right: AI output
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("AI Generated Request (Raw HTTP)"));
        
        outputArea = new JTextArea();
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        outputArea.setEditable(true);
        outputArea.setBackground(new Color(35, 35, 35));
        outputArea.setForeground(new Color(180, 220, 180));
        outputArea.setCaretColor(Color.WHITE);
        
        JScrollPane outputScroll = new JScrollPane(outputArea);
        rightPanel.add(outputScroll, BorderLayout.CENTER);
        
        // Output buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        copyButton = new JButton("Copy to Clipboard");
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copyOutput());
        buttonPanel.add(copyButton);
        
        sendToRepeaterButton = new JButton("Send to Repeater");
        sendToRepeaterButton.setEnabled(false);
        sendToRepeaterButton.setFont(sendToRepeaterButton.getFont().deriveFont(Font.BOLD));
        sendToRepeaterButton.addActionListener(e -> sendToRepeater());
        buttonPanel.add(sendToRepeaterButton);
        
        rightPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        splitPane.setRightComponent(rightPanel);
        
        add(splitPane, BorderLayout.CENTER);
    }
    
    private JPanel createControlBar() {
        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlBar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Target label
        controlBar.add(new JLabel("Target:"));
        targetLabel = new JLabel("[No endpoint selected]");
        targetLabel.setFont(targetLabel.getFont().deriveFont(Font.BOLD));
        targetLabel.setForeground(new Color(80, 150, 220));
        controlBar.add(targetLabel);
        
        controlBar.add(Box.createHorizontalStrut(30));
        
        // Context scope options
        controlBar.add(new JLabel("Context Scope:"));
        
        fullFileRadio = new JRadioButton("Full File");
        customRangeRadio = new JRadioButton("Custom Range (Lines):", true);
        
        ButtonGroup scopeGroup = new ButtonGroup();
        scopeGroup.add(fullFileRadio);
        scopeGroup.add(customRangeRadio);
        
        controlBar.add(fullFileRadio);
        controlBar.add(customRangeRadio);
        
        // Range spinner (min 10, max 2000, step 10)
        rangeSpinner = new JSpinner(new SpinnerNumberModel(100, 10, 2000, 50));
        rangeSpinner.setPreferredSize(new Dimension(80, 25));
        controlBar.add(rangeSpinner);
        
        // Update context when options change
        fullFileRadio.addActionListener(e -> updateContextPreview());
        customRangeRadio.addActionListener(e -> updateContextPreview());
        rangeSpinner.addChangeListener(e -> {
            if (customRangeRadio.isSelected()) {
                updateContextPreview();
            }
        });
        
        controlBar.add(Box.createHorizontalStrut(20));
        
        // Generate button
        generateButton = new JButton("🤖 Generate Request with AI");
        generateButton.setFont(generateButton.getFont().deriveFont(Font.BOLD, 14f));
        generateButton.setEnabled(false);
        generateButton.addActionListener(e -> generateRequest());
        controlBar.add(generateButton);
        
        return controlBar;
    }
    
    /**
     * Load an endpoint from Live Discovery.
     */
    public void loadEndpoint(DiscoveredEndpoint endpoint) {
        this.currentEndpoint = endpoint;
        
        // Update target label
        targetLabel.setText(String.format("[%s] %s (from %s)", 
            endpoint.getMethod(), 
            endpoint.getEndpoint(),
            getFileName(endpoint.getSourceUrl())
        ));
        
        // Enable generate button
        generateButton.setEnabled(true);
        
        // Clear previous output
        outputArea.setText("");
        copyButton.setEnabled(false);
        sendToRepeaterButton.setEnabled(false);
        
        // Update context preview
        updateContextPreview();
    }
    
    private void updateContextPreview() {
        if (currentEndpoint == null) return;
        
        String content = currentEndpoint.getSourceContent();
        
        if (fullFileRadio.isSelected()) {
            currentContext = content;
        } else {
            int lines = (Integer) rangeSpinner.getValue();
            ContextResult result = ContextExtractor.extractContext(
                content,
                currentEndpoint.getMatchStartPosition(),
                lines
            );
            currentContext = result.context;
        }
        
        contextPreview.setText(currentContext);
        contextPreview.setCaretPosition(0);
    }
    
    private void generateRequest() {
        if (currentEndpoint == null || currentContext == null) return;
        
        // Get the configured AI provider
        AiProvider provider = settingsPanel.getAiProvider();
        String model = settingsPanel.getSelectedModel();
        
        // Disable button during generation
        generateButton.setEnabled(false);
        generateButton.setText("Generating...");
        outputArea.setText("Generating request with " + provider.getProviderName() + " (" + model + ")...\n\nPlease wait...");
        
        executor.submit(() -> {
            try {
                // Build the prompt with endpoint, context, host, and method
                String prompt = PromptBuilder.buildPrompt(
                    currentEndpoint.getEndpoint(),
                    currentContext,
                    currentEndpoint.getHost(),
                    currentEndpoint.getMethod()
                );
                
                // Generate request using the configured provider
                String response = provider.generateRequest(prompt, null);
                
                // Update UI on EDT
                SwingUtilities.invokeLater(() -> {
                    outputArea.setText(response);
                    outputArea.setCaretPosition(0);
                    copyButton.setEnabled(true);
                    sendToRepeaterButton.setEnabled(true);
                    generateButton.setEnabled(true);
                    generateButton.setText("🤖 Generate Request with AI");
                });
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    String errorMsg = "Error generating request:\n\n" + e.getMessage();
                    if (!settingsPanel.isGroqProvider()) {
                        errorMsg += "\n\nMake sure Ollama is running at: " + settingsPanel.getOllamaUrl();
                    }
                    outputArea.setText(errorMsg);
                    generateButton.setEnabled(true);
                    generateButton.setText("🤖 Generate Request with AI");
                    
                    // Show popup for Groq auth errors
                    if (e.getMessage() != null && e.getMessage().contains("Invalid API Key")) {
                        JOptionPane.showMessageDialog(AIWorkbenchPanel.this,
                            "Invalid Groq API Key. Please check your API key in Settings.",
                            "Authentication Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
                E2RExtension.logError("AI generation failed: " + e.getMessage());
            }
        });
    }
    
    private void copyOutput() {
        String text = outputArea.getText();
        if (text != null && !text.isEmpty()) {
            Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
        }
    }
    
    private void sendToRepeater() {
        String rawRequest = outputArea.getText();
        if (rawRequest == null || rawRequest.isEmpty()) return;
        
        try {
            // Parse the raw request to create HttpRequest
            // Determine host - try to get from Host header or use current endpoint host
            String host = currentEndpoint != null ? currentEndpoint.getHost() : "localhost";
            boolean useHttps = true;
            
            // Look for Host header in the raw request
            String[] lines = rawRequest.split("\n");
            for (String line : lines) {
                if (line.toLowerCase().startsWith("host:")) {
                    String hostValue = line.substring(5).trim();
                    if (!hostValue.equals("TARGET_HOST")) {
                        host = hostValue;
                    }
                    break;
                }
            }
            
            // Create HTTP request
            HttpRequest request = HttpRequest.httpRequest(rawRequest);
            
            // Send to Repeater
            api.repeater().sendToRepeater(request, "E2R - " + currentEndpoint.getEndpoint());
            
            E2RExtension.log("Sent request to Repeater: " + currentEndpoint.getEndpoint());
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Failed to send to Repeater: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            E2RExtension.logError("Send to Repeater failed: " + e.getMessage());
        }
    }
    
    private String getFileName(String url) {
        if (url == null) return "";
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            String name = url.substring(lastSlash + 1);
            int query = name.indexOf('?');
            if (query > 0) name = name.substring(0, query);
            return name;
        }
        return url;
    }
}
