package com.hdev.ollamaproxy.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.hdev.ollamaproxy.server.ProxyServer;
import org.jetbrains.annotations.NotNull;

public class StartProxyAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ProxyServer.start();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Enable this action only if the server is not running
        e.getPresentation().setEnabled(!ProxyServer.isRunning());
    }
}