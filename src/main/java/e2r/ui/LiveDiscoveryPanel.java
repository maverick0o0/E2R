package e2r.ui;

import burp.api.montoya.MontoyaApi;

import e2r.core.ContextExtractor;
import e2r.core.ContextExtractor.ContextResult;
import e2r.core.DiscoveredEndpoint;
import e2r.core.EndpointStore;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import javax.swing.SortOrder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Tab 1: Live Discovery Panel
 * Master-detail view with results table and context viewer.
 */
public class LiveDiscoveryPanel extends JPanel {
    
    private final MontoyaApi api;
    private final EndpointStore endpointStore;
    private final AIWorkbenchPanel aiWorkbenchPanel;
    
    private final JTable resultsTable;
    private final EndpointTableModel tableModel;
    private final ContextViewer contextViewer;
    private JTextField searchField;
    private TableRowSorter<EndpointTableModel> rowSorter;
    
    // Filter state
    private boolean showEndpoints = true;
    private boolean showLinks = true;
    
    // Method colors
    private static final Color GET_COLOR = new Color(80, 180, 80);
    private static final Color POST_COLOR = new Color(80, 130, 220);
    private static final Color PUT_COLOR = new Color(220, 160, 50);
    private static final Color DELETE_COLOR = new Color(220, 80, 80);
    private static final Color PATCH_COLOR = new Color(160, 80, 220);
    
    // Context display mode
    private static final int CONTEXT_LINES = 100;
    
    public LiveDiscoveryPanel(MontoyaApi api, EndpointStore endpointStore, AIWorkbenchPanel aiWorkbenchPanel) {
        this.api = api;
        this.endpointStore = endpointStore;
        this.aiWorkbenchPanel = aiWorkbenchPanel;
        
        setLayout(new BorderLayout());
        
        // Top toolbar
        JPanel toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);
        
        // Create split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(300);
        
        // Top: Results table
        tableModel = new EndpointTableModel();
        resultsTable = new JTable(tableModel);
        
        // Setup row sorter for filtering and sorting
        rowSorter = new TableRowSorter<>(tableModel);
        resultsTable.setRowSorter(rowSorter);
        
        // Default sort by Endpoint column (alphabetical)
        rowSorter.setSortKeys(java.util.List.of(
            new RowSorter.SortKey(2, SortOrder.ASCENDING)  // Column 2 = Endpoint
        ));
        
        configureTable();
        
        JScrollPane tableScroll = new JScrollPane(resultsTable);
        splitPane.setTopComponent(tableScroll);
        
        // Bottom: Context viewer
        contextViewer = new ContextViewer();
        splitPane.setBottomComponent(contextViewer);
        
        add(splitPane, BorderLayout.CENTER);
        
        // Listen for new endpoints
        endpointStore.addListener(endpoint -> {
            SwingUtilities.invokeLater(() -> tableModel.addEndpoint(endpoint));
        });
        
