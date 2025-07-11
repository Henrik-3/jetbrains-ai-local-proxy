package com.hdev.lmstudioproxy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a choice in LM Studio chat completion responses
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LMStudioChoice {
    @JsonProperty("index")
    private Integer index;

    @JsonProperty("delta")
    private LMStudioDelta delta;

    @JsonProperty("logprobs")
    private Object logprobs;

    @JsonProperty("finish_reason")
    private String finishReason;

    // Default constructor
    public LMStudioChoice() {}

    // Constructor
    public LMStudioChoice(Integer index, LMStudioDelta delta, Object logprobs, String finishReason) {
        this.index = index;
        this.delta = delta;
        this.logprobs = logprobs;
        this.finishReason = finishReason;
    }

    // Getters and setters
    public Integer getIndex() { return index; }
    public void setIndex(Integer index) { this.index = index; }

    public LMStudioDelta getDelta() { return delta; }
    public void setDelta(LMStudioDelta delta) { this.delta = delta; }

    public Object getLogprobs() { return logprobs; }
    public void setLogprobs(Object logprobs) { this.logprobs = logprobs; }

    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
}