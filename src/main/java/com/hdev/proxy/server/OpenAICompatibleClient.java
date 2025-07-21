package com.hdev.proxy.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.openapi.diagnostic.Logger;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.*;
import com.openai.core.JsonValue;
import io.javalin.http.Context;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Drop‑in replacement that targets the official
 * {@code com.openai:openai-java:2.16.0} SDK instead of the now‑deprecated
 * Theo Kanning retrofit client.
 */
public class OpenAICompatibleClient implements ProviderClient {
    private static final Logger LOG = Logger.getInstance(OpenAICompatibleClient.class);

    private final OpenAIClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAICompatibleClient(String apiKey, String baseUrl) {
        // Build an OkHttp‑backed client with a 60 s timeout.
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(60));

        //if openrouter add ranking headers
        if (baseUrl.contains("openrouter.ai")) {
            builder.putHeader("HTTP-Referer", "https://henrikdev.xyz")
                    .putHeader("X-Title", "JetBrains AI Proxy Plugin");
        }

        if (baseUrl != null && !baseUrl.isBlank()) {
            baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            if (baseUrl.contains("openrouter.ai")) {
                // add /api/ to the base url
                baseUrl = baseUrl + "/api";
            }
            baseUrl = baseUrl + "/v1";
            builder.baseUrl(baseUrl); // e.g. Azure/Ollama/OpenRouter endpoints
        }
        System.out.println("OpenAI client created with base URL: " + baseUrl);
        this.client = builder.build();
    }

    @Override
    public String getModels() throws Exception {
        var resp = client.models()
                .withRawResponse()
                .list();          // returns HttpResponse<String>
        try (InputStream in = resp.body()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }


    @Override
    public String chat(ObjectNode request) throws Exception {
        ChatCompletionCreateParams params = buildParams(request);
        ChatCompletion result = client.chat().completions().create(params);
        return mapper.writeValueAsString(result);
    }

    @Override
    public void chatStream(ObjectNode request, Context ctx, StreamHandler handler) {
        ChatCompletionCreateParams params = buildParams(request);

        System.out.println("params: " + params);

        // The SDK returns a StreamResponse we can iterate over.
        try (StreamResponse<ChatCompletionChunk> stream =
                     client.chat().completions().createStreaming(params)) {

            stream.stream().forEach(chunk -> {
                try {
                    handler.handle(mapper.writeValueAsString(chunk));
                } catch (Exception e) {
                    throw new RuntimeException("Error serializing chunk", e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Error while streaming ChatCompletion", e);
        }
    }

    /**
     * Convert the inbound proxy JSON into {@link ChatCompletionCreateParams}.
     * Only the fields we actually use (model, messages, stream) are mapped.
     */
    private ChatCompletionCreateParams buildParams(JsonNode request) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(request.get("model").asText());

        JsonNode messagesNode = request.get("messages");
        java.util.List<JsonNode> messages = new java.util.ArrayList<>();
        messagesNode.forEach(messages::add);

        // Clean up message sequence for Mistral compatibility BEFORE processing
        messages = cleanMessageSequence(messages);

        // Map each cleaned message to the builder - but handle tools separately
        for (JsonNode node : messages) {
            String role = node.get("role").asText();
            String content = node.get("content").asText();

            switch (role) {
                case "system" -> builder.addSystemMessage(content);
                case "assistant" -> builder.addAssistantMessage(content);
                case "user" -> builder.addUserMessage(content);
                case "tool", "function" -> {
                    // Tool messages need to be passed through as raw JSON
                    // We'll add all messages as additional body property to preserve tool structure
                }
                default -> {
                    System.err.println("Warning: Unexpected message role '" + role + "', skipping: " + content);
                }
            }
        }

        // If we have any tool/function messages, pass all messages as raw JSON to preserve structure
        boolean hasToolMessages = messages.stream()
                .anyMatch(msg -> "tool".equals(msg.get("role").asText()) ||
                        "function".equals(msg.get("role").asText()));

        if (hasToolMessages) {
            // Override with raw messages to preserve tool message structure
            builder.putAdditionalBodyProperty("messages", JsonValue.from(messages));
        }

        // Add tool support using the latest OpenAI Java SDK API
        if (request.has("tools")) {
            JsonNode toolsNode = request.get("tools");
            if (toolsNode.isArray()) {
                for (JsonNode tool : toolsNode) {
                    JsonNode function = tool.get("function");
                    if (function != null) {
                        builder.putAdditionalBodyProperty("tools", JsonValue.from(toolsNode));
                        break; // Add all tools at once
                    }
                }
            }
        }

        // Add tool_choice if present
        if (request.has("tool_choice")) {
            JsonNode toolChoice = request.get("tool_choice");
            builder.putAdditionalBodyProperty("tool_choice", JsonValue.from(toolChoice));
        }

        return builder.build();
    }

    /**
     * Clean up message sequence to ensure compatibility with strict providers like Mistral
     * Ensures tool/function messages are followed by assistant messages, never by user messages
     */
    private java.util.List<JsonNode> cleanMessageSequence(java.util.List<JsonNode> messages) {
        java.util.List<JsonNode> cleaned = new java.util.ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            JsonNode message = messages.get(i);
            String currentRole = message.get("role").asText();

            cleaned.add(message);

            // Check if this is a tool/function message followed by a user message
            if (("tool".equals(currentRole) || "function".equals(currentRole)) &&
                    i + 1 < messages.size()) {

                JsonNode nextMessage = messages.get(i + 1);
                String nextRole = nextMessage.get("role").asText();

                if ("user".equals(nextRole)) {
                    // Insert an assistant message between tool and user
                    ObjectNode assistantMessage = mapper.createObjectNode();
                    assistantMessage.put("role", "assistant");
                    assistantMessage.put("content", "I've processed the tool result. How can I help you further?");
                    cleaned.add(assistantMessage);
                }
            }
        }

        return cleaned;
    }
}
