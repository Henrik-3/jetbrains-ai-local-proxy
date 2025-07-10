// File: src/main/java/com/example/lmstudioproxy/actions/StartServerAction.java
package com.hdev.lmstudioproxy.actions;

import com.hdev.lmstudioproxy.server.ProxyServer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;

public class StartServerAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        ProxyServer server = ProxyServer.getInstance();
        if (server.isRunning()) {
            Messages.showInfoMessage("Proxy server is already running", "LM Studio Proxy");
        } else {
            server.start();
            Messages.showInfoMessage("Proxy server started successfully", "LM Studio Proxy");
        }
    }
}