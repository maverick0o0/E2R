package e2r.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.Preferences;

import e2r.E2RExtension;
import e2r.ai.AiProvider;
import e2r.ai.OllamaProvider;
import e2r.ai.GroqProvider;
import e2r.ai.GeminiProvider;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tab 3: Settings Panel
 * Configure AI providers (Ollama/Groq), extension blacklist, and other options.
 */
public class SettingsPanel extends JPanel {
    
    private final MontoyaApi api;
    private final Preferences prefs;
    
    // Provider selection
    private final JComboBox<String> providerCombo;
    private static final String PROVIDER_OLLAMA = "Ollama (Local)";
    private static final String PROVIDER_GROQ = "Groq (Cloud)";
    private static final String PROVIDER_GEMINI = "Gemini (Cloud)";
    
    // Memory storage for keys during UI switching
    private String groqApiKey = "";
    private String geminiApiKey = "";
    private String lastSelectedProvider = PROVIDER_OLLAMA;
    
    // Ollama settings
    private final JLabel ollamaUrlLabel;
    private final JTextField ollamaUrlField;
    
    // Groq settings
    private final JLabel groqApiKeyLabel;
    private final JPasswordField groqApiKeyField;
    
    // Model selection
    private final JComboBox<String> modelCombo;
    
    // Buttons
    private final JButton saveButton;
    private final JButton testConnectionButton;
    private final JLabel connectionStatus;
    
    // Blacklist
    private final JTextArea blacklistArea;
    private final JTextArea pathBlacklistArea;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    // Preference keys
    private static final String PREF_PROVIDER = "e2r.provider";
    private static final String PREF_OLLAMA_URL = "e2r.ollama.url";
    private static final String PREF_GROQ_API_KEY = "e2r.groq.apikey";
    private static final String PREF_GEMINI_API_KEY = "e2r.gemini.apikey";
    private static final String PREF_MODEL = "e2r.model";
    
    // Default values
    private static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";
    private static final String DEFAULT_OLLAMA_MODEL = "qwen2.5-coder:7b";
    private static final String DEFAULT_GROQ_MODEL = "llama-3.3-70b-versatile";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-1.5-flash";
    
    // Available models
    private static final String[] GROQ_MODELS = {
        "llama-3.1-8b-instant",
        "llama-3.3-70b-versatile",
        "qwen/qwen3-32b",
        "mixtral-8x7b-32768"
    };
    
    private static final String[] OLLAMA_MODELS = {
        "qwen2.5-coder:7b",
        "llama3",
        "codellama",
        "deepseek-coder",
        "mistral"
    };
    
