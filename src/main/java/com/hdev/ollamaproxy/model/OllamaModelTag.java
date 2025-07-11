package com.hdev.ollamaproxy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaModelTag {
    @JsonProperty("name")
    private String name;

    @JsonProperty("modified_at")
    private String modifiedAt;

    @JsonProperty("size")
    private long size;

    @JsonProperty("digest")
    private String digest;

    // Details object can be complex, representing as JsonNode or Map<String, Object> if needed,
    // or specific fields if they are consistent. For now, keeping it simple.
    // @JsonProperty("details")
    // private Object details;


    public OllamaModelTag() {
    }

    public OllamaModelTag(String name, String modifiedAt, long size, String digest) {
        this.name = name;
        this.modifiedAt = modifiedAt;
        this.size = size;
        this.digest = digest;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(String modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }
}
