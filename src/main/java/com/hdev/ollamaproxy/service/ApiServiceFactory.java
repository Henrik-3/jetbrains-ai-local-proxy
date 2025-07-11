package com.hdev.ollamaproxy.service;

import com.hdev.ollamaproxy.model.ApiMode;
import com.hdev.ollamaproxy.model.ModelsResponse;
import com.hdev.ollamaproxy.settings.ProxySettingsState;

/**
 * Factory class to create appropriate API service based on the configured mode
 */
public class ApiServiceFactory {
    
    /**
     * Gets the appropriate API service based on the current settings
     */
    public static ApiServiceInterface getApiService() {
        ProxySettingsState settings = ProxySettingsState.getInstance();
        ApiMode mode = settings.apiMode != null ? settings.apiMode : ApiMode.OPENAI_STYLE; // Default to OPENAI_STYLE
        
        switch (mode) {
            case LM_STUDIO: // Kept for potential backend compatibility
                return new LMStudioApiServiceWrapper();
            case OPENWEBUI:
                return new OpenWebUIApiServiceWrapper();
            case OPENAI_STYLE:
            default: // Fallback to OPENAI_STYLE
                return new OpenAIApiServiceWrapper();
        }
    }
    
    /**
     * Interface for API services
     */
    public interface ApiServiceInterface {
        ModelsResponse fetchModels();
    }
    
    /**
     * Wrapper for LMStudioApiService
     */
    private static class LMStudioApiServiceWrapper implements ApiServiceInterface {
        private final LMStudioApiService service = new LMStudioApiService();
        
        @Override
        public ModelsResponse fetchModels() {
            return service.fetchModels();
        }
    }
    
    /**
     * Wrapper for OpenWebUIApiService
     */
    private static class OpenWebUIApiServiceWrapper implements ApiServiceInterface {
        private final OpenWebUIApiService service = new OpenWebUIApiService();
        
        @Override
        public ModelsResponse fetchModels() {
            return service.fetchModels();
        }
    }
    
    /**
     * Wrapper for OpenAIApiService
     */
    private static class OpenAIApiServiceWrapper implements ApiServiceInterface {
        private final OpenAIApiService service = new OpenAIApiService();
        
        @Override
        public ModelsResponse fetchModels() {
            return service.fetchModels();
        }
    }
}
