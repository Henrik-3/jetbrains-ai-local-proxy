package com.hdev.ollamaproxy.server;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.model.Model;
import com.theokanning.openai.service.OpenAiService;
import com.hdev.ollamaproxy.config.AppSettingsState;
import io.reactivex.Flowable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.client.OpenAiApi;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;
import com.intellij.openapi.diagnostic.Logger;


import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class OpenAiServiceProvider {
    private static final Logger LOG = Logger.getInstance(ProxyServer.class);

    private final OpenAiService service;
    // Shared, thread-safe storage for model names
    private final AtomicReference<List<String>> modelNamesCache = new AtomicReference<>(Collections.emptyList());

    public OpenAiServiceProvider() {
        AppSettingsState settings = AppSettingsState.getInstance();
        Duration timeout = Duration.ofSeconds(60);

        if (settings.openAiBaseUrl != null && !settings.openAiBaseUrl.isBlank() && !settings.openAiBaseUrl.equals("https://api.openai.com/v1/")) {
            // Build a custom service with the specified base URL for OpenRouter, etc.
            ObjectMapper mapper = OpenAiService.defaultObjectMapper();
            OkHttpClient client = OpenAiService.defaultClient(settings.openAiApiKey, timeout);
            // log setting for debugging
            LOG.info("Using custom base URL: " + settings.openAiBaseUrl);

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(settings.openAiBaseUrl)
                    .client(client)
                    .addConverterFactory(JacksonConverterFactory.create(mapper))
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .build();

            OpenAiApi api = retrofit.create(OpenAiApi.class);
            this.service = new OpenAiService(api);
        } else {
            // Use the default service which points to the standard OpenAI URL
            this.service = new OpenAiService(settings.openAiApiKey, timeout);
        }
    }

    public ChatCompletionResult chat(List<ChatMessage> messages, String modelName) {
        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .model(modelName)
                .messages(messages)
                .stream(false)
                .build();
        return service.createChatCompletion(req);
    }

    public Flowable<com.theokanning.openai.completion.chat.ChatCompletionChunk> chatStream(List<ChatMessage> messages, String modelName) {
        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .model(modelName)
                .messages(messages)
                .stream(true)
                .build();
        return service.streamChatCompletion(req);
    }

    public List<Model> getModels() {
        List<Model> apiModels = service.listModels();
        // Update shared cache
        List<String> fullNames = apiModels.stream().map(Model::getId).collect(Collectors.toList());
        this.modelNamesCache.set(fullNames);
        return apiModels;
    }

    public String getFullModelName(String alias) {
        if (this.modelNamesCache.get().isEmpty()) {
            getModels(); // Populate cache if empty
        }

        List<String> currentModels = this.modelNamesCache.get();

        // Exact match
        Optional<String> exactMatch = currentModels.stream().filter(name -> name.equals(alias)).findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        // Suffix match (e.g., "claude-3-haiku" matches "anthropic/claude-3-haiku")
        Optional<String> suffixMatch = currentModels.stream().filter(name -> name.endsWith("/" + alias)).findFirst();
        if (suffixMatch.isPresent()) {
            return suffixMatch.get();
        }

        // Default to alias if no match is found
        return alias;
    }

    // This method creates a stubbed response similar to the Go version.
    public static java.util.Map<String, Object> getOllamaModelDetails() {
        return java.util.Map.of(
                "license", "STUB License",
                "modified_at", DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                "details", java.util.Map.of(
                        "format", "gguf",
                        "family", "claude",
                        "families", List.of("claude"),
                        "parameter_size", "175B",
                        "quantization_level", "Q4_K_M"
                )
        );
    }
}