package com.hdev.lmstudioproxy.service;

import com.hdev.lmstudioproxy.model.ApiMode;
import com.hdev.lmstudioproxy.model.ModelsResponse;
import com.hdev.lmstudioproxy.settings.ProxySettingsState;

/**
 * Factory class to create appropriate API service based on the configured mode
 */
public class ApiServiceFactory {
    
    /**
     * Gets the appropriate API service based on the current settings
     */
    public static ApiServiceInterface getApiService() {
        ProxySettingsState settings = ProxySettingsState.getInstance();
        ApiMode mode = settings.apiMode;
        
        switch (mode) {
            case LM_STUDIO:
                return new LMStudioApiServiceWrapper();
            case OPENWEBUI:
                return new OpenWebUIApiServiceWrapper();
            case OPENAI_STYLE:
                return new OpenAIApiServiceWrapper();
            default:
                return new LMStudioApiServiceWrapper(); // Default fallback
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
