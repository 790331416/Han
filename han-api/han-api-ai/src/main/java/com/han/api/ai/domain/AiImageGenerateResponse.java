package com.han.api.ai.domain;

import java.io.Serializable;
import java.util.List;

/**
 * Image generation response from internal AI service.
 */
public class AiImageGenerateResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long modelId;

    private String provider;

    private String modelCode;

    private String prompt;

    private List<AiImageCandidate> candidates;

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public List<AiImageCandidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<AiImageCandidate> candidates) {
        this.candidates = candidates;
    }
}
