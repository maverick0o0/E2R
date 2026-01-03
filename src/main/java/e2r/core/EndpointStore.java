package e2r.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe store for discovered endpoints with deduplication.
 * Ensures unique {Method + URL + Host} combinations per session.
 */
public class EndpointStore {
    
    private final Set<String> seenKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> processedFiles = ConcurrentHashMap.newKeySet();
    private final List<DiscoveredEndpoint> endpoints = new ArrayList<>();
    private final List<EndpointStoreListener> listeners = new ArrayList<>();
    
    /**
     * Check if a file has already been processed.
     */
    public boolean isFileProcessed(String fileUrl) {
        return processedFiles.contains(fileUrl);
    }
    
    /**
     * Mark a file as processed.
     */
    public void markFileProcessed(String fileUrl) {
        processedFiles.add(fileUrl);
    }
    
    /**
     * Add an endpoint if not already seen.
     * 
     * @param endpoint The endpoint to add
     * @return true if added (new endpoint), false if duplicate
     */
    public synchronized boolean addEndpoint(DiscoveredEndpoint endpoint) {
        String key = endpoint.getDeduplicationKey();
        if (seenKeys.add(key)) {
            endpoints.add(endpoint);
            notifyListeners(endpoint);
            return true;
        }
        return false;
    }
    
    /**
     * Remove an endpoint by index.
     */
    public synchronized void removeEndpoint(int index) {
        if (index >= 0 && index < endpoints.size()) {
            DiscoveredEndpoint removed = endpoints.remove(index);
            seenKeys.remove(removed.getDeduplicationKey());
        }
    }
    
    /**
     * Remove an endpoint.
     */
    public synchronized void removeEndpoint(DiscoveredEndpoint endpoint) {
        if (endpoints.remove(endpoint)) {
            seenKeys.remove(endpoint.getDeduplicationKey());
        }
    }
    
    /**
     * Get all endpoints.
     */
    public synchronized List<DiscoveredEndpoint> getEndpoints() {
        return new ArrayList<>(endpoints);
    }
    
    /**
     * Get endpoint by index.
     */
    public synchronized DiscoveredEndpoint getEndpoint(int index) {
        if (index >= 0 && index < endpoints.size()) {
            return endpoints.get(index);
        }
        return null;
    }
    
    /**
     * Get endpoint count.
     */
    public synchronized int size() {
        return endpoints.size();
    }
    
    /**
     * Clear all endpoints.
     */
    public synchronized void clear() {
        seenKeys.clear();
        endpoints.clear();
        processedFiles.clear();
    }
    
    /**
     * Clear only processed files (to allow re-scanning).
     */
    public void clearProcessedFiles() {
        processedFiles.clear();
    }
    
    /**
     * Import endpoints from a list (for import feature).
     */
    public synchronized void importEndpoints(List<DiscoveredEndpoint> importedEndpoints) {
        for (DiscoveredEndpoint endpoint : importedEndpoints) {
            String key = endpoint.getDeduplicationKey();
            if (seenKeys.add(key)) {
                endpoints.add(endpoint);
                notifyListeners(endpoint);
            }
        }
    }
    
    /**
     * Check if an endpoint has been seen.
     */
    public boolean contains(String method, String endpoint, String host) {
        String key = method + "|" + endpoint + "|" + host;
        return seenKeys.contains(key);
    }
    
    /**
     * Add a listener for new endpoints.
     */
    public void addListener(EndpointStoreListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove a listener.
     */
    public void removeListener(EndpointStoreListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyListeners(DiscoveredEndpoint endpoint) {
        for (EndpointStoreListener listener : listeners) {
            try {
                listener.onEndpointAdded(endpoint);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }
    
    /**
     * Listener interface for new endpoint notifications.
     */
    public interface EndpointStoreListener {
        void onEndpointAdded(DiscoveredEndpoint endpoint);
    }
}
