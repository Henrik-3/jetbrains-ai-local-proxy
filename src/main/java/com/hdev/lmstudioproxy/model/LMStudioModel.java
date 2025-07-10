package com.hdev.lmstudioproxy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a model from LM Studio API response
 * Also compatible with OpenAI and OpenWebUI API formats
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LMStudioModel {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("object")
    private String object;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("publisher")
    private String publisher;
    
    @JsonProperty("arch")
    private String arch;
    
    @JsonProperty("compatibility_type")
    private String compatibilityType;
    
    @JsonProperty("quantization")
    private String quantization;
    
    @JsonProperty("state")
    private String state;
    
    @JsonProperty("max_context_length")
    private Integer maxContextLength;

    // Default constructor
    public LMStudioModel() {}

    // Constructor for creating mock/fallback models
    public LMStudioModel(String id, String type, String publisher, String arch,
                        String compatibilityType, String quantization, String state,
                        Integer maxContextLength) {
        this.id = id;
        this.object = "model";
        this.type = type;
        this.publisher = publisher;
        this.arch = arch;
        this.compatibilityType = compatibilityType;
        this.quantization = quantization;
        this.state = state;
        this.maxContextLength = maxContextLength;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public String getArch() { return arch; }
    public void setArch(String arch) { this.arch = arch; }

    public String getCompatibilityType() { return compatibilityType; }
    public void setCompatibilityType(String compatibilityType) { this.compatibilityType = compatibilityType; }

    public String getQuantization() { return quantization; }
    public void setQuantization(String quantization) { this.quantization = quantization; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public Integer getMaxContextLength() { return maxContextLength; }
    public void setMaxContextLength(Integer maxContextLength) { this.maxContextLength = maxContextLength; }
}
