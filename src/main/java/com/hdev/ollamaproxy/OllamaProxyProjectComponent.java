// File: src/main/java/com/hdev/ollamaproxy/OllamaProxyProjectComponent.java
package com.hdev.ollamaproxy;

import com.hdev.ollamaproxy.server.ProxyServer;
import com.hdev.ollamaproxy.settings.ProxySettingsState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;

public class OllamaProxyProjectComponent implements ProjectComponent { // Renamed class
    private final Project project;

    public OllamaProxyProjectComponent(Project project) { // Renamed constructor
        this.project = project;
    }

    @Override
    public void projectOpened() {
        ProxySettingsState settings = ProxySettingsState.getInstance();
        if (settings.autoStart) {
            ProxyServer.getInstance().start();
        }
    }

    @Override
    public void projectClosed() {
        ProxyServer server = ProxyServer.getInstance();
        if (server.isRunning()) {
            server.stop();
        }
    }
}
