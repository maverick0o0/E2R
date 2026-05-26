package e2r.scanner;

import e2r.E2RExtension;
import e2r.core.*;
import e2r.core.EndpointPattern.EndpointMatch;
import e2r.core.ContextExtractor.ContextResult;
import e2r.core.SecretPatterns.SecretMatch;
import e2r.core.EmailPatterns.EmailMatch;
import e2r.core.FilePatterns.FileMatch;

import java.util.List;

/**
 * Analyzes JavaScript content to extract endpoints, secrets, emails, and file references.
 * Combines endpoint pattern matching with method detection and additional pattern scanning.
 */
public class JavaScriptAnalyzer {
    
    private final EndpointStore endpointStore;
    private final DiscoveryStore discoveryStore;
    
    // Size limits to prevent memory issues
    private static final int MAX_CONTENT_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int CONTEXT_WINDOW = 50; // Lines for method detection
    
    /**
     * Constructor with legacy EndpointStore (for backward compatibility).
     */
    public JavaScriptAnalyzer(EndpointStore endpointStore) {
        this.endpointStore = endpointStore;
        this.discoveryStore = null;
    }
    
    /**
     * Constructor with new DiscoveryStore.
     */
    public JavaScriptAnalyzer(EndpointStore endpointStore, DiscoveryStore discoveryStore) {
        this.endpointStore = endpointStore;
        this.discoveryStore = discoveryStore;
    }
    
    /**
     * Analyze JavaScript content and extract endpoints, secrets, emails, and files.
     * 
     * @param content The JavaScript source code
     * @param sourceUrl URL of the JavaScript file
     * @param host Host from which the content was served
     */
    public void analyze(String content, String sourceUrl, String host) {
        if (content == null || content.isEmpty()) {
            return;
        }
        
        // Mark file as processed, but don't return early if already processed.
        // This allows re-scanning if the user triggers it manually.
        endpointStore.markFileProcessed(sourceUrl);
        if (discoveryStore != null) {
            discoveryStore.markFileProcessed(sourceUrl);
        }
        
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
        analyzeEndpoints(processedContent, sourceUrl, host);
        
        // If DiscoveryStore is available, also scan for secrets, emails, files, parameters, sources, and sinks
        if (discoveryStore != null) {
            analyzeSecrets(processedContent, sourceUrl, host);
            analyzeEmails(processedContent, sourceUrl, host);
            analyzeFiles(processedContent, sourceUrl, host);
            analyzeParameters(processedContent, sourceUrl, host);
            analyzeSources(processedContent, sourceUrl, host);
            analyzeSinks(processedContent, sourceUrl, host);
        }
    }
    
    /**
     * Analyze content for endpoints/URLs.
     */
    private void analyzeEndpoints(String content, String sourceUrl, String host) {
        List<EndpointMatch> matches = EndpointPattern.findEndpoints(content);
        
        E2RExtension.log("Found " + matches.size() + " potential endpoints in " + sourceUrl);
        
        for (EndpointMatch match : matches) {
            try {
                processEndpointMatch(match, content, sourceUrl, host);
            } catch (Exception e) {
                E2RExtension.logError("Error processing endpoint match: " + e.getMessage());
            }
        }
    }
    
    /**
     * Process a single endpoint match.
     */
    private void processEndpointMatch(EndpointMatch match, String content, String sourceUrl, String host) {
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
        
        // Add to legacy store
        boolean added = endpointStore.addEndpoint(endpoint);
        
        // Also add to new DiscoveryStore if available
        if (discoveryStore != null) {
            discoveryStore.addEndpoint(endpoint);
        }
        
        if (added) {
            E2RExtension.log(String.format("New endpoint: [%s] %s (line %d)", 
                method, match.endpoint, lineNumber));
        }
    }
    
    /**
     * Analyze content for secrets.
     */
    private void analyzeSecrets(String content, String sourceUrl, String host) {
        List<SecretMatch> matches = SecretPatterns.findSecrets(content);
        
        if (!matches.isEmpty()) {
            E2RExtension.log("Found " + matches.size() + " potential secrets in " + sourceUrl);
        }
        
        for (SecretMatch match : matches) {
            try {
                int lineNumber = ContextExtractor.getLineNumber(content, match.startPosition);
                boolean added = discoveryStore.addSecret(match, sourceUrl, content, host, lineNumber);
                
                if (added) {
                    E2RExtension.log(String.format("New secret: [%s] %s... (line %d)", 
                        match.secretType, 
                        truncateValue(match.value, 20),
                        lineNumber));
                }
            } catch (Exception e) {
                E2RExtension.logError("Error processing secret match: " + e.getMessage());
            }
        }
    }
    
