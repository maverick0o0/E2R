package e2r.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.util.concurrent.TimeUnit;

/**
 * Ollama AI Provider - Local LLM integration.
 */
public class OllamaProvider implements AiProvider {
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final String baseUrl;
    private final String model;
    private final OkHttpClient client;
    private final Gson gson;
    
    public OllamaProvider(String baseUrl, String model) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
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
        // Use chat endpoint for better results
        String url = baseUrl + "/api/chat";
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false);
        
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
            .url(url)
            .post(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Ollama error: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            if (jsonResponse.has("message")) {
                return jsonResponse.getAsJsonObject("message").get("content").getAsString();
            } else if (jsonResponse.has("response")) {
                return jsonResponse.get("response").getAsString();
            }
            
            return responseBody;
        }
    }
    
    @Override
    public boolean testConnection() {
        try {
            Request request = new Request.Builder()
                .url(baseUrl + "/api/tags")
                .get()
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
        return "Ollama (Local)";
    }
}
