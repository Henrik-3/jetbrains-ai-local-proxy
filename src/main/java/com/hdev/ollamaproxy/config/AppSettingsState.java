package com.hdev.ollamaproxy.config;

import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;

@State(name = "AppSettingsState", storages = @Storage("ollamaProxy.xml"))
public class AppSettingsState implements PersistentStateComponent<AppSettingsState> {

    // Enum to define the supported service types
    public enum ServiceType {
        OPENAI("OpenAI"),
        OPENWEBUI("OpenWebUI");

        private final String displayName;

        ServiceType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // Common settings (shared across all service types)
    public ServiceType serviceType = ServiceType.OPENAI;
    public String apiKey = "";
    public String baseUrl = "https://api.openai.com/v1/";
    public int serverPort = 11434;
    public boolean autoStartServer = false;
    public String modelFilter = "";

    // General model settings
    public String normalModel = "anthropic/claude-3.5-sonnet";
    public String smallModel = "anthropic/claude-3.5-haiku";

    // OpenRouter specific settings (applied when base URL contains openrouter.ai)
    public String openrouterNormalProvider = ""; // Provider preference for normal model
    public String openrouterSmallProvider = ""; // Provider preference for small/fast model

    // Legacy settings for backward compatibility - will be migrated
    public String openAiApiKey = "";
    public String openAiBaseUrl = "https://api.openai.com/v1/";
    public String anthropicApiKey = "";
    public String anthropicBaseUrl = "https://openrouter.ai/api/v1";
    public String anthropicModel = "anthropic/claude-sonnet-4";
    public String anthropicSmallFastModel = "anthropic/claude-3.5-haiku";
    public String openrouterProvider = ""; // Legacy single provider field
    public String openrouterNormalModel = "anthropic/claude-3.5-sonnet"; // Legacy
    public String openrouterSmallModel = "anthropic/claude-3.5-haiku"; // Legacy
    public String claudeCodeNormalModel = "anthropic/claude-3.5-sonnet"; // Legacy
    public String claudeCodeSmallModel = "anthropic/claude-3.5-haiku"; // Legacy

    @NotNull
    public static AppSettingsState getInstance() {
        return ServiceManager.getService(AppSettingsState.class);
    }

    @Override
    public AppSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull AppSettingsState state) {
        // Load common settings
        this.serviceType = state.serviceType;
        this.apiKey = state.apiKey;
        this.baseUrl = state.baseUrl;
        this.serverPort = state.serverPort;
        this.autoStartServer = state.autoStartServer;
        this.modelFilter = state.modelFilter;

        // Load general model settings
        this.normalModel = state.normalModel;
        this.smallModel = state.smallModel;

        // Load OpenRouter provider preferences
        this.openrouterNormalProvider = state.openrouterNormalProvider;
        this.openrouterSmallProvider = state.openrouterSmallProvider;

        // Load legacy settings for backward compatibility
        this.openAiApiKey = state.openAiApiKey;
        this.openAiBaseUrl = state.openAiBaseUrl;
        this.anthropicApiKey = state.anthropicApiKey;
        this.anthropicBaseUrl = state.anthropicBaseUrl;
        this.anthropicModel = state.anthropicModel;
        this.anthropicSmallFastModel = state.anthropicSmallFastModel;
        this.openrouterProvider = state.openrouterProvider;
        this.openrouterNormalModel = state.openrouterNormalModel;
        this.openrouterSmallModel = state.openrouterSmallModel;
        this.claudeCodeNormalModel = state.claudeCodeNormalModel;
        this.claudeCodeSmallModel = state.claudeCodeSmallModel;

        // Migrate legacy settings if needed
        migrateLegacySettings();
    }

    private void migrateLegacySettings() {
        // Migrate OpenAI settings to common settings if common settings are empty
        if (apiKey.isEmpty() && !openAiApiKey.isEmpty()) {
            apiKey = openAiApiKey;
            baseUrl = openAiBaseUrl;
            serviceType = ServiceType.OPENAI;
        }

        // Migrate Anthropic API key and base URL to common settings
        if (apiKey.isEmpty() && !anthropicApiKey.isEmpty()) {
            apiKey = anthropicApiKey;
            baseUrl = anthropicBaseUrl;
            serviceType = ServiceType.OPENAI;
        }

        // Migrate model settings from legacy fields to general model settings
        if (normalModel.equals("anthropic/claude-3.5-sonnet")) {
            // Check legacy sources in order of preference
            if (!claudeCodeNormalModel.isEmpty() && !claudeCodeNormalModel.equals("anthropic/claude-3.5-sonnet")) {
                normalModel = claudeCodeNormalModel;
            } else if (!openrouterNormalModel.isEmpty() && !openrouterNormalModel.equals("anthropic/claude-3.5-sonnet")) {
                normalModel = openrouterNormalModel;
            } else if (!anthropicModel.isEmpty()) {
                normalModel = anthropicModel;
            }
        }

        if (smallModel.equals("anthropic/claude-3.5-haiku")) {
            // Check legacy sources in order of preference
            if (!claudeCodeSmallModel.isEmpty() && !claudeCodeSmallModel.equals("anthropic/claude-3.5-haiku")) {
                smallModel = claudeCodeSmallModel;
            } else if (!openrouterSmallModel.isEmpty() && !openrouterSmallModel.equals("anthropic/claude-3.5-haiku")) {
                smallModel = openrouterSmallModel;
            } else if (!anthropicSmallFastModel.isEmpty()) {
                smallModel = anthropicSmallFastModel;
            }
        }

        // Migrate legacy single provider field to separate provider fields
        if (!openrouterProvider.isEmpty() && openrouterNormalProvider.isEmpty() && openrouterSmallProvider.isEmpty()) {
            openrouterNormalProvider = openrouterProvider;
            openrouterSmallProvider = openrouterProvider;
        }
    }

    // Helper method to check if OpenRouter settings should be used
    public boolean isOpenRouterEnabled() {
        return baseUrl != null && baseUrl.contains("openrouter.ai");
    }

    // Helper methods to get the appropriate model
    public String getNormalModel() {
        return normalModel;
    }

    public String getSmallModel() {
        return smallModel;
    }

    // Helper methods to get provider preferences (only relevant for OpenRouter)
    public String getNormalProvider() {
        return isOpenRouterEnabled() ? openrouterNormalProvider : "";
    }

    public String getSmallProvider() {
        return isOpenRouterEnabled() ? openrouterSmallProvider : "";
    }
}
