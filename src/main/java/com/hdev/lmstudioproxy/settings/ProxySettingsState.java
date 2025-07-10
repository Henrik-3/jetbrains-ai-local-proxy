package com.hdev.lmstudioproxy.settings;

import com.hdev.lmstudioproxy.model.ApiMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "ProxySettingsState",
        storages = @Storage("LMStudioProxySettings.xml")
)
public class ProxySettingsState implements PersistentStateComponent<ProxySettingsState> {
    public String openaiApiUrl = "http://localhost:11434/v1"; // Default to Ollama
    public String openaiApiKey = "";
    public int proxyPort = 1234; // Default LM Studio port
    public boolean autoStart = false;
    public String defaultModel = "mistralai/devstral-medium";
    public ApiMode apiMode = ApiMode.OPENAI_STYLE; // Default to LM Studio mode

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