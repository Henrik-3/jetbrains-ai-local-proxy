package com.hdev.ollamaproxy.config;

import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;

@State(name = "AppSettingsState", storages = @Storage("ollamaProxy.xml"))
public class AppSettingsState implements PersistentStateComponent<AppSettingsState> {

    // Enum to define the supported service types
    public enum ServiceType {
        OPENAI_COMPATIBLE("OpenAI-Compatible (OpenRouter, etc.)"),
        OPEN_WEBUI("OpenWebUI");

        private final String displayName;

        ServiceType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public ServiceType serviceType = ServiceType.OPENAI_COMPATIBLE;

    public String openAiApiKey = "";
    public String openAiBaseUrl = "https://api.openai.com/v1/";
    public int serverPort = 11434;
    public boolean autoStartServer = false;
    public String modelFilter = "";

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
        this.openAiApiKey = state.openAiApiKey;
        this.openAiBaseUrl = state.openAiBaseUrl;
        this.serverPort = state.serverPort;
        this.autoStartServer = state.autoStartServer;
        this.modelFilter = state.modelFilter;
        this.serviceType = state.serviceType;
    }
}