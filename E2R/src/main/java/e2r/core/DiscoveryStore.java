package e2r.core;

import e2r.core.DiscoveredItem.ItemType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified thread-safe store for all discovered items.
 * Supports endpoints, URLs, secrets, emails, and file references.
 */
public class DiscoveryStore {
    
    // Deduplication keys per type
    private final Map<ItemType, Set<String>> seenKeys = new EnumMap<>(ItemType.class);
    
    // Items per type
    private final Map<ItemType, List<DiscoveredItem>> items = new EnumMap<>(ItemType.class);
    
    // Processed files
    private final Set<String> processedFiles = ConcurrentHashMap.newKeySet();
    
    // Listeners per type
    private final Map<ItemType, List<ItemListener>> listeners = new EnumMap<>(ItemType.class);
    
    // Global listeners (receive all items)
    private final List<ItemListener> globalListeners = new ArrayList<>();
    
    public DiscoveryStore() {
        // Initialize collections for each type
        for (ItemType type : ItemType.values()) {
            seenKeys.put(type, ConcurrentHashMap.newKeySet());
            items.put(type, new ArrayList<>());
            listeners.put(type, new ArrayList<>());
        }
    }
    
    // ===== File Processing =====
    
    public boolean isFileProcessed(String fileUrl) {
        return processedFiles.contains(fileUrl);
    }
    
    public void markFileProcessed(String fileUrl) {
        processedFiles.add(fileUrl);
    }
    
    // ===== Add Items =====
    
    /**
     * Add an item if not already seen.
     * @return true if added (new item), false if duplicate
     */
    public synchronized boolean addItem(DiscoveredItem item) {
        String key = item.getDeduplicationKey();
        ItemType type = item.getType();
        
        if (seenKeys.get(type).add(key)) {
            items.get(type).add(item);
            notifyListeners(type, item);
            return true;
        }
        return false;
    }
    
    /**
     * Add endpoint (convenience wrapper).
     */
    public synchronized boolean addEndpoint(DiscoveredEndpoint endpoint) {
        DiscoveredItem item = DiscoveredItem.fromEndpoint(endpoint);
        return addItem(item);
    }
    
    /**
     * Add secret.
     */
    public synchronized boolean addSecret(SecretPatterns.SecretMatch match,
                                          String sourceUrl, String sourceContent,
                                          String host, int lineNumber) {
        DiscoveredItem item = DiscoveredItem.fromSecret(match, sourceUrl, sourceContent, host, lineNumber);
        return addItem(item);
    }
    
    /**
     * Add email.
     */
    public synchronized boolean addEmail(EmailPatterns.EmailMatch match,
                                         String sourceUrl, String sourceContent,
                                         String host, int lineNumber) {
        DiscoveredItem item = DiscoveredItem.fromEmail(match, sourceUrl, sourceContent, host, lineNumber);
        return addItem(item);
    }
    
    /**
     * Add file.
     */
    public synchronized boolean addFile(FilePatterns.FileMatch match,
                                        String sourceUrl, String sourceContent,
                                        String host, int lineNumber) {
        DiscoveredItem item = DiscoveredItem.fromFile(match, sourceUrl, sourceContent, host, lineNumber);
        return addItem(item);
    }
    
    /**
     * Add parameter.
     */
    public synchronized boolean addParameter(String name, String type,
                                             String sourceUrl, String sourceContent,
                                             String host, int lineNumber,
                                             int startPosition, int endPosition) {
        DiscoveredItem item = DiscoveredItem.fromParameter(name, type, sourceUrl, sourceContent, host, lineNumber, startPosition, endPosition);
        return addItem(item);
    }
    
    /**
     * Add source.
     */
    public synchronized boolean addSource(String name, String category,
                                          String sourceUrl, String sourceContent,
                                          String host, int lineNumber,
                                          int startPosition, int endPosition) {
        DiscoveredItem item = DiscoveredItem.fromSource(name, category, sourceUrl, sourceContent, host, lineNumber, startPosition, endPosition);
        return addItem(item);
    }
    
    /**
     * Add sink.
     */
    public synchronized boolean addSink(String name, String category,
                                        String sourceUrl, String sourceContent,
                                        String host, int lineNumber,
                                        int startPosition, int endPosition) {
        DiscoveredItem item = DiscoveredItem.fromSink(name, category, sourceUrl, sourceContent, host, lineNumber, startPosition, endPosition);
        return addItem(item);
    }
    
    // ===== Get Items =====
    
