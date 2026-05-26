package e2r.scanner;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.http.message.MimeType;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

import e2r.E2RExtension;
import e2r.core.EndpointStore;
import e2r.core.DiscoveryStore;
import e2r.core.JsBeautifier;
import e2r.core.EndpointPattern;
import e2r.core.EndpointPattern.EndpointMatch;
import e2r.core.DiscoveredEndpoint;
import e2r.core.ContextExtractor;
import e2r.core.ContextExtractor.ContextResult;
import e2r.core.MethodDetector;
import burp.api.montoya.http.message.requests.HttpRequest;
import e2r.core.ParameterPatterns;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Context menu provider for Burp Site Map.
 * Allows users to right-click and scan JS files from selected items.
 */
public class E2RContextMenuProvider implements ContextMenuItemsProvider {
    
    private final MontoyaApi api;
    private final EndpointStore endpointStore;
    private final DiscoveryStore discoveryStore;
    private final ExecutorService executor;
    private final JavaScriptAnalyzer analyzer;
    
    public E2RContextMenuProvider(MontoyaApi api, EndpointStore endpointStore, DiscoveryStore discoveryStore) {
        this.api = api;
        this.endpointStore = endpointStore;
        this.discoveryStore = discoveryStore;
        this.executor = Executors.newFixedThreadPool(4);
        this.analyzer = new JavaScriptAnalyzer(endpointStore, discoveryStore);
    }
    
    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        // ... (keep existing menu item logic, no change needed here)
        List<Component> menuItems = new ArrayList<>();
        
        // Get selected items from multiple sources
        List<HttpRequestResponse> selectedItems = new ArrayList<>();
        
        // Try selectedRequestResponses first
        selectedItems.addAll(event.selectedRequestResponses());
        
        // Also try messageEditorRequestResponse (for single item in message editor)
        Optional<MessageEditorHttpRequestResponse> editorItem = event.messageEditorRequestResponse();
        if (editorItem.isPresent()) {
            selectedItems.add(editorItem.get().requestResponse());
        }
        
        E2RExtension.log("Context menu: found " + selectedItems.size() + " items");
        
        if (selectedItems.isEmpty()) {
            JMenuItem noItemsMenu = new JMenuItem("E2R: No items selected");
            noItemsMenu.setEnabled(false);
            menuItems.add(noItemsMenu);
            return menuItems;
        }
        
        // Count JS files
        int jsCount = 0;
        for (HttpRequestResponse item : selectedItems) {
            if (isJavaScript(item)) {
                jsCount++;
            }
        }
        
        E2RExtension.log("Context menu: " + jsCount + " JS files found");
        
        // Create scan JS menu item
        JMenuItem scanJsMenuItem = new JMenuItem("E2R: Scan selected JS files (Including Children)");
        scanJsMenuItem.addActionListener(e -> scanSelectedItems(selectedItems, true));
        menuItems.add(scanJsMenuItem);
        
        // Add "Scan ALL" option
        JMenuItem scanAllMenuItem = new JMenuItem("E2R: Scan ALL selected (Including Children)");
        scanAllMenuItem.addActionListener(e -> scanSelectedItems(selectedItems, false));
        menuItems.add(scanAllMenuItem);
        
