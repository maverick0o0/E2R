package e2r.ui;

import burp.api.montoya.MontoyaApi;

import e2r.core.ContextExtractor;
import e2r.core.ContextExtractor.ContextResult;
import e2r.core.DiscoveredEndpoint;
import e2r.core.DiscoveredItem;
import e2r.core.DiscoveredItem.ItemType;
import e2r.core.EndpointStore;
import e2r.core.DiscoveryStore;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Tab 1: Live Discovery Panel
 * Master-detail view with tabbed results and context viewer.
 */
public class LiveDiscoveryPanel extends JPanel {
    
    private final MontoyaApi api;
    private final EndpointStore endpointStore;
    private final DiscoveryStore discoveryStore;
    private final AIWorkbenchPanel aiWorkbenchPanel;
    
    // UI Components
    private final JTabbedPane resultsTabPane;
    private final ContextViewer contextViewer;
    private JTextField searchField;
    private JButton addParameterBtn;
    
    // Tables and Models per ItemType
    private final Map<ItemType, JTable> tables = new EnumMap<>(ItemType.class);
    private final Map<ItemType, DiscoveryTableModel> models = new EnumMap<>(ItemType.class);
    private final Map<ItemType, TableRowSorter<DiscoveryTableModel>> sorters = new EnumMap<>(ItemType.class);
    
    // Method colors
    private static final Color GET_COLOR = new Color(80, 180, 80);
    private static final Color POST_COLOR = new Color(80, 130, 220);
    private static final Color PUT_COLOR = new Color(220, 160, 50);
    private static final Color DELETE_COLOR = new Color(220, 80, 80);
    private static final Color PATCH_COLOR = new Color(160, 80, 220);
    
    // Context display mode
    private static final int CONTEXT_LINES = 100;
    
    /**
     * Constructor for new version with DiscoveryStore
     */
    public LiveDiscoveryPanel(MontoyaApi api, EndpointStore endpointStore, 
                              DiscoveryStore discoveryStore, AIWorkbenchPanel aiWorkbenchPanel) {
        this.api = api;
        this.endpointStore = endpointStore;
        this.discoveryStore = discoveryStore;
        this.aiWorkbenchPanel = aiWorkbenchPanel;
        
        setLayout(new BorderLayout());
        
        // Top toolbar
        JPanel toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);
        
        // Create split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(300);
        
        // Top: Tabbed Results
        resultsTabPane = new JTabbedPane();
        
        // Initialize tables for each type
        createTableForType(ItemType.ENDPOINT, "Endpoints");
        createTableForType(ItemType.URL, "URLs");
        createTableForType(ItemType.SECRET, "Secrets");
        createTableForType(ItemType.EMAIL, "Emails");
        createTableForType(ItemType.FILE, "Files");
        createTableForType(ItemType.PARAMETER, "Parameters");
        createTableForType(ItemType.SOURCE, "Sources");
        createTableForType(ItemType.SINK, "Sinks");
        
        splitPane.setTopComponent(resultsTabPane);
        
        // Bottom: Context viewer
        contextViewer = new ContextViewer();
        splitPane.setBottomComponent(contextViewer);
        
        add(splitPane, BorderLayout.CENTER);
        
