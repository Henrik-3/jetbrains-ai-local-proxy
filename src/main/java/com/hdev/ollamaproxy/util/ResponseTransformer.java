package com.hdev.ollamaproxy.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdev.ollamaproxy.model.OpenAIChatResponse;
import com.hdev.ollamaproxy.model.OpenAIChoice;
import com.hdev.ollamaproxy.model.OpenAIDelta;
import com.hdev.ollamaproxy.model.OpenAIModel;
import com.hdev.ollamaproxy.model.ModelsResponse;
import com.hdev.ollamaproxy.model.OllamaModelTag;
import com.hdev.ollamaproxy.model.OllamaTagsResponse;
import com.intellij.openapi.diagnostic.Logger;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for transforming OpenAI API responses to LM Studio format
 */
public class ResponseTransformer {
    private static final Logger LOG = Logger.getInstance(ResponseTransformer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Transforms an OpenAI chat completion response if necessary (e.g., for streaming).
     * Currently, it mainly handles the structure for streaming vs non-streaming.
     * The output format aims to be compatible with what JetBrains AI expects from an Ollama-like endpoint.
     */
    public static String transformChatCompletionResponse(String openAIResponse) {
        try {
            // Check if this is a streaming response (contains "data: " prefix) FIRST
            if (openAIResponse.trim().startsWith("data: ")) {
                return transformStreamingResponse(openAIResponse);
            }

            // Try to parse as regular JSON response
            JsonNode rootNode = objectMapper.readTree(openAIResponse);

            // Handle regular (non-streaming) response
            return transformRegularResponse(rootNode);

        } catch (Exception e) {
            LOG.error("Failed to transform OpenAI response to LM Studio format", e);
            return openAIResponse; // Return original response if transformation fails
        }
    }

    /**
     * Transforms a regular (non-streaming) OpenAI response to a format expected by the client.
     */
    private static String transformRegularResponse(JsonNode openAINode) throws Exception {
        OpenAIChatResponse lmResponse = new OpenAIChatResponse(); // Renamed
        
        // Set basic fields
        lmResponse.setId(generateChatId());
        lmResponse.setObject("chat.completion.chunk"); // This object type might need to change for Ollama
        lmResponse.setCreated(System.currentTimeMillis() / 1000);
        lmResponse.setModel(openAINode.has("model") ? openAINode.get("model").asText() : "unknown");
        lmResponse.setSystemFingerprint(lmResponse.getModel()); // Ollama might not use/expect this
        
        // Transform choices
        List<OpenAIChoice> lmChoices = new ArrayList<>(); // Renamed
        if (openAINode.has("choices")) {
            JsonNode choicesNode = openAINode.get("choices");
            for (int i = 0; i < choicesNode.size(); i++) {
                JsonNode choiceNode = choicesNode.get(i);
                OpenAIChoice lmChoice = new OpenAIChoice(); // Renamed
                
                lmChoice.setIndex(i);
                lmChoice.setLogprobs(null); // Ollama might not use/expect this
                lmChoice.setFinishReason(choiceNode.has("finish_reason") ? 
                    choiceNode.get("finish_reason").asText() : "stop");
                
                // Create delta from message content
                OpenAIDelta delta = new OpenAIDelta(); // Renamed
                if (choiceNode.has("message")) {
                    JsonNode messageNode = choiceNode.get("message");
                    if (messageNode.has("content")) {
                        delta.setContent(messageNode.get("content").asText());
                    }
                    if (messageNode.has("role")) {
                        delta.setRole(messageNode.get("role").asText());
                    }
                } else {
                    delta = new OpenAIDelta(); // Renamed // Empty delta for finish
                }
                
                lmChoice.setDelta(delta);
                lmChoices.add(lmChoice);
            }
        }
        
        lmResponse.setChoices(lmChoices);
        
        return objectMapper.writeValueAsString(lmResponse);
    }

    /**
     * Transforms a streaming OpenAI response line by line to a format expected by the client.
     */
    private static String transformStreamingResponse(String openAIResponse) throws Exception {
        StringBuilder result = new StringBuilder();
        String[] lines = openAIResponse.split("\n");
        
        for (String line : lines) {
            if (line.trim().startsWith("data: ")) {
                String jsonData = line.substring(6).trim(); // Remove "data: " prefix
                
                if ("[DONE]".equals(jsonData)) {
                    result.append("data: [DONE]\n\n");
                    continue;
                }
                
                try {
                    JsonNode chunkNode = objectMapper.readTree(jsonData);
                    String transformedChunk = transformStreamingChunk(chunkNode);
                    result.append("data: ").append(transformedChunk).append("\n\n");
                } catch (Exception e) {
                    LOG.warn("Failed to transform streaming chunk: " + jsonData, e);
                    result.append(line).append("\n"); // Keep original if transformation fails
                }
            } else {
                result.append(line).append("\n");
            }
        }
        
        return result.toString();
    }

    /**
     * Transforms a single streaming chunk to a format expected by the client.
     */
    private static String transformStreamingChunk(JsonNode openAIChunk) throws Exception {
        ObjectNode lmChunk = objectMapper.createObjectNode();
        
        // Set basic fields
        lmChunk.put("id", generateChatId());
        lmChunk.put("object", "chat.completion.chunk");
        lmChunk.put("created", System.currentTimeMillis() / 1000);
        lmChunk.put("model", openAIChunk.has("model") ? openAIChunk.get("model").asText() : "unknown");
        lmChunk.put("system_fingerprint", lmChunk.get("model").asText());
        
        // Transform choices
        if (openAIChunk.has("choices")) {
            lmChunk.set("choices", openAIChunk.get("choices"));
        }
        
        return objectMapper.writeValueAsString(lmChunk);
    }

    /**
     * Generates a unique chat completion ID, similar to OpenAI's format.
     */
    private static String generateChatId() {
        return "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    /**
     * Transforms an OpenAI /v1/models style response (ModelsResponse) to Ollama /api/tags style response (OllamaTagsResponse)
     */
    public static OllamaTagsResponse transformToOllamaTagsResponse(ModelsResponse openAIModelsResponse) {
        if (openAIModelsResponse == null || openAIModelsResponse.getData() == null) {
            return new OllamaTagsResponse(new ArrayList<>());
        }

        List<OllamaModelTag> ollamaModelTags = new ArrayList<>();
        for (OpenAIModel openAIModel : openAIModelsResponse.getData()) {
            String name = openAIModel.getId(); // Typically, OpenAI model ID is the name Ollama expects.
            // Ollama's modified_at is ISO 8601. OpenAI doesn't provide this directly.
            // We'll use the current time as a placeholder or a fixed old date.
            // A more sophisticated approach might involve trying to get this from headers if available,
            // or storing it if the model was downloaded locally by this proxy (not current scope).
            String modifiedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atZone(ZoneOffset.UTC));
            long size = 0; // OpenAI API doesn't provide model size. Placeholder.

            // Some OpenAI compatible APIs might provide 'created' timestamp for model.
            // If openAIModel has a getCreated() long timestamp, we could use it.
            // For now, using current time.

            ollamaModelTags.add(new OllamaModelTag(name, modifiedAt, size, null)); // digest is also not typically available
        }
        return new OllamaTagsResponse(ollamaModelTags);
    }
}
