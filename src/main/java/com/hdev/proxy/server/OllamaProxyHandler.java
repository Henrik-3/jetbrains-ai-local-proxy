package com.hdev.proxy.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdev.proxy.config.AppSettingsState;
import io.javalin.http.Context;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OllamaProxyHandler {

    private final ProviderClient providerClient;
    private final AppSettingsState.ServiceType serviceType;
    private final ObjectMapper mapper = new ObjectMapper();

    public OllamaProxyHandler() {
        AppSettingsState settings = AppSettingsState.getInstance();
        this.serviceType = settings.serviceType;

        if (this.serviceType == AppSettingsState.ServiceType.OPENWEBUI) {
            this.providerClient = new OllamaWebUIClient(settings.apiKey, settings.baseUrl);
        } else {
            this.providerClient = new OpenAICompatibleClient(settings.apiKey, settings.baseUrl);
        }
    }

    // Handler for GET /api/tags
    public void handleGetModels(Context ctx) throws Exception {
        String providerResponse = providerClient.getModels();
        JsonNode providerJson = mapper.readTree(providerResponse);

        List<Map<String, Object>> ollamaModels = new ArrayList<>();

        // Find the array of models, which is in a different place for each service type
        JsonNode modelsArray = providerJson.path("data");

        for (JsonNode modelNode : modelsArray) {
            String modelName = modelNode.get("id").asText();

            Map<String, Object> ollamaModel = new LinkedHashMap<>();
            ollamaModel.put("name", modelName);
            ollamaModel.put("model", modelName);
            ollamaModel.put("modified_at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            ollamaModel.put("size", 0); // Stubbed
            ollamaModel.put("digest", modelName); // Stubbed
            ollamaModels.add(ollamaModel);
        }

        ctx.json(Map.of("models", ollamaModels));
    }

    public void handleShowModel(Context ctx) throws Exception {
        ctx.json(Map.of(
                "license", "STUB License",
                "modified_at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                "details", Map.of("family", "proxy")
        ));
    }

    // Handler for POST /api/chat
    public void handleChat(Context ctx) throws Exception {
        ObjectNode request = mapper.readValue(ctx.body(), ObjectNode.class);
        boolean stream = request.path("stream").asBoolean(true);

        if (stream) {
            handleChatStream(ctx, request);
        } else {
            handleChatNonStream(ctx, request);
        }
    }

    private void handleChatNonStream(Context ctx, ObjectNode request) throws Exception {
        String providerResponse = providerClient.chat(request);
        JsonNode responseJson = mapper.readTree(providerResponse);

        // Check if this is already an Ollama-format response (from OllamaWebUIClient with tool calls)
        if (responseJson.has("message") && responseJson.has("done")) {
            // Already in Ollama format, return as-is
            ctx.json(responseJson);
            return;
        }

        // Translate the response back to Ollama format
        ObjectNode ollamaResponse = mapper.createObjectNode();
        String content = "";
        String finishReason = "stop";

        if (serviceType == AppSettingsState.ServiceType.OPENAI) {
            JsonNode choice = responseJson.path("choices").get(0);
            JsonNode message = choice.path("message");

            // Handle tool calls in OpenAI format
            if (message.has("tool_calls")) {
                ObjectNode ollamaMessage = mapper.createObjectNode();
                ollamaMessage.put("role", "assistant");
                ollamaMessage.put("content", message.path("content").asText(""));

                // Convert tool calls to Ollama format
                if (message.get("tool_calls").isArray()) {
                    ArrayNode toolCalls = mapper.createArrayNode();
                    for (JsonNode toolCall : message.get("tool_calls")) {
                        ObjectNode ollamaToolCall = mapper.createObjectNode();
                        ollamaToolCall.put("id", toolCall.get("id").asText());
                        ollamaToolCall.put("type", toolCall.get("type").asText());

                        ObjectNode function = mapper.createObjectNode();
                        function.put("name", toolCall.get("function").get("name").asText());
                        function.put("arguments", toolCall.get("function").get("arguments").asText());

                        ollamaToolCall.set("function", function);
                        toolCalls.add(ollamaToolCall);
                    }
                    ollamaMessage.set("tool_calls", toolCalls);
                }

                ollamaResponse.set("message", ollamaMessage);
            } else {
                content = message.path("content").asText();
                ObjectNode messageNode = mapper.createObjectNode();
                messageNode.put("role", "assistant");
                messageNode.put("content", content);
                ollamaResponse.set("message", messageNode);
            }

            finishReason = choice.path("finish_reason").asText("stop");
        } else { // OpenWebUI/Ollama format
            content = responseJson.path("message").path("content").asText();
            if(responseJson.has("finish_reason")) {
                finishReason = responseJson.path("finish_reason").asText("stop");
            }

            ObjectNode messageNode = mapper.createObjectNode();
            messageNode.put("role", "assistant");
            messageNode.put("content", content);
            ollamaResponse.set("message", messageNode);
        }

        ollamaResponse.put("model", request.get("model").asText());
        ollamaResponse.put("created_at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        ollamaResponse.put("done", true);
        ollamaResponse.put("finish_reason", finishReason);

        ctx.json(ollamaResponse);
    }

    private void handleChatStream(Context ctx, ObjectNode request) throws Exception {
        ctx.header("Content-Type", "application/x-ndjson");
        // It's good practice to get the output stream once
        ctx.res().setBufferSize(0);
        var outputStream = ctx.res().getOutputStream();

        try {
            // The main streaming logic
            providerClient.chatStream(request, ctx, chunkString -> {
                try {
                    if (chunkString.isBlank()) return;

                    JsonNode chunkJson = mapper.readTree(chunkString);

                    // Check if this is already in Ollama format (from OllamaWebUIClient)
                    if (chunkJson.has("message") && chunkJson.has("model")) {
                        // Already converted by OllamaWebUIClient, send as-is
                        String ndjsonLine = chunkString + "\n";
                        outputStream.write(ndjsonLine.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                        return;
                    }

                    ObjectNode ollamaChunk = mapper.createObjectNode();
                    String content = "";

                    // Handle OpenAI streaming format
                    if (chunkJson.has("choices") && chunkJson.get("choices").isArray()) {
                        JsonNode choice = chunkJson.get("choices").get(0);
                        JsonNode delta = choice.path("delta");

                        ObjectNode messageNode = mapper.createObjectNode();
                        messageNode.put("role", "assistant");

                        if (delta.has("content")) {
                            content = delta.get("content").asText("");
                            messageNode.put("content", content);
                        } else {
                            messageNode.put("content", "");
                        }

                        // Handle tool calls in streaming delta
                        if (delta.has("tool_calls")) {
                            ArrayNode toolCalls = mapper.createArrayNode();
                            for (JsonNode toolCall : delta.get("tool_calls")) {
                                ObjectNode ollamaToolCall = mapper.createObjectNode();

                                if (toolCall.has("id")) {
                                    ollamaToolCall.put("id", toolCall.get("id").asText());
                                }
                                if (toolCall.has("type")) {
                                    ollamaToolCall.put("type", toolCall.get("type").asText());
                                }

                                if (toolCall.has("function")) {
                                    ObjectNode function = mapper.createObjectNode();
                                    JsonNode funcNode = toolCall.get("function");

                                    if (funcNode.has("name")) {
                                        function.put("name", funcNode.get("name").asText());
                                    }
                                    if (funcNode.has("arguments")) {
                                        function.put("arguments", funcNode.get("arguments").asText());
                                    }

                                    ollamaToolCall.set("function", function);
                                }

                                toolCalls.add(ollamaToolCall);
                            }
                            messageNode.set("tool_calls", toolCalls);
                        }

                        ollamaChunk.set("message", messageNode);

                        // Check for finish reason
                        if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
                            ollamaChunk.put("done", true);
                            ollamaChunk.put("finish_reason", choice.get("finish_reason").asText());
                        } else {
                            ollamaChunk.put("done", false);
                        }
                    } else {
                        // Fallback for other formats
                        ObjectNode messageNode = mapper.createObjectNode();
                        messageNode.put("role", "assistant");
                        messageNode.put("content", content);
                        ollamaChunk.set("message", messageNode);
                        ollamaChunk.put("done", false);
                    }

                    ollamaChunk.put("model", request.get("model").asText());
                    ollamaChunk.put("created_at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

                    // Write the chunk. This will throw an IOException if the client has disconnected.
                    String ndjsonLine = ollamaChunk.toString() + "\n";
                    outputStream.write(ndjsonLine.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();

                } catch (java.io.IOException e) {
                    // This is the correct way to detect a closed connection.
                    // We re-throw a custom exception to signal the outer stream to stop.
                    throw new ClientDisconnectedException(e);
                } catch (Exception e) {
                    // Log other unexpected errors
                    System.err.println("Error processing stream chunk: " + e.getMessage());
                }
            });

            // Send the final "done" message after the stream concludes successfully.
            ObjectNode finalChunk = mapper.createObjectNode();
            finalChunk.put("model", request.get("model").asText());
            finalChunk.put("created_at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            finalChunk.set("message", mapper.createObjectNode().put("role", "assistant").put("content", ""));
            finalChunk.put("done", true);
            finalChunk.put("finish_reason", "stop");

            String finalNdjsonLine = finalChunk.toString() + "\n";
            outputStream.write(finalNdjsonLine.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();

        } catch (ClientDisconnectedException e) {
            // This is expected. The client closed the connection. Log it quietly and stop.
            System.out.println("Client disconnected during stream. Halting gracefully.");
        } catch (Exception e) {
            // Handle other upstream errors
            System.err.println("An upstream error occurred during streaming: " + e.getMessage());
        } finally {
            // It's good practice to ensure the stream is closed.
            // Javalin typically handles this, but being explicit can't hurt.
            try {
                outputStream.close();
            } catch (java.io.IOException e) {
                // Ignore, probably already closed.
            }
        }
    }

    // A custom exception to signal that the client has disconnected.
    private static class ClientDisconnectedException extends RuntimeException {
        public ClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
