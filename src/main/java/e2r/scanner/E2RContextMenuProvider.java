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
import e2r.core.JsBeautifier;
import e2r.core.EndpointPattern;
import e2r.core.EndpointPattern.EndpointMatch;
import e2r.core.DiscoveredEndpoint;
import e2r.core.ContextExtractor;
import e2r.core.ContextExtractor.ContextResult;
import e2r.core.MethodDetector;

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
    private final ExecutorService executor;
    
    public E2RContextMenuProvider(MontoyaApi api, EndpointStore endpointStore) {
        this.api = api;
        this.endpointStore = endpointStore;
        this.executor = Executors.newFixedThreadPool(4);
    }
    
    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
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
            // Add a disabled menu item explaining no items found
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
                E2RExtension.log("  - JS file: " + item.request().url());
            }
        }
        
        E2RExtension.log("Context menu: " + jsCount + " JS files found");
        
        // Create scan JS menu item
        JMenuItem scanJsMenuItem = new JMenuItem("E2R: Scan for Endpoints" + 
            (jsCount > 0 ? " (" + jsCount + " JS)" : ""));
        scanJsMenuItem.addActionListener(e -> scanSelectedItems(selectedItems, true));
        menuItems.add(scanJsMenuItem);
        
        // Add "Scan ALL" option
        JMenuItem scanAllMenuItem = new JMenuItem("E2R: Scan ALL selected (" + selectedItems.size() + " items)");
        scanAllMenuItem.addActionListener(e -> scanSelectedItems(selectedItems, false));
        menuItems.add(scanAllMenuItem);
        
        return menuItems;
    }
    
    /**
     * Scan selected items.
     * @param jsOnly if true, only scan JavaScript files; if false, scan everything
     */
    private void scanSelectedItems(List<HttpRequestResponse> items, boolean jsOnly) {
        executor.submit(() -> {
            int scanned = 0;
            int found = 0;
            
            E2RExtension.log("Starting scan of " + items.size() + " items (jsOnly=" + jsOnly + ")");
            
            for (HttpRequestResponse item : items) {
                try {
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
                    
                    // Analyze directly (bypass file processed check)
                    int before = endpointStore.size();
                    analyzeContentDirect(body, url, host);
                    int after = endpointStore.size();
                    
                    int newEndpoints = after - before;
                    found += newEndpoints;
                    scanned++;
                    
                    E2RExtension.log("  - Found " + newEndpoints + " new endpoints");
                    
                } catch (Exception e) {
                    E2RExtension.logError("Scan failed: " + e.getMessage());
                }
            }
            
            final int finalScanned = scanned;
            final int finalFound = found;
            
            E2RExtension.log("Scan complete: " + finalScanned + " files, " + finalFound + " new endpoints");
        });
    }
    
    /**
     * Analyze content directly without using the analyzer's file processed check.
     */
    private void analyzeContentDirect(String content, String sourceUrl, String host) {
        if (content == null || content.isEmpty()) return;
        
        // Beautify if minified
        String processedContent = content;
        if (JsBeautifier.isMinified(content)) {
            processedContent = JsBeautifier.beautify(content);
        }
        
        // Find endpoints
        List<EndpointMatch> matches = EndpointPattern.findEndpoints(processedContent);
        
        for (EndpointMatch match : matches) {
            try {
                // Extract context
                ContextResult contextResult = ContextExtractor.extractContext(
                    processedContent, match.startPosition, 50
                );
                
                // Detect method
                String method = MethodDetector.detectMethod(contextResult.context, match.endpoint);
                
                // Get line number
                int lineNumber = ContextExtractor.getLineNumber(processedContent, match.startPosition);
                
                // Create endpoint
                DiscoveredEndpoint endpoint = new DiscoveredEndpoint(
                    match.endpoint,
                    method,
                    host,
                    sourceUrl,
                    processedContent,
                    lineNumber,
                    match.startPosition,
                    match.endPosition
                );
                
                // Add to store
                endpointStore.addEndpoint(endpoint);
                
            } catch (Exception e) {
                E2RExtension.logError("Error processing match: " + e.getMessage());
            }
        }
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
}
