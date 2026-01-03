package e2r.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Client for Ollama API.
 * Handles model listing and text generation.
 */
public class OllamaClient {
    
    private final String serverUrl;
    private final String model;
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    public OllamaClient(String serverUrl, String model) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.model = model;
        this.gson = new Gson();
        
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)  // AI generation can be slow
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    /**
     * Test connection to Ollama server.
     */
    public boolean testConnection() {
        try {
            Request request = new Request.Builder()
                .url(serverUrl + "/api/tags")
                .get()
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * List available models.
     */
    public List<String> listModels() throws IOException {
        List<String> models = new ArrayList<>();
        
        Request request = new Request.Builder()
            .url(serverUrl + "/api/tags")
            .get()
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to list models: " + response.code());
            }
            
            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            
            if (json.has("models")) {
                JsonArray modelsArray = json.getAsJsonArray("models");
                for (int i = 0; i < modelsArray.size(); i++) {
                    JsonObject modelObj = modelsArray.get(i).getAsJsonObject();
                    if (modelObj.has("name")) {
                        models.add(modelObj.get("name").getAsString());
                    }
                }
            }
        }
        
        return models;
    }
    
    /**
     * Generate text using the model.
     */
    public String generate(String prompt) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("prompt", prompt);
        requestBody.addProperty("stream", false);
        
        // Add generation options for better output
        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.3);  // Lower for more deterministic output
        options.addProperty("num_predict", 2000);  // Max tokens
        requestBody.add("options", options);
        
        RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);
        
        Request request = new Request.Builder()
            .url(serverUrl + "/api/generate")
            .post(body)
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Generation failed: " + response.code() + " - " + errorBody);
            }
            
            String responseBody = response.body().string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            
            if (json.has("response")) {
                return json.get("response").getAsString().trim();
            } else {
                throw new IOException("Invalid response format: missing 'response' field");
            }
        }
    }
    
    /**
     * Chat completion (alternative API).
     */
    public String chat(String systemPrompt, String userMessage) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false);
        
        JsonArray messages = new JsonArray();
        
        // System message
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);
        
        // User message
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);
        
        requestBody.add("messages", messages);
        
        RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);
        
        Request request = new Request.Builder()
            .url(serverUrl + "/api/chat")
            .post(body)
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Chat failed: " + response.code());
            }
            
            String responseBody = response.body().string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            
            if (json.has("message")) {
                JsonObject message = json.getAsJsonObject("message");
                if (message.has("content")) {
                    return message.get("content").getAsString().trim();
                }
            }
            
            throw new IOException("Invalid chat response format");
        }
    }
}
