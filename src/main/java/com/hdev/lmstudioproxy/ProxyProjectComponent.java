// File: src/main/java/com/example/lmstudioproxy/ProxyProjectComponent.java
package com.hdev.lmstudioproxy;

import com.hdev.lmstudioproxy.server.ProxyServer;
import com.hdev.lmstudioproxy.settings.ProxySettingsState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;

public class ProxyProjectComponent implements ProjectComponent {
    private final Project project;

    public ProxyProjectComponent(Project project) {
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
