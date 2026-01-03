package e2r.ui;

import e2r.core.DiscoveredEndpoint;
import e2r.core.EndpointStore;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Table model for the endpoint results table.
 */
public class EndpointTableModel extends AbstractTableModel {
    
    private static final String[] COLUMN_NAMES = {"#", "Method", "Endpoint", "Host", "Source"};
    private static final Class<?>[] COLUMN_CLASSES = {Integer.class, String.class, String.class, String.class, String.class};
    
    private final List<DiscoveredEndpoint> endpoints = new ArrayList<>();
    
    @Override
    public int getRowCount() {
        return endpoints.size();
    }
    
    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }
    
    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }
    
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return COLUMN_CLASSES[columnIndex];
    }
    
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= endpoints.size()) {
            return null;
        }
        
        DiscoveredEndpoint endpoint = endpoints.get(rowIndex);
        
        switch (columnIndex) {
            case 0: return rowIndex + 1;  // Row number
            case 1: return endpoint.getMethod();
            case 2: return endpoint.getEndpoint();
            case 3: return endpoint.getHost();
            case 4: return getSourceFileName(endpoint.getSourceUrl());
            default: return null;
        }
    }
    
    private String getSourceFileName(String url) {
        if (url == null) return "";
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            String fileName = url.substring(lastSlash + 1);
            // Remove query parameters
            int queryPos = fileName.indexOf('?');
            if (queryPos > 0) {
                fileName = fileName.substring(0, queryPos);
            }
            return fileName;
        }
        return url;
    }
    
    /**
     * Add an endpoint to the table.
     */
    public void addEndpoint(DiscoveredEndpoint endpoint) {
        endpoints.add(endpoint);
        int row = endpoints.size() - 1;
        fireTableRowsInserted(row, row);
    }
    
    /**
     * Get endpoint at specified row.
     */
    public DiscoveredEndpoint getEndpointAt(int row) {
        if (row >= 0 && row < endpoints.size()) {
            return endpoints.get(row);
        }
        return null;
    }
    
    /**
     * Clear all endpoints.
     */
    public void clear() {
        int size = endpoints.size();
        endpoints.clear();
        if (size > 0) {
            fireTableRowsDeleted(0, size - 1);
        }
    }
    
    /**
     * Remove endpoint at specified row.
     */
    public void removeEndpoint(int row) {
        if (row >= 0 && row < endpoints.size()) {
            endpoints.remove(row);
            fireTableRowsDeleted(row, row);
        }
    }
    
    /**
     * Remove multiple endpoints.
     */
    public void removeEndpoints(java.util.List<DiscoveredEndpoint> toRemove) {
        if (toRemove == null || toRemove.isEmpty()) return;
        
        endpoints.removeAll(toRemove);
        fireTableDataChanged();
    }
    
    /**
     * Get all endpoints.
     */
    public List<DiscoveredEndpoint> getEndpoints() {
        return new ArrayList<>(endpoints);
    }
}
