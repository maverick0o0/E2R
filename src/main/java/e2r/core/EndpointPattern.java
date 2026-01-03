package e2r.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Master Regex Engine for endpoint extraction.
 * Synthesized from LinkFinder, GAP, and BurpJSLinkFinder patterns.
 */
public class EndpointPattern {
    
    // Combined master regex pattern
    private static final String ENDPOINT_REGEX = 
        "(?:\"|'|`)" +
        "(" +
            // Pattern 1: Full URLs (scheme://domain/path)
            "((?:[a-zA-Z]{1,10}://|//)" +
            "[^\"\'/\\s]{1,}\\." +
            "[a-zA-Z]{2,}[^\"'\\s]{0,})" +
            "|" +
            // Pattern 2: Relative paths starting with /, ../, ./
            "((?:/|\\.\\./|\\./)"+
            "[^\"'><,;| *()(%%$^/\\\\\\[\\]]" +
            "[^\"'><,;|()\\s]{1,})" +
            "|" +
            // Pattern 3: API-style paths with extension
            "([a-zA-Z0-9_\\-/]{1,}/" +
            "[a-zA-Z0-9_\\-/.]{1,}" +
            "\\.(?:[a-zA-Z]{1,4}|action)" +
            "(?:[\\?|#][^\"|']{0,}|))" +
            "|" +
            // Pattern 4: REST endpoints without extension
            "([a-zA-Z0-9_\\-/]{1,}/" +
            "[a-zA-Z0-9_\\-/]{3,}" +
            "(?:[\\?|#][^\"|']{0,}|))" +
            "|" +
            // Pattern 5: Files with known extensions
            "([a-zA-Z0-9_\\-]{1,}" +
            "\\.(?:php|asp|aspx|jsp|json|action|html|js|txt|xml|graphql|api)" +
            "(?:[\\?|#][^\"|']{0,}|))" +
        ")" +
        "(?:\"|'|`)";
    
    private static final Pattern COMPILED_PATTERN = Pattern.compile(ENDPOINT_REGEX);
    
    // Static exclusions for common false positives
    private static final String[] STATIC_EXCLUSIONS = {
        // Libraries
        "jquery", "bootstrap", "angular", "react", "vue",
        "googleapis", "gstatic", "cloudflare", "cdn",
        // Common domains
        "facebook.com", "twitter.com", "google.com",
        "w3.org", "schema.org", "mozilla.org",
        // Build tools
        "node_modules", "webpack", "polyfill",
        // MIME types (common false positives)
        "application/", "text/", "image/", "audio/", "video/",
        "multipart/", "message/", "font/",
        // Common false positive patterns
        "octet-stream", "x-www-form", "charset=",
        "Content-Type", "Accept:", "undefined",
        // Framework internals
        "/_next/"
    };
    
    // Default file extension blacklist (user-configurable)
    private static final Set<String> DEFAULT_EXTENSION_BLACKLIST = Set.of(
        ".js", ".mjs", ".cjs",      // JavaScript files
        ".css", ".scss", ".less",   // Stylesheets
        ".map",                     // Source maps
        ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".webp", // Images
        ".woff", ".woff2", ".ttf", ".eot", ".otf", // Fonts
        ".mp3", ".mp4", ".webm", ".ogg", ".wav",   // Media
        ".pdf", ".doc", ".docx", ".xls", ".xlsx"   // Documents
    );
    
    // User-customizable extension blacklist
    private static Set<String> extensionBlacklist = new HashSet<>(DEFAULT_EXTENSION_BLACKLIST);
    
    // Default path blacklist (user-configurable)
    private static final Set<String> DEFAULT_PATH_BLACKLIST = Set.of(
        "/_next/", 
        "/assets/",
        "/static/",
        "/wp-content/", 
        "/wp-includes/"
    );
    
    // User-customizable path blacklist
    private static Set<String> pathBlacklist = new HashSet<>(DEFAULT_PATH_BLACKLIST);
    
    /**
     * Get the current extension blacklist.
     */
    public static Set<String> getExtensionBlacklist() {
        return new HashSet<>(extensionBlacklist);
    }
    
    /**
     * Set the extension blacklist.
     */
    public static void setExtensionBlacklist(Set<String> blacklist) {
        extensionBlacklist = new HashSet<>(blacklist);
    }
    
    /**
     * Add an extension to the blacklist.
     */
    public static void addToBlacklist(String extension) {
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }
        extensionBlacklist.add(extension.toLowerCase());
    }
    
    /**
     * Remove an extension from the blacklist.
     */
    public static void removeFromBlacklist(String extension) {
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }
        extensionBlacklist.remove(extension.toLowerCase());
    }
    
    /**
     * Reset blacklist to defaults.
     */
    public static void resetBlacklist() {
        extensionBlacklist = new HashSet<>(DEFAULT_EXTENSION_BLACKLIST);
    }
    
    // --- Path Blacklist Methods ---
    
    public static Set<String> getPathBlacklist() {
        return new HashSet<>(pathBlacklist);
    }
    
    public static void setPathBlacklist(Set<String> blacklist) {
        pathBlacklist = new HashSet<>(blacklist);
    }
    
    public static void resetPathBlacklist() {
        pathBlacklist = new HashSet<>(DEFAULT_PATH_BLACKLIST);
    }
    
    /**
     * Find all endpoints in the given content.
     */
    public static List<EndpointMatch> findEndpoints(String content) {
        List<EndpointMatch> matches = new ArrayList<>();
        
        if (content == null || content.isEmpty()) {
            return matches;
        }
        
        Matcher matcher = COMPILED_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String endpoint = matcher.group(1);
            if (endpoint != null && isValidEndpoint(endpoint)) {
                int startPos = matcher.start(1);
                int endPos = matcher.end(1);
                matches.add(new EndpointMatch(endpoint, startPos, endPos));
            }
        }
        
        return matches;
    }
    
    /**
     * Validate if an endpoint is worth keeping (filter false positives).
     */
    public static boolean isValidEndpoint(String endpoint) {
        if (endpoint == null || endpoint.length() < 2) {
            return false;
        }
        
        String lower = endpoint.toLowerCase();
        
        // Check static exclusions
        for (String exclusion : STATIC_EXCLUSIONS) {
            if (lower.contains(exclusion)) {
                return false;
            }
        }
        
        // Check extension blacklist
        for (String ext : extensionBlacklist) {
            if (lower.endsWith(ext) || lower.contains(ext + "?") || lower.contains(ext + "#")) {
                return false;
            }
        }
        
        // Check path blacklist
        for (String path : pathBlacklist) {
            if (lower.contains(path)) {
                return false;
            }
        }
        
        // Filter out data: and javascript: schemes
        if (lower.startsWith("data:") || lower.startsWith("javascript:") || 
            lower.startsWith("mailto:") || lower.startsWith("tel:")) {
            return false;
        }
        
        // Must contain path-like characters
        if (!endpoint.contains("/") && !endpoint.contains(".")) {
            return false;
        }
        
        // Filter out obvious non-URLs (too many special chars)
        int specialCount = 0;
        for (char c : endpoint.toCharArray()) {
            if (c == '{' || c == '}' || c == '<' || c == '>' || c == '|') {
                specialCount++;
            }
        }
        if (specialCount > 2) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Simple data class for endpoint matches.
     */
    public static class EndpointMatch {
        public final String endpoint;
        public final int startPosition;
        public final int endPosition;
        
        public EndpointMatch(String endpoint, int startPosition, int endPosition) {
            this.endpoint = endpoint;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }
    }
}
