package com.hdev.ollamaproxy.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.http.Context;

/**
 * A functional interface for handling streaming chat responses.
 * The handler can throw an exception to signal that the stream should be terminated,
 * for example, if the downstream client has disconnected.
 */
@FunctionalInterface
interface StreamHandler {
    void handle(String chunk) throws Exception;
}

/**
 * Defines the contract for a client that communicates with an upstream AI provider.
 * This allows the proxy to easily switch between different services like OpenWebUI
 * or any other OpenAI-compatible API.
 */
public interface ProviderClient {
    /**
     * Fetches the list of available models from the destination service.
     * @return A raw JSON string representing the list of models from the provider.
     * @throws Exception if the request fails.
     */
    String getModels() throws Exception;

    /**
     * Handles a non-streaming chat request.
     * @param request The original request from the client, as an ObjectNode.
     * @return A raw JSON string representing the complete response from the provider.
     * @throws Exception if the request fails.
     */
    String chat(ObjectNode request) throws Exception;

    /**
     * Handles a streaming chat request.
     * This method will read from the provider's streaming endpoint and call the handler
     * for each chunk received.
     * @param request The original request from the client, as an ObjectNode.
     * @param ctx The Javalin context, which can be useful for checking the client connection.
     * @param handler The callback to be executed for each data chunk from the stream.
     * @throws Exception if the initial connection to the provider fails.
     */
    void chatStream(ObjectNode request, Context ctx, StreamHandler handler) throws Exception;
}