package com.hdev.proxy.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdev.proxy.config.AppSettingsState;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Client for communicating with Anthropic-compatible endpoints (OpenRouter, OpenWebUI)
 * Handles the conversion from Anthropic format to OpenAI format and routing to appropriate endpoints.
 */
public class AnthropicClient {

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final AppSettingsState.ServiceType serviceType;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnthropicClient() {
        AppSettingsState settings = AppSettingsState.getInstance();
        this.baseUrl = settings.baseUrl.endsWith("/") ? settings.baseUrl : settings.baseUrl + "/";
        this.apiKey = settings.apiKey;
        this.serviceType = settings.serviceType;

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(30))
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder builder = original.newBuilder()
                            // Use "Authorization" header, which is standard. OpenWebUI uses this.
                            .header("Authorization", "Bearer " + apiKey);
                    return chain.proceed(builder.build());
                })
                .build();
    }

    public String chatCompletion(ObjectNode anthropicRequest) throws Exception {
        ObjectNode openAIRequest = AnthropicMessageConverter.formatAnthropicToOpenAI(anthropicRequest);
        String endpoint = getEndpoint();

        Request.Builder requestBuilder = new Request.Builder()
                .url(baseUrl + endpoint)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json");

        if (baseUrl.contains("openrouter.ai")) {
            addOpenRouterHeaders(requestBuilder, anthropicRequest.path("model").asText(), openAIRequest);
        }

        RequestBody body = RequestBody.create(openAIRequest.toString(), MediaType.get("application/json"));
        Request request = requestBuilder.post(body).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Request failed: " + response.code() + " " + response.message());
            }

            String responseBody = Objects.requireNonNull(response.body()).string();
            return convertOpenAIResponseToAnthropic(responseBody, anthropicRequest.path("model").asText());
        }
    }

    public boolean chatCompletionStream(ObjectNode anthropicRequest, StreamHandler handler) throws Exception {
        System.out.println("Starting streaming chat completion for model: " + anthropicRequest.path("model").asText());
        AnthropicMessageConverter.resetStreamState(); // Reset for a new request

        ObjectNode openAIRequest = AnthropicMessageConverter.formatAnthropicToOpenAI(anthropicRequest);
        openAIRequest.put("stream", true);
        System.out.println("Converted request: " + openAIRequest.toString());

        String endpoint = getEndpoint();
        String fullUrl = baseUrl + endpoint;
        System.out.println("Making request to: " + fullUrl);

        Request.Builder requestBuilder = new Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json");

        if (baseUrl.contains("openrouter.ai")) {
            addOpenRouterHeaders(requestBuilder, anthropicRequest.path("model").asText(), openAIRequest);
        }

        RequestBody body = RequestBody.create(openAIRequest.toString(), MediaType.get("application/json"));
        Request request = requestBuilder.post(body).build();

        try (Response response = httpClient.newCall(request).execute()) {
            System.out.println("Response status: " + response.code() + " " + response.message());
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                System.err.println("Streaming request failed with status " + response.code() + ": " + errorBody);
                throw new IOException("Streaming request failed: " + response.code() + " " + response.message() + " - " + errorBody);
            }

            ResponseBody responseBody = Objects.requireNonNull(response.body());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
                String line;
                int chunkCount = 0;
                while ((line = reader.readLine()) != null) {
                    chunkCount++;
                    System.out.println("Processing chunk #" + chunkCount + ": " + line.substring(0, Math.min(50, line.length())));
                    if (!line.startsWith("data: ")) {
                        continue;
                    }

                    String data = line.substring(6).trim();

                    // REFACTORED: All logic, including [DONE] and finish_reason, is now inside the converter.
                    // This loop's only job is to pass the data along.
                    String anthropicChunk = AnthropicMessageConverter.convertOpenAIStreamChunkToAnthropic(data, anthropicRequest.path("model").asText());
                    if (anthropicChunk != null && !anthropicChunk.isEmpty()) {
                        try {
                            handler.handle(anthropicChunk);
                            System.out.println("Successfully sent chunk #" + chunkCount);
                            System.out.println("Chunk content: " + anthropicChunk);
                        } catch (Exception e) {
                            System.err.println("Failed to send chunk #" + chunkCount + ": " + e.getMessage());
                            break; // Stop processing if client disconnected
                        }
                    }

                    // The stream is over when the reader is exhausted.
                    // The special 'tool_calls' check that prematurely terminated the loop is removed.
                    if ("[DONE]".equals(data)) {
                        break; // Exit loop after processing the [DONE] signal
                    }
                }
            }
        } catch (IOException e) {
            // This is the correct place to catch client disconnections.
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("client disconnected") || message.contains("broken pipe")) {
                System.out.println("Stream stopped gracefully because the client disconnected.");
            } else {
                System.err.println("Error during streaming: " + e.getMessage());
                throw e; // Re-throw unexpected errors
            }
        }

        System.out.println("Streaming completed.");
        return false; // No special termination from this method is needed.
    }

    private void addOpenRouterHeaders(Request.Builder requestBuilder, String modelName, ObjectNode openAIRequest) {
        requestBuilder.addHeader("HTTP-Referer", "https://henrikdev.xyz")
                .addHeader("X-Title", "JetBrains AI Proxy Plugin");

        String providerPreference = getProviderPreferenceForModel(modelName);
        if (providerPreference != null && !providerPreference.trim().isEmpty()) {
            ObjectNode providerNode = mapper.createObjectNode();
            ArrayNode orderArray = mapper.createArrayNode().add(providerPreference);
            providerNode.set("order", orderArray);
            providerNode.put("allow_fallbacks", false);
            openAIRequest.set("provider", providerNode);
        }
    }

    private String getEndpoint() {
        return serviceType == AppSettingsState.ServiceType.OPENWEBUI ?
                "api/chat/completions" :
                serviceType == AppSettingsState.ServiceType.OPENAI && baseUrl.contains("openrouter.ai") ?
                "api/v1/chat/completions" : "v1/chat/completions";
    }

    private String getProviderPreferenceForModel(String modelName) {
        AppSettingsState settings = AppSettingsState.getInstance();
        if (modelName.equals(settings.getNormalModel())) {
            return settings.getNormalProvider();
        }
        if (modelName.equals(settings.getSmallModel())) {
            return settings.getSmallProvider();
        }
        return settings.getNormalProvider();
    }

    private String convertOpenAIResponseToAnthropic(String openAIResponse, String model) throws IOException {
        ObjectNode openAIJson = (ObjectNode) mapper.readTree(openAIResponse);
        ObjectNode anthropicResponse = AnthropicMessageConverter.formatOpenAIToAnthropic(openAIJson, model);
        return anthropicResponse.toString();
    }
}