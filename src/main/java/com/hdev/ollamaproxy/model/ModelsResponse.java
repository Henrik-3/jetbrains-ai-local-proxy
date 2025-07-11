package com.hdev.ollamaproxy.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents the response structure for OpenAI-style models API
 */
public class ModelsResponse {
    @JsonProperty("object")
    private String object;
    
    @JsonProperty("data")
    private List<OpenAIModel> data;

    // Default constructor
    public ModelsResponse() {
        this.object = "list";
    }

    // Constructor
    public ModelsResponse(String object, List<OpenAIModel> data) {
        this.object = "list";
        this.data = data;
    }

    // Getters and setters
    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }

    public List<OpenAIModel> getData() { return data; }
    public void setData(List<OpenAIModel> data) { this.data = data; }
}
