package com.hdev.lmstudioproxy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the delta object in LM Studio streaming chat completion responses
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LMStudioDelta {
    @JsonProperty("content")
    private String content;
    
    @JsonProperty("role")
    private String role;

    // Default constructor
    public LMStudioDelta() {}

    // Constructor
    public LMStudioDelta(String content, String role) {
        this.content = content;
        this.role = role;
    }

    // Getters and setters
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
