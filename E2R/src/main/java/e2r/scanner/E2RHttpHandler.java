package e2r.scanner;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.http.message.MimeType;

import e2r.E2RExtension;
import e2r.core.EndpointStore;
import e2r.core.DiscoveryStore;
import e2r.core.DiscoveredEndpoint;
import e2r.core.ParameterPatterns;
import e2r.ui.E2RTab;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Passive HTTP handler that monitors responses for JavaScript content.
 * Uses Montoya API for modern Burp Suite integration.
 */
public class E2RHttpHandler implements HttpHandler {
    
    private final MontoyaApi api;
    private final EndpointStore endpointStore;
    private final DiscoveryStore discoveryStore;
    private final E2RTab mainTab;
    private final ExecutorService executor;
    private final JavaScriptAnalyzer analyzer;
    
    public E2RHttpHandler(MontoyaApi api, EndpointStore endpointStore, DiscoveryStore discoveryStore, E2RTab mainTab) {
        this.api = api;
        this.endpointStore = endpointStore;
        this.discoveryStore = discoveryStore;
        this.mainTab = mainTab;
        this.executor = Executors.newFixedThreadPool(4);
        this.analyzer = new JavaScriptAnalyzer(endpointStore, discoveryStore);
    }
    
    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent request) {
        // We only care about responses, pass requests through
        return RequestToBeSentAction.continueWith(request);
    }
    
    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived response) {
        try {
            // Check if in scope
            if (!api.scope().isInScope(response.initiatingRequest().url())) {
                return ResponseReceivedAction.continueWith(response);
            }
            
            // Extract the request path itself as an endpoint and find query parameters
            processRequestAsEndpoint(response.initiatingRequest());
            
            // Check MIME type
            if (!isJavaScriptResponse(response)) {
                return ResponseReceivedAction.continueWith(response);
            }
            
            // Process asynchronously to avoid blocking
            final String url = response.initiatingRequest().url();
            final String host = response.initiatingRequest().httpService().host();
            final String body = response.bodyToString();
            
            executor.submit(() -> {
                try {
                    analyzer.analyze(body, url, host);
                } catch (Exception e) {
                    E2RExtension.logError("Analysis failed for " + url + ": " + e.getMessage());
                }
            });
            
        } catch (Exception e) {
            E2RExtension.logError("Handler error: " + e.getMessage());
        }
        
        return ResponseReceivedAction.continueWith(response);
    }
    
    /**
     * Check if the response contains JavaScript content.
     */
    private boolean isJavaScriptResponse(HttpResponseReceived response) {
        // Check stated MIME type
        MimeType mimeType = response.statedMimeType();
        if (mimeType == MimeType.SCRIPT) {
            return true;
        }
        
        // Check inferred MIME type
        MimeType inferredMime = response.inferredMimeType();
        if (inferredMime == MimeType.SCRIPT) {
            return true;
        }
        
        // Check URL for .js extension
        String url = response.initiatingRequest().url().toLowerCase();
        if (url.endsWith(".js") || url.contains(".js?")) {
            return true;
        }
        
        // Check if HTML contains <script> tags
        if (mimeType == MimeType.HTML || inferredMime == MimeType.HTML) {
            String body = response.bodyToString();
            if (body != null && body.contains("<script")) {
                return true;
            }
        }
        
        // Check Content-Type header
        String contentType = response.headerValue("Content-Type");
        if (contentType != null) {
            contentType = contentType.toLowerCase();
            if (contentType.contains("javascript") || 
                contentType.contains("application/json") ||
                contentType.contains("text/html")) {
                return true;
            }
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
            if (!e2r.core.EndpointPattern.isValidEndpoint(path)) {
                return;
            }
            
            // Add to endpoint store (which handles duplication checks)
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
            E2RExtension.logError("Failed to extract endpoint from request: " + e.getMessage());
        }
    }
    
    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
