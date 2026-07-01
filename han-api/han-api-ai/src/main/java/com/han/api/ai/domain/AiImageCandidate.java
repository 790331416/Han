package com.han.api.ai.domain;

import java.io.Serializable;

/**
 * One image candidate returned by a provider.
 */
public class AiImageCandidate implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer index;

    private String url;

    private String base64Data;

    private String mimeType;

    private String revisedPrompt;

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBase64Data() {
        return base64Data;
    }

    public void setBase64Data(String base64Data) {
        this.base64Data = base64Data;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getRevisedPrompt() {
        return revisedPrompt;
    }

    public void setRevisedPrompt(String revisedPrompt) {
        this.revisedPrompt = revisedPrompt;
    }
}
