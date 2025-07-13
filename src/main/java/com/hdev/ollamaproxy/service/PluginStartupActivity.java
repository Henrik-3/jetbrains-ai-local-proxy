package com.hdev.ollamaproxy.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.hdev.ollamaproxy.config.AppSettingsState;
import com.hdev.ollamaproxy.server.ProxyServer;
import org.jetbrains.annotations.NotNull;

public class PluginStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
        if (AppSettingsState.getInstance().autoStartServer) {
            ProxyServer.start();
        }
    }
}