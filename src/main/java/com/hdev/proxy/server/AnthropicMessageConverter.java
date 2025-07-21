package com.hdev.proxy.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * Utility class for converting between Anthropic and OpenAI message formats.
 * Based on the JavaScript conversion logic provided.
 */
public class AnthropicMessageConverter {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Maps Anthropic model names to OpenRouter model IDs
     */
    public static String mapModel(String anthropicModel) {
        // If model already contains '/', it's an OpenRouter model ID - return as-is
        if (anthropicModel.contains("/")) {
            return anthropicModel;
        }

        // Handle null or empty model names
        if (anthropicModel == null || anthropicModel.trim().isEmpty()) {
            return "anthropic/claude-3.5-sonnet";
        }

        // Normalize model name to lowercase for comparison
        String normalizedModel = anthropicModel.toLowerCase();

        // Map Claude model names to OpenRouter format
        if (normalizedModel.contains("haiku")) {
            if (normalizedModel.contains("3.5")) {
                return "anthropic/claude-3.5-haiku";
            } else {
                return "anthropic/claude-3-haiku-20240307";
            }
        } else if (normalizedModel.contains("sonnet")) {
            if (normalizedModel.contains("3.5")) {
                return "anthropic/claude-3.5-sonnet";
            } else if (normalizedModel.contains("4")) {
                return "anthropic/claude-3.5-sonnet"; // Map sonnet-4 to 3.5-sonnet as it's more available
            } else {
                return "anthropic/claude-3-sonnet-20240229";
            }
        } else if (normalizedModel.contains("opus")) {
            return "anthropic/claude-3-opus-20240229";
        }

        // If no specific mapping found, return as-is (might be already correct)
        return anthropicModel;
    }

