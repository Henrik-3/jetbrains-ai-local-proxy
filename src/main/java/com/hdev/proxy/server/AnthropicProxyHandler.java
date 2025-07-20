package com.hdev.proxy.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdev.proxy.config.AppSettingsState;
import org.eclipse.jetty.io.EofException;
import java.io.IOException;
import io.javalin.http.Context;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for Anthropic API endpoints that converts between Anthropic and OpenAI/OpenWebUI formats.
 * This handler provides Claude-compatible endpoints while routing to OpenAI-compatible backends.
 */
public class AnthropicProxyHandler {

    private final AnthropicClient anthropicClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AppSettingsState settings;

    // Note: Removed activeToolStreams as we now handle tool calls properly with stream completion
    public AnthropicProxyHandler() {
        this.anthropicClient = new AnthropicClient();
        this.settings = AppSettingsState.getInstance();
    }

    /**
     * Handles POST /v1/messages - Anthropic's chat completions endpoint
     */
    public void handleMessages(Context ctx) throws Exception {
        ObjectNode request = mapper.readValue(ctx.body(), ObjectNode.class);
        boolean stream = request.path("stream").asBoolean(false);

        // Validate required fields
        if (!request.has("model") || !request.has("messages")) {
            ctx.status(400).json(Map.of("error", "Missing required fields: model and messages"));
            return;
        }

        // Check if API key is configured
        if (settings.apiKey == null || settings.apiKey.trim().isEmpty()) {
            System.err.println("API key not configured");
            ctx.status(401).json(Map.of("error", "API key not configured. Please set it in plugin settings."));
            return;
        }

        // Modify model with the variable from settings
        if (request.get("model").asText().equals("base_model")) {
			request.put("model", settings.normalModel);
		} else if (request.get("model").asText().equals("small_fast_model")) {
			request.put("model", settings.smallModel);
		}

        handleMessagesStream(ctx, request);
        /*if (stream) {
            handleMessagesStream(ctx, request);
        } else {
            handleMessagesNonStream(ctx, request);
        }*/
    }

    /**
     * Handles non-streaming message requests
     */
    private void handleMessagesNonStream(Context ctx, ObjectNode request) throws Exception {
        try {
            System.out.println("Processing non-streaming request for model: " + request.path("model").asText());
            String response = anthropicClient.chatCompletion(request);
            ctx.header("Content-Type", "application/json");
            ctx.result(response);
        } catch (Exception e) {
            System.err.println("Error in non-streaming chat completion: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Handles streaming message requests
     */
    private void handleMessagesStream(Context ctx, ObjectNode request) {
        ctx.header("Content-Type", "text/event-stream");
        ctx.header("Cache-Control", "no-cache");
        ctx.header("Connection", "keep-alive");
        ctx.header("Access-Control-Allow-Origin", "*");
        ctx.res().setBufferSize(0);

        boolean toolCallsDetected = false;

        try {
            OutputStream outputStream = ctx.res().getOutputStream();

            // 2. Define the handler that will write chunks to the output stream
            StreamHandler handler = chunk -> {
                try {
                    outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } catch (IOException e) {
                    // If we fail to write, it's because the client is gone. Throw our custom exception.
                    if (e instanceof EofException || (e.getMessage() != null && e.getMessage().contains("Connection reset by peer"))) {
                        throw new ClientDisconnectedException();
                    }
                    throw e; // Re-throw other IOExceptions
                }
            };

            // 3. Delegate to the client to process the stream
            anthropicClient.chatCompletionStream(request, handler);

            // 4. AFTER the stream, check the state from the converter
            toolCallsDetected = AnthropicMessageConverter.wasToolUseDetectedInStream();

            // 5. *** THIS IS THE CRITICAL NEW LOGIC ***
            // Only send a message_stop event if the conversation truly ended.
            // If a tool call was used, the stream is expected to end without
            // a message_stop, so the client can continue the conversation.
            ObjectNode messageStop = mapper.createObjectNode();
            messageStop.put("type", "message_stop");
            writeSSE(outputStream, "message_stop", messageStop);
            System.out.println("No tool calls detected, sending message_stop event.");

        } catch (ClientDisconnectedException e) {
            System.out.println("Stream stopped because client disconnected.");
        } catch (Exception e) {
            System.err.println("Error in streaming message handler: " + e.getMessage());
            e.printStackTrace();
            // Optionally, write an error event to the stream if it's still open
        } finally {
            // 4. Always close the response stream
            try {
                System.out.println("Closing response output stream. Tool calls detected: " + toolCallsDetected);
                ctx.res().getOutputStream().close();
            } catch (IOException e) {
                // Ignore, the stream is likely already closed or broken.
            }
        }
    }

    private ObjectNode createMessageStartEvent(String messageId, String model) {
        ObjectNode messageStart = mapper.createObjectNode();
        messageStart.put("type", "message_start");

        ObjectNode message = mapper.createObjectNode();
        message.put("id", messageId);
        message.put("type", "message");
        message.put("role", "assistant");
        message.set("content", mapper.createArrayNode());
        message.put("model", model);
        message.putNull("stop_reason");
        message.putNull("stop_sequence");

        ObjectNode usage = mapper.createObjectNode();
        usage.put("input_tokens", 10); // Placeholder, actual value isn't critical here
        usage.put("output_tokens", 1); // Placeholder
        message.set("usage", usage);

        messageStart.set("message", message);
        return messageStart;
    }

    private static class ClientDisconnectedException extends IOException {
        public ClientDisconnectedException() {
            super("Client disconnected");
        }
    }

    /**
     * Handles GET /v1/models - Returns available models
     */
    public void handleModels(Context ctx) throws Exception {
        List<Map<String, Object>> models = new ArrayList<>();

        // Add configured models
        String mainModel = settings.normalModel;
        String smallModel = settings.smallModel;

        if (mainModel != null && !mainModel.trim().isEmpty()) {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("id", mainModel);
            model.put("object", "model");
            model.put("created", Instant.now().getEpochSecond());
            model.put("owned_by", "anthropic");
            models.add(model);
        }

        if (smallModel != null && !smallModel.trim().isEmpty() && !smallModel.equals(mainModel)) {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("id", smallModel);
            model.put("object", "model");
            model.put("created", Instant.now().getEpochSecond());
            model.put("owned_by", "anthropic");
            models.add(model);
        }

        // Add some default Claude models if none configured
        if (models.isEmpty()) {
            String[] defaultModels = {
                "claude-3-sonnet-20240229",
                "claude-3-haiku-20240307",
                "claude-3-opus-20240229"
            };

            for (String modelId : defaultModels) {
                Map<String, Object> model = new LinkedHashMap<>();
                model.put("id", modelId);
                model.put("object", "model");
                model.put("created", Instant.now().getEpochSecond());
                model.put("owned_by", "anthropic");
                models.add(model);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("object", "list");
        response.put("data", models);

        ctx.json(response);
    }

    // Note: Removed handleToolResponse method as we now implement proper Anthropic tool call flow
    // where streams complete normally and clients make new requests with tool results

    /**
     * Handles health check endpoint
     */
    public void handleHealth(Context ctx) {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "ok");
        health.put("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        health.put("service_type", settings.serviceType.toString());
        health.put("base_url", settings.baseUrl);
        health.put("api_key_configured", settings.apiKey != null && !settings.apiKey.trim().isEmpty());

        ctx.json(health);
    }

    /**
     * Utility method to write Server-Sent Events
     */
    private void writeSSE(OutputStream outputStream, String event, ObjectNode data) throws Exception {
        try {
            String sseMessage = "event: " + event + "\ndata: " + data.toString() + "\n\n";
            outputStream.write(sseMessage.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException e) {
            // Handle EofException gracefully - client may have disconnected
            if (e instanceof EofException || (e.getMessage() != null && e.getMessage().contains("Closed"))) {
                System.out.println("Client disconnected, can't write event: " + event);
            } else {
                throw e;  // Re-throw other exceptions
            }
        }
    }
}
