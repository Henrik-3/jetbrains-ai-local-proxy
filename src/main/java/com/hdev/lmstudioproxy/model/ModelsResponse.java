package com.hdev.lmstudioproxy.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents the response structure for LM Studio models API
 */
public class ModelsResponse {
    @JsonProperty("object")
    private String object;
    
    @JsonProperty("data")
    private List<LMStudioModel> data;

    // Default constructor
    public ModelsResponse() {
        this.object = "list";
    }

    // Constructor
    public ModelsResponse(String object, List<LMStudioModel> data) {
        this.object = "list";
        this.data = data;
    }

    // Getters and setters
    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }

    public List<LMStudioModel> getData() { return data; }
    public void setData(List<LMStudioModel> data) { this.data = data; }
}
