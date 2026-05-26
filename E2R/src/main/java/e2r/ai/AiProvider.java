package e2r.ai;

/**
 * Interface for AI providers (Ollama, Groq, etc.)
 * Uses Strategy pattern for swappable AI backends.
 */
public interface AiProvider {
    
    /**
     * Generate a request using the AI model.
     * 
     * @param prompt The user prompt with context
     * @param systemInstruction Optional system instruction
     * @return The AI-generated response
     * @throws Exception if generation fails
     */
    String generateRequest(String prompt, String systemInstruction) throws Exception;
    
    /**
     * Test the connection to the AI provider.
     * 
     * @return true if connected successfully
     */
    boolean testConnection();
    
    /**
     * Get the provider name for display.
     */
    String getProviderName();
}