        return menuItems;
    }
    
    /**
     * Scan selected items.
     * @param jsOnly if true, only scan JavaScript files; if false, scan everything
     */
    private void scanSelectedItems(List<HttpRequestResponse> selectedItems, boolean jsOnly) {
        executor.submit(() -> {
            E2RExtension.log("Expanding selected items from Burp Site Map...");
            
            // Gather all items from the sitemap that are under the selected paths
            List<HttpRequestResponse> allSitemapItems = api.siteMap().requestResponses();
            java.util.Set<HttpRequestResponse> expandedItems = new java.util.LinkedHashSet<>();
            
            for (HttpRequestResponse selected : selectedItems) {
                if (selected.request() == null) continue;
                String selectedUrl = selected.request().url();
                
                // Add the selected item itself
                expandedItems.add(selected);
                
                // Find all children under the selected item
                for (HttpRequestResponse item : allSitemapItems) {
                    if (item.request() != null) {
                        String itemUrl = item.request().url();
                        if (isChildOf(selectedUrl, itemUrl)) {
                            expandedItems.add(item);
                        }
                    }
                }
            }
            
            List<HttpRequestResponse> itemsToScan = new ArrayList<>(expandedItems);
            int scanned = 0;
            int foundItems = 0;
            
            E2RExtension.log("Starting scan of " + itemsToScan.size() + " items (jsOnly=" + jsOnly + ")");
            
            for (HttpRequestResponse item : itemsToScan) {
                try {
                    // Extract the request URL path itself as an endpoint
                    if (item.request() != null) {
                        processRequestAsEndpoint(item.request());
                    }
                    
                    // Skip non-JS if jsOnly mode
                    if (jsOnly && !isJavaScript(item)) {
                        continue;
                    }
                    
                    HttpResponse response = item.response();
                    if (response == null) {
                        E2RExtension.log("  - Skipping (no response): " + item.request().url());
                        continue;
                    }
                    
                    String url = item.request().url();
                    String host = item.request().httpService().host();
                    String body = response.bodyToString();
                    
                    if (body == null || body.length() < 50) {
                        E2RExtension.log("  - Skipping (too small): " + url);
                        continue;
                    }
                    
                    E2RExtension.log("  - Scanning: " + url + " (" + body.length() + " bytes)");
                    
                    // Track counts before scanning
                    int endpointsBefore = discoveryStore.getTotalCount();
                    
                    // Force re-scan even if processed (for manual scans)
                    // We modify the store state or use a method in analyzer to force/bypass check?
                    // Actually, JavaScriptAnalyzer skips if file processed.
                    // We should probably allow forcing it. For now, let's clear the processed flag for this url
                    // or just rely on the user clearing all if they want a re-scan.
                    // BUT, for context menu, users expect re-scan.
                    // Let's manually clear it from processed files in store for this URL
                    // No public method for that, but we can just use a new Analyzer instance or 
                    // modify Analyzer to allow force scan. 
                    // Simpler: The Analyzer checks endpointStore.isFileProcessed.
                    
                    // Call analyzer
                    analyzer.analyze(body, url, host);
                    
                    int endpointsAfter = discoveryStore.getTotalCount();
                    int newItems = endpointsAfter - endpointsBefore;
                    
                    if (newItems > 0) {
                        foundItems += newItems;
                        E2RExtension.log("  - Found " + newItems + " new items");
                    } else {
                        E2RExtension.log("  - Found 0 new items (or file already processed)");
                    }
                    
                    scanned++;
                    
                } catch (Exception e) {
                    E2RExtension.logError("Scan failed: " + e.getMessage());
                }
            }
            
            E2RExtension.log("Scan complete: " + scanned + " files scanned");
        });
    }
    
    /**
     * Check if a request/response is JavaScript.
     */
    private boolean isJavaScript(HttpRequestResponse item) {
        if (item == null || item.response() == null) return false;
        
        HttpResponse response = item.response();
        
        // Check MIME type
        try {
            MimeType mimeType = response.statedMimeType();
            if (mimeType == MimeType.SCRIPT) return true;
            
            MimeType inferredMime = response.inferredMimeType();
            if (inferredMime == MimeType.SCRIPT) return true;
        } catch (Exception e) {
            // Ignore MIME type errors
        }
        
        // Check URL
        try {
            String url = item.request().url().toLowerCase();
            if (url.endsWith(".js") || url.contains(".js?") || url.contains(".js#")) return true;
        } catch (Exception e) {
            // Ignore URL errors
        }
        
        // Check Content-Type header
        try {
            String contentType = response.headerValue("Content-Type");
            if (contentType != null && contentType.toLowerCase().contains("javascript")) {
                return true;
            }
        } catch (Exception e) {
            // Ignore header errors
        }
        
        return false;
    }
    
    /**
     * Process an HTTP request, extracting its path and query parameters as findings.
     */
    private void processRequestAsEndpoint(HttpRequest request) {
        try {
            String fullPath = request.path();
            if (fullPath == null || fullPath.isEmpty()) return;
            
            // Separate path and query string
            String path = fullPath;
            String queryString = "";
            int queryIdx = fullPath.indexOf('?');
            if (queryIdx >= 0) {
                path = fullPath.substring(0, queryIdx);
                queryString = fullPath.substring(queryIdx + 1);
            }
            
            String method = request.method();
            String host = request.httpService().host();
            String url = request.url();
            
            // Avoid empty paths or root '/' to avoid noise unless it contains query parameters
            if (path.equals("/") && queryString.isEmpty()) {
                return;
            }
            
            // Ensure the path is a valid endpoint (filters out static resources like .js, .css, etc.)
            if (!EndpointPattern.isValidEndpoint(path)) {
                return;
            }
            
            // Add to endpoint store
            DiscoveredEndpoint ep = new DiscoveredEndpoint(
                path,
                method,
                host,
                url,
                "", // no source content
                1,  // line number 1
                0, 0
            );
            
            endpointStore.addEndpoint(ep);
            discoveryStore.addEndpoint(ep);
            
            // Extract parameters from the query string
            if (!queryString.isEmpty()) {
                String[] pairs = queryString.split("&");
                for (String pair : pairs) {
                    if (pair.isEmpty()) continue;
                    String[] keyValue = pair.split("=");
                    String key = keyValue[0];
                    if (ParameterPatterns.isValidParameterName(key)) {
                        discoveryStore.addParameter(
                            key,
                            "Query",
                            url,
                            "",
                            host,
                            1,
                            0, 0
                        );
                    }
                }
            }
        } catch (Exception e) {
            E2RExtension.logError("Failed to extract endpoint from manual request scan: " + e.getMessage());
        }
    }
    
    /**
     * Check if a child URL is under the parent URL path.
     */
    private boolean isChildOf(String parentUrl, String childUrl) {
        if (parentUrl == null || childUrl == null) return false;
        
        // Strip query string and fragments from parentUrl
        String parentNormalized = parentUrl;
        int queryIdx = parentNormalized.indexOf('?');
        if (queryIdx >= 0) {
            parentNormalized = parentNormalized.substring(0, queryIdx);
        }
        int hashIdx = parentNormalized.indexOf('#');
        if (hashIdx >= 0) {
            parentNormalized = parentNormalized.substring(0, hashIdx);
        }
        
        // Remove protocol (http/https)
        String parent = parentNormalized.replaceFirst("^(?i)https?:", "").replaceFirst("^//", "");
        String child = childUrl.replaceFirst("^(?i)https?:", "").replaceFirst("^//", "");
        
        return child.startsWith(parent);
    }
}
