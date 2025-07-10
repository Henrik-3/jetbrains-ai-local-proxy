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
 * Service to communicate with LM Studio API
 */
public class LMStudioApiService {
    private static final Logger LOG = Logger.getInstance(LMStudioApiService.class);
    private final ObjectMapper objectMapper;

    public LMStudioApiService() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetches models from LM Studio API
     * @return ModelsResponse containing the list of models
     */
    public ModelsResponse fetchModels() {
        try {
            ProxySettingsState settings = ProxySettingsState.getInstance();
            String lmStudioUrl = buildLMStudioUrl(settings.openaiApiUrl);
            
            LOG.info("Fetching models from LM Studio at: " + lmStudioUrl);
            
            URL url = new URL(lmStudioUrl + "/v1/models");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
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
                LOG.warn("LM Studio API returned status code: " + responseCode);
                return createFallbackResponse();
            }
            
        } catch (Exception e) {
            LOG.warn("Failed to fetch models from LM Studio: " + e.getMessage(), e);
            return createFallbackResponse();
        }
    }

    /**
     * Builds the LM Studio URL from the configured API URL
     */
    private String buildLMStudioUrl(String apiUrl) {
        // Remove trailing /v1 if present and any trailing slashes
        String baseUrl = apiUrl.replaceAll("/v1/?$", "").replaceAll("/$", "");
        return baseUrl;
    }

    /**
     * Parses the LM Studio response and converts it to our expected format
     */
    private ModelsResponse parseAndConvertResponse(String responseBody) {
        try {
            // First try to parse as LM Studio format
            ModelsResponse lmStudioResponse = objectMapper.readValue(responseBody, ModelsResponse.class);

            // Ensure the object field is set to "List"
            lmStudioResponse.setObject("List");

            // If successful, convert the models to match our expected format
            if (lmStudioResponse.getData() != null) {
                for (LMStudioModel model : lmStudioResponse.getData()) {
                    // Ensure all required fields are set with defaults if missing
                    if (model.getObject() == null) model.setObject("model");
                    if (model.getType() == null) model.setType("llm");
                    if (model.getPublisher() == null) model.setPublisher("lmstudio");
                    if (model.getArch() == null) model.setArch("transformer");
                    if (model.getCompatibilityType() == null) model.setCompatibilityType("gguf");
                    if (model.getQuantization() == null) model.setQuantization("unknown");
                    if (model.getState() == null) model.setState("not-loaded");
                    if (model.getMaxContextLength() == null) model.setMaxContextLength(4096);
                }
            }
            
            return lmStudioResponse;
            
        } catch (Exception e) {
            LOG.warn("Failed to parse LM Studio response, creating fallback: " + e.getMessage());
            return createFallbackResponse();
        }
    }

    /**
     * Creates a fallback response when LM Studio is not available
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
