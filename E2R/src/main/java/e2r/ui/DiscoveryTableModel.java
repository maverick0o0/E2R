package e2r.ui;

import e2r.core.DiscoveredItem;
import e2r.core.DiscoveredItem.ItemType;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic table model for all discovery types.
 * Adapts columns based on item type.
 */
public class DiscoveryTableModel extends AbstractTableModel {
    
    private final ItemType itemType;
    private final List<DiscoveredItem> items = new ArrayList<>();
    
    // Column definitions per type
    private final String[] columnNames;
    private final Class<?>[] columnClasses;
    
    public DiscoveryTableModel(ItemType itemType) {
        this.itemType = itemType;
        
        switch (itemType) {
            case ENDPOINT:
                columnNames = new String[]{"#", "Method", "Endpoint", "Host", "Source"};
                columnClasses = new Class<?>[]{Integer.class, String.class, String.class, String.class, String.class};
                break;
            case URL:
                columnNames = new String[]{"#", "URL", "Host", "Source"};
                columnClasses = new Class<?>[]{Integer.class, String.class, String.class, String.class};
                break;
            case SECRET:
                columnNames = new String[]{"#", "Type", "Value", "Source", "Line"};
                columnClasses = new Class<?>[]{Integer.class, String.class, String.class, String.class, Integer.class};
                break;
            case EMAIL:
                columnNames = new String[]{"#", "Email", "Source", "Line"};
                columnClasses = new Class<?>[]{Integer.class, String.class, String.class, Integer.class};
                break;
            case FILE:
                columnNames = new String[]{"#", "Category", "Filename", "Source", "Line"};
                columnClasses = new Class<?>[]{Integer.class, String.class, String.class, String.class, Integer.class};
                break;
            case PARAMETER:
                columnNames = new String[]{"#", "Name", "Type", "Source", "Line"};
                columnClasses = new Class<?>[]{Integer.class, String.class, String.class, String.class, Integer.class};
                break;
            case SOURCE:
                columnNames = new String[]{"#", "Category", "Source Code", "Source File", "Line"};
                columnClasses = new Class<?>[]{Integer.class, String.class, String.class, String.class, Integer.class};
                break;
            case SINK:
                columnNames = new String[]{"#", "Category", "Sink Code", "Source File", "Line"};
                columnClasses = new Class<?>[]{Integer.class, String.class, String.class, String.class, Integer.class};
                break;
            default:
                columnNames = new String[]{"#", "Value", "Source"};
                columnClasses = new Class<?>[]{Integer.class, String.class, String.class};
        }
    }
    
    @Override
    public int getRowCount() {
        return items.size();
    }
    