    private static final String[] GEMINI_MODELS = {
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-2.0-flash-exp",
        "gemini-2.5-flash"
    };
    

    
    public SettingsPanel(MontoyaApi api) {
        this.api = api;
        this.prefs = api.persistence().preferences();
        
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        
        // AI Provider Configuration Section
        JPanel aiPanel = new JPanel(new GridBagLayout());
        aiPanel.setBorder(BorderFactory.createTitledBorder("AI Provider Configuration"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Provider Selector
        gbc.gridx = 0; gbc.gridy = 0;
        aiPanel.add(new JLabel("Provider:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        providerCombo = new JComboBox<>(new String[]{PROVIDER_OLLAMA, PROVIDER_GROQ, PROVIDER_GEMINI});
        providerCombo.addActionListener(e -> updateProviderUI());
        aiPanel.add(providerCombo, gbc);
        
        // Ollama URL (shown for Ollama)
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        ollamaUrlLabel = new JLabel("Server URL:");
        aiPanel.add(ollamaUrlLabel, gbc);
        
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        ollamaUrlField = new JTextField(DEFAULT_OLLAMA_URL, 30);
        aiPanel.add(ollamaUrlField, gbc);
        
        // Groq API Key (shown for Groq)
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        groqApiKeyLabel = new JLabel("API Key:");
        aiPanel.add(groqApiKeyLabel, gbc);
        
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        groqApiKeyField = new JPasswordField(30);
        groqApiKeyField.setToolTipText("Get your API key from console.groq.com");
        aiPanel.add(groqApiKeyField, gbc);
        
        // Model Selection (dropdown)
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        aiPanel.add(new JLabel("Model:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        modelCombo = new JComboBox<>(OLLAMA_MODELS);
        modelCombo.setEditable(true); // Allow custom models
        aiPanel.add(modelCombo, gbc);
        
        // Buttons row
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        
        saveButton = new JButton("💾 Save Settings");
        saveButton.addActionListener(e -> saveSettings());
        buttonPanel.add(saveButton);
        
        testConnectionButton = new JButton("Test Connection");
        testConnectionButton.addActionListener(e -> testConnection());
        buttonPanel.add(testConnectionButton);
        
        gbc.gridwidth = 2;
        aiPanel.add(buttonPanel, gbc);
        
        // Connection status
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.gridwidth = 2;
        connectionStatus = new JLabel("");
        aiPanel.add(connectionStatus, gbc);
        
        // Available Groq models hint
        gbc.gridx = 0; gbc.gridy = 6;
        gbc.gridwidth = 2;
        JLabel modelsHint = new JLabel("<html><small>Models: Groq (llama-3.3-70b-versatile), Gemini (gemini-1.5-flash)</small></html>");
        modelsHint.setForeground(Color.GRAY);
        aiPanel.add(modelsHint, gbc);
        
        mainPanel.add(aiPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        
        // Extension Blacklist Section
        JPanel blacklistPanel = new JPanel(new BorderLayout(5, 5));
        blacklistPanel.setBorder(BorderFactory.createTitledBorder("Extension Blacklist (Filtered from Results)"));
        
        JLabel blacklistNote = new JLabel("<html>File extensions to exclude (one per line, with dot):</html>");
        blacklistPanel.add(blacklistNote, BorderLayout.NORTH);
        
        blacklistArea = new JTextArea(5, 30);
        blacklistArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        loadBlacklistToUI();
        
        JScrollPane blacklistScroll = new JScrollPane(blacklistArea);
        blacklistPanel.add(blacklistScroll, BorderLayout.CENTER);
        
        JPanel blacklistButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton applyBtn = new JButton("Apply Changes");
        applyBtn.addActionListener(e -> applyBlacklist());
        blacklistButtons.add(applyBtn);
        
        JButton resetBtn = new JButton("Reset to Defaults");
        resetBtn.addActionListener(e -> {
            e2r.core.EndpointPattern.resetBlacklist();
            loadBlacklistToUI();
            JOptionPane.showMessageDialog(this, "Blacklist reset to defaults.", 
                "Reset", JOptionPane.INFORMATION_MESSAGE);
        });
        blacklistButtons.add(resetBtn);
        
        blacklistPanel.add(blacklistButtons, BorderLayout.SOUTH);
        
        mainPanel.add(blacklistPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        
        // Path Blacklist Section
        JPanel pathBlacklistPanel = new JPanel(new BorderLayout(5, 5));
        pathBlacklistPanel.setBorder(BorderFactory.createTitledBorder("Path Blacklist (Filtered from Results)"));
        
        JLabel pathBlacklistNote = new JLabel("<html>Partial paths to exclude (one per line, e.g. /_next/):</html>");
        pathBlacklistPanel.add(pathBlacklistNote, BorderLayout.NORTH);
        
        pathBlacklistArea = new JTextArea(5, 30);
        pathBlacklistArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        loadPathBlacklistToUI();
        
        JScrollPane pathBlacklistScroll = new JScrollPane(pathBlacklistArea);
        pathBlacklistPanel.add(pathBlacklistScroll, BorderLayout.CENTER);
        
        JPanel pathBlacklistButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton applyPathBtn = new JButton("Apply Changes");
        applyPathBtn.addActionListener(e -> applyPathBlacklist());
        pathBlacklistButtons.add(applyPathBtn);
        
        JButton resetPathBtn = new JButton("Reset to Defaults");
        resetPathBtn.addActionListener(e -> {
            e2r.core.EndpointPattern.resetPathBlacklist();
            loadPathBlacklistToUI();
            JOptionPane.showMessageDialog(this, "Path blacklist reset to defaults.", 
                "Reset", JOptionPane.INFORMATION_MESSAGE);
        });
        pathBlacklistButtons.add(resetPathBtn);
        
        pathBlacklistPanel.add(pathBlacklistButtons, BorderLayout.SOUTH);
        
        mainPanel.add(pathBlacklistPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        
        // About Section
        JPanel aboutPanel = new JPanel(new BorderLayout());
        aboutPanel.setBorder(BorderFactory.createTitledBorder("About E2R"));
        
        JTextArea aboutText = new JTextArea(
            "E2R (Endpoint To Request) v" + E2RExtension.VERSION + "\n" +
            "By Erfan Tavakoli - Maverick0o0\n\n" +
            "AI-powered endpoint discovery and request generation.\n" +
            "Supports Ollama (local), Groq (cloud), and Gemini (cloud) providers."
        );
        aboutText.setEditable(false);
        aboutText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        aboutText.setBackground(getBackground());
        aboutPanel.add(aboutText, BorderLayout.CENTER);
        
        mainPanel.add(aboutPanel);
        
        // Wrap in scroll pane
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
        
        // Load saved settings
        loadSettings();
        
        // Initialize UI state
        updateProviderUI();
    }
    
    /**
     * Load settings from Burp Preferences.
     */
    private void loadSettings() {
        try {
            // Load API Keys first
            groqApiKey = prefs.getString(PREF_GROQ_API_KEY);
            if (groqApiKey == null) groqApiKey = "";
            
            geminiApiKey = prefs.getString(PREF_GEMINI_API_KEY);
            if (geminiApiKey == null) geminiApiKey = "";
            

            
            // Load provider
            String provider = prefs.getString(PREF_PROVIDER);
            if (provider != null) {
                providerCombo.setSelectedItem(provider);
                lastSelectedProvider = provider;
            } else {
                providerCombo.setSelectedItem(PROVIDER_OLLAMA);
                lastSelectedProvider = PROVIDER_OLLAMA;
            }
            
            // Load Ollama URL
            String ollamaUrl = prefs.getString(PREF_OLLAMA_URL);
            if (ollamaUrl != null && !ollamaUrl.isEmpty()) {
                ollamaUrlField.setText(ollamaUrl);
            }
            
            // Load active API Key into password field
            if (PROVIDER_GROQ.equals(provider)) {
                groqApiKeyField.setText(groqApiKey);
            } else if (PROVIDER_GEMINI.equals(provider)) {
                groqApiKeyField.setText(geminiApiKey);
            }
            
            // Load model
            String model = prefs.getString(PREF_MODEL);
            if (model != null && !model.isEmpty()) {
                modelCombo.setSelectedItem(model);
            }
            
            E2RExtension.log("Settings loaded from preferences");
        } catch (Exception e) {
            E2RExtension.logError("Failed to load settings: " + e.getMessage());
        }
    }
    
    /**
     * Save settings to Burp Preferences.
     */
    private void saveSettings() {
        try {
            // Save current field value to the active provider's memory variable
            String currentKey = new String(groqApiKeyField.getPassword()).trim();
            String selectedProvider = (String) providerCombo.getSelectedItem();
            if (selectedProvider.equals(PROVIDER_GROQ)) {
                groqApiKey = currentKey;
            } else if (selectedProvider.equals(PROVIDER_GEMINI)) {
                geminiApiKey = currentKey;
            }
            
            // Save provider
            prefs.setString(PREF_PROVIDER, selectedProvider);
            
            // Save Ollama URL
            prefs.setString(PREF_OLLAMA_URL, ollamaUrlField.getText().trim());
            
            // Save API Keys
            prefs.setString(PREF_GROQ_API_KEY, groqApiKey);
            prefs.setString(PREF_GEMINI_API_KEY, geminiApiKey);

            
            // Save model
            prefs.setString(PREF_MODEL, (String) modelCombo.getSelectedItem());
            
            E2RExtension.log("Settings saved to preferences");
            
            JOptionPane.showMessageDialog(this, 
                "Settings saved successfully!\nThey will persist across Burp restarts.",
                "Saved", JOptionPane.INFORMATION_MESSAGE);
                
        } catch (Exception e) {
            E2RExtension.logError("Failed to save settings: " + e.getMessage());
            JOptionPane.showMessageDialog(this, 
                "Failed to save settings: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Update UI based on selected provider.
     */
    private void updateProviderUI() {
        String selectedProvider = (String) providerCombo.getSelectedItem();
        boolean isOllama = PROVIDER_OLLAMA.equals(selectedProvider);
        
        // Save current password value to memory before switching
        if (groqApiKeyField != null) {
            String currentKey = new String(groqApiKeyField.getPassword()).trim();
            if (lastSelectedProvider.equals(PROVIDER_GROQ)) {
                groqApiKey = currentKey;
            } else if (lastSelectedProvider.equals(PROVIDER_GEMINI)) {
                geminiApiKey = currentKey;
            }
        }
        
        // Show/hide Ollama fields
        ollamaUrlLabel.setVisible(isOllama);
        ollamaUrlField.setVisible(isOllama);
        
        // Show/hide Key fields (used by Groq, Gemini, GPT)
        groqApiKeyLabel.setVisible(!isOllama);
        groqApiKeyField.setVisible(!isOllama);
        
        // Change label dynamically
        if (PROVIDER_GROQ.equals(selectedProvider)) {
            groqApiKeyLabel.setText("Groq API Key:");
            groqApiKeyField.setText(groqApiKey);
        } else if (PROVIDER_GEMINI.equals(selectedProvider)) {
            groqApiKeyLabel.setText("Gemini API Key:");
            groqApiKeyField.setText(geminiApiKey);
        }
        
        // Track last selected provider
        lastSelectedProvider = selectedProvider;
        
        // Update model dropdown
        String currentModel = (String) modelCombo.getSelectedItem();
        modelCombo.removeAllItems();
        
        if (isOllama) {
            for (String model : OLLAMA_MODELS) {
                modelCombo.addItem(model);
            }
            if (currentModel != null && isModelForProvider(currentModel, PROVIDER_OLLAMA)) {
                modelCombo.setSelectedItem(currentModel);
            } else {
                modelCombo.setSelectedItem(DEFAULT_OLLAMA_MODEL);
            }
        } else if (PROVIDER_GROQ.equals(selectedProvider)) {
            for (String model : GROQ_MODELS) {
                modelCombo.addItem(model);
            }
            if (currentModel != null && isModelForProvider(currentModel, PROVIDER_GROQ)) {
                modelCombo.setSelectedItem(currentModel);
            } else {
                modelCombo.setSelectedItem(DEFAULT_GROQ_MODEL);
            }
        } else if (PROVIDER_GEMINI.equals(selectedProvider)) {
            for (String model : GEMINI_MODELS) {
                modelCombo.addItem(model);
            }
            if (currentModel != null && isModelForProvider(currentModel, PROVIDER_GEMINI)) {
                modelCombo.setSelectedItem(currentModel);
            } else {
                modelCombo.setSelectedItem(DEFAULT_GEMINI_MODEL);
            }
        }
        
        // Reset connection status
        connectionStatus.setText("");
    }
    
    private boolean isModelForProvider(String model, String provider) {
        String[] models;
        if (provider.equals(PROVIDER_OLLAMA)) models = OLLAMA_MODELS;
        else if (provider.equals(PROVIDER_GROQ)) models = GROQ_MODELS;
        else if (provider.equals(PROVIDER_GEMINI)) models = GEMINI_MODELS;

        else return false;
        
        for (String m : models) {
            if (m.equals(model)) return true;
        }
        return false;
    }
    
    /**
     * Get the currently configured AI provider.
     */
    public AiProvider getAiProvider() {
        String model = (String) modelCombo.getSelectedItem();
        if (model == null) model = DEFAULT_OLLAMA_MODEL;
        
        String selectedProvider = (String) providerCombo.getSelectedItem();
        String apiKey = new String(groqApiKeyField.getPassword()).trim();
        
        if (PROVIDER_GROQ.equals(selectedProvider)) {
            return new GroqProvider(apiKey, model);
        } else if (PROVIDER_GEMINI.equals(selectedProvider)) {
            return new GeminiProvider(apiKey, model);
        } else {
            String url = ollamaUrlField.getText().trim();
            return new OllamaProvider(url, model);
        }
    }
    
    /**
     * Check if using a cloud provider (requires API key).
     */
    public boolean isCloudProvider() {
        String provider = (String) providerCombo.getSelectedItem();
        return PROVIDER_GROQ.equals(provider) || PROVIDER_GEMINI.equals(provider);
    }
    
    /**
     * Get the configured model name.
     */
    public String getSelectedModel() {
        String model = (String) modelCombo.getSelectedItem();
        return model != null ? model : DEFAULT_OLLAMA_MODEL;
    }
    
    /**
     * Get Ollama URL (for backward compatibility).
     */
    public String getOllamaUrl() {
        return ollamaUrlField.getText().trim();
    }
    
    private void loadBlacklistToUI() {
        java.util.Set<String> blacklist = e2r.core.EndpointPattern.getExtensionBlacklist();
        StringBuilder sb = new StringBuilder();
        for (String ext : blacklist) {
            sb.append(ext).append("\n");
        }
        blacklistArea.setText(sb.toString().trim());
    }
    
    private void applyBlacklist() {
        String text = blacklistArea.getText().trim();
        String[] lines = text.split("\n");
        
        java.util.Set<String> newBlacklist = new java.util.HashSet<>();
        
        for (String line : lines) {
            String ext = line.trim().toLowerCase();
            if (!ext.isEmpty()) {
                if (!ext.startsWith(".")) {
                    ext = "." + ext;
                }
                newBlacklist.add(ext);
            }
        }
        
        e2r.core.EndpointPattern.setExtensionBlacklist(newBlacklist);
        E2RExtension.log("Updated extension blacklist: " + newBlacklist.size() + " extensions");
        
        JOptionPane.showMessageDialog(this, 
            "Blacklist updated with " + newBlacklist.size() + " extensions.",
            "Applied", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void loadPathBlacklistToUI() {
        java.util.Set<String> blacklist = e2r.core.EndpointPattern.getPathBlacklist();
        StringBuilder sb = new StringBuilder();
        for (String path : blacklist) {
            sb.append(path).append("\n");
        }
        pathBlacklistArea.setText(sb.toString().trim());
    }
    
    private void applyPathBlacklist() {
        String text = pathBlacklistArea.getText().trim();
        String[] lines = text.split("\n");
        
        java.util.Set<String> newBlacklist = new java.util.HashSet<>();
        
        for (String line : lines) {
            String path = line.trim();
            if (!path.isEmpty()) {
                newBlacklist.add(path);
            }
        }
        
        e2r.core.EndpointPattern.setPathBlacklist(newBlacklist);
        E2RExtension.log("Updated path blacklist: " + newBlacklist.size() + " paths");
        
        JOptionPane.showMessageDialog(this, 
            "Path blacklist updated with " + newBlacklist.size() + " paths.",
            "Applied", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void testConnection() {
        testConnectionButton.setEnabled(false);
        connectionStatus.setText("Testing...");
        connectionStatus.setForeground(Color.GRAY);
        
        executor.submit(() -> {
            try {
                AiProvider provider = getAiProvider();
                boolean connected = provider.testConnection();
                
                SwingUtilities.invokeLater(() -> {
                    if (connected) {
                        connectionStatus.setText("✓ Connected to " + provider.getProviderName());
                        connectionStatus.setForeground(new Color(0, 150, 0));
                    } else {
                        connectionStatus.setText("✗ Connection failed");
                        connectionStatus.setForeground(Color.RED);
                    }
                    testConnectionButton.setEnabled(true);
                });
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    connectionStatus.setText("✗ Error: " + e.getMessage());
                    connectionStatus.setForeground(Color.RED);
                    testConnectionButton.setEnabled(true);
                });
            }
        });
    }
}
