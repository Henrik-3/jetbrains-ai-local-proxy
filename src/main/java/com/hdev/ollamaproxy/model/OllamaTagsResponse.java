package com.hdev.ollamaproxy.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OllamaTagsResponse {
    @JsonProperty("models")
    private List<OllamaModelTag> models;

    public OllamaTagsResponse() {
    }

    public OllamaTagsResponse(List<OllamaModelTag> models) {
        this.models = models;
    }

    public List<OllamaModelTag> getModels() {
        return models;
    }

    public void setModels(List<OllamaModelTag> models) {
        this.models = models;
    }
}
