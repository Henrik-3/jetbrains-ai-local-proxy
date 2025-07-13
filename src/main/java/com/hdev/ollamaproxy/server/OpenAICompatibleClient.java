package com.hdev.ollamaproxy.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.model.Model;
import com.theokanning.openai.service.OpenAiService;
import io.javalin.http.Context;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class OpenAICompatibleClient implements ProviderClient {

    private final OpenAiService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAICompatibleClient(String apiKey, String baseUrl) {
        // This is the corrected constructor logic
        if (baseUrl != null && !baseUrl.isBlank()) {
            // If a custom base URL is provided, build the service with Retrofit
            ObjectMapper customMapper = OpenAiService.defaultObjectMapper();
            OkHttpClient client = OpenAiService.defaultClient(apiKey, Duration.ofSeconds(60));
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(JacksonConverterFactory.create(customMapper))
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .build();

            OpenAiApi api = retrofit.create(OpenAiApi.class);
            this.service = new OpenAiService(api);
        } else {
            // Otherwise, use the default constructor which points to api.openai.com
            this.service = new OpenAiService(apiKey, Duration.ofSeconds(60));
        }
    }

    @Override
    public String getModels() throws Exception {
        List<Model> models = service.listModels();
        // The response format from OpenAI is { "data": [ ... ] }
        return mapper.writeValueAsString(models);
    }

    @Override
    public String chat(ObjectNode request) throws Exception {
        ChatCompletionRequest apiRequest = buildRequest(request);
        ChatCompletionResult result = service.createChatCompletion(apiRequest);
        return mapper.writeValueAsString(result);
    }

    @Override
    public void chatStream(ObjectNode request, Context ctx, StreamHandler handler) {
        ChatCompletionRequest apiRequest = buildRequest(request);

        service.streamChatCompletion(apiRequest)
                .doOnNext(chunk -> {
                    try {
                        handler.handle(mapper.writeValueAsString(chunk));
                    } catch (Exception e) {
                        throw new RuntimeException("Error serializing chunk", e);
                    }
                })
                .blockingSubscribe(); // Block until stream is complete
    }

    private ChatCompletionRequest buildRequest(JsonNode request) {
        List<ChatMessage> messages = new ArrayList<>();
        request.get("messages").forEach(node -> {
            messages.add(new ChatMessage(node.get("role").asText(), node.get("content").asText()));
        });

        return ChatCompletionRequest.builder()
                .model(request.get("model").asText())
                .messages(messages)
                .stream(request.get("stream") != null && request.get("stream").asBoolean())
                .build();
    }
}