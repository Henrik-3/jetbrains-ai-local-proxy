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
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service to communicate with LM Studio API with improved connection stability
 */
public class LMStudioApiService {
    private static final Logger LOG = Logger.getInstance(LMStudioApiService.class);
    private final ObjectMapper objectMapper;

    // Cache for models response to reduce frequent API calls
    private static final ConcurrentHashMap<String, CachedResponse> modelCache = new ConcurrentHashMap<>();
    private static final Duration CACHE_DURATION = Duration.ofMinutes(5); // Cache for 5 minutes

    // Connection configuration
    private static final int CONNECT_TIMEOUT = 30000; // 30 seconds
    private static final int READ_TIMEOUT = 60000;    // 60 seconds
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY = 1000; // 1 second

    /**
     * Cached response wrapper
     */
    private static class CachedResponse {
        final ModelsResponse response;
        final Instant timestamp;

        CachedResponse(ModelsResponse response) {
            this.response = response;
            this.timestamp = Instant.now();
        }

        boolean isExpired() {
            return Duration.between(timestamp, Instant.now()).compareTo(CACHE_DURATION) > 0;
        }
    }

    public LMStudioApiService() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Clears the model cache
     */
    public static void clearCache() {
        modelCache.clear();
    }

    /**
     * Gets the current cache size
     */
    public static int getCacheSize() {
        return modelCache.size();
    }

    /**
     * Fetches models from LM Studio API with caching and retry logic
     * @return ModelsResponse containing the list of models
     */
    public ModelsResponse fetchModels() {
        ProxySettingsState settings = ProxySettingsState.getInstance();
        String lmStudioUrl = buildLMStudioUrl(settings.openaiApiUrl);
        String cacheKey = lmStudioUrl;

        // Check cache first
        CachedResponse cached = modelCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            LOG.info("Returning cached models response for: " + lmStudioUrl);
            return cached.response;
        }

        LOG.info("Fetching models from LM Studio at: " + lmStudioUrl);

        // Try with retry logic
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ModelsResponse response = fetchModelsWithRetry(lmStudioUrl, attempt);

                // Cache successful response
                modelCache.put(cacheKey, new CachedResponse(response));

                return response;

            } catch (Exception e) {
                LOG.warn("Attempt " + attempt + " failed to fetch models from LM Studio: " + e.getMessage());

                if (attempt == MAX_RETRIES) {
                    LOG.error("All retry attempts failed for LM Studio API", e);
                    return createFallbackResponse();
                }

                // Wait before retry with exponential backoff
                try {
                    long delay = INITIAL_RETRY_DELAY * (1L << (attempt - 1)); // Exponential backoff
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return createFallbackResponse();
                }
            }
        }

        return createFallbackResponse();
    }

    /**
     * Internal method to fetch models with improved connection handling
     */
    private ModelsResponse fetchModelsWithRetry(String lmStudioUrl, int attempt) throws Exception {
        URL url = new URL(lmStudioUrl + "/v1/models");
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "JetBrains-LMStudio-Proxy/1.0");
            connection.setRequestProperty("Connection", "close"); // Prevent connection reuse issues

            // Use longer timeouts for better stability
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);

            // Disable caching to ensure fresh responses
            connection.setUseCaches(false);

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
                LOG.info("Received response from LM Studio (attempt " + attempt + "): " + responseBody.length() + " characters");

                // Parse the response and convert to our format
                return parseAndConvertResponse(responseBody);

            } else {
                String errorMessage = "LM Studio API returned status code: " + responseCode;
                if (responseCode >= 500) {
                    // Server errors are retryable
                    throw new IOException(errorMessage);
                } else {
                    // Client errors are not retryable
                    LOG.warn(errorMessage);
                    return createFallbackResponse();
                }
            }

        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception e) {
                    LOG.debug("Error disconnecting HTTP connection", e);
                }
            }
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