    /**
     * Analyze content for email addresses.
     */
    private void analyzeEmails(String content, String sourceUrl, String host) {
        List<EmailMatch> matches = EmailPatterns.findEmails(content);
        
        if (!matches.isEmpty()) {
            E2RExtension.log("Found " + matches.size() + " potential emails in " + sourceUrl);
        }
        
        for (EmailMatch match : matches) {
            try {
                int lineNumber = ContextExtractor.getLineNumber(content, match.startPosition);
                boolean added = discoveryStore.addEmail(match, sourceUrl, content, host, lineNumber);
                
                if (added) {
                    E2RExtension.log(String.format("New email: %s (line %d)", 
                        match.email, lineNumber));
                }
            } catch (Exception e) {
                E2RExtension.logError("Error processing email match: " + e.getMessage());
            }
        }
    }
    
    /**
     * Analyze content for sensitive file references.
     */
    private void analyzeFiles(String content, String sourceUrl, String host) {
        List<FileMatch> matches = FilePatterns.findFiles(content);
        
        if (!matches.isEmpty()) {
            E2RExtension.log("Found " + matches.size() + " file references in " + sourceUrl);
        }
        
        for (FileMatch match : matches) {
            try {
                int lineNumber = ContextExtractor.getLineNumber(content, match.startPosition);
                boolean added = discoveryStore.addFile(match, sourceUrl, content, host, lineNumber);
                
                if (added) {
                    E2RExtension.log(String.format("New file reference: [%s] %s (line %d)", 
                        match.category.displayName, match.filename, lineNumber));
                }
            } catch (Exception e) {
                E2RExtension.logError("Error processing file match: " + e.getMessage());
            }
        }
    }
    
    
    /**
     * Analyze content for parameters.
     */
    private void analyzeParameters(String content, String sourceUrl, String host) {
        List<ParameterPatterns.ParameterMatch> matches = new java.util.ArrayList<>(
            ParameterPatterns.findParameters(content)
        );
        
        // Extract parameters from endpoints as well
        List<EndpointPattern.EndpointMatch> endpointMatches = EndpointPattern.findEndpoints(content);
        for (EndpointPattern.EndpointMatch epMatch : endpointMatches) {
            matches.addAll(ParameterPatterns.extractFromUrl(epMatch.endpoint, epMatch.startPosition));
        }
        
        if (!matches.isEmpty()) {
            E2RExtension.log("Found " + matches.size() + " potential parameters in " + sourceUrl);
        }
        
        for (ParameterPatterns.ParameterMatch match : matches) {
            try {
                int lineNumber = ContextExtractor.getLineNumber(content, match.startPosition);
                boolean added = discoveryStore.addParameter(
                    match.name,
                    match.type,
                    sourceUrl,
                    content,
                    host,
                    lineNumber,
                    match.startPosition,
                    match.endPosition
                );
                
                if (added) {
                    E2RExtension.log(String.format("New parameter: [%s] %s (line %d)", 
                        match.type, match.name, lineNumber));
                }
            } catch (Exception e) {
                E2RExtension.logError("Error processing parameter match: " + e.getMessage());
            }
        }
    }
    
    
    /**
     * Analyze content for DOM XSS sources.
     */
    private void analyzeSources(String content, String sourceUrl, String host) {
        List<SourceSinkPatterns.PatternMatch> matches = SourceSinkPatterns.findSources(content);
        
        if (!matches.isEmpty()) {
            E2RExtension.log("Found " + matches.size() + " DOM sources in " + sourceUrl);
        }
        
        for (SourceSinkPatterns.PatternMatch match : matches) {
            try {
                int lineNumber = ContextExtractor.getLineNumber(content, match.startPosition);
                boolean added = discoveryStore.addSource(
                    match.value,
                    match.category,
                    sourceUrl,
                    content,
                    host,
                    lineNumber,
                    match.startPosition,
                    match.endPosition
                );
                if (added) {
                    E2RExtension.log(String.format("New DOM Source: [%s] %s (line %d)", 
                        match.category, match.value, lineNumber));
                }
            } catch (Exception e) {
                E2RExtension.logError("Error processing DOM source match: " + e.getMessage());
            }
        }
    }
    
    /**
     * Analyze content for DOM XSS sinks.
     */
    private void analyzeSinks(String content, String sourceUrl, String host) {
        List<SourceSinkPatterns.PatternMatch> matches = SourceSinkPatterns.findSinks(content);
        
        if (!matches.isEmpty()) {
            E2RExtension.log("Found " + matches.size() + " DOM sinks in " + sourceUrl);
        }
        
        for (SourceSinkPatterns.PatternMatch match : matches) {
            try {
                int lineNumber = ContextExtractor.getLineNumber(content, match.startPosition);
                boolean added = discoveryStore.addSink(
                    match.value,
                    match.category,
                    sourceUrl,
                    content,
                    host,
                    lineNumber,
                    match.startPosition,
                    match.endPosition
                );
                if (added) {
                    E2RExtension.log(String.format("New DOM Sink: [%s] %s (line %d)", 
                        match.category, match.value, lineNumber));
                }
            } catch (Exception e) {
                E2RExtension.logError("Error processing DOM sink match: " + e.getMessage());
            }
        }
    }
    
    /**
     * Truncate a value for logging.
     */
    private String truncateValue(String value, int maxLength) {
        if (value == null) return "";
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }
}
