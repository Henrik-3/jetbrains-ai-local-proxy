package com.hdev.ollamaproxy.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.openapi.diagnostic.Logger;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.models.Model;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.javalin.http.Context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Drop‑in replacement that targets the official
 * {@code com.openai:openai-java:2.12.4} SDK instead of the now‑deprecated
 * Theo Kanning retrofit client.
 */
public class OpenAICompatibleClient implements ProviderClient {
    private static final Logger LOG = Logger.getInstance(OpenAICompatibleClient.class);

    private final OpenAIClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAICompatibleClient(String apiKey, String baseUrl) {
        // Build an OkHttp‑backed client with a 60 s timeout.
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(60));

        //if openrouter add ranking headers
        if (baseUrl.contains("openrouter.ai")) {
            builder.putHeader("HTTP-Referer", "https://henrikdev.xyz")
                    .putHeader("X-Title", "JetBrains AI Proxy Plugin");
        }

        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl); // e.g. Azure/Ollama/OpenRouter endpoints
        }
        this.client = builder.build();
    }

    @Override
    public String getModels() throws Exception {
        // ask the SDK for the raw HTTP response, skip POJO mapping
        var resp = client.models()
                .withRawResponse()
                .list();          // returns HttpResponse<String>
        try (InputStream in = resp.body()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }


    @Override
    public String chat(ObjectNode request) throws Exception {
        ChatCompletionCreateParams params = buildParams(request);
        ChatCompletion result = client.chat().completions().create(params);
        return mapper.writeValueAsString(result);
    }

    @Override
    public void chatStream(ObjectNode request, Context ctx, StreamHandler handler) {
        ChatCompletionCreateParams params = buildParams(request);

        // The SDK returns a StreamResponse we can iterate over.
        try (StreamResponse<ChatCompletionChunk> stream =
                     client.chat().completions().createStreaming(params)) {

            stream.stream().forEach(chunk -> {
                try {
                    handler.handle(mapper.writeValueAsString(chunk));
                } catch (Exception e) {
                    throw new RuntimeException("Error serializing chunk", e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Error while streaming ChatCompletion", e);
        }
    }

    /**
     * Convert the inbound proxy JSON into {@link ChatCompletionCreateParams}.
     * Only the fields we actually use (model, messages, stream) are mapped.
     */
    private ChatCompletionCreateParams buildParams(JsonNode request) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(request.get("model").asText());

        // Map each OpenAI‑style message (role, content)
        for (JsonNode node : request.get("messages")) {
            String role = node.get("role").asText();
            String content = node.get("content").asText();
            switch (role) {
                case "system" -> builder.addSystemMessage(content);
                case "assistant" -> builder.addAssistantMessage(content);
                case "user" -> builder.addUserMessage(content);
                default -> builder.addUserMessage(content); // fallback
            }
        }

        return builder.build();
    }
}
