// File: src/main/java/com/example/lmstudioproxy/server/ProxyServer.java
package com.hdev.lmstudioproxy.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hdev.lmstudioproxy.model.ModelsResponse;
import com.hdev.lmstudioproxy.service.ApiServiceFactory;
import com.hdev.lmstudioproxy.settings.ProxySettingsState;
import com.hdev.lmstudioproxy.util.ResponseTransformer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.Executors;

@Service
public final class ProxyServer {
    private static final Logger LOG = Logger.getInstance(ProxyServer.class);
    private HttpServer server;
    private boolean isRunning = false;

    public static ProxyServer getInstance() {
        return ApplicationManager.getApplication().getService(ProxyServer.class);
    }

    public void start() {
        if (isRunning) return;

        try {
            ProxySettingsState settings = ProxySettingsState.getInstance();
            server = HttpServer.create(new InetSocketAddress(settings.proxyPort), 0);

            // Set up endpoints - support multiple endpoint patterns
            server.createContext("/api/v0/models", new ModelsHandler());
            server.createContext("/api/v0/chat/completions", new ChatCompletionsHandler());
            server.createContext("/api/v0/completions", new CompletionsHandler());
            server.createContext("/api/v0/embeddings", new EmbeddingsHandler());

            // Also support v1 endpoints for better compatibility
            server.createContext("/v1/models", new ModelsHandler());
            server.createContext("/v1/chat/completions", new ChatCompletionsHandler());
            server.createContext("/v1/completions", new CompletionsHandler());
            server.createContext("/v1/embeddings", new EmbeddingsHandler());

            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();
            isRunning = true;

            LOG.info("LM Studio Proxy server started on port " + settings.proxyPort);
        } catch (Exception e) {
            LOG.error("Failed to start proxy server", e);
        }
    }

    public void stop() {
        if (!isRunning || server == null) return;

        server.stop(0);
        isRunning = false;
        LOG.info("LM Studio Proxy server stopped");
    }

    public boolean isRunning() {
        return isRunning;
    }

    private static class ModelsHandler implements HttpHandler {
        private final ObjectMapper objectMapper;

