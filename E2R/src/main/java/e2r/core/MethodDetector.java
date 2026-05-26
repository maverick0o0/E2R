package e2r.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP Method Detection - Ported from jsluice Go logic.
 * Analyzes code context to infer the HTTP method for an endpoint.
 */
public class MethodDetector {
    
    // Fetch API patterns
    private static final Pattern FETCH_PATTERN = Pattern.compile(
        "fetch\\s*\\(\\s*['\"`]([^'\"`]+)['\"`]\\s*,?\\s*\\{?([^}]*)}?",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern FETCH_METHOD_PATTERN = Pattern.compile(
        "method\\s*:\\s*['\"`]?(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)['\"`]?",
        Pattern.CASE_INSENSITIVE
    );
    
    // Axios patterns
    private static final Pattern AXIOS_METHOD_PATTERN = Pattern.compile(
        "axios\\s*\\.\\s*(get|post|put|delete|patch|head|options)\\s*\\(",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern AXIOS_CONFIG_PATTERN = Pattern.compile(
        "axios\\s*\\(\\s*\\{[^}]*method\\s*:\\s*['\"`]?(GET|POST|PUT|DELETE|PATCH)['\"`]?",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // jQuery patterns
    private static final Pattern JQUERY_METHOD_PATTERN = Pattern.compile(
        "\\$\\s*\\.\\s*(get|post|ajax|getJSON)\\s*\\(",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern JQUERY_AJAX_TYPE_PATTERN = Pattern.compile(
        "(?:type|method)\\s*:\\s*['\"`]?(GET|POST|PUT|DELETE|PATCH)['\"`]?",
        Pattern.CASE_INSENSITIVE
    );
    
    // XMLHttpRequest pattern
    private static final Pattern XHR_OPEN_PATTERN = Pattern.compile(
        "\\.open\\s*\\(\\s*['\"`](GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)['\"`]",
        Pattern.CASE_INSENSITIVE
    );
    
    // Location/href assignment (always GET)
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
        "(location\\.href|window\\.location|location\\.replace|location\\.assign)\\s*=",
        Pattern.CASE_INSENSITIVE
    );
    
    // Form submission detection
    private static final Pattern FORM_PATTERN = Pattern.compile(
        "\\.submit\\s*\\(|form\\.action|method\\s*=\\s*['\"`]?(post|get)['\"`]?",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Detect HTTP method from code context around an endpoint.
     * 
     * @param codeContext The surrounding code (typically ±10-50 lines)
     * @param endpoint The endpoint URL being analyzed
     * @return Detected HTTP method (GET, POST, PUT, DELETE, etc.) or "GET" as default
     */
    public static String detectMethod(String codeContext, String endpoint) {
        if (codeContext == null || codeContext.isEmpty()) {
            return "GET";
        }
        
        // 1. Check for fetch() with method option
        Matcher fetchMatcher = FETCH_PATTERN.matcher(codeContext);
        while (fetchMatcher.find()) {
            String url = fetchMatcher.group(1);
            String options = fetchMatcher.group(2);
            
            // Check if this fetch matches our endpoint
            if (url != null && (url.contains(endpoint) || endpoint.contains(url))) {
                if (options != null) {
                    Matcher methodMatcher = FETCH_METHOD_PATTERN.matcher(options);
                    if (methodMatcher.find()) {
                        return methodMatcher.group(1).toUpperCase();
                    }
                }
                // fetch without method defaults to GET
                return "GET";
            }
        }
        
        // 2. Check for axios.get/post/put/delete
        Matcher axiosMatcher = AXIOS_METHOD_PATTERN.matcher(codeContext);
        if (axiosMatcher.find()) {
            // Check if this axios call is related to our endpoint
            int pos = axiosMatcher.end();
            String afterMethod = codeContext.substring(pos, Math.min(pos + 200, codeContext.length()));
            if (afterMethod.contains(endpoint) || endpointNearPosition(codeContext, endpoint, axiosMatcher.start(), 200)) {
                return axiosMatcher.group(1).toUpperCase();
            }
        }
        
        // 3. Check for axios({ method: 'POST' })
        Matcher axiosConfigMatcher = AXIOS_CONFIG_PATTERN.matcher(codeContext);
        if (axiosConfigMatcher.find()) {
            if (endpointNearPosition(codeContext, endpoint, axiosConfigMatcher.start(), 300)) {
                return axiosConfigMatcher.group(1).toUpperCase();
            }
        }
        
        // 4. Check for jQuery $.get/$.post/$.ajax
        Matcher jqueryMatcher = JQUERY_METHOD_PATTERN.matcher(codeContext);
        if (jqueryMatcher.find()) {
            String jqMethod = jqueryMatcher.group(1).toLowerCase();
            if (endpointNearPosition(codeContext, endpoint, jqueryMatcher.start(), 200)) {
                if (jqMethod.equals("post")) {
                    return "POST";
                } else if (jqMethod.equals("get") || jqMethod.equals("getjson")) {
                    return "GET";
                } else if (jqMethod.equals("ajax")) {
                    // Look for type/method in ajax options
                    int pos = jqueryMatcher.end();
                    String ajaxOptions = codeContext.substring(pos, Math.min(pos + 500, codeContext.length()));
                    Matcher typeMatcher = JQUERY_AJAX_TYPE_PATTERN.matcher(ajaxOptions);
                    if (typeMatcher.find()) {
                        return typeMatcher.group(1).toUpperCase();
                    }
                }
            }
        }
        
        // 5. Check for XMLHttpRequest.open('METHOD', url)
        Matcher xhrMatcher = XHR_OPEN_PATTERN.matcher(codeContext);
        if (xhrMatcher.find()) {
            if (endpointNearPosition(codeContext, endpoint, xhrMatcher.start(), 150)) {
                return xhrMatcher.group(1).toUpperCase();
            }
        }
        
        // 6. Check for location assignment (always GET)
        Matcher locationMatcher = LOCATION_PATTERN.matcher(codeContext);
        if (locationMatcher.find()) {
            if (endpointNearPosition(codeContext, endpoint, locationMatcher.start(), 100)) {
                return "GET";
            }
        }
        
        // 7. Check for form submission hints
        Matcher formMatcher = FORM_PATTERN.matcher(codeContext);
        if (formMatcher.find()) {
            String formHint = formMatcher.group(0).toLowerCase();
            if (formHint.contains("post")) {
                return "POST";
            }
        }
        
        // 8. GENERIC: Check for method: "post" or method: "get" anywhere near the endpoint
        // This catches patterns like: tc({ method: "post", url: "/endpoint" })
        int endpointPos = codeContext.indexOf(endpoint);
        if (endpointPos >= 0) {
            // Look in ±300 chars around the endpoint
            int searchStart = Math.max(0, endpointPos - 300);
            int searchEnd = Math.min(codeContext.length(), endpointPos + 300);
            String vicinity = codeContext.substring(searchStart, searchEnd).toLowerCase();
            
            // Look for method: "post" or method: 'post' patterns
            Pattern genericMethodPattern = Pattern.compile(
                "method\\s*:\\s*['\"`]?(post|put|delete|patch|get)['\"`]?",
                Pattern.CASE_INSENSITIVE
            );
            Matcher genericMatcher = genericMethodPattern.matcher(vicinity);
            if (genericMatcher.find()) {
                String method = genericMatcher.group(1).toUpperCase();
                // Make sure it's not GET (we want to find non-default methods)
                if (!method.equals("GET")) {
                    return method;
                }
            }
        }
        
        // 9. Heuristic: if endpoint contains "create", "add", "save", "update" -> POST/PUT
        String lowerEndpoint = endpoint.toLowerCase();
        if (lowerEndpoint.contains("create") || lowerEndpoint.contains("add") || 
            lowerEndpoint.contains("save") || lowerEndpoint.contains("submit") ||
            lowerEndpoint.contains("send")) {
            return "POST";
        }
        if (lowerEndpoint.contains("update") || lowerEndpoint.contains("edit")) {
            return "PUT";
        }
        if (lowerEndpoint.contains("delete") || lowerEndpoint.contains("remove")) {
            return "DELETE";
        }
        
        // Default to GET
        return "GET";
    }
    
    /**
     * Check if the endpoint appears near a certain position in the code.
     */
    private static boolean endpointNearPosition(String code, String endpoint, int position, int range) {
        int start = Math.max(0, position - range);
        int end = Math.min(code.length(), position + range);
        String region = code.substring(start, end);
        return region.contains(endpoint);
    }
    
    /**
     * Extract request body hints from code context.
     */
    public static String extractBodyHints(String codeContext) {
        // Look for JSON.stringify, FormData, or object literals
        StringBuilder hints = new StringBuilder();
        
        // Look for object being sent as body
        Pattern bodyPattern = Pattern.compile(
            "(?:body|data)\\s*:\\s*(?:JSON\\.stringify\\s*\\()?\\s*\\{([^}]+)\\}",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        
        Matcher bodyMatcher = bodyPattern.matcher(codeContext);
        if (bodyMatcher.find()) {
            hints.append("Body object keys: ").append(bodyMatcher.group(1).trim());
        }
        
        return hints.toString();
    }
}