        // Handle row selection
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = resultsTable.getSelectedRow();
                if (selectedRow >= 0) {
                    showContextForRow(selectedRow);
                }
            }
        });
        
        // Setup context menu
        setupContextMenu();
    }
    
    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        // Status indicator
        JLabel statusLabel = new JLabel("● Active");
        statusLabel.setForeground(new Color(0, 150, 0));
        toolbar.add(statusLabel);
        
        toolbar.add(Box.createHorizontalStrut(15));
        
        // Stats label
        JLabel statsLabel = new JLabel("Endpoints: 0");
        toolbar.add(statsLabel);
        
        // Update stats when endpoints change
        endpointStore.addListener(endpoint -> {
            SwingUtilities.invokeLater(() -> 
                statsLabel.setText("Endpoints: " + endpointStore.size())
            );
        });
        
        toolbar.add(Box.createHorizontalStrut(15));
        
        // Filter checkboxes
        JCheckBox endpointsCheckbox = new JCheckBox("Endpoints", true);
        endpointsCheckbox.setToolTipText("Show API paths (e.g., /api/users)");
        endpointsCheckbox.addActionListener(e -> {
            showEndpoints = endpointsCheckbox.isSelected();
            applyFilters();
        });
        toolbar.add(endpointsCheckbox);
        
        JCheckBox linksCheckbox = new JCheckBox("Links", true);
        linksCheckbox.setToolTipText("Show full URLs (http://, //)");
        linksCheckbox.addActionListener(e -> {
            showLinks = linksCheckbox.isSelected();
            applyFilters();
        });
        toolbar.add(linksCheckbox);
        
        toolbar.add(Box.createHorizontalStrut(15));
        
        // Search field
        toolbar.add(new JLabel("Search:"));
        searchField = new JTextField(12);
        searchField.setToolTipText("Filter by text");
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                applyFilters();
            }
        });
        toolbar.add(searchField);
        
        toolbar.add(Box.createHorizontalStrut(10));
        
        // Delete button
        JButton deleteBtn = new JButton("Delete");
        deleteBtn.setForeground(new Color(180, 60, 60));
        deleteBtn.setToolTipText("Remove selected false positive");
        deleteBtn.addActionListener(e -> deleteSelectedEndpoint());
        toolbar.add(deleteBtn);
        
        // Clear All button
        JButton clearBtn = new JButton("Clear All");
        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Clear all endpoints and reset processed files?",
                "Confirm Clear", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                tableModel.clear();
                endpointStore.clear();
                contextViewer.clear();
            }
        });
        toolbar.add(clearBtn);
        
        toolbar.add(Box.createHorizontalStrut(15));
        
        // Export button
        JButton exportBtn = new JButton("Export");
        exportBtn.setToolTipText("Save endpoints to JSON file");
        exportBtn.addActionListener(e -> exportEndpoints());
        toolbar.add(exportBtn);
        
        // Import button
        JButton importBtn = new JButton("Import");
        importBtn.setToolTipText("Load endpoints from JSON file");
        importBtn.addActionListener(e -> importEndpoints());
        toolbar.add(importBtn);
        
        return toolbar;
    }
    
    private void applyFilters() {
        List<RowFilter<EndpointTableModel, Object>> filters = new ArrayList<>();
        
        // Type filter (Endpoints vs Links)
        if (!showEndpoints || !showLinks) {
            filters.add(new RowFilter<EndpointTableModel, Object>() {
                @Override
                public boolean include(Entry<? extends EndpointTableModel, ? extends Object> entry) {
                    String endpoint = (String) entry.getValue(2); // Endpoint column
                    boolean isLink = endpoint.startsWith("http://") || 
                                     endpoint.startsWith("https://") || 
                                     endpoint.startsWith("//");
                    
                    if (isLink) {
                        return showLinks;
                    } else {
                        return showEndpoints;
                    }
                }
            });
        }
        
        // Text search filter
        String searchText = searchField.getText().trim().toLowerCase();
        if (!searchText.isEmpty()) {
            filters.add(new RowFilter<EndpointTableModel, Object>() {
                @Override
                public boolean include(Entry<? extends EndpointTableModel, ? extends Object> entry) {
                    for (int i = 0; i < entry.getValueCount(); i++) {
                        Object value = entry.getValue(i);
                        if (value != null && value.toString().toLowerCase().contains(searchText)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
        
        // Apply combined filter
        if (filters.isEmpty()) {
            rowSorter.setRowFilter(null);
        } else if (filters.size() == 1) {
            rowSorter.setRowFilter(filters.get(0));
        } else {
            rowSorter.setRowFilter(RowFilter.andFilter(filters));
        }
    }
    
    private void deleteSelectedEndpoint() {
        int[] selectedRows = resultsTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Select endpoint(s) to delete.", 
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Confirm if deleting many
        if (selectedRows.length > 10) {
            int confirm = JOptionPane.showConfirmDialog(this, 
                "Are you sure you want to delete " + selectedRows.length + " endpoints?",
                "Confirm Bulk Delete", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
        }
        
        // Collect endpoints to delete
        List<DiscoveredEndpoint> toDelete = new ArrayList<>();
        for (int viewRow : selectedRows) {
            int modelRow = resultsTable.convertRowIndexToModel(viewRow);
            DiscoveredEndpoint endpoint = tableModel.getEndpointAt(modelRow);
            if (endpoint != null) {
                toDelete.add(endpoint);
            }
        }
        
        // Remove from store and model
        for (DiscoveredEndpoint endpoint : toDelete) {
            endpointStore.removeEndpoint(endpoint);
        }
        tableModel.removeEndpoints(toDelete);
        
        contextViewer.clear();
    }
    
    private void configureTable() {
        resultsTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        resultsTable.setRowHeight(24);
        resultsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // Column widths
        resultsTable.getColumnModel().getColumn(0).setMaxWidth(50);   // #
        resultsTable.getColumnModel().getColumn(1).setMaxWidth(80);   // Method
        resultsTable.getColumnModel().getColumn(1).setMinWidth(60);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(300); // Endpoint
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(150); // Host
        resultsTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Source
        
        // Method column renderer (color-coded)
        resultsTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (value != null && !isSelected) {
                    String method = value.toString();
                    switch (method) {
                        case "GET": c.setForeground(GET_COLOR); break;
                        case "POST": c.setForeground(POST_COLOR); break;
                        case "PUT": c.setForeground(PUT_COLOR); break;
                        case "DELETE": c.setForeground(DELETE_COLOR); break;
                        case "PATCH": c.setForeground(PATCH_COLOR); break;
                        default: c.setForeground(table.getForeground()); break;
                    }
                    setFont(getFont().deriveFont(Font.BOLD));
                }
                
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });
    }
    
    private void setupContextMenu() {
        JPopupMenu contextMenu = new JPopupMenu();
        
        JMenuItem copyUrl = new JMenuItem("Copy URL");
        copyUrl.addActionListener(e -> copySelectedUrl());
        contextMenu.add(copyUrl);
        
        JMenuItem copySourceUrl = new JMenuItem("Copy Source URL");
        copySourceUrl.addActionListener(e -> copySelectedSourceUrl());
        contextMenu.add(copySourceUrl);
        
        contextMenu.addSeparator();
        
        JMenuItem sendToWorkbench = new JMenuItem("Send to E2R AI Workbench");
        sendToWorkbench.setFont(sendToWorkbench.getFont().deriveFont(Font.BOLD));
        sendToWorkbench.addActionListener(e -> sendToAIWorkbench());
        contextMenu.add(sendToWorkbench);
        
        contextMenu.addSeparator();
        
        JMenuItem deleteItem = new JMenuItem("Delete (False Positive)");
        deleteItem.setForeground(new Color(180, 60, 60));
        deleteItem.addActionListener(e -> deleteSelectedEndpoint());
        contextMenu.add(deleteItem);
        
        resultsTable.setComponentPopupMenu(contextMenu);
        
        // Double-click to send to workbench
        resultsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    sendToAIWorkbench();
                }
            }
        });
    }
    
    private void showContextForRow(int viewRow) {
        // Convert view index to model index
        int modelRow = resultsTable.convertRowIndexToModel(viewRow);
        DiscoveredEndpoint endpoint = tableModel.getEndpointAt(modelRow);
        if (endpoint == null) return;
        
        String sourceContent = endpoint.getSourceContent();
        
        // If no source content (loaded from persistence), show message
        if (sourceContent == null || sourceContent.isEmpty()) {
            contextViewer.displayCode(
                endpoint.getSourceUrl(),
                "// Source content not available (loaded from persistence)\n// Re-browse this URL to load content",
                1, 1
            );
            return;
        }
        
        // Extract ±100 lines context around the match
        ContextResult context = ContextExtractor.extractContext(
            sourceContent,
            endpoint.getMatchStartPosition(),
            CONTEXT_LINES
        );
        
        contextViewer.displayCode(
            endpoint.getSourceUrl(),
            context.context,
            context.matchLine,
            context.startLine
        );
    }
    
    private void copySelectedUrl() {
        int row = resultsTable.getSelectedRow();
        if (row < 0) return;
        
        int modelRow = resultsTable.convertRowIndexToModel(row);
        DiscoveredEndpoint endpoint = tableModel.getEndpointAt(modelRow);
        if (endpoint != null) {
            copyToClipboard(endpoint.getEndpoint());
        }
    }
    
    private void copySelectedSourceUrl() {
        int row = resultsTable.getSelectedRow();
        if (row < 0) return;
        
        int modelRow = resultsTable.convertRowIndexToModel(row);
        DiscoveredEndpoint endpoint = tableModel.getEndpointAt(modelRow);
        if (endpoint != null) {
            copyToClipboard(endpoint.getSourceUrl());
        }
    }
    
    private void sendToAIWorkbench() {
        int row = resultsTable.getSelectedRow();
        if (row < 0) return;
        
        int modelRow = resultsTable.convertRowIndexToModel(row);
        DiscoveredEndpoint endpoint = tableModel.getEndpointAt(modelRow);
        if (endpoint != null) {
            // Check if source content is available
            if (endpoint.getSourceContent() == null || endpoint.getSourceContent().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "Source content not available (loaded from persistence).\n" +
                    "Re-browse the source URL to load content.",
                    "Content Not Available", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            aiWorkbenchPanel.loadEndpoint(endpoint);
            
            // Switch to AI Workbench tab
            Container parent = getParent();
            while (parent != null && !(parent instanceof JTabbedPane)) {
                parent = parent.getParent();
            }
            if (parent instanceof JTabbedPane) {
                ((JTabbedPane) parent).setSelectedIndex(1);
            }
        }
    }
    
    private void copyToClipboard(String text) {
        if (text == null) return;
        Toolkit.getDefaultToolkit()
            .getSystemClipboard()
            .setContents(new java.awt.datatransfer.StringSelection(text), null);
    }
    
    private void exportEndpoints() {
        List<DiscoveredEndpoint> endpoints = endpointStore.getEndpoints();
        if (endpoints.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No endpoints to export.", 
                "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Endpoints");
        fileChooser.setSelectedFile(new File("e2r_endpoints.json"));
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".json")) {
                file = new File(file.getAbsolutePath() + ".json");
            }
            
            try {
                // Create export data (without full content to save space)
                List<ExportedEndpoint> exportData = new ArrayList<>();
                for (DiscoveredEndpoint ep : endpoints) {
                    exportData.add(new ExportedEndpoint(
                        ep.getMethod(),
                        ep.getEndpoint(),
                        ep.getHost(),
                        ep.getSourceUrl(),
                        ep.getMatchLineNumber()
                    ));
                }
                
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(exportData);
                Files.writeString(file.toPath(), json);
                
                JOptionPane.showMessageDialog(this, 
                    "Exported " + endpoints.size() + " endpoints to:\n" + file.getAbsolutePath(),
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "Export failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void importEndpoints() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Import Endpoints");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json"));
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            
            try {
                String json = Files.readString(file.toPath());
                Gson gson = new Gson();
                List<ExportedEndpoint> importData = gson.fromJson(json, 
                    new TypeToken<List<ExportedEndpoint>>(){}.getType());
                
                int imported = 0;
                for (ExportedEndpoint exp : importData) {
                    DiscoveredEndpoint endpoint = new DiscoveredEndpoint(
                        exp.endpoint,
                        exp.method,
                        exp.host,
                        exp.sourceUrl,
                        "", // No content for imported endpoints
                        exp.lineNumber,
                        0, 0
                    );
                    
                    if (endpointStore.addEndpoint(endpoint)) {
                        tableModel.addEndpoint(endpoint);
                        imported++;
                    }
                }
                
                JOptionPane.showMessageDialog(this, 
                    "Imported " + imported + " new endpoints (" + (importData.size() - imported) + " duplicates skipped)",
                    "Import Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "Import failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Simple data class for JSON export/import (without source content).
     */
    private static class ExportedEndpoint {
        String method;
        String endpoint;
        String host;
        String sourceUrl;
        int lineNumber;
        
        ExportedEndpoint(String method, String endpoint, String host, String sourceUrl, int lineNumber) {
            this.method = method;
            this.endpoint = endpoint;
            this.host = host;
            this.sourceUrl = sourceUrl;
            this.lineNumber = lineNumber;
        }
    }
}
