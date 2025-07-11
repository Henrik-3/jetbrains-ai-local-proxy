package com.hdev.lmstudioproxy.service;

import com.intellij.openapi.diagnostic.Logger;

/**
 * Utility class for managing connections and caches across all API services
 */
public class ConnectionManager {
    private static final Logger LOG = Logger.getInstance(ConnectionManager.class);
    
    /**
     * Clears all cached responses from all API services
     */
    public static void clearAllCaches() {
        try {
            // Clear LM Studio cache
            LMStudioApiService.clearCache();
            LOG.info("Cleared LM Studio API cache");
            
            // Clear OpenAI cache
            OpenAIApiService.clearCache();
            LOG.info("Cleared OpenAI API cache");
            
            // Clear OpenWebUI cache
            OpenWebUIApiService.clearCache();
            LOG.info("Cleared OpenWebUI API cache");
            
        } catch (Exception e) {
            LOG.warn("Error clearing API caches", e);
        }
    }
    
    /**
     * Gets cache statistics for monitoring
     */
    public static String getCacheStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("API Cache Statistics:\n");
        stats.append("LM Studio cache size: ").append(LMStudioApiService.getCacheSize()).append("\n");
        stats.append("OpenAI cache size: ").append(OpenAIApiService.getCacheSize()).append("\n");
        stats.append("OpenWebUI cache size: ").append(OpenWebUIApiService.getCacheSize()).append("\n");
        return stats.toString();
    }
}
