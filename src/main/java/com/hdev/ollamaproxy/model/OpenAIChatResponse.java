package com.hdev.ollamaproxy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents the OpenAI-style chat completion response format
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAIChatResponse {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("object")
    private String object;
    
    @JsonProperty("created")
    private Long created;
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("system_fingerprint")
    private String systemFingerprint;
    
    @JsonProperty("choices")
    private List<OpenAIChoice> choices;

    // Default constructor
    public OpenAIChatResponse() {}

    // Constructor
    public OpenAIChatResponse(String id, String object, Long created, String model,
                               String systemFingerprint, List<OpenAIChoice> choices) {
        this.id = id;
        this.object = object;
        this.created = created;
        this.model = model;
        this.systemFingerprint = systemFingerprint;
        this.choices = choices;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }

    public Long getCreated() { return created; }
    public void setCreated(Long created) { this.created = created; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getSystemFingerprint() { return systemFingerprint; }
    public void setSystemFingerprint(String systemFingerprint) { this.systemFingerprint = systemFingerprint; }

    public List<OpenAIChoice> getChoices() { return choices; }
    public void setChoices(List<OpenAIChoice> choices) { this.choices = choices; }
}
