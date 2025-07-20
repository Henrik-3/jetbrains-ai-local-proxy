package com.hdev.proxy.config;

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
        return "AI Service Proxy";
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
                !mySettingsComponent.getApiKey().equals(settings.apiKey) ||
                !mySettingsComponent.getBaseUrl().equals(settings.baseUrl) ||
                mySettingsComponent.getPort() != settings.serverPort ||
                mySettingsComponent.getAutoStart() != settings.autoStartServer ||
                !mySettingsComponent.getModelFilter().equals(settings.modelFilter) ||
                !mySettingsComponent.getNormalModel().equals(settings.normalModel) ||
                !mySettingsComponent.getSmallModel().equals(settings.smallModel) ||
                !mySettingsComponent.getOpenrouterNormalProvider().equals(settings.openrouterNormalProvider) ||
                !mySettingsComponent.getOpenrouterSmallProvider().equals(settings.openrouterSmallProvider);
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

        //make sure only a base url without any paths is set
        String baseUrl = mySettingsComponent.getBaseUrl();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            if (baseUrl.matches(".*\\/(v1|chat|completions|api)\\/?.*$")) {
                throw new ConfigurationException("Please enter a base URL without API paths (e.g. /v1, /chat, /completions, /api)");
            }
            if (!baseUrl.endsWith("/")) {
                baseUrl += "/";
            }
        }


        AppSettingsState settings = AppSettingsState.getInstance();

        // Save common settings
        settings.serviceType = mySettingsComponent.getServiceType();
        settings.apiKey = mySettingsComponent.getApiKey();
        settings.baseUrl = baseUrl;
        settings.serverPort = mySettingsComponent.getPort();
        settings.autoStartServer = mySettingsComponent.getAutoStart();
        settings.modelFilter = mySettingsComponent.getModelFilter();

        // Save general model settings
        settings.normalModel = mySettingsComponent.getNormalModel();
        settings.smallModel = mySettingsComponent.getSmallModel();

        // Save OpenRouter provider preferences
        settings.openrouterNormalProvider = mySettingsComponent.getOpenrouterNormalProvider();
        settings.openrouterSmallProvider = mySettingsComponent.getOpenrouterSmallProvider();

        if (com.hdev.proxy.server.ProxyServer.isRunning()) {
            com.hdev.proxy.server.ProxyServer.stop();
            com.hdev.proxy.server.ProxyServer.start();
        }

    }

    @Override
    public void reset() {
        if (mySettingsComponent == null) {
            return;
        }
        AppSettingsState settings = AppSettingsState.getInstance();

        // Reset common settings
        mySettingsComponent.setServiceType(settings.serviceType);
        mySettingsComponent.setApiKey(settings.apiKey);
        mySettingsComponent.setBaseUrl(settings.baseUrl);
        mySettingsComponent.setPort(settings.serverPort);
        mySettingsComponent.setAutoStart(settings.autoStartServer);
        mySettingsComponent.setModelFilter(settings.modelFilter);

        // Reset general model settings
        mySettingsComponent.setNormalModel(settings.normalModel);
        mySettingsComponent.setSmallModel(settings.smallModel);

        // Reset OpenRouter provider preferences
        mySettingsComponent.setOpenrouterNormalProvider(settings.openrouterNormalProvider);
        mySettingsComponent.setOpenrouterSmallProvider(settings.openrouterSmallProvider);
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }
}
