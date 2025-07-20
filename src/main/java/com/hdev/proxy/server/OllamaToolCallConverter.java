package com.hdev.proxy.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Utility class for converting tool calls between Ollama and OpenAI formats.
 */
public class OllamaToolCallConverter {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Converts Ollama tool call format to OpenAI format for upstream requests.
     * Ollama uses a different tool format than OpenAI, so we need to convert.
     */
    public static ObjectNode convertOllamaToOpenAI(ObjectNode ollamaRequest) {
        ObjectNode openAIRequest = ollamaRequest.deepCopy();

        // Convert tools if present
        if (ollamaRequest.has("tools")) {
            ArrayNode ollamaTools = (ArrayNode) ollamaRequest.get("tools");
            ArrayNode openAITools = mapper.createArrayNode();

            for (JsonNode tool : ollamaTools) {
                ObjectNode openAITool = mapper.createObjectNode();
                openAITool.put("type", "function");

                ObjectNode function = mapper.createObjectNode();
                function.put("name", tool.get("function").get("name").asText());
                function.put("description", tool.get("function").path("description").asText(""));

                if (tool.get("function").has("parameters")) {
                    function.set("parameters", tool.get("function").get("parameters"));
                }

                openAITool.set("function", function);
                openAITools.add(openAITool);
            }

            openAIRequest.set("tools", openAITools);
        }

        // Convert tool_choice if present
        if (ollamaRequest.has("tool_choice")) {
            openAIRequest.set("tool_choice", ollamaRequest.get("tool_choice"));
        }

        return openAIRequest;
    }

    /**
     * Converts OpenAI tool call response format back to Ollama format.
     */
    public static ObjectNode convertOpenAIToOllama(ObjectNode openAIResponse, String model) {
        ObjectNode ollamaResponse = mapper.createObjectNode();

        ollamaResponse.put("model", model);
        ollamaResponse.put("created_at", java.time.Instant.now().toString());
        ollamaResponse.put("done", true);

        // Handle choices array
        if (openAIResponse.has("choices") && openAIResponse.get("choices").isArray()) {
            JsonNode choice = openAIResponse.get("choices").get(0);
            JsonNode message = choice.get("message");

            ObjectNode ollamaMessage = mapper.createObjectNode();
            ollamaMessage.put("role", "assistant");

            // Add content if present
            if (message.has("content") && !message.get("content").isNull()) {
                ollamaMessage.put("content", message.get("content").asText());
            } else {
                ollamaMessage.put("content", "");
            }

            // Convert tool_calls if present
            if (message.has("tool_calls")) {
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

            // Add finish_reason
            if (choice.has("finish_reason")) {
                ollamaResponse.put("finish_reason", choice.get("finish_reason").asText());
            }
        }

        return ollamaResponse;
    }

    /**
     * Converts streaming OpenAI tool call chunk to Ollama format.
     */
    public static ObjectNode convertStreamChunkToOllama(ObjectNode openAIChunk, String model) {
        ObjectNode ollamaChunk = mapper.createObjectNode();

        ollamaChunk.put("model", model);
        ollamaChunk.put("created_at", java.time.Instant.now().toString());
        ollamaChunk.put("done", false);

        if (openAIChunk.has("choices") && openAIChunk.get("choices").isArray()) {
            JsonNode choice = openAIChunk.get("choices").get(0);
            JsonNode delta = choice.get("delta");

            ObjectNode ollamaMessage = mapper.createObjectNode();
            ollamaMessage.put("role", "assistant");

            // Handle content delta
            if (delta.has("content") && !delta.get("content").isNull()) {
                ollamaMessage.put("content", delta.get("content").asText());
            } else {
                ollamaMessage.put("content", "");
            }

            // Handle tool_calls delta
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
                ollamaMessage.set("tool_calls", toolCalls);
            }

            ollamaChunk.set("message", ollamaMessage);

            // Check if stream is done
            if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
                ollamaChunk.put("done", true);
                ollamaChunk.put("finish_reason", choice.get("finish_reason").asText());
            }
        }

        return ollamaChunk;
    }

    /**
     * Checks if a request contains tool calls.
     */
    public static boolean hasToolCalls(ObjectNode request) {
        return request.has("tools") || request.has("tool_choice");
    }

    /**
     * Checks if a response contains tool calls.
     */
    public static boolean responseHasToolCalls(JsonNode response) {
        if (response.has("choices") && response.get("choices").isArray()) {
            JsonNode choice = response.get("choices").get(0);
            if (choice.has("message")) {
                return choice.get("message").has("tool_calls");
            }
            if (choice.has("delta")) {
                return choice.get("delta").has("tool_calls");
            }
        }
        return false;
    }
}
