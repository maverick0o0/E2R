package e2r.ui;

import burp.api.montoya.MontoyaApi;

import e2r.core.EndpointStore;

import javax.swing.*;
import java.awt.*;

/**
 * Main tabbed pane for E2R extension.
 * Contains 3 tabs: Live Discovery, AI Workbench, Settings.
 */
public class E2RTab extends JPanel {
    
    private final MontoyaApi api;
    private final EndpointStore endpointStore;
    
    private final LiveDiscoveryPanel liveDiscoveryPanel;
    private final AIWorkbenchPanel aiWorkbenchPanel;
    private final SettingsPanel settingsPanel;
    
    private final JTabbedPane tabbedPane;
    
    public E2RTab(MontoyaApi api, EndpointStore endpointStore) {
        this.api = api;
        this.endpointStore = endpointStore;
        
        setLayout(new BorderLayout());
        
        // Create the settings panel first (needed by other panels)
        settingsPanel = new SettingsPanel(api);
        
        // Create the AI Workbench panel
        aiWorkbenchPanel = new AIWorkbenchPanel(api, settingsPanel);
        
        // Create the Live Discovery panel
        liveDiscoveryPanel = new LiveDiscoveryPanel(api, endpointStore, aiWorkbenchPanel);
        
        // Create tabbed pane
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Live Discovery", createIcon("🔍"), liveDiscoveryPanel, 
            "Real-time endpoint discovery from JavaScript files");
        tabbedPane.addTab("E2R AI Workbench", createIcon("🤖"), aiWorkbenchPanel,
            "Generate HTTP requests using AI");
        tabbedPane.addTab("Settings", createIcon("⚙"), settingsPanel,
            "Configure Ollama and filters");
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // Add status bar
        JPanel statusBar = createStatusBar();
        add(statusBar, BorderLayout.SOUTH);
    }
    
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        
        JLabel statusLabel = new JLabel("● Active");
        statusLabel.setForeground(new Color(0, 150, 0));
        statusBar.add(statusLabel);
        
        statusBar.add(new JSeparator(JSeparator.VERTICAL));
        
        JLabel countLabel = new JLabel("Endpoints: 0");
        statusBar.add(countLabel);
        
        // Update count when endpoints are added
        endpointStore.addListener(endpoint -> {
            SwingUtilities.invokeLater(() -> 
                countLabel.setText("Endpoints: " + endpointStore.size())
            );
        });
        
        return statusBar;
    }
    
    private Icon createIcon(String emoji) {
        // Create a simple text-based icon
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
                g.drawString(emoji, x, y + 14);
            }
            
            @Override
            public int getIconWidth() {
                return 20;
            }
            
            @Override
            public int getIconHeight() {
                return 20;
            }
        };
    }
    
    /**
     * Switch to the AI Workbench tab.
     */
    public void switchToAIWorkbench() {
        tabbedPane.setSelectedIndex(1);
    }
    
    /**
     * Get the Live Discovery panel.
     */
    public LiveDiscoveryPanel getLiveDiscoveryPanel() {
        return liveDiscoveryPanel;
    }
    
    /**
     * Get the AI Workbench panel.
     */
    public AIWorkbenchPanel getAIWorkbenchPanel() {
        return aiWorkbenchPanel;
    }
}