        public ModelsHandler() {
            this.objectMapper = new ObjectMapper();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                // Fetch models using the appropriate API service based on mode
                ApiServiceFactory.ApiServiceInterface apiService = ApiServiceFactory.getApiService();
                ModelsResponse modelsResponse = apiService.fetchModels();

                // Convert to JSON
                String jsonResponse = objectMapper.writeValueAsString(modelsResponse);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes());
                }

            } catch (Exception e) {
                LOG.error("Error handling models request", e);

                // Return error responsegg
                String errorResponse = "{\"error\": \"Failed to fetch models: " + e.getMessage() + "\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, errorResponse.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes());
                }
            }
        }
    }

    private static class ChatCompletionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Method not allowed. Expected POST request.");
                return;
            }

            try {
                // Read request body
                String requestBody = readRequestBody(exchange);
                LOG.info("Received chat completions request, body length: " + requestBody.length());

                // Check if streaming is requested
                boolean isStreaming = isStreamingRequested(requestBody);
                LOG.info("Streaming requested: " + isStreaming);

                // Filter request body to remove tool_choice when tools is empty
                String filteredRequestBody = filterRequestBody(requestBody);

                if (isStreaming) {
                    // Handle streaming response
                    handleStreamingResponse(exchange, filteredRequestBody);
                } else {
                    // Handle regular (non-streaming) response
                    handleRegularResponse(exchange, filteredRequestBody);
                }
            } catch (Exception e) {
                LOG.error("Error handling chat completions request", e);
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }

        private void handleRegularResponse(HttpExchange exchange, String requestBody) throws IOException {
            // Forward to target API
            String response = forwardToOpenAI("/v1/chat/completions", requestBody);
            LOG.info("Received response, length: " + response.length());

            // Transform OpenAI response to LM Studio format
            String transformedResponse = ResponseTransformer.transformChatCompletionResponse(response);
            LOG.info("Transformed response, length: " + transformedResponse.length());

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] responseBytes = transformedResponse.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
                os.flush();
            }
        }

        private void handleStreamingResponse(HttpExchange exchange, String requestBody) throws IOException {
            // Set headers for streaming response
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            // Use chunked transfer encoding (length = 0 means chunked)
            exchange.sendResponseHeaders(200, 0);

            try (OutputStream os = exchange.getResponseBody()) {
                forwardStreamingToOpenAI("/v1/chat/completions", requestBody, os);
            }
        }
    }

    private static class CompletionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Method not allowed. Expected POST request.");
                return;
            }

            try {
                String requestBody = readRequestBody(exchange);
                String response = forwardToOpenAI("/v1/completions", requestBody);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } catch (Exception e) {
                LOG.error("Error handling completions request", e);
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
    }

    private static class EmbeddingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Method not allowed. Expected POST request.");
                return;
            }

            try {
                String requestBody = readRequestBody(exchange);
                String response = forwardToOpenAI("/v1/embeddings", requestBody);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } catch (Exception e) {
                LOG.error("Error handling embeddings request", e);
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
                // Only add newline if there are more lines to read
                if (reader.ready()) {
                    requestBody.append("\n");
                }
            }
        }
        return requestBody.toString();
    }

    /**
     * Checks if streaming is requested in the request body
     */
    private static boolean isStreamingRequested(String requestBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(requestBody);

            if (rootNode.has("stream")) {
                return rootNode.get("stream").asBoolean(false);
            }
            return false;
        } catch (Exception e) {
            LOG.warn("Failed to parse request body for streaming check: " + e.getMessage());
            return false;
        }
    }

    /**
     * Filters the request body to remove tool_choice parameter when tools array is empty or null
     */
    private static String filterRequestBody(String requestBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(requestBody);

            if (rootNode.isObject()) {
                ObjectNode objectNode = (ObjectNode) rootNode;
                boolean modified = false;

                JsonNode toolsNode = objectNode.get("tools");
                boolean rootToolsIsEmpty = toolsNode == null ||
                        toolsNode.isNull() ||
                        (toolsNode.isArray() && toolsNode.isEmpty());

                if (rootToolsIsEmpty) {
                    objectNode.remove("tools");
                    modified = true;

                    JsonNode messagesNode = objectNode.get("messages");
                    if (messagesNode != null && messagesNode.isArray()) {
                        for (JsonNode messageNode : messagesNode) {
                            if (messageNode.isObject()) {
                                ObjectNode messageObject = (ObjectNode) messageNode;
                                if (messageObject.has("tool_calls")) {
                                    LOG.info("Removing tool_calls from message because root level tools array is empty or null");
                                    messageObject.remove("tool_calls");
                                    modified = true;
                                }
                            }
                        }
                    }

                }

                if (modified) {
                    return mapper.writeValueAsString(objectNode);
                }
            }

            // Return original request body if no filtering needed
            return requestBody;

        } catch (Exception e) {
            LOG.warn("Failed to filter request body, using original: " + e.getMessage());
            return requestBody;
        }
    }

    private static String forwardToOpenAI(String endpoint, String requestBody) {
        HttpURLConnection connection = null;
        try {
            ProxySettingsState settings = ProxySettingsState.getInstance();
            String targetUrl = buildTargetUrl(settings, endpoint);
            LOG.info("Forwarding request to: " + targetUrl + " (API Mode: " + settings.apiMode + ")");

            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();

            // Set more generous timeouts to prevent hanging
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(120000);   // 120 seconds for long responses

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "JetBrains-LMStudio-Proxy/1.0");
            connection.setRequestProperty("Connection", "close"); // Prevent connection reuse issues

            if (!settings.openaiApiKey.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + settings.openaiApiKey);
            }
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false); // Disable caching

            // Send request body
            if (requestBody != null && !requestBody.trim().isEmpty()) {
                // Log the request being sent (truncate if too long)
                String logRequest = requestBody.length() > 300 ?
                    requestBody.substring(0, 300) + "..." : requestBody;
                LOG.info("Sending request body: " + requestBody);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestBody.getBytes("UTF-8"));
                    os.flush();
                }
            } else {
                LOG.warn("Request body is empty or null");
            }

            // Get response code first
            int responseCode = connection.getResponseCode();
            LOG.info("Response code: " + responseCode);

            // Read response based on status code
            StringBuilder response = new StringBuilder();
            InputStream inputStream = null;

            try {
                if (responseCode >= 200 && responseCode < 300) {
                    inputStream = connection.getInputStream();
                } else {
                    inputStream = connection.getErrorStream();
                    if (inputStream == null) {
                        inputStream = connection.getInputStream();
                    }
                }

                if (inputStream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line).append("\n");
                        }
                    }
                }
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        LOG.warn("Failed to close input stream", e);
                    }
                }
            }

            String responseBody = response.toString().trim();
            if (responseBody.isEmpty()) {
                return "{\"error\": \"Empty response from target server\", \"status_code\": " + responseCode + "}";
            }

            // Log the actual response content for debugging (truncate if too long)
            String logResponse = responseBody.length() > 200 ?
                responseBody.substring(0, 200) + "..." : responseBody;
            LOG.info("Response content: " + logResponse);

            return responseBody;

        } catch (Exception e) {
            LOG.error("Failed to forward request to target server", e);
            return "{\"error\": \"Failed to forward request: " + e.getMessage().replace("\"", "\\\"") + "\"}";
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception e) {
                    LOG.warn("Failed to disconnect connection", e);
                }
            }
        }
    }

    /**
     * Forwards a streaming request to the target API and streams the response back
     */
    private static void forwardStreamingToOpenAI(String endpoint, String requestBody, OutputStream responseStream) throws IOException {
        HttpURLConnection connection = null;
        try {
            ProxySettingsState settings = ProxySettingsState.getInstance();
            String targetUrl = buildTargetUrl(settings, endpoint);
            LOG.info("Forwarding streaming request to: " + targetUrl + " (API Mode: " + settings.apiMode + ")");

            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();

            // Set timeouts
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(300000);   // 5 minutes for streaming

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("User-Agent", "JetBrains-LMStudio-Proxy/1.0");
            connection.setRequestProperty("Connection", "keep-alive");

            if (!settings.openaiApiKey.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + settings.openaiApiKey);
            }
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);

            // Send request body
            if (requestBody != null && !requestBody.trim().isEmpty()) {
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestBody.getBytes("UTF-8"));
                    os.flush();
                }
            }

            int responseCode = connection.getResponseCode();
            LOG.info("Streaming response code: " + responseCode);

            InputStream inputStream = null;
            try {
                if (responseCode >= 200 && responseCode < 300) {
                    inputStream = connection.getInputStream();
                } else {
                    inputStream = connection.getErrorStream();
                    if (inputStream == null) {
                        inputStream = connection.getInputStream();
                    }
                }

                if (inputStream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
                        String line;

                        while ((line = reader.readLine()) != null) {
                            // For SSE format, we need to process each line individually
                            if (line.trim().startsWith("data: ")) {
                                // Transform the SSE line
                                String transformedLine = ResponseTransformer.transformChatCompletionResponse(line);

                                // Write the transformed line to response stream
                                responseStream.write(transformedLine.getBytes("UTF-8"));
                                responseStream.write("\n".getBytes("UTF-8")); // Add newline for SSE format
                                responseStream.flush();

                                // Check if this is the end of stream
                                if (line.trim().equals("data: [DONE]")) {
                                    break;
                                }
                            } else if (line.trim().isEmpty()) {
                                // Empty lines are part of SSE format - pass them through
                                responseStream.write("\n".getBytes("UTF-8"));
                                responseStream.flush();
                            } else {
                                // Pass through other lines (like event: or id: lines)
                                responseStream.write((line + "\n").getBytes("UTF-8"));
                                responseStream.flush();
                            }
                        }
                    }
                }
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        LOG.warn("Failed to close input stream", e);
                    }
                }
            }

        } catch (Exception e) {
            LOG.error("Failed to forward streaming request to target server", e);
            // Send error as SSE event
            String errorEvent = "data: {\"error\": \"Failed to forward request: " + e.getMessage().replace("\"", "\\\"") + "\"}\n\n";
            responseStream.write(errorEvent.getBytes("UTF-8"));
            responseStream.flush();
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception e) {
                    LOG.warn("Failed to disconnect connection", e);
                }
            }
        }
    }

    /**
     * Sends a standardized error response in JSON format
     */
    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        String errorResponse = String.format("{\"error\": {\"message\": \"%s\", \"type\": \"invalid_request_error\", \"code\": %d}}",
                                           message.replace("\"", "\\\""), statusCode);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, errorResponse.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(errorResponse.getBytes());
        }
    }

    /**
     * Builds the target URL based on the API mode and endpoint
     */
    private static String buildTargetUrl(ProxySettingsState settings, String endpoint) {
        String baseUrl = settings.openaiApiUrl;

        switch (settings.apiMode) {
            case OPENWEBUI:
                // OpenWebUI uses /api endpoints without /v1 prefix
                baseUrl = baseUrl.replaceAll("/v1/?$", "").replaceAll("/$", "");
                // Convert /v1/chat/completions to /api/chat/completions
                // Convert /v1/completions to /api/completions
                // Convert /v1/embeddings to /api/embeddings
                // Convert /v1/models to /api/models
                String openWebUIEndpoint = endpoint.replaceFirst("^/v1/", "/");
                return baseUrl + "/api" + openWebUIEndpoint;
            case OPENAI_STYLE:
                // OpenAI uses /v1 endpoints
                baseUrl = baseUrl.replaceAll("/v1/?$", "").replaceAll("/$", "");
                return baseUrl + endpoint;
            case LM_STUDIO:
            default:
                // LM Studio uses /v1 endpoints
                baseUrl = baseUrl.replaceAll("/v1/?$", "").replaceAll("/$", "");
                return baseUrl + endpoint;
        }
    }
}