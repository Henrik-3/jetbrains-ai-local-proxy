// File: src/main/java/com/example/lmstudioproxy/actions/StopServerAction.java
package com.hdev.lmstudioproxy.actions;

import com.hdev.lmstudioproxy.server.ProxyServer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;

public class StopServerAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        ProxyServer server = ProxyServer.getInstance();
        if (!server.isRunning()) {
            Messages.showInfoMessage("Proxy server is not running", "LM Studio Proxy");
        } else {
            server.stop();
            Messages.showInfoMessage("Proxy server stopped", "LM Studio Proxy");
        }
    }
}