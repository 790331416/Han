package com.han.aivideo.service.impl;

import com.han.aivideo.domain.dto.AivideoProjectDto;
import com.han.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AivideoTextServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildStrategyParamsStoresCharacterDesignType() throws Exception {
        AivideoProjectDto dto = new AivideoProjectDto();
        dto.setProjectName("测试项目");
        dto.setCharacterDesignType("Q版萌系全身");
        AivideoProjectServiceImpl service = new AivideoProjectServiceImpl(
                null, null, null, null, null, null, null, null);
        Method method = AivideoProjectServiceImpl.class.getDeclaredMethod(
                "buildStrategyParams", AivideoProjectDto.class, String.class);
        method.setAccessible(true);

        Map<String, Object> params = (Map<String, Object>) method.invoke(service, dto, "{}");

        assertEquals("Q版萌系全身", params.get("characterDesignType"));
    }

    @Test
    void characterDesignInstructionKeepsQVersionAsFullBodyAnchor() {
        TestSupport support = new TestSupport();

        String instruction = support.characterDesignInstruction("Q版萌系全身");

        assertTrue(instruction.contains("Q版"));
        assertTrue(instruction.contains("完整全身"));
        assertTrue(instruction.contains("禁止"));
        assertTrue(instruction.contains("大头贴"));
    }

    @Test
    void normalizeAssetJsonBlockWrapsTopLevelAssetFragment() {
        String raw = """
                "characters": [
                  {
                    "characterName": "dog",
                    "personalityTags": ["brave", "kind"]
                  }
                ],
                "scenes": [
                  {
                    "sceneName": "street corner"
                  }
                ],
                "shots": [
                  {
                    "shotNo": 1,
                    "durationSec": 5
                  }
                ]
                """;

        String normalized = AivideoTextServiceImpl.normalizeAssetJsonBlock(raw);

        assertEquals("{\"characters\": [\n" +
                "  {\n" +
                "    \"characterName\": \"dog\",\n" +
                "    \"personalityTags\": [\"brave\", \"kind\"]\n" +
                "  }\n" +
                "],\n" +
                "\"scenes\": [\n" +
                "  {\n" +
                "    \"sceneName\": \"street corner\"\n" +
                "  }\n" +
                "],\n" +
                "\"shots\": [\n" +
                "  {\n" +
                "    \"shotNo\": 1,\n" +
                "    \"durationSec\": 5\n" +
                "  }\n" +
                "]}", normalized);
    }

    @Test
    void normalizeAssetJsonBlockPreservesCompleteJsonObject() {
        String raw = """
                {
                  "characters": [],
                  "scenes": [],
                  "shots": []
                }
                """;

        String normalized = AivideoTextServiceImpl.normalizeAssetJsonBlock(raw);

        assertEquals("{\n" +
                "  \"characters\": [],\n" +
                "  \"scenes\": [],\n" +
                "  \"shots\": []\n" +
                "}", normalized);
    }

    @Test
    void isProbablyTruncatedAssetJsonDetectsUnclosedStreamOutput() {
        String raw = """
                {"characters":[{"characterName":"dog"}],"scenes":[],"shots":[{"shotNo":1,"voiceOver":"follow me next
                """;

        assertTrue(AivideoTextServiceImpl.isProbablyTruncatedAssetJson(raw));
    }

    @Test
    void isProbablyTruncatedAssetJsonIgnoresCompleteAssetJson() {
        String raw = """
                {"characters":[{"characterName":"dog"}],"scenes":[],"shots":[{"shotNo":1,"voiceOver":"done"}]}
                """;

        assertFalse(AivideoTextServiceImpl.isProbablyTruncatedAssetJson(raw));
    }

    @Test
    void validateShotSpatialContinuityRejectsDoghouseJumpAfterSignFrameDanger() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                AivideoTextServiceImpl.validateShotSpatialContinuity(List.of(
                        new AivideoTextServiceImpl.ShotContinuitySnapshot(
                                2,
                                "暴雨夜小区街道",
                                "一道闪电划破夜空，照亮对面商铺屋顶上摇摇欲坠的广告牌铁架，一个模糊的小身影蜷缩在上面。",
                                ""
                        ),
                        new AivideoTextServiceImpl.ShotContinuitySnapshot(
                                3,
                                "狗窝角落",
                                "狗狗蜷缩在窝的角落，身体随着雷声微微发抖。雨水泼溅到窝口，打湿边缘。",
                                ""
                        )
                )));

        assertTrue(exception.getMessage().contains("第2镜"));
        assertTrue(exception.getMessage().contains("第3镜"));
        assertTrue(exception.getMessage().contains("狗窝"));
    }

    @Test
    void validateShotSpatialContinuityAllowsStreetBridgeTowardSignFrameDanger() {
        assertDoesNotThrow(() ->
                AivideoTextServiceImpl.validateShotSpatialContinuity(List.of(
                        new AivideoTextServiceImpl.ShotContinuitySnapshot(
                                2,
                                "暴雨夜小区街道",
                                "一道闪电划破夜空，照亮对面商铺屋顶上摇摇欲坠的广告牌铁架，一个模糊的小身影蜷缩在上面。",
                                ""
                        ),
                        new AivideoTextServiceImpl.ShotContinuitySnapshot(
                                3,
                                "暴雨夜小区街道",
                                "延续上一镜，狗狗在街边抬头望向广告牌铁架，身体绷紧，准备冲向商铺雨棚。",
                                ""
                        )
                )));
    }

    @Test
    void sendSseSafelyReturnsFalseAfterEmitterCompleted() {
        SseEmitter emitter = new SseEmitter();
        emitter.complete();

        boolean sent = AivideoTextServiceImpl.sendSseSafely(emitter, "delta", "chunk");

        assertFalse(sent);
    }

    private static final class TestSupport extends AivideoServiceSupport {
        @Override
        protected String characterDesignInstruction(String value) {
            return super.characterDesignInstruction(value);
        }
    }
}
