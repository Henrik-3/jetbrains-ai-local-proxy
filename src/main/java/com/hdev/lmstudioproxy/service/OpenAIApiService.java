package com.hdev.lmstudioproxy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdev.lmstudioproxy.model.LMStudioModel;
import com.hdev.lmstudioproxy.model.ModelsResponse;
import com.hdev.lmstudioproxy.settings.ProxySettingsState;
import com.intellij.openapi.diagnostic.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * Service to communicate with OpenAI-style API
 */
public class OpenAIApiService {
    private static final Logger LOG = Logger.getInstance(OpenAIApiService.class);
    private final ObjectMapper objectMapper;

    public OpenAIApiService() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetches models from OpenAI-style API
     * @return ModelsResponse containing the list of models
     */
    public ModelsResponse fetchModels() {
        try {
            ProxySettingsState settings = ProxySettingsState.getInstance();
            String openAIUrl = buildOpenAIUrl(settings.openaiApiUrl);
            
            LOG.info("Fetching models from OpenAI-style API at: " + openAIUrl);
            
            URL url = new URL(openAIUrl + "/v1/models");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            
            // Add authorization header if API key is provided
            if (!settings.openaiApiKey.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + settings.openaiApiKey);
            }
            
            connection.setConnectTimeout(5000); // 5 second timeout
            connection.setReadTimeout(10000); // 10 second timeout

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                String responseBody = response.toString();

                // Parse the response and convert to our format
                return parseAndConvertResponse(responseBody);
                
            } else {
                LOG.warn("OpenAI-style API returned status code: " + responseCode);
                return createFallbackResponse();
            }
            
        } catch (Exception e) {
            LOG.warn("Failed to fetch models from OpenAI-style API: " + e.getMessage(), e);
            return createFallbackResponse();
        }
    }

    /**
     * Builds the OpenAI URL from the configured API URL
     */
    private String buildOpenAIUrl(String apiUrl) {
        // Remove trailing /v1 if present and any trailing slashes
        String baseUrl = apiUrl.replaceAll("/v1/?$", "").replaceAll("/$", "");
        return baseUrl;
    }

    /**
     * Parses the OpenAI response and converts it to our expected format
     */
    private ModelsResponse parseAndConvertResponse(String responseBody) {
        try {
            // Try to parse as OpenAI format
            ModelsResponse openAIResponse = objectMapper.readValue(responseBody, ModelsResponse.class);

            // Ensure the object field is set to "List"
            openAIResponse.setObject("List");

            // If successful, convert the models to match our expected format
            if (openAIResponse.getData() != null) {
                for (LMStudioModel model : openAIResponse.getData()) {
                    // Ensure all required fields are set with defaults if missing
                    if (model.getObject() == null) model.setObject("model");
                    if (model.getType() == null) model.setType("llm");
                    if (model.getPublisher() == null) model.setPublisher("openai");
                    if (model.getArch() == null) model.setArch("transformer");
                    if (model.getCompatibilityType() == null) model.setCompatibilityType("openai");
                    if (model.getQuantization() == null) model.setQuantization("unknown");
                    if (model.getState() == null) model.setState("loaded");
                    if (model.getMaxContextLength() == null) model.setMaxContextLength(4096);
                }
            }
            
            return openAIResponse;
            
        } catch (Exception e) {
            LOG.warn("Failed to parse OpenAI response, creating fallback: " + e.getMessage());
            return createFallbackResponse();
        }
    }

    /**
     * Creates a fallback response when OpenAI API is not available
     */
    private ModelsResponse createFallbackResponse() {
        ProxySettingsState settings = ProxySettingsState.getInstance();
        
        LMStudioModel fallbackModel = new LMStudioModel(
            settings.defaultModel,
            "llm",
            "openai-proxy",
            "transformer", 
            "openai",
            "fp16",
            "loaded",
            4096
        );
        
        List<LMStudioModel> models = Arrays.asList(fallbackModel);
        return new ModelsResponse("List", models);
    }
}
