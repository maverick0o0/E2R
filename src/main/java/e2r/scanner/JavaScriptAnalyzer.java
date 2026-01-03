package e2r.scanner;

import e2r.E2RExtension;
import e2r.core.*;
import e2r.core.EndpointPattern.EndpointMatch;
import e2r.core.ContextExtractor.ContextResult;

import java.util.List;

/**
 * Analyzes JavaScript content to extract endpoints.
 * Combines endpoint pattern matching with method detection.
 */
public class JavaScriptAnalyzer {
    
    private final EndpointStore endpointStore;
    
    // Size limits to prevent memory issues
    private static final int MAX_CONTENT_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int CONTEXT_WINDOW = 50; // Lines for method detection
    
    public JavaScriptAnalyzer(EndpointStore endpointStore) {
        this.endpointStore = endpointStore;
    }
    
    /**
     * Analyze JavaScript content and extract endpoints.
     * 
     * @param content The JavaScript source code
     * @param sourceUrl URL of the JavaScript file
     * @param host Host from which the content was served
     */
    public void analyze(String content, String sourceUrl, String host) {
        if (content == null || content.isEmpty()) {
            return;
        }
        
        // Skip if this file has already been processed
        if (endpointStore.isFileProcessed(sourceUrl)) {
            return;
        }
        
        // Mark file as processed
        endpointStore.markFileProcessed(sourceUrl);
        
        // Skip extremely large files
        if (content.length() > MAX_CONTENT_SIZE) {
            E2RExtension.log("Skipping large file (" + content.length() + " bytes): " + sourceUrl);
            return;
        }
        
        // Beautify minified JavaScript for better context extraction
        String processedContent = content;
        if (JsBeautifier.isMinified(content)) {
            E2RExtension.log("Beautifying minified JS: " + sourceUrl);
            processedContent = JsBeautifier.beautify(content);
        }
        
        // Find all endpoint matches
        List<EndpointMatch> matches = EndpointPattern.findEndpoints(processedContent);
        
        E2RExtension.log("Found " + matches.size() + " potential endpoints in " + sourceUrl);
        
        for (EndpointMatch match : matches) {
            try {
                processMatch(match, processedContent, sourceUrl, host);
            } catch (Exception e) {
                E2RExtension.logError("Error processing match: " + e.getMessage());
            }
        }
    }
    
    /**
     * Process a single endpoint match.
     */
    private void processMatch(EndpointMatch match, String content, String sourceUrl, String host) {
        // Extract context around the match for method detection
        ContextResult contextResult = ContextExtractor.extractContext(
            content, match.startPosition, CONTEXT_WINDOW
        );
        
        // Detect HTTP method from context
        String method = MethodDetector.detectMethod(contextResult.context, match.endpoint);
        
        // Get line number for display
        int lineNumber = ContextExtractor.getLineNumber(content, match.startPosition);
        
        // Create endpoint object
        DiscoveredEndpoint endpoint = new DiscoveredEndpoint(
            match.endpoint,
            method,
            host,
            sourceUrl,
            content,
            lineNumber,
            match.startPosition,
            match.endPosition
        );
        
        // Add to store (will be deduplicated)
        boolean added = endpointStore.addEndpoint(endpoint);
        
        if (added) {
            E2RExtension.log(String.format("New endpoint: [%s] %s (line %d)", 
                method, match.endpoint, lineNumber));
        }
    }
}
