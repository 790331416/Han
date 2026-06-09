package com.han.ai.service.impl;

import com.han.ai.domain.po.AiModelPo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiOpenAiCompatibleClientTest {

    @Test
    void videoRequestUsesReferenceImageRolesWhenMultipleImagesAreProvided() throws Exception {
        Object request = buildVideoRequest(List.of(
                "https://example.com/tail.png",
                "https://example.com/role.png"
        ), null, null, false);

        List<String> imageRoles = imageRolesOf(request);

        assertEquals(List.of("reference_image", "reference_image"), imageRoles);
        assertFalse(imageRoles.contains("first_frame"));
    }

    @Test
    void videoRequestKeepsReferenceImageRoleForSingleImageByDefault() throws Exception {
        Object request = buildVideoRequest(List.of("https://example.com/scene.png"), null, null, false);

        assertEquals(List.of("reference_image"), imageRolesOf(request));
    }

    @Test
    void videoRequestKeepsFirstFrameRoleForSingleImageWhenExplicitlyRequested() throws Exception {
        Object request = buildVideoRequest(List.of("https://example.com/tail.png"), null, null, true);

        assertEquals(List.of("first_frame"), imageRolesOf(request));
    }

    @Test
    void videoRequestAvoidsFirstFrameWhenSingleImageIsMixedWithReferenceAudio() throws Exception {
        Object request = buildVideoRequest(List.of("https://example.com/tail.png"),
                null, "https://example.com/voice.wav", true);

        assertEquals(List.of("reference_image"), imageRolesOf(request));
    }

    @Test
    void videoRequestDropsReferenceVideoAndAudioForSeedance15() throws Exception {
        Object request = buildVideoRequest("doubao-seedance-1-5-pro",
                List.of("https://example.com/tail.png"),
                "https://example.com/previous.mp4",
                "https://example.com/voice.wav",
                true,
                true);

        assertEquals(List.of("first_frame"), imageRolesOf(request));
        assertFalse(contentTypesOf(request).contains("video_url"));
        assertFalse(contentTypesOf(request).contains("audio_url"));
        assertEquals(false, readBooleanField(request, "generateAudio"));
    }

    @Test
    void videoRequestKeepsReferenceVideoAndAudioForSeedance20() throws Exception {
        Object request = buildVideoRequest("doubao-seedance-2-0-pro",
                List.of("https://example.com/tail.png"),
                "https://example.com/previous.mp4",
                "https://example.com/voice.wav",
                true,
                true);

        assertEquals(List.of("reference_image"), imageRolesOf(request));
        assertEquals(List.of("reference_video"), rolesOfType(request, "video_url"));
        assertEquals(List.of("reference_audio"), rolesOfType(request, "audio_url"));
        assertEquals(true, readBooleanField(request, "generateAudio"));
    }

    private Object buildVideoRequest(List<String> referenceImageUrls, String referenceVideoUrl,
                                     String referenceAudioUrl, Boolean referenceImageAsFirstFrame) throws Exception {
        return buildVideoRequest("doubao-seedance-test", referenceImageUrls, referenceVideoUrl,
                referenceAudioUrl, true, referenceImageAsFirstFrame);
    }

    private Object buildVideoRequest(String modelCode, List<String> referenceImageUrls, String referenceVideoUrl,
                                     String referenceAudioUrl, Boolean generateAudio,
                                     Boolean referenceImageAsFirstFrame) throws Exception {
        AiOpenAiCompatibleClient client = new AiOpenAiCompatibleClient();
        Method method = AiOpenAiCompatibleClient.class.getDeclaredMethod("buildVideoRequest",
                AiModelPo.class, String.class, List.class, String.class, String.class,
                Integer.class, String.class, String.class, Boolean.class, Boolean.class, Boolean.class);
        method.setAccessible(true);
        return method.invoke(client, model(modelCode), "test prompt", referenceImageUrls, referenceVideoUrl,
                referenceAudioUrl, 5, "9:16", "720p", true, generateAudio, referenceImageAsFirstFrame);
    }

    private AiModelPo model(String modelCode) {
        AiModelPo model = new AiModelPo();
        model.setModelCode(modelCode);
        return model;
    }

    private List<String> imageRolesOf(Object request) throws Exception {
        Field contentField = request.getClass().getDeclaredField("content");
        contentField.setAccessible(true);
        List<?> content = (List<?>) contentField.get(request);
        return content.stream()
                .filter(part -> "image_url".equals(readField(part, "type")))
                .map(part -> readField(part, "role"))
                .toList();
    }

    private List<String> contentTypesOf(Object request) throws Exception {
        Field contentField = request.getClass().getDeclaredField("content");
        contentField.setAccessible(true);
        List<?> content = (List<?>) contentField.get(request);
        return content.stream()
                .map(part -> readField(part, "type"))
                .toList();
    }

    private List<String> rolesOfType(Object request, String type) throws Exception {
        Field contentField = request.getClass().getDeclaredField("content");
        contentField.setAccessible(true);
        List<?> content = (List<?>) contentField.get(request);
        return content.stream()
                .filter(part -> type.equals(readField(part, "type")))
                .map(part -> readField(part, "role"))
                .toList();
    }

    private String readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (String) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private Boolean readBooleanField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (Boolean) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
