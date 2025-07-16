package com.hdev.ollamaproxy.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdev.ollamaproxy.config.AppSettingsState;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
        this.baseUrl = settings.baseUrl;
        this.apiKey = settings.apiKey;
        this.serviceType = settings.serviceType;

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(30));

        this.httpClient = builder.build();
    }

    /**
     * Sends a chat completion request to the appropriate endpoint
     */
    public String chatCompletion(ObjectNode anthropicRequest) throws Exception {
        // Convert Anthropic format to OpenAI format
        ObjectNode openAIRequest = AnthropicMessageConverter.formatAnthropicToOpenAI(anthropicRequest);

        // Determine the endpoint based on service type
        String endpoint = getEndpoint();

        // Build the HTTP request
        Request.Builder requestBuilder = new Request.Builder()
                .url(baseUrl + endpoint)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json");

        // Add OpenRouter specific headers and provider preference if needed
        if (baseUrl.contains("openrouter.ai")) {
            requestBuilder.addHeader("HTTP-Referer", "https://henrikdev.xyz")
                          .addHeader("X-Title", "JetBrains AI Proxy Plugin");

            // Add provider preference based on model
            String modelName = anthropicRequest.path("model").asText();
            String providerPreference = getProviderPreferenceForModel(modelName);

            if (providerPreference != null && !providerPreference.trim().isEmpty()) {
                ObjectNode providerNode = mapper.createObjectNode();

                // Create the 'order' array and add the provider
                ArrayNode orderArray = mapper.createArrayNode();
                orderArray.add(providerPreference);
                providerNode.set("order", orderArray);

                // Add 'allow_fallbacks' as false
                providerNode.put("allow_fallbacks", false);

                // Set the 'provider' object in the request
                openAIRequest.set("provider", providerNode);
            }
        }

        RequestBody body = RequestBody.create(openAIRequest.toString(), MediaType.get("application/json"));
        Request request = requestBuilder.post(body).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Request failed: " + response.code() + " " + response.message());
            }

            String responseBody = Objects.requireNonNull(response.body()).string();

            // Convert OpenAI response back to Anthropic format
            return convertOpenAIResponseToAnthropic(responseBody, anthropicRequest.path("model").asText());
        }
    }

    /**
     * Handles streaming chat completion requests
     * @return boolean indicating whether tool calls were detected in the response
     */
    public boolean chatCompletionStream(ObjectNode anthropicRequest, StreamHandler handler) throws Exception {
        System.out.println("Starting streaming chat completion for model: " + anthropicRequest.path("model").asText());

        // Check if this is a tool continuation request
        boolean isToolContinuation = anthropicRequest.has("tool_results");
        if (isToolContinuation) {
            System.out.println("Processing tool continuation request with tool results");
        }

        // Flag to track if tool calls are detected in the stream
        final boolean[] toolCallsDetected = { false };

        // Convert Anthropic format to OpenAI format and enable streaming
        ObjectNode openAIRequest = AnthropicMessageConverter.formatAnthropicToOpenAI(anthropicRequest);
        openAIRequest.put("stream", true);

        System.out.println("Converted request: " + openAIRequest.toString());

        // Determine the endpoint based on service type
        String endpoint = getEndpoint();
        String fullUrl = baseUrl + endpoint;
        System.out.println("Making request to: " + fullUrl);

        // Build the HTTP request
        Request.Builder requestBuilder = new Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json");

        // Add OpenRouter specific headers and provider preference if needed
        if (baseUrl.contains("openrouter.ai")) {
            requestBuilder.addHeader("HTTP-Referer", "https://henrikdev.xyz")
                          .addHeader("X-Title", "JetBrains AI Proxy Plugin");

            // Add provider preference based on model
            String modelName = anthropicRequest.path("model").asText();
            String providerPreference = getProviderPreferenceForModel(modelName);

            if (providerPreference != null && !providerPreference.trim().isEmpty()) {
                ObjectNode providerNode = mapper.createObjectNode();

                // Create the 'order' array and add the provider
                ArrayNode orderArray = mapper.createArrayNode();
                orderArray.add(providerPreference);
                providerNode.set("order", orderArray);

                // Add 'allow_fallbacks' as false
                providerNode.put("allow_fallbacks", false);

                // Set the 'provider' object in the request
                openAIRequest.set("provider", providerNode);
            }
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

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                System.err.println("Empty response body received");
                throw new IOException("Empty response body");
            }

            // Process the streaming response
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {

                String line;
                int chunkCount = 0;
                System.out.println("Starting to read from stream...");

                while ((line = reader.readLine()) != null) {
                    // The BufferedReader gives us one complete line at a time. Process it directly.
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();

                        if ("[DONE]".equals(data)) {
                            System.out.println("Received [DONE] signal from provider.");
                            // Don't break immediately if tool calls were detected
                            if (!toolCallsDetected[0]) {
                                System.out.println("No tool calls detected, stopping read loop.");
                                break; // Exit the loop cleanly only if no tool calls
                            } else {
                                System.out.println("Tool calls were detected, continuing to keep stream open.");
                                // Skip this DONE signal but keep the stream open
                                return toolCallsDetected[0]; // End streaming but signal that tool calls were detected
                            }
                        }

                        if (!data.isEmpty()) {
                            try {
                                chunkCount++;
                                // Convert OpenAI streaming chunk to Anthropic format and send
                                String anthropicChunk = AnthropicMessageConverter.convertOpenAIStreamChunkToAnthropic(data,
                                        anthropicRequest.path("model").asText());

                                // Check if this chunk contains tool call information
                                boolean containsToolCall = data.contains("\"type\":\"function\"") ||
                                                          data.contains("\"type\": \"function\"") ||
                                                          data.contains("\"function_call\"") ||
                                                          data.contains("\"tool_calls\"") ||
                                                          (anthropicChunk != null &&
                                                           (anthropicChunk.contains("\"type\":\"tool_use\"") ||
                                                            anthropicChunk.contains("\"type\": \"tool_use\"") ||
                                                            anthropicChunk.contains("\"delta\":{\"type\":\"tool_use\"}")));

                                if (containsToolCall) {
                                    toolCallsDetected[0] = true;
                                    System.out.println("Tool call detected in chunk #" + chunkCount);
                                }

                                if (anthropicChunk != null) {
                                    handler.handle(anthropicChunk);
                                }
                            } catch (Exception e) {
                                System.err.println("Error processing stream chunk #" + chunkCount + ": " + e.getMessage());
                                System.err.println("Raw chunk data: " + data);
                                // Decide if you want to continue or re-throw
                            }
                        }
                    }
                    // SSE spec also includes blank lines as separators, events, ids, etc.
                    // The 'if' condition correctly ignores them and only processes 'data:' lines.
                }

                System.out.println("Streaming completed successfully, total chunks processed: " + chunkCount);
                System.out.println("Tool calls detected: " + toolCallsDetected[0]);

                // Return the flag indicating whether tool calls were detected
                return toolCallsDetected[0];

            } catch (Exception e) {
                System.err.println("Error reading streaming response: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        } catch (Exception e) {
            System.err.println("Error in streaming chat completion: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Determines the correct endpoint based on service type
     */
    private String getEndpoint() {
        if (serviceType == AppSettingsState.ServiceType.OPENWEBUI) {
            return "api/chat/completions";  // OpenWebUI uses this endpoint
        } else {
            return "v1/chat/completions";   // OpenAI compatible (default)
        }
    }

    /**
     * Process tool results to continue a conversation with the LLM
     * @param streamId The ID of the stream to continue
     * @param toolResults The results from tool execution
     * @param model The model to use for continuation
     * @param handler The stream handler to receive continued response
     */
    public void continueWithToolResults(String streamId, JsonNode toolResults, String model, StreamHandler handler) throws Exception {
        System.out.println("Continuing stream " + streamId + " with tool results");

        // Build a new request that includes the tool results
        ObjectNode continuationRequest = mapper.createObjectNode();
        continuationRequest.put("model", model);
        continuationRequest.put("stream", true);

        // Add tool results to the request
        continuationRequest.set("tool_results", toolResults);

        // Call the streaming endpoint with this continuation request
        chatCompletionStream(continuationRequest, handler);
    }

    /**
     * Gets the provider preference for a given model
     */
    private String getProviderPreferenceForModel(String modelName) {
        AppSettingsState settings = AppSettingsState.getInstance();

        // Check if the model matches the normal model
        if (modelName.equals(settings.getNormalModel())) {
            return settings.getNormalProvider();
        }

        // Check if the model matches the small model
        if (modelName.equals(settings.getSmallModel())) {
            return settings.getSmallProvider();
        }

        // Default to normal provider if no specific match
        return settings.getNormalProvider();
    }

    /**
     * Converts OpenAI response format back to Anthropic format
     */
    private String convertOpenAIResponseToAnthropic(String openAIResponse, String model) throws Exception {
        ObjectNode openAIJson = (ObjectNode) mapper.readTree(openAIResponse);
        ObjectNode anthropicResponse = AnthropicMessageConverter.formatOpenAIToAnthropic(openAIJson, model);
        return anthropicResponse.toString();
    }
}