    public synchronized List<DiscoveredItem> getItems(ItemType type) {
        return new ArrayList<>(items.get(type));
    }
    
    public synchronized List<DiscoveredItem> getEndpoints() {
        return getItems(ItemType.ENDPOINT);
    }
    
    public synchronized List<DiscoveredItem> getUrls() {
        return getItems(ItemType.URL);
    }
    
    public synchronized List<DiscoveredItem> getSecrets() {
        return getItems(ItemType.SECRET);
    }
    
    public synchronized List<DiscoveredItem> getEmails() {
        return getItems(ItemType.EMAIL);
    }
    
    public synchronized List<DiscoveredItem> getFiles() {
        return getItems(ItemType.FILE);
    }
    
    public synchronized List<DiscoveredItem> getParameters() {
        return getItems(ItemType.PARAMETER);
    }
    
    public synchronized List<DiscoveredItem> getSources() {
        return getItems(ItemType.SOURCE);
    }
    
    public synchronized List<DiscoveredItem> getSinks() {
        return getItems(ItemType.SINK);
    }
    
    public synchronized List<DiscoveredItem> getAllItems() {
        List<DiscoveredItem> all = new ArrayList<>();
        for (ItemType type : ItemType.values()) {
            all.addAll(items.get(type));
        }
        return all;
    }
    
    public synchronized DiscoveredItem getItem(ItemType type, int index) {
        List<DiscoveredItem> list = items.get(type);
        if (index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return null;
    }
    
    // ===== Counts =====
    
    public synchronized int getCount(ItemType type) {
        return items.get(type).size();
    }
    
    public synchronized int getEndpointCount() {
        return getCount(ItemType.ENDPOINT);
    }
    
    public synchronized int getUrlCount() {
        return getCount(ItemType.URL);
    }
    
    public synchronized int getSecretCount() {
        return getCount(ItemType.SECRET);
    }
    
    public synchronized int getEmailCount() {
        return getCount(ItemType.EMAIL);
    }
    
    public synchronized int getFileCount() {
        return getCount(ItemType.FILE);
    }
    
    public synchronized int getParameterCount() {
        return getCount(ItemType.PARAMETER);
    }
    
    public synchronized int getSourceCount() {
        return getCount(ItemType.SOURCE);
    }
    
    public synchronized int getSinkCount() {
        return getCount(ItemType.SINK);
    }
    
    public synchronized int getTotalCount() {
        int total = 0;
        for (ItemType type : ItemType.values()) {
            total += items.get(type).size();
        }
        return total;
    }
    
    // ===== Remove Items =====
    
    public synchronized void removeItem(DiscoveredItem item) {
        ItemType type = item.getType();
        if (items.get(type).remove(item)) {
            seenKeys.get(type).remove(item.getDeduplicationKey());
        }
    }
    
    public synchronized void removeItems(ItemType type, List<DiscoveredItem> toRemove) {
        if (toRemove == null || toRemove.isEmpty()) return;
        
        for (DiscoveredItem item : toRemove) {
            seenKeys.get(type).remove(item.getDeduplicationKey());
        }
        items.get(type).removeAll(toRemove);
    }
    
    // ===== Clear =====
    
    public synchronized void clear() {
        for (ItemType type : ItemType.values()) {
            seenKeys.get(type).clear();
            items.get(type).clear();
        }
        processedFiles.clear();
    }
    
    public synchronized void clear(ItemType type) {
        seenKeys.get(type).clear();
        items.get(type).clear();
    }
    
    public void clearProcessedFiles() {
        processedFiles.clear();
    }
    
    // ===== Listeners =====
    
    public void addListener(ItemType type, ItemListener listener) {
        listeners.get(type).add(listener);
    }
    
    public void addGlobalListener(ItemListener listener) {
        globalListeners.add(listener);
    }
    
    public void removeListener(ItemType type, ItemListener listener) {
        listeners.get(type).remove(listener);
    }
    
    public void removeGlobalListener(ItemListener listener) {
        globalListeners.remove(listener);
    }
    
    private void notifyListeners(ItemType type, DiscoveredItem item) {
        // Type-specific listeners
        for (ItemListener listener : listeners.get(type)) {
            try {
                listener.onItemAdded(item);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
        
        // Global listeners
        for (ItemListener listener : globalListeners) {
            try {
                listener.onItemAdded(item);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }
    
    /**
     * Listener interface for new item notifications.
     */
    public interface ItemListener {
        void onItemAdded(DiscoveredItem item);
    }
}
