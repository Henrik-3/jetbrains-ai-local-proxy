package com.hdev.lmstudioproxy.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdev.lmstudioproxy.model.LMStudioChatResponse;
import com.hdev.lmstudioproxy.model.LMStudioChoice;
import com.hdev.lmstudioproxy.model.LMStudioDelta;
import com.intellij.openapi.diagnostic.Logger;

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
     * Transforms an OpenAI chat completion response to LM Studio format
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
     * Transforms a regular (non-streaming) OpenAI response to LM Studio format
     */
    private static String transformRegularResponse(JsonNode openAINode) throws Exception {
        LMStudioChatResponse lmResponse = new LMStudioChatResponse();
        
        // Set basic fields
        lmResponse.setId(generateChatId());
        lmResponse.setObject("chat.completion.chunk");
        lmResponse.setCreated(System.currentTimeMillis() / 1000);
        lmResponse.setModel(openAINode.has("model") ? openAINode.get("model").asText() : "unknown");
        lmResponse.setSystemFingerprint(lmResponse.getModel());
        
        // Transform choices
        List<LMStudioChoice> lmChoices = new ArrayList<>();
        if (openAINode.has("choices")) {
            JsonNode choicesNode = openAINode.get("choices");
            for (int i = 0; i < choicesNode.size(); i++) {
                JsonNode choiceNode = choicesNode.get(i);
                LMStudioChoice lmChoice = new LMStudioChoice();
                
                lmChoice.setIndex(i);
                lmChoice.setLogprobs(null);
                lmChoice.setFinishReason(choiceNode.has("finish_reason") ? 
                    choiceNode.get("finish_reason").asText() : "stop");
                
                // Create delta from message content
                LMStudioDelta delta = new LMStudioDelta();
                if (choiceNode.has("message")) {
                    JsonNode messageNode = choiceNode.get("message");
                    if (messageNode.has("content")) {
                        delta.setContent(messageNode.get("content").asText());
                    }
                    if (messageNode.has("role")) {
                        delta.setRole(messageNode.get("role").asText());
                    }
                } else {
                    delta = new LMStudioDelta(); // Empty delta for finish
                }
                
                lmChoice.setDelta(delta);
                lmChoices.add(lmChoice);
            }
        }
        
        lmResponse.setChoices(lmChoices);
        
        return objectMapper.writeValueAsString(lmResponse);
    }

    /**
     * Transforms streaming OpenAI response to LM Studio format
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
     * Transforms a single streaming chunk to LM Studio format
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
     * Generates a unique chat completion ID in LM Studio format
     */
    private static String generateChatId() {
        return "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
