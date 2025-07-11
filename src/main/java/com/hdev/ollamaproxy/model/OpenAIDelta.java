package com.hdev.ollamaproxy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the delta object in OpenAI-style streaming chat completion responses
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAIDelta {
    @JsonProperty("content")
    private String content;
    
    @JsonProperty("role")
    private String role;

    // Default constructor
    public OpenAIDelta() {}

    // Constructor
    public OpenAIDelta(String content, String role) {
        this.content = content;
        this.role = role;
    }

    // Getters and setters
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
