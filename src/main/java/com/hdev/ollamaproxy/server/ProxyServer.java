package com.hdev.ollamaproxy.server;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.hdev.ollamaproxy.config.AppSettingsState;
import io.javalin.Javalin;

import java.util.concurrent.atomic.AtomicReference;

public class ProxyServer {

    private static final AtomicReference<Javalin> serverInstance = new AtomicReference<>(null);
    private static final String NOTIFICATION_GROUP = "OllamaProxy.NotificationGroup";

    public static synchronized void start() {
        if (isRunning()) {
            showNotification("Proxy server is already running.", NotificationType.INFORMATION);
            return;
        }

        AppSettingsState settings = AppSettingsState.getInstance();
        if (settings.openAiApiKey == null || settings.openAiApiKey.trim().isEmpty()) {
            showNotification("API Key is not set. Please configure it in Settings -> Tools -> Ollama OpenAI Proxy.", NotificationType.ERROR);
            return;
        }

        try {
            OllamaProxyHandler handler = new OllamaProxyHandler();
            Javalin app = Javalin.create().start(settings.serverPort);

            // Register handlers, emulating Ollama/OpenWebUI API
            app.get("/", ctx -> ctx.result("Ollama is running"));
            app.head("/", ctx -> ctx.status(200));
            app.get("/api/tags", handler::handleGetModels);
            app.post("/api/show", handler::handleShowModel);
            app.post("/api/chat", handler::handleChat);

            serverInstance.set(app);
            showNotification("Proxy server started on port " + settings.serverPort, NotificationType.INFORMATION);
        } catch (Exception e) {
            showNotification("Failed to start proxy server: " + e.getMessage(), NotificationType.ERROR);
            e.printStackTrace();
        }
    }

    public static synchronized void stop() {
        Javalin server = serverInstance.getAndSet(null);
        if (server != null) {
            server.stop();
            showNotification("Proxy server stopped.", NotificationType.INFORMATION);
        } else {
            showNotification("Proxy server is not running.", NotificationType.INFORMATION);
        }
    }

    public static boolean isRunning() {
        return serverInstance.get() != null;
    }

    private static void showNotification(String content, NotificationType type) {
        Notifications.Bus.notify(new Notification(NOTIFICATION_GROUP, "Ollama Proxy", content, type));
    }
}