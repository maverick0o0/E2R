package e2r.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.util.concurrent.TimeUnit;

/**
 * Groq AI Provider - Cloud LLM integration.
 * Uses OpenAI-compatible API format.
 */
public class GroqProvider implements AiProvider {
    
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final String apiKey;
    private final String model;
    private final OkHttpClient client;
    private final Gson gson;
    
    public GroqProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.gson = new Gson();
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    @Override
    public String generateRequest(String prompt, String systemInstruction) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", 2048);
        
        JsonArray messages = new JsonArray();
        
        // Add system message if provided
        if (systemInstruction != null && !systemInstruction.isEmpty()) {
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemInstruction);
            messages.add(systemMsg);
        }
        
        // Add user message
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt);
        messages.add(userMsg);
        
        requestBody.add("messages", messages);
        
        RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);
        Request request = new Request.Builder()
            .url(GROQ_API_URL)
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            
            if (response.code() == 401) {
                throw new Exception("Invalid API Key - please check your Groq API key in Settings");
            }
            
            if (!response.isSuccessful()) {
                // Try to extract error message from response
                try {
                    JsonObject errorJson = gson.fromJson(responseBody, JsonObject.class);
                    if (errorJson.has("error")) {
                        JsonObject error = errorJson.getAsJsonObject("error");
                        String message = error.has("message") ? error.get("message").getAsString() : "Unknown error";
                        throw new Exception("Groq API error: " + message);
                    }
                } catch (Exception parseError) {
                    // Ignore parse error, use generic message
                }
                throw new Exception("Groq error: " + response.code() + " " + response.message());
            }
            
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            if (jsonResponse.has("choices")) {
                JsonArray choices = jsonResponse.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject firstChoice = choices.get(0).getAsJsonObject();
                    if (firstChoice.has("message")) {
                        return firstChoice.getAsJsonObject("message").get("content").getAsString();
                    }
                }
            }
            
            return responseBody;
        }
    }
    
    @Override
    public boolean testConnection() {
        try {
            // Simple test with a minimal request
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("max_tokens", 5);
            
            JsonArray messages = new JsonArray();
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", "Hi");
            messages.add(userMsg);
            requestBody.add("messages", messages);
            
            RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);
            Request request = new Request.Builder()
                .url(GROQ_API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "Groq (Cloud)";
    }
}
