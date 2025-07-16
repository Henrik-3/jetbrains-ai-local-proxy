package com.hdev.ollamaproxy.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdev.ollamaproxy.config.AppSettingsState;
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

        // Translate the response back to Ollama format
        ObjectNode ollamaResponse = mapper.createObjectNode();
        String content;
        String finishReason = "stop";

        if (serviceType == AppSettingsState.ServiceType.OPENAI) {
            JsonNode choice = responseJson.path("choices").get(0);
            content = choice.path("message").path("content").asText();
            finishReason = choice.path("finish_reason").asText("stop");
        } else { // OpenWebUI/Ollama format
            content = responseJson.path("message").path("content").asText();
            if(responseJson.has("finish_reason")) {
                finishReason = responseJson.path("finish_reason").asText("stop");
            }
        }

        ObjectNode messageNode = mapper.createObjectNode();
        messageNode.put("role", "assistant");
        messageNode.put("content", content);

        ollamaResponse.put("model", request.get("model").asText());
        ollamaResponse.put("created_at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        ollamaResponse.set("message", messageNode);
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
                    ObjectNode ollamaChunk = mapper.createObjectNode();
                    String content = "";

                    JsonNode delta = chunkJson.path("choices").get(0).path("delta");
                    if (delta.has("content")) {
                        content = delta.get("content").asText("");
                    }

                    ObjectNode messageNode = mapper.createObjectNode();
                    messageNode.put("role", "assistant");
                    messageNode.put("content", content);

                    ollamaChunk.put("model", request.get("model").asText());
                    ollamaChunk.put("created_at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
                    ollamaChunk.set("message", messageNode);
                    ollamaChunk.put("done", false);

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