    /**
     * Validates OpenAI format messages to ensure complete tool_calls/tool message pairing.
     */
    public static ArrayNode validateOpenAIToolCalls(ArrayNode messages) {
        ArrayNode validatedMessages = mapper.createArrayNode();

        for (int i = 0; i < messages.size(); i++) {
            ObjectNode currentMessage = (ObjectNode) messages.get(i).deepCopy();

            // Process assistant messages with tool_calls
            if ("assistant".equals(currentMessage.path("role").asText()) && currentMessage.has("tool_calls")) {
                ArrayNode validToolCalls = mapper.createArrayNode();
                Set<String> removedToolCallIds = new HashSet<>();

                // Collect all immediately following tool messages
                List<JsonNode> immediateToolMessages = new ArrayList<>();
                int j = i + 1;
                while (j < messages.size() && "tool".equals(messages.get(j).path("role").asText())) {
                    immediateToolMessages.add(messages.get(j));
                    j++;
                }

                // For each tool_call, check if there's an immediately following tool message
                JsonNode toolCallsNode = currentMessage.get("tool_calls");
                if (toolCallsNode != null && toolCallsNode.isArray()) {
                    ArrayNode toolCalls = (ArrayNode) toolCallsNode;
                    for (JsonNode toolCall : toolCalls) {
                        String toolCallId = toolCall.path("id").asText();
                        boolean hasImmediateToolMessage = immediateToolMessages.stream()
                                .anyMatch(toolMsg -> toolCallId.equals(toolMsg.path("tool_call_id").asText()));

                        if (hasImmediateToolMessage) {
                            validToolCalls.add(toolCall);
                        } else {
                            removedToolCallIds.add(toolCallId);
                        }
                    }
                }

                // Update the assistant message
                if (validToolCalls.size() > 0) {
                    currentMessage.set("tool_calls", validToolCalls);
                } else {
                    currentMessage.remove("tool_calls");
                }

                // Only include message if it has content or valid tool_calls
                if (currentMessage.has("content") || currentMessage.has("tool_calls")) {
                    validatedMessages.add(currentMessage);
                }
            }
            // Process tool messages
            else if ("tool".equals(currentMessage.path("role").asText())) {
                boolean hasImmediateToolCall = false;
                String toolCallId = currentMessage.path("tool_call_id").asText();

                // Check if the immediately preceding assistant message has matching tool_call
                if (i > 0) {
                    JsonNode prevMessage = messages.get(i - 1);
                    if ("assistant".equals(prevMessage.path("role").asText()) && prevMessage.has("tool_calls")) {
                        JsonNode toolCallsNode = prevMessage.get("tool_calls");
                        if (toolCallsNode != null && toolCallsNode.isArray()) {
                            ArrayNode toolCalls = (ArrayNode) toolCallsNode;
                            for (JsonNode toolCall : toolCalls) {
                                if (toolCallId.equals(toolCall.path("id").asText())) {
                                    hasImmediateToolCall = true;
                                    break;
                                }
                            }
                        }
                    } else if ("tool".equals(prevMessage.path("role").asText())) {
                        // Check for assistant message before the sequence of tool messages
                        for (int k = i - 1; k >= 0; k--) {
                            if ("tool".equals(messages.get(k).path("role").asText())) continue;
                            if ("assistant".equals(messages.get(k).path("role").asText()) && messages.get(k).has("tool_calls")) {
                                JsonNode toolCallsNode = messages.get(k).get("tool_calls");
                                if (toolCallsNode != null && toolCallsNode.isArray()) {
                                    ArrayNode toolCalls = (ArrayNode) toolCallsNode;
                                    for (JsonNode toolCall : toolCalls) {
                                        if (toolCallId.equals(toolCall.path("id").asText())) {
                                            hasImmediateToolCall = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            break;
                        }
                    }
                }

                if (hasImmediateToolCall) {
                    validatedMessages.add(currentMessage);
                }
            }
            // For all other message types, include as-is
            else {
                validatedMessages.add(currentMessage);
            }
        }

        return validatedMessages;
    }

    /**
     * Converts Anthropic format request to OpenAI format
     */
    public static ObjectNode formatAnthropicToOpenAI(ObjectNode body) {
        String model = body.path("model").asText();
        JsonNode messagesNode = body.path("messages");
        ArrayNode messages = messagesNode.isArray() ? (ArrayNode) messagesNode : null;
        JsonNode system = body.path("system");
        Double temperature = body.has("temperature") ? body.path("temperature").asDouble() : null;
        JsonNode toolsNode = body.path("tools");
        ArrayNode tools = toolsNode.isArray() ? (ArrayNode) toolsNode : null;
        Boolean stream = body.has("stream") ? body.path("stream").asBoolean() : null;

        ArrayNode openAIMessages = messages != null ? convertAnthropicMessagesToOpenAI(messages) : mapper.createArrayNode();
        ArrayNode systemMessages = convertSystemMessages(system, model);

        ObjectNode data = mapper.createObjectNode();
        data.put("model", mapModel(model));

        // Combine system and regular messages
        ArrayNode allMessages = mapper.createArrayNode();
        allMessages.addAll(systemMessages);
        allMessages.addAll(openAIMessages);

        // Validate and set messages
        data.set("messages", validateOpenAIToolCalls(allMessages));

        if (temperature != null) {
            data.put("temperature", temperature);
        }
        if (stream != null) {
            data.put("stream", stream);
        }

        if (tools != null && tools.size() > 0) {
            ArrayNode openAITools = mapper.createArrayNode();
            for (JsonNode tool : tools) {
                ObjectNode openAITool = mapper.createObjectNode();
                openAITool.put("type", "function");
                ObjectNode function = mapper.createObjectNode();
                function.put("name", tool.path("name").asText());
                function.put("description", tool.path("description").asText());
                function.set("parameters", tool.path("input_schema"));
                openAITool.set("function", function);
                openAITools.add(openAITool);
            }
            data.set("tools", openAITools);
        }

        return data;
    }

    private static ArrayNode convertAnthropicMessagesToOpenAI(ArrayNode anthropicMessages) {
        ArrayNode openAIMessages = mapper.createArrayNode();

        for (JsonNode anthropicMessage : anthropicMessages) {
            String role = anthropicMessage.path("role").asText();
            JsonNode content = anthropicMessage.path("content");

            if (!content.isArray()) {
                if (content.isTextual()) {
                    ObjectNode openAIMessage = mapper.createObjectNode();
                    openAIMessage.put("role", role);
                    openAIMessage.put("content", content.asText());
                    openAIMessages.add(openAIMessage);
                }
                continue;
            }

            if ("assistant".equals(role)) {
                ObjectNode assistantMessage = mapper.createObjectNode();
                assistantMessage.put("role", "assistant");
                assistantMessage.putNull("content");

                StringBuilder textContent = new StringBuilder();
                ArrayNode toolCalls = mapper.createArrayNode();

                for (JsonNode contentPart : content) {
                    String type = contentPart.path("type").asText();
                    if ("text".equals(type)) {
                        JsonNode text = contentPart.path("text");
                        String textStr = text.isTextual() ? text.asText() : text.toString();
                        textContent.append(textStr).append("\n");
                    } else if ("tool_use".equals(type)) {
                        ObjectNode toolCall = mapper.createObjectNode();
                        toolCall.put("id", contentPart.path("id").asText());
                        toolCall.put("type", "function");
                        ObjectNode function = mapper.createObjectNode();
                        function.put("name", contentPart.path("name").asText());
                        function.put("arguments", contentPart.path("input").toString());
                        toolCall.set("function", function);
                        toolCalls.add(toolCall);
                    }
                }

                String trimmedTextContent = textContent.toString().trim();
                if (!trimmedTextContent.isEmpty()) {
                    assistantMessage.put("content", trimmedTextContent);
                }
                if (toolCalls.size() > 0) {
                    assistantMessage.set("tool_calls", toolCalls);
                }

                if (assistantMessage.has("content") || (assistantMessage.has("tool_calls") && toolCalls.size() > 0)) {
                    openAIMessages.add(assistantMessage);
                }
            } else if ("user".equals(role)) {
                StringBuilder userTextContent = new StringBuilder();
                List<ObjectNode> toolMessages = new ArrayList<>();

                // First pass: collect tool results and user text separately
                for (JsonNode contentPart : content) {
                    String type = contentPart.path("type").asText();
                    if ("text".equals(type)) {
                        JsonNode text = contentPart.path("text");
                        String textStr = text.isTextual() ? text.asText() : text.toString();
                        userTextContent.append(textStr).append("\n");
                    } else if ("tool_result".equals(type)) {
                        ObjectNode toolMessage = mapper.createObjectNode();
                        toolMessage.put("role", "tool");
                        toolMessage.put("tool_call_id", contentPart.path("tool_use_id").asText());
                        JsonNode toolContent = contentPart.path("content");
                        String contentStr = toolContent.isTextual() ? toolContent.asText() : toolContent.toString();
                        toolMessage.put("content", contentStr);
                        toolMessages.add(toolMessage);
                    }
                }

                // Add tool messages first (these should come after assistant tool_calls)
                for (ObjectNode toolMessage : toolMessages) {
                    openAIMessages.add(toolMessage);
                }

                // Only add user message if there's actual text content AND no tool results
                // If there were tool results, the conversation flow should be:
                // assistant (with tool_calls) -> tool -> assistant (response) -> user (next message)
                String trimmedUserText = userTextContent.toString().trim();
                if (!trimmedUserText.isEmpty()) {
                    // If we just added tool messages, we need an assistant response before the next user message
                    if (!toolMessages.isEmpty()) {
                        ObjectNode assistantResponse = mapper.createObjectNode();
                        assistantResponse.put("role", "assistant");
                        assistantResponse.put("content", "I've processed the tool results.");
                        openAIMessages.add(assistantResponse);
                    }

                    ObjectNode userMessage = mapper.createObjectNode();
                    userMessage.put("role", "user");
                    userMessage.put("content", trimmedUserText);
                    openAIMessages.add(userMessage);
                }
            }
        }

        return openAIMessages;
    }

    private static ArrayNode convertSystemMessages(JsonNode system, String model) {
        ArrayNode systemMessages = mapper.createArrayNode();

        if (system.isArray()) {
            for (JsonNode item : system) {
                ObjectNode systemMessage = createSystemMessage(item.path("text").asText(), model);
                systemMessages.add(systemMessage);
            }
        } else if (system.isTextual()) {
            ObjectNode systemMessage = createSystemMessage(system.asText(), model);
            systemMessages.add(systemMessage);
        }

        return systemMessages;
    }

    private static ObjectNode createSystemMessage(String text, String model) {
        ObjectNode systemMessage = mapper.createObjectNode();
        systemMessage.put("role", "system");

        ArrayNode contentArray = mapper.createArrayNode();
        ObjectNode contentItem = mapper.createObjectNode();
        contentItem.put("type", "text");
        contentItem.put("text", text);

        if (model.contains("claude")) {
            ObjectNode cacheControl = mapper.createObjectNode();
            cacheControl.put("type", "ephemeral");
            contentItem.set("cache_control", cacheControl);
        }

        contentArray.add(contentItem);
        systemMessage.set("content", contentArray);

        return systemMessage;
    }

    /**
     * Converts OpenAI completion response to Anthropic format
     */
    public static ObjectNode formatOpenAIToAnthropic(ObjectNode completion, String model) {
        String messageId = "msg_" + System.currentTimeMillis();

        ArrayNode content = mapper.createArrayNode();
        JsonNode choice = completion.path("choices").get(0);
        JsonNode message = choice.path("message");

        if (message.has("content") && !message.path("content").isNull()) {
            ObjectNode textContent = mapper.createObjectNode();
            textContent.put("text", message.path("content").asText());
            textContent.put("type", "text");
            content.add(textContent);
        } else if (message.has("tool_calls")) {
            JsonNode toolCallsNode = message.path("tool_calls");
            if (toolCallsNode.isArray()) {
                ArrayNode toolCalls = (ArrayNode) toolCallsNode;
                for (JsonNode toolCall : toolCalls) {
                    ObjectNode toolUse = mapper.createObjectNode();
                    toolUse.put("type", "tool_use");
                    toolUse.put("id", toolCall.path("id").asText());
                    toolUse.put("name", toolCall.path("function").path("name").asText());

                    String argumentsStr = toolCall.path("function").path("arguments").asText();
                    try {
                        JsonNode input = mapper.readTree(argumentsStr);
                        toolUse.set("input", input);
                    } catch (Exception e) {
                        System.err.println("Failed to parse tool arguments: " + e.getMessage());
                        toolUse.set("input", mapper.createObjectNode());
                    }
                    content.add(toolUse);
                }
            }
        }

        ObjectNode result = mapper.createObjectNode();
        result.put("id", messageId);
        result.put("type", "message");
        result.put("role", "assistant");
        result.set("content", content);

        String finishReason = choice.path("finish_reason").asText();
        result.put("stop_reason", "tool_calls".equals(finishReason) ? "tool_use" : "end_turn");
        result.putNull("stop_sequence");
        result.put("model", model);

        return result;
    }

    // State tracking for streaming tool calls
    private static class StreamState {
        boolean messageStartSent = false;
        boolean contentBlockOpen = false;
        boolean isToolUse = false;
        int contentBlockIndex = 0;
    }

    private static final ThreadLocal<StreamState> streamState = new ThreadLocal<StreamState>() {
        @Override
        protected StreamState initialValue() {
            return new StreamState();
        }
    };

    private static final ThreadLocal<Boolean> toolUseDetectedInStream = ThreadLocal.withInitial(() -> false);

    /**
     * Checks if a tool use event was processed during the current stream's lifecycle.
     * @return true if a tool call was detected, false otherwise.
     */
    public static boolean wasToolUseDetectedInStream() {
        return toolUseDetectedInStream.get();
    }

    /**
     * Reset stream state for new streaming request
     */
    public static void resetStreamState() {
        streamState.remove();
        toolUseDetectedInStream.remove();
    }

    /**
     * Converts OpenAI streaming chunk to Anthropic streaming format
     * Now with proper content block management for tool calls
     */
    public static String convertOpenAIStreamChunkToAnthropic(String openAIChunk, String model) {
        try {
            // FINAL TERMINATION: [DONE] is the definitive end of the stream. Its only job
            // is to send the final message_stop event.
            if ("[DONE]".equals(openAIChunk.trim())) {
                StreamState state = streamState.get();

                // Ensure the final content block is closed if it was left open.
                if (state.contentBlockOpen) {
                    return createContentBlockStopEvent(state);
                }

                return null;
            }

            StreamState state = streamState.get();
            StringBuilder result = new StringBuilder();
            JsonNode chunkJson = mapper.readTree(openAIChunk);

            JsonNode choices = chunkJson.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null; // Ignore chunks without choices (like pings)
            }

            JsonNode choice = choices.get(0);
            JsonNode delta = choice.path("delta");

            // --- Step 1: Process Content of the Chunk ---
            // This now uses independent 'if' statements instead of 'if-else-if'

            // First message only: Send message_start event
            if (delta.has("role") && !state.messageStartSent) {
                result.append(createMessageStartEvent(model));
                state.messageStartSent = true;
            }

            // Handle text content
            if (delta.has("content") && !delta.path("content").isNull()) {
                String content = delta.path("content").asText();
                if (!content.isEmpty()) {
                    if (!state.contentBlockOpen) {
                        result.append(createContentBlockStartEvent(state, "text", null, null));
                    }
                    result.append(createTextDeltaEvent(state, content));
                }
            }

            // Handle tool calls
            if (delta.has("tool_calls")) {
                toolUseDetectedInStream.set(true);
                for (JsonNode toolCall : delta.path("tool_calls")) {
                    JsonNode function = toolCall.path("function");
                    // New tool call starts a new content block
                    if (function.has("name")) {
                        // Close previous block if it was open
                        if (state.contentBlockOpen) {
                            result.append(createContentBlockStopEvent(state));
                        }
                        String toolId = toolCall.path("id").asText();
                        String toolName = function.path("name").asText();
                        result.append(createContentBlockStartEvent(state, "tool_use", toolId, toolName));
                        state.isToolUse = true;
                    }
                    // Stream arguments for the current tool
                    if (function.has("arguments")) {
                        String args = function.path("arguments").asText();
                        if (!args.isEmpty()) {
                            result.append(createToolDeltaEvent(state, args));
                        }
                    }
                }
            }

            // --- Step 2: Check for Finish Reason in the SAME Chunk ---
            JsonNode finishReasonNode = choice.path("finish_reason");
            if (finishReasonNode.isTextual() && !finishReasonNode.asText().equals("null")) {
                String finishReason = finishReasonNode.asText();

                // Close the last open content block
                if (state.contentBlockOpen) {
                    result.append(createContentBlockStopEvent(state));
                }

                // Send the message_delta with the stop reason
                String anthropicStopReason = "tool_calls".equals(finishReason) ? "tool_use" : "end_turn";
                result.append(createMessageDeltaStopEvent(anthropicStopReason));
            }

            return result.toString();

        } catch (Exception e) {
            System.err.println("Error converting OpenAI stream chunk to Anthropic: " + e.getMessage());
            System.err.println("Chunk content that caused error: " + openAIChunk);
            e.printStackTrace();
            return null; // Return null on error
        }
    }

    // --- Helper methods for creating SSE events ---

    private static String createMessageStartEvent(String model) {
        ObjectNode startChunk = mapper.createObjectNode();
        startChunk.put("type", "message_start");
        ObjectNode messageNode = mapper.createObjectNode();
        messageNode.put("id", "msg_" + System.currentTimeMillis());
        messageNode.put("type", "message");
        messageNode.put("role", "assistant");
        messageNode.set("content", mapper.createArrayNode());
        messageNode.put("model", model);
        messageNode.putNull("stop_reason");
        messageNode.putNull("stop_sequence");
        ObjectNode usageNode = mapper.createObjectNode();
        usageNode.put("input_tokens", 1);
        usageNode.put("output_tokens", 1);
        messageNode.set("usage", usageNode);
        startChunk.set("message", messageNode);
        return "event: message_start\ndata: " + startChunk.toString() + "\n\n";
    }

    private static String createContentBlockStartEvent(StreamState state, String type, String toolId, String toolName) {
        ObjectNode contentBlockStart = mapper.createObjectNode();
        contentBlockStart.put("type", "content_block_start");
        contentBlockStart.put("index", state.contentBlockIndex);

        ObjectNode contentBlock = mapper.createObjectNode();
        contentBlock.put("type", type);
        if ("tool_use".equals(type)) {
            contentBlock.put("id", toolId);
            contentBlock.put("name", toolName);
            contentBlock.set("input", mapper.createObjectNode());
        } else {
            contentBlock.put("text", "");
        }
        contentBlockStart.set("content_block", contentBlock);

        state.contentBlockOpen = true;
        state.isToolUse = "tool_use".equals(type);
        return "event: content_block_start\ndata: " + contentBlockStart.toString() + "\n\n";
    }

    private static String createTextDeltaEvent(StreamState state, String text) {
        ObjectNode delta = mapper.createObjectNode();
        delta.put("type", "text_delta");
        delta.put("text", text);
        return createContentBlockDeltaEvent(state, delta);
    }

    private static String createToolDeltaEvent(StreamState state, String partialJson) {
        ObjectNode delta = mapper.createObjectNode();
        delta.put("type", "input_json_delta");
        delta.put("partial_json", partialJson);
        return createContentBlockDeltaEvent(state, delta);
    }

    private static String createContentBlockDeltaEvent(StreamState state, JsonNode delta) {
        ObjectNode contentBlockDelta = mapper.createObjectNode();
        contentBlockDelta.put("type", "content_block_delta");
        contentBlockDelta.put("index", state.contentBlockIndex);
        contentBlockDelta.set("delta", delta);
        return "event: content_block_delta\ndata: " + contentBlockDelta.toString() + "\n\n";
    }

    private static String createContentBlockStopEvent(StreamState state) {
        ObjectNode contentBlockStop = mapper.createObjectNode();
        contentBlockStop.put("type", "content_block_stop");
        contentBlockStop.put("index", state.contentBlockIndex);
        state.contentBlockOpen = false;
        state.contentBlockIndex++; // Increment index for the *next* block
        return "event: content_block_stop\ndata: " + contentBlockStop.toString() + "\n\n";
    }

    private static String createMessageDeltaStopEvent(String stopReason) {
        ObjectNode messageDelta = mapper.createObjectNode();
        messageDelta.put("type", "message_delta");
        ObjectNode deltaNode = mapper.createObjectNode();
        deltaNode.put("stop_reason", stopReason);
        deltaNode.putNull("stop_sequence");
        messageDelta.set("delta", deltaNode);
        ObjectNode usage = mapper.createObjectNode();
        usage.put("output_tokens", 1);
        messageDelta.set("usage", usage);
        return "event: message_delta\ndata: " + messageDelta.toString() + "\n\n";
    }
}