        // Setup listeners
        setupStoreListeners();
        setupTabChangeListeners();
    }
    
    /**
     * Legacy constructor (for backward compatibility during refactor)
     */
    public LiveDiscoveryPanel(MontoyaApi api, EndpointStore endpointStore, AIWorkbenchPanel aiWorkbenchPanel) {
        this(api, endpointStore, new DiscoveryStore(), aiWorkbenchPanel);
    }
    
    private void createTableForType(ItemType type, String title) {
        DiscoveryTableModel model = new DiscoveryTableModel(type);
        models.put(type, model);
        
        JTable table = new JTable(model);
        tables.put(type, table);
        
        // Setup sorter
        TableRowSorter<DiscoveryTableModel> sorter = new TableRowSorter<>(model);
        sorters.put(type, sorter);
        table.setRowSorter(sorter);
        
        configureTable(table, type);
        
        // Add table to tab inside scroll pane
        JScrollPane scrollPane = new JScrollPane(table);
        resultsTabPane.addTab(String.format("%s (0)", title), scrollPane);
        
        // Handle row selection
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                // Clear selection in other tables to avoid confusion
                if (table.getSelectedRow() >= 0) {
                    clearOtherTableSelections(type);
                    showContextForSelectedRow();
                }
            }
        });
        
        // Context menu
        setupContextMenu(table);
    }
    
    private void clearOtherTableSelections(ItemType activeType) {
        for (Map.Entry<ItemType, JTable> entry : tables.entrySet()) {
            if (entry.getKey() != activeType) {
                entry.getValue().clearSelection();
            }
        }
    }
    
    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        // Search field
        toolbar.add(new JLabel("Search:"));
        searchField = new JTextField(20);
        searchField.setToolTipText("Filter by text");
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                applyFilters();
            }
        });
        toolbar.add(searchField);
        
        toolbar.add(Box.createHorizontalStrut(15));
        
        // Delete button
        JButton deleteBtn = new JButton("Delete");
        deleteBtn.setForeground(new Color(180, 60, 60));
        deleteBtn.setToolTipText("Remove selected items");
        deleteBtn.addActionListener(e -> deleteSelectedItems());
        toolbar.add(deleteBtn);
        
        // Add Parameter button
        addParameterBtn = new JButton("Add Parameter");
        addParameterBtn.setEnabled(false); // starts on Endpoints tab
        addParameterBtn.setToolTipText("Manually add a parameter");
        addParameterBtn.addActionListener(e -> showAddParameterDialog());
        toolbar.add(addParameterBtn);
        
        // Clear All button
        JButton clearBtn = new JButton("Clear All");
        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Clear all discovered items and reset processed files?",
                "Confirm Clear", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                discoveryStore.clear();
                for (DiscoveryTableModel model : models.values()) {
                    model.clear();
                }
                contextViewer.clear();
                updateTabTitles();
            }
        });
        toolbar.add(clearBtn);
        
        toolbar.add(Box.createHorizontalStrut(15));
        
        // Export button
        JButton exportBtn = new JButton("Export");
        exportBtn.setToolTipText("Save items to JSON file");
        exportBtn.addActionListener(e -> exportItems());
        toolbar.add(exportBtn);
        
        // Import button
        JButton importBtn = new JButton("Import");
        importBtn.setToolTipText("Load items from JSON file");
        importBtn.addActionListener(e -> importItems());
        toolbar.add(importBtn);
        
        return toolbar;
    }
    
    private void setupStoreListeners() {
        // Global listener for count updates and table population
        discoveryStore.addGlobalListener(item -> {
            SwingUtilities.invokeLater(() -> {
                ItemType type = item.getType();
                DiscoveryTableModel model = models.get(type);
                if (model != null) {
                    model.addItem(item);
                    updateTabTitle(type);
                }
            });
        });
    }
    
    private void setupTabChangeListeners() {
        resultsTabPane.addChangeListener((ChangeEvent e) -> {
            // Apply search filter to the newly selected tab
            applyFilters();
            
            // Enable/disable Add Parameter button
            if (addParameterBtn != null) {
                ItemType activeType = getTypeForTabIndex(resultsTabPane.getSelectedIndex());
                addParameterBtn.setEnabled(activeType == ItemType.PARAMETER);
            }
        });
    }
    
    private void updateTabTitles() {
        for (ItemType type : ItemType.values()) {
            updateTabTitle(type);
        }
    }
    
    private void updateTabTitle(ItemType type) {
        int index = getTabIndexForType(type);
        if (index >= 0) {
             String title = type.displayName + " (" + discoveryStore.getCount(type) + ")";
             resultsTabPane.setTitleAt(index, title);
        }
    }
    
    private int getTabIndexForType(ItemType type) {
        // Warning: This assumes tabs are added in the specific order defined in constructor
        switch (type) {
            case ENDPOINT: return 0;
            case URL: return 1;
            case SECRET: return 2;
            case EMAIL: return 3;
            case FILE: return 4;
            case PARAMETER: return 5;
            case SOURCE: return 6;
            case SINK: return 7;
            default: return -1;
        }
    }
    
    private ItemType getTypeForTabIndex(int index) {
        switch (index) {
            case 0: return ItemType.ENDPOINT;
            case 1: return ItemType.URL;
            case 2: return ItemType.SECRET;
            case 3: return ItemType.EMAIL;
            case 4: return ItemType.FILE;
            case 5: return ItemType.PARAMETER;
            case 6: return ItemType.SOURCE;
            case 7: return ItemType.SINK;
            default: return null;
        }
    }
    
    private void applyFilters() {
        String searchText = searchField.getText().trim().toLowerCase();
        
        // Apply to all sorters
        for (TableRowSorter<DiscoveryTableModel> sorter : sorters.values()) {
            if (searchText.isEmpty()) {
                sorter.setRowFilter(null);
            } else {
                sorter.setRowFilter(new RowFilter<DiscoveryTableModel, Object>() {
                    @Override
                    public boolean include(Entry<? extends DiscoveryTableModel, ? extends Object> entry) {
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
        }
    }
    
    private void deleteSelectedItems() {
        JTable currentTable = getSelectedTable();
        if (currentTable == null) return;
        
        int[] selectedRows = currentTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Select item(s) to delete.", 
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Confirm if deleting many
        if (selectedRows.length > 10) {
            int confirm = JOptionPane.showConfirmDialog(this, 
                "Are you sure you want to delete " + selectedRows.length + " items?",
                "Confirm Bulk Delete", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
        }
        
        ItemType currentType = getTypeForTabIndex(resultsTabPane.getSelectedIndex());
        DiscoveryTableModel model = models.get(currentType);
        
        // Collect items to delete
        List<DiscoveredItem> toDelete = new ArrayList<>();
        for (int viewRow : selectedRows) {
            int modelRow = currentTable.convertRowIndexToModel(viewRow);
            DiscoveredItem item = model.getItemAt(modelRow);
            if (item != null) {
                toDelete.add(item);
            }
        }
        
        // Remove from store and model
        discoveryStore.removeItems(currentType, toDelete);
        model.removeItems(toDelete);
        
        // Sync legacy store if deleting endpoints
        if (currentType == ItemType.ENDPOINT) {
             for (DiscoveredItem item : toDelete) {
                 // Best effort removal from legacy store using loop
                 // (Ideally legacy store would support this better, but this is a migration phase)
                 // endpointStore.removeEndpoint(...); 
             }
        }
        
        contextViewer.clear();
        updateTabTitle(currentType);
    }
    
    private JTable getSelectedTable() {
        ItemType type = getTypeForTabIndex(resultsTabPane.getSelectedIndex());
        return tables.get(type);
    }
    
    private void configureTable(JTable table, ItemType type) {
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // Common column width for '#'
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        
        // Specific configs
        if (type == ItemType.ENDPOINT) {
            table.getColumnModel().getColumn(1).setMaxWidth(80); // Method
            table.getColumnModel().getColumn(1).setMinWidth(60);
            
            // Method color renderer
            table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
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
                    } else {
                        c.setForeground(table.getSelectionForeground());
                    }
                    setHorizontalAlignment(SwingConstants.CENTER);
                    return c;
                }
            });
        }
        
        if (type == ItemType.PARAMETER) {
            table.getColumnModel().getColumn(2).setMaxWidth(100); // Type
            table.getColumnModel().getColumn(2).setMinWidth(70);
            
            table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                        boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    
                    if (value != null && !isSelected) {
                        String pType = value.toString();
                        switch (pType) {
                            case "Query": c.setForeground(GET_COLOR); break;
                            case "Body": c.setForeground(POST_COLOR); break;
                            case "JS Variable": c.setForeground(PATCH_COLOR); break;
                            case "Manual": c.setForeground(PUT_COLOR); break;
                            default: c.setForeground(table.getForeground()); break;
                        }
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else {
                        c.setForeground(table.getSelectionForeground());
                    }
                    setHorizontalAlignment(SwingConstants.CENTER);
                    return c;
                }
            });
        }
        
        if (type == ItemType.SOURCE) {
            table.getColumnModel().getColumn(1).setMaxWidth(150); // Category
            table.getColumnModel().getColumn(1).setMinWidth(100);
            
            table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                        boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    
                    if (value != null && !isSelected) {
                        c.setForeground(PUT_COLOR); // Orange
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else {
                        c.setForeground(table.getSelectionForeground());
                    }
                    setHorizontalAlignment(SwingConstants.CENTER);
                    return c;
                }
            });
        }
        
        if (type == ItemType.SINK) {
            table.getColumnModel().getColumn(1).setMaxWidth(150); // Category
            table.getColumnModel().getColumn(1).setMinWidth(100);
            
            table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                        boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    
                    if (value != null && !isSelected) {
                        c.setForeground(DELETE_COLOR); // Red
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else {
                        c.setForeground(table.getSelectionForeground());
                    }
                    setHorizontalAlignment(SwingConstants.CENTER);
                    return c;
                }
            });
        }
    }
    
    private void setupContextMenu(JTable table) {
        JPopupMenu contextMenu = new JPopupMenu();
        
        JMenuItem copyValue = new JMenuItem("Copy Value");
        copyValue.addActionListener(e -> copySelectedValue(table));
        contextMenu.add(copyValue);
        
        JMenuItem copySource = new JMenuItem("Copy Source URL");
        copySource.addActionListener(e -> copySelectedSourceUrl(table));
        contextMenu.add(copySource);
        
        JMenuItem copyAllValues = new JMenuItem("Copy All Values");
        copyAllValues.addActionListener(e -> copyAllTableValues(table));
        contextMenu.add(copyAllValues);
        
        contextMenu.addSeparator();
        
        // Only show "Send to Workbench" for endpoints
        JMenuItem sendToWorkbench = new JMenuItem("Send to E2R AI Workbench");
        sendToWorkbench.setFont(sendToWorkbench.getFont().deriveFont(Font.BOLD));
        sendToWorkbench.addActionListener(e -> sendSelectedToWorkbench(table));
        
        // Disable sendToWorkbench if table type is not ENDPOINT
        ItemType tableType = ((DiscoveryTableModel) table.getModel()).getItemType();
        if (tableType != ItemType.ENDPOINT) {
            sendToWorkbench.setEnabled(false);
        }
        contextMenu.add(sendToWorkbench);
        
        contextMenu.addSeparator();
        
        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.setForeground(new Color(180, 60, 60));
        deleteItem.addActionListener(e -> deleteSelectedItems());
        contextMenu.add(deleteItem);
        
        table.setComponentPopupMenu(contextMenu);
        
        // Double-click to send to workbench (only endpoints)
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    sendSelectedToWorkbench(table);
                }
            }
        });
    }
    
    private void showContextForSelectedRow() {
        JTable table = getSelectedTable();
        if (table == null) return;
        
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return;
        
        int modelRow = table.convertRowIndexToModel(viewRow);
        DiscoveryTableModel model = (DiscoveryTableModel) table.getModel();
        DiscoveredItem item = model.getItemAt(modelRow);
        
        if (item == null) return;
        
        if (item.getParameterType() != null && item.getParameterType().equals("Manual")) {
            contextViewer.displayCode(
                item.getSourceUrl(),
                "// Manually added parameter\n// No source context available.",
                1, 1
            );
            return;
        }
        
        String sourceContent = item.getSourceContent();
        
        if (sourceContent == null || sourceContent.isEmpty()) {
            contextViewer.displayCode(
                item.getSourceUrl(),
                "// Source content not available (loaded from persistence)\n// Re-browse this URL to load content",
                1, 1
            );
            return;
        }
        
        // Extract context around the match
        ContextResult context = ContextExtractor.extractContext(
            sourceContent,
            item.getStartPosition(),
            CONTEXT_LINES
        );
        
        contextViewer.displayCode(
            item.getSourceUrl(),
            context.context,
            context.matchLine,
            context.startLine
        );
    }
    
    private void copySelectedValue(JTable table) {
        DiscoveredItem item = getSelectedItem(table);
        if (item != null) {
            copyToClipboard(item.getValue());
        }
    }
    
    private void copySelectedSourceUrl(JTable table) {
        DiscoveredItem item = getSelectedItem(table);
        if (item != null) {
            copyToClipboard(item.getSourceUrl());
        }
    }
    
    private void copyAllTableValues(JTable table) {
        DiscoveryTableModel model = (DiscoveryTableModel) table.getModel();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < model.getRowCount(); i++) {
            DiscoveredItem item = model.getItemAt(i);
            if (item != null) {
                sb.append(item.getValue()).append("\n");
            }
        }
        if (sb.length() > 0) {
            copyToClipboard(sb.toString().trim());
        }
    }
    
    private void sendSelectedToWorkbench(JTable table) {
        DiscoveredItem item = getSelectedItem(table);
        if (item != null && item.getType() == ItemType.ENDPOINT) {
            // Convert to DiscoveredEndpoint for compatibility with AI Workbench
            // (Note: This reconstructs it, so we lose original reference, but content is same)
            DiscoveredEndpoint endpoint = new DiscoveredEndpoint(
                item.getValue(),
                item.getMethod(),
                item.getHost(),
                item.getSourceUrl(),
                item.getSourceContent(),
                item.getLineNumber(),
                item.getStartPosition(),
                item.getEndPosition()
            );
            
            aiWorkbenchPanel.loadEndpoint(endpoint);
            
            // Switch tab
            Container parent = getParent();
            while (parent != null && !(parent instanceof JTabbedPane)) {
                parent = parent.getParent();
            }
            if (parent instanceof JTabbedPane) {
                ((JTabbedPane) parent).setSelectedIndex(1);
            }
        }
    }
    
    private DiscoveredItem getSelectedItem(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        int modelRow = table.convertRowIndexToModel(row);
        return ((DiscoveryTableModel) table.getModel()).getItemAt(modelRow);
    }
    
    private void copyToClipboard(String text) {
        if (text == null) return;
        Toolkit.getDefaultToolkit()
            .getSystemClipboard()
            .setContents(new java.awt.datatransfer.StringSelection(text), null);
    }
    
    private void exportItems() {
        if (discoveryStore.getTotalCount() == 0) {
            JOptionPane.showMessageDialog(this, "No items to export.", 
                "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Discovered Items");
        fileChooser.setSelectedFile(new File("e2r_discovery.json"));
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".json")) {
                file = new File(file.getAbsolutePath() + ".json");
            }
            
            try {
                // Export all items
                List<DiscoveredItem> allItems = discoveryStore.getAllItems();
                
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(allItems);
                Files.writeString(file.toPath(), json);
                
                JOptionPane.showMessageDialog(this, 
                    "Exported " + allItems.size() + " items to:\n" + file.getAbsolutePath(),
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "Export failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void importItems() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Import Discovered Items");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json"));
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            
            try {
                String json = Files.readString(file.toPath());
                Gson gson = new Gson();
                List<DiscoveredItem> importData = gson.fromJson(json, 
                    new TypeToken<List<DiscoveredItem>>(){}.getType());
                
                int imported = 0;
                for (DiscoveredItem item : importData) {
                    if (discoveryStore.addItem(item)) {
                        imported++;
                    }
                }
                
                JOptionPane.showMessageDialog(this, 
                    "Imported " + imported + " new items (" + (importData.size() - imported) + " duplicates skipped)",
                    "Import Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "Import failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void showAddParameterDialog() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
        
        panel.add(new JLabel("Parameter Name:"));
        JTextField nameField = new JTextField(20);
        panel.add(nameField);
        
        panel.add(new JLabel("Parameter Type:"));
        String[] types = {"Query", "Body", "JS Variable", "Manual"};
        JComboBox<String> typeCombo = new JComboBox<>(types);
        typeCombo.setSelectedItem("Manual");
        panel.add(typeCombo);
        
        panel.add(new JLabel("Source (Optional):"));
        JTextField sourceField = new JTextField("Manually Added");
        panel.add(sourceField);
        
        int result = JOptionPane.showConfirmDialog(this, panel, 
            "Add Parameter", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String type = (String) typeCombo.getSelectedItem();
            String source = sourceField.getText().trim();
            
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Parameter name cannot be empty.", 
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (source.isEmpty()) {
                source = "Manually Added";
            }
            
            boolean added = discoveryStore.addParameter(
                name,
                type,
                source,
                null,
                "localhost",
                0,
                0, 0
            );
            
            if (!added) {
                JOptionPane.showMessageDialog(this, "Parameter already exists.", 
                    "Duplicate", JOptionPane.WARNING_MESSAGE);
            }
        }
    }
}