    @Override
    public int getColumnCount() {
        return columnNames.length;
    }
    
    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }
    
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnClasses[columnIndex];
    }
    
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= items.size()) {
            return null;
        }
        
        DiscoveredItem item = items.get(rowIndex);
        
        switch (itemType) {
            case ENDPOINT:
                return getEndpointValue(item, rowIndex, columnIndex);
            case URL:
                return getUrlValue(item, rowIndex, columnIndex);
            case SECRET:
                return getSecretValue(item, rowIndex, columnIndex);
            case EMAIL:
                return getEmailValue(item, rowIndex, columnIndex);
            case FILE:
                return getFileValue(item, rowIndex, columnIndex);
            case PARAMETER:
                return getParameterValue(item, rowIndex, columnIndex);
            case SOURCE:
            case SINK:
                return getSourceSinkValue(item, rowIndex, columnIndex);
            default:
                return getDefaultValue(item, rowIndex, columnIndex);
        }
    }
    
    private Object getEndpointValue(DiscoveredItem item, int row, int col) {
        switch (col) {
            case 0: return row + 1;
            case 1: return item.getMethod();
            case 2: return item.getValue();
            case 3: return item.getHost();
            case 4: return getSourceFileName(item.getSourceUrl());
            default: return null;
        }
    }
    
    private Object getUrlValue(DiscoveredItem item, int row, int col) {
        switch (col) {
            case 0: return row + 1;
            case 1: return item.getValue();
            case 2: return item.getHost();
            case 3: return getSourceFileName(item.getSourceUrl());
            default: return null;
        }
    }
    
    private Object getSecretValue(DiscoveredItem item, int row, int col) {
        switch (col) {
            case 0: return row + 1;
            case 1: return item.getSecretType();
            case 2: return maskSecret(item.getValue());
            case 3: return getSourceFileName(item.getSourceUrl());
            case 4: return item.getLineNumber();
            default: return null;
        }
    }
    
    private Object getEmailValue(DiscoveredItem item, int row, int col) {
        switch (col) {
            case 0: return row + 1;
            case 1: return item.getValue();
            case 2: return getSourceFileName(item.getSourceUrl());
            case 3: return item.getLineNumber();
            default: return null;
        }
    }
    
    private Object getFileValue(DiscoveredItem item, int row, int col) {
        switch (col) {
            case 0: return row + 1;
            case 1: return item.getFileCategory();
            case 2: return item.getValue();
            case 3: return getSourceFileName(item.getSourceUrl());
            case 4: return item.getLineNumber();
            default: return null;
        }
    }
    
    private Object getParameterValue(DiscoveredItem item, int row, int col) {
        switch (col) {
            case 0: return row + 1;
            case 1: return item.getValue();
            case 2: return item.getParameterType();
            case 3: return getSourceFileName(item.getSourceUrl());
            case 4: return item.getLineNumber() > 0 ? item.getLineNumber() : null;
            default: return null;
        }
    }
    
    private Object getSourceSinkValue(DiscoveredItem item, int row, int col) {
        switch (col) {
            case 0: return row + 1;
            case 1: return item.getSourceSinkCategory();
            case 2: return item.getValue();
            case 3: return getSourceFileName(item.getSourceUrl());
            case 4: return item.getLineNumber() > 0 ? item.getLineNumber() : null;
            default: return null;
        }
    }
    
    private Object getDefaultValue(DiscoveredItem item, int row, int col) {
        switch (col) {
            case 0: return row + 1;
            case 1: return item.getValue();
            case 2: return getSourceFileName(item.getSourceUrl());
            default: return null;
        }
    }
    
    private String getSourceFileName(String url) {
        if (url == null) return "";
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            String fileName = url.substring(lastSlash + 1);
            int queryPos = fileName.indexOf('?');
            if (queryPos > 0) {
                fileName = fileName.substring(0, queryPos);
            }
            return fileName;
        }
        return url;
    }
    
    /**
     * Mask secret values for display (show first 8 chars + asterisks).
     */
    private String maskSecret(String secret) {
        if (secret == null) return "";
        if (secret.length() <= 12) {
            return secret.substring(0, Math.min(4, secret.length())) + "****";
        }
        return secret.substring(0, 8) + "****" + secret.substring(secret.length() - 4);
    }
    
    /**
     * Add an item to the table.
     */
    public void addItem(DiscoveredItem item) {
        items.add(item);
        int row = items.size() - 1;
        fireTableRowsInserted(row, row);
    }
    
    /**
     * Get item at specified row.
     */
    public DiscoveredItem getItemAt(int row) {
        if (row >= 0 && row < items.size()) {
            return items.get(row);
        }
        return null;
    }
    
    /**
     * Clear all items.
     */
    public void clear() {
        int size = items.size();
        items.clear();
        if (size > 0) {
            fireTableRowsDeleted(0, size - 1);
        }
    }
    
    /**
     * Remove multiple items.
     */
    public void removeItems(List<DiscoveredItem> toRemove) {
        if (toRemove == null || toRemove.isEmpty()) return;
        items.removeAll(toRemove);
        fireTableDataChanged();
    }
    
    /**
     * Get all items.
     */
    public List<DiscoveredItem> getItems() {
        return new ArrayList<>(items);
    }
    
    /**
     * Get the item type for this model.
     */
    public ItemType getItemType() {
        return itemType;
    }
}
