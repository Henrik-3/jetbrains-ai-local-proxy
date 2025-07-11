package com.hdev.ollamaproxy.settings;

import com.hdev.ollamaproxy.model.ApiMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "OllamaProxySettingsState", // Changed name
        storages = @Storage("OllamaProxySettings.xml") // Changed storage file
)
public class ProxySettingsState implements PersistentStateComponent<ProxySettingsState> {
    public String openaiApiUrl = "http://localhost:8080/v1"; // Default to a common OpenWebUI backend
    public String openaiApiKey = "";
    public int proxyPort = 1234; // Port this Ollama Proxy will listen on
    public boolean autoStart = false;
    public String defaultModel = "llama2"; // Common Ollama model
    public ApiMode apiMode = ApiMode.OPENAI_STYLE; // Default backend type

    public static ProxySettingsState getInstance() {
        return ApplicationManager.getApplication().getService(ProxySettingsState.class);
    }

    @Nullable
    @Override
    public ProxySettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ProxySettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}