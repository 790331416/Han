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

    private Object buildVideoRequest(List<String> referenceImageUrls, String referenceVideoUrl,
                                     String referenceAudioUrl, Boolean referenceImageAsFirstFrame) throws Exception {
        AiOpenAiCompatibleClient client = new AiOpenAiCompatibleClient();
        Method method = AiOpenAiCompatibleClient.class.getDeclaredMethod("buildVideoRequest",
                AiModelPo.class, String.class, List.class, String.class, String.class,
                Integer.class, String.class, String.class, Boolean.class, Boolean.class, Boolean.class);
        method.setAccessible(true);
        return method.invoke(client, model(), "test prompt", referenceImageUrls, referenceVideoUrl,
                referenceAudioUrl, 5, "9:16", "720p", true, false, referenceImageAsFirstFrame);
    }

    private AiModelPo model() {
        AiModelPo model = new AiModelPo();
        model.setModelCode("doubao-seedance-test");
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

    private String readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (String) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
