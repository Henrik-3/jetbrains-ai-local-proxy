package com.hdev.ollamaproxy.config;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AppSettingsConfigurable implements SearchableConfigurable {

    private AppSettingsComponent mySettingsComponent;

    @NotNull
    @Override
    public String getId() {
        return "com.hdev.ollamaproxy.settings";
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Ollama OpenAI Proxy";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return mySettingsComponent != null ? mySettingsComponent.getPanel() : null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        if (mySettingsComponent == null) {
            mySettingsComponent = new AppSettingsComponent();
        }
        return mySettingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        if (mySettingsComponent == null) {
            return false;
        }
        AppSettingsState settings = AppSettingsState.getInstance();
        return mySettingsComponent.getServiceType() != settings.serviceType ||
                !mySettingsComponent.getApiKey().equals(settings.openAiApiKey) ||
                !mySettingsComponent.getBaseUrl().equals(settings.openAiBaseUrl) ||
                mySettingsComponent.getPort() != settings.serverPort ||
                mySettingsComponent.getAutoStart() != settings.autoStartServer ||
                !mySettingsComponent.getModelFilter().equals(settings.modelFilter) ||
                !mySettingsComponent.getAnthropicApiKey().equals(settings.anthropicApiKey) ||
                !mySettingsComponent.getAnthropicBaseUrl().equals(settings.anthropicBaseUrl) ||
                !mySettingsComponent.getAnthropicModel().equals(settings.anthropicModel) ||
                !mySettingsComponent.getAnthropicSmallFastModel().equals(settings.anthropicSmallFastModel) ||
                !mySettingsComponent.getClaudeCodeProxyMode().equals(settings.claudeCodeProxyMode) ||
                !mySettingsComponent.getOpenrouterProvider().equals(settings.openrouterProvider);
    }

    @Override
    public void apply() throws ConfigurationException {
        if (mySettingsComponent == null) {
            return;
        }
        // Optional: Add validation (e.g., throw if port is invalid)
        int port = mySettingsComponent.getPort();
        if (port < 1024 || port > 65535) {
            throw new ConfigurationException("Port must be between 1024 and 65535");
        }

        AppSettingsState settings = AppSettingsState.getInstance();
        settings.openAiApiKey = mySettingsComponent.getApiKey();
        settings.openAiBaseUrl = mySettingsComponent.getBaseUrl();
        settings.serverPort = mySettingsComponent.getPort();
        settings.autoStartServer = mySettingsComponent.getAutoStart();
        settings.modelFilter = mySettingsComponent.getModelFilter();
        settings.serviceType = mySettingsComponent.getServiceType();

        // Save Anthropic settings
        settings.anthropicApiKey = mySettingsComponent.getAnthropicApiKey();
        settings.anthropicBaseUrl = mySettingsComponent.getAnthropicBaseUrl();
        settings.anthropicModel = mySettingsComponent.getAnthropicModel();
        settings.anthropicSmallFastModel = mySettingsComponent.getAnthropicSmallFastModel();
        settings.claudeCodeProxyMode = mySettingsComponent.getClaudeCodeProxyMode();
        settings.openrouterProvider = mySettingsComponent.getOpenrouterProvider();
    }

    @Override
    public void reset() {
        if (mySettingsComponent == null) {
            return;
        }
        AppSettingsState settings = AppSettingsState.getInstance();
        mySettingsComponent.setApiKey(settings.openAiApiKey);
        mySettingsComponent.setBaseUrl(settings.openAiBaseUrl);
        mySettingsComponent.setPort(settings.serverPort);
        mySettingsComponent.setAutoStart(settings.autoStartServer);
        mySettingsComponent.setModelFilter(settings.modelFilter);
        mySettingsComponent.setServiceType(settings.serviceType);

        // Reset Anthropic settings
        mySettingsComponent.setAnthropicApiKey(settings.anthropicApiKey);
        mySettingsComponent.setAnthropicBaseUrl(settings.anthropicBaseUrl);
        mySettingsComponent.setAnthropicModel(settings.anthropicModel);
        mySettingsComponent.setAnthropicSmallFastModel(settings.anthropicSmallFastModel);
        mySettingsComponent.setClaudeCodeProxyMode(settings.claudeCodeProxyMode);
        mySettingsComponent.setOpenrouterProvider(settings.openrouterProvider);
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }
}