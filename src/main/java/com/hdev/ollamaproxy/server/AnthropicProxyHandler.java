package com.hdev.ollamaproxy.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdev.ollamaproxy.config.AppSettingsState;
import org.eclipse.jetty.io.EofException;
import java.io.IOException;
import io.javalin.http.Context;

import java.io.OutputStream;
import java.util.concurrent.ConcurrentHashMap;
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

    // Map to store active streams with tool calls
    private final ConcurrentHashMap<String, OutputStream> activeToolStreams = new ConcurrentHashMap<>();

    public AnthropicProxyHandler() {
        this.anthropicClient = new AnthropicClient();
        this.settings = AppSettingsState.getInstance();
    }

    /**
     * Handles POST /v1/messages - Anthropic's chat completions endpoint
     */
    public void handleMessages(Context ctx) throws Exception {
        // Check if this is a tool response
        if (ctx.header("X-Tool-Response-For") != null) {
            handleToolResponse(ctx);
            return;
        }

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

        if (stream) {
            handleMessagesStream(ctx, request);
        } else {
            handleMessagesNonStream(ctx, request);
        }
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
    private void handleMessagesStream(Context ctx, ObjectNode request) throws Exception {
        ctx.header("Content-Type", "text/event-stream");
        ctx.header("Cache-Control", "no-cache");
        ctx.header("Connection", "keep-alive");
        ctx.header("Access-Control-Allow-Origin", "*");
        ctx.res().setBufferSize(0);

        OutputStream outputStream = ctx.res().getOutputStream();
        // Flag to track if tool calls are detected in the response
        boolean hasToolCalls = false;

        // Store the stream ID for potential tool call responses
        String streamId = "stream_" + System.currentTimeMillis();
        // Add to response headers so client can use it for tool responses
        ctx.header("X-Stream-ID", streamId);

        try {
            String messageId = "msg_" + System.currentTimeMillis();
            String model = request.path("model").asText();

            // Store the stream for potential tool call responses
            activeToolStreams.put(streamId, outputStream);

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
            usage.put("input_tokens", 10);
            usage.put("output_tokens", 0);
            message.set("usage", usage);

            messageStart.set("message", message);

            writeSSE(outputStream, "message_start", messageStart);

            // Send content_block_start event
            ObjectNode contentBlockStart = mapper.createObjectNode();
            contentBlockStart.put("type", "content_block_start");
            contentBlockStart.put("index", 0);

            ObjectNode contentBlock = mapper.createObjectNode();
            contentBlock.put("type", "text");
            contentBlock.put("text", "");
            contentBlockStart.set("content_block", contentBlock);

            writeSSE(outputStream, "content_block_start", contentBlockStart);

            // Stream the actual content with proper error handling
            try {
                // Use the return value to determine if tool calls were detected
                hasToolCalls = anthropicClient.chatCompletionStream(request, chunk -> {
                    try {
                        outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    } catch (IOException e) {
                        // Jetty throws EofException when client disconnects.
                        if (e instanceof EofException
                            || (e.getMessage() != null && e.getMessage().contains("Connection reset by peer"))) {
                            // silent stop: client went away
                            return;
                        }
                        // for any other I/O error, log and abort
                        System.err.println("Error writing stream chunk: " + e.getMessage());
                        throw new RuntimeException("Error writing stream chunk", e);
                    }
                });
            } catch (Exception e) {
                System.err.println("Error in streaming chat completion: " + e.getMessage());
                e.printStackTrace();

                // Send error event
                ObjectNode error = mapper.createObjectNode();
                error.put("type", "error");
                ObjectNode errorDetails = mapper.createObjectNode();
                errorDetails.put("type", "api_error");
                errorDetails.put("message", "Error processing stream: " + e.getMessage());
                error.set("error", errorDetails);
                writeSSE(outputStream, "error", error);
                return;
            }

            // Send appropriate ending events only if we're not in a tool call scenario
            if (!hasToolCalls) {
                // Send content_block_stop event
                ObjectNode contentBlockStop = mapper.createObjectNode();
                contentBlockStop.put("type", "content_block_stop");
                contentBlockStop.put("index", 0);
                writeSSE(outputStream, "content_block_stop", contentBlockStop);

                // Send message_delta event
                ObjectNode messageDelta = mapper.createObjectNode();
                messageDelta.put("type", "message_delta");

                ObjectNode delta = mapper.createObjectNode();
                delta.put("stop_reason", "end_turn");
                delta.putNull("stop_sequence");
                messageDelta.set("delta", delta);

                ObjectNode deltaUsage = mapper.createObjectNode();
                deltaUsage.put("output_tokens", 100);
                messageDelta.set("usage", deltaUsage);

                writeSSE(outputStream, "message_delta", messageDelta);

                // Send message_stop event
                ObjectNode messageStop = mapper.createObjectNode();
                messageStop.put("type", "message_stop");
                writeSSE(outputStream, "message_stop", messageStop);
            } else {
                System.out.println("Tool calls detected, skipping ending events to keep stream open");
            }

        } catch (Exception e) {
            System.err.println("Error in streaming message handler: " + e.getMessage());
            e.printStackTrace();
            // Send error event
            ObjectNode error = mapper.createObjectNode();
            error.put("type", "error");
            ObjectNode errorDetails = mapper.createObjectNode();
            errorDetails.put("type", "api_error");
            errorDetails.put("message", "Internal server error: " + e.getMessage());
            error.set("error", errorDetails);
            writeSSE(outputStream, "error", error);
        } finally {
            // Only close the stream if no tool calls were detected
            if (!hasToolCalls) {
                try {
                    System.out.println("No tool calls detected, closing the stream");
                    outputStream.close();
                    activeToolStreams.remove(streamId);
                } catch (Exception e) {
                    // Ignore close errors
                }
            } else {
                System.out.println("Tool calls detected, keeping the stream open for continuation");

                // Write an event to inform client that tool call processing is needed
                ObjectNode toolCallWaiting = mapper.createObjectNode();
                toolCallWaiting.put("type", "content_block_delta");
                toolCallWaiting.put("index", 0);

                ObjectNode delta = mapper.createObjectNode();
                delta.put("type", "tool_calls_waiting");
                delta.put("message", "Waiting for tool call response");
                toolCallWaiting.set("delta", delta);

                writeSSE(outputStream, "content_block_delta", toolCallWaiting);

                // Note: we don't close the stream here
                // The stream will be managed by the handleToolResponse method
                // and closed when the full response cycle completes
            }
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

    /**
     * Handles tool response submissions which continue an existing stream
     */
    public void handleToolResponse(Context ctx) throws Exception {
        String streamId = ctx.header("X-Tool-Response-For");
        if (streamId == null || streamId.isEmpty()) {
            ctx.status(400).json(Map.of("error", "Missing X-Tool-Response-For header"));
            return;
        }

        OutputStream outputStream = activeToolStreams.get(streamId);
        if (outputStream == null) {
            ctx.status(404).json(Map.of("error", "No active stream found for the given ID"));
            return;
        }

        try {
            ObjectNode request = mapper.readValue(ctx.body(), ObjectNode.class);

            if (!request.has("tool_outputs") || !request.path("tool_outputs").isArray()) {
                ctx.status(400).json(Map.of("error", "Missing or invalid tool_outputs in request"));
                return;
            }

            // Extract tool outputs from request
            JsonNode toolOutputs = request.get("tool_outputs");

            // Write each tool result to the stream
            for (int i = 0; i < toolOutputs.size(); i++) {
                JsonNode toolOutput = toolOutputs.get(i);

                ObjectNode toolResultContent = mapper.createObjectNode();
                toolResultContent.put("type", "tool_result");
                toolResultContent.put("index", i);
                toolResultContent.put("tool_call_id", toolOutput.path("tool_call_id").asText());

                ObjectNode toolResult = mapper.createObjectNode();
                toolResult.put("type", "tool_result");
                toolResult.set("content", toolOutput.path("output"));

                toolResultContent.set("tool_result", toolResult);

                writeSSE(outputStream, "content_block_delta", toolResultContent);
            }

            // Continue generation with the tool results
            ObjectNode continuationRequest = mapper.createObjectNode();
            continuationRequest.put("model", request.path("model").asText());
            continuationRequest.put("stream", true);
            continuationRequest.set("tool_outputs", toolOutputs);

            // If original messages are available in the stored context, include them
            if (request.has("messages")) {
                continuationRequest.set("messages", request.get("messages"));
            }

            // Continue the stream with the new request
            boolean hasMoreToolCalls = anthropicClient.chatCompletionStream(continuationRequest, chunk -> {
                try {
                    outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } catch (IOException e) {
                    if (e instanceof EofException || (e.getMessage() != null && e.getMessage().contains("Connection reset by peer"))) {
                        // silent stop: client went away
                        return;
                    }
                    System.err.println("Error writing continuation chunk: " + e.getMessage());
                    throw new RuntimeException("Error writing continuation chunk", e);
                }
            });

            // If no more tool calls, finalize the stream
            if (!hasMoreToolCalls) {
                // Send content_block_stop event
                ObjectNode contentBlockStop = mapper.createObjectNode();
                contentBlockStop.put("type", "content_block_stop");
                contentBlockStop.put("index", 0);
                writeSSE(outputStream, "content_block_stop", contentBlockStop);

                // Send message_delta event
                ObjectNode messageDelta = mapper.createObjectNode();
                messageDelta.put("type", "message_delta");

                ObjectNode delta = mapper.createObjectNode();
                delta.put("stop_reason", "end_turn");
                delta.putNull("stop_sequence");
                messageDelta.set("delta", delta);

                ObjectNode deltaUsage = mapper.createObjectNode();
                deltaUsage.put("output_tokens", 100);
                messageDelta.set("usage", deltaUsage);

                writeSSE(outputStream, "message_delta", messageDelta);

                // Send message_stop event
                ObjectNode messageStop = mapper.createObjectNode();
                messageStop.put("type", "message_stop");
                writeSSE(outputStream, "message_stop", messageStop);

                // Close and remove stream
                outputStream.close();
                activeToolStreams.remove(streamId);
            }

            ctx.status(200).json(Map.of("status", "success"));
        } catch (Exception e) {
            System.err.println("Error handling tool response: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Internal server error: " + e.getMessage()));

            // Clean up the stream on error
            try {
                outputStream.close();
            } catch (Exception closeError) {
                // Ignore
            }
            activeToolStreams.remove(streamId);
        }
    }

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
