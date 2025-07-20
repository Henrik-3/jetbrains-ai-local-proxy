package com.hdev.proxy.config;

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
    public String baseUrl = "https://openrouter.ai/";
    public int serverPort = 11434;
    public boolean autoStartServer = true;
    public String modelFilter = "";

    // General model settings
    public String normalModel = "moonshotai/kimi-k2";
    public String smallModel = "mistralai/devstral-small";

    // OpenRouter specific settings (applied when base URL contains openrouter.ai)
    public String openrouterNormalProvider = ""; // Provider preference for normal model
    public String openrouterSmallProvider = ""; // Provider preference for small/fast model

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
