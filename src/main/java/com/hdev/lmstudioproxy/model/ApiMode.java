package com.hdev.lmstudioproxy.model;

/**
 * Enum representing different API modes supported by the proxy
 */
public enum ApiMode {
    LM_STUDIO("LM Studio", "LM Studio local API"),
    OPENWEBUI("OpenWebUI", "OpenWebUI compatible API"),
    OPENAI_STYLE("OpenAI Style", "OpenAI compatible API");

    private final String displayName;
    private final String description;

    ApiMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
