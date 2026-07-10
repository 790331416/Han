package com.han.aivideo.service;

/**
 * Thin abstraction over Volcengine VOD direct edit APIs.
 */
public interface AivideoDirectEditProvider {

    String uploader();

    String application();

    SubmitResult submit(String editParamJson, int priority, String callbackArgs);

    int progress(String providerTaskId);

    EditResult result(String providerTaskId);

    default String playUrl(String outputVid) {
        return "";
    }

    record SubmitResult(String reqId) {
    }

    record EditResult(String reqId, String status, String message, String outputVid) {
    }
}
