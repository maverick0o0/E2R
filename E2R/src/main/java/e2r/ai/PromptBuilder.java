package e2r.ai;

/**
 * Builds prompts for Ollama AI request generation.
 * Uses the user-specified prompt template.
 */
public class PromptBuilder {
    
    private static final String SYSTEM_PROMPT = """
        You are a Security Code Parser. Generate a RAW HTTP request for the EXACT endpoint specified.
        
        ============= MANDATORY ENDPOINT =============
        ENDPOINT: {ENDPOINT}
        HOST: {HOST}
        METHOD HINT: {METHOD}
        ===============================================
        
        CODE CONTEXT (for understanding parameters):
        {CODE_CONTEXT}
        
        RULES:
        1. You MUST use the EXACT endpoint path shown above: {ENDPOINT}
        2. Use the method hint if provided, otherwise infer from code.
        3. Analyze code for headers (Content-Type, Authorization, etc.)
        4. For request body, use realistic dummy values (e.g., "test_user", "123", "user@example.com")
        5. If endpoint has template variables like ${id} or {id}, replace with dummy values like "1"
        6. Do not guess or assume the body values.
        
        CRITICAL:
        - Output ONLY the raw HTTP request
        - NO markdown, NO explanation, NO backticks
        - Ready for Burp Suite Repeater
        
        EXAMPLE OUTPUT:
        POST /api/endpoint HTTP/1.1
        Host: example.com
        Content-Type: application/json
        
        {"key": "value"}
        """;
    
    /**
     * Build the full prompt for AI generation.
     */
    public static String buildPrompt(String endpoint, String codeContext, String host, String method) {
        String hostValue = (host != null && !host.isEmpty()) ? host : "TARGET_HOST";
        String methodValue = (method != null && !method.isEmpty()) ? method : "GET";
        
        String prompt = SYSTEM_PROMPT
            .replace("{ENDPOINT}", endpoint)
            .replace("{HOST}", hostValue)
            .replace("{METHOD}", methodValue)
            .replace("{CODE_CONTEXT}", truncateContext(codeContext));
        
        return prompt;
    }
    
    /**
     * Legacy method for compatibility.
     */
    public static String buildPrompt(String endpoint, String codeContext, String host) {
        return buildPrompt(endpoint, codeContext, host, "GET");
    }
    
    /**
     * Truncate context if too long (to avoid token limits).
     */
    private static String truncateContext(String context) {
        if (context == null) return "";
        
        int maxChars = 12000;
        
        if (context.length() <= maxChars) {
            return context;
        }
        
        int keepStart = maxChars / 2;
        int keepEnd = maxChars / 2;
        
        return context.substring(0, keepStart) + 
               "\n\n... [TRUNCATED] ...\n\n" + 
               context.substring(context.length() - keepEnd);
    }
    
    /**
     * Build a simpler prompt for GET requests.
     */
    public static String buildSimplePrompt(String endpoint, String method, String host) {
        return String.format("""
            Generate a raw HTTP request:
            
            %s %s HTTP/1.1
            Host: %s
            
            Output ONLY the raw HTTP request, ready for Burp Repeater.
            """, method, endpoint, host != null ? host : "TARGET_HOST");
    }
}
