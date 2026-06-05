package com.han.aivideo.service.impl;

import com.han.aivideo.domain.dto.AivideoProjectDto;
import com.han.aivideo.domain.dto.AivideoShotVideoGenerateDto;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoProjectSettingPo;
import com.han.aivideo.domain.po.AiVideoMediaAssetPo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.mapper.AiVideoCharacterMapper;
import com.han.aivideo.mapper.AiVideoMediaAssetMapper;
import com.han.api.ai.domain.AiVideoGenerateRequest;
import com.han.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
        assertTrue(instruction.contains("猫耳"));
        assertTrue(instruction.contains("猫尾"));
        assertTrue(instruction.contains("单主体"));
        assertTrue(instruction.contains("禁止"));
        assertTrue(instruction.contains("禁止拉长为正常比例"));
        assertTrue(instruction.contains("大头贴"));
        assertTrue(instruction.contains("四视图"));
        assertTrue(instruction.contains("三视图"));
    }

    @Test
    void videoGenerateRequestCarriesMultipleReferenceImageUrls() {
        AiVideoGenerateRequest request = new AiVideoGenerateRequest();

        request.setReferenceImageUrls(List.of("https://media.example/scene.jpg", "https://media.example/character.jpg"));

        assertEquals(List.of("https://media.example/scene.jpg", "https://media.example/character.jpg"),
                request.getReferenceImageUrls());
    }

    @Test
    void shotVideoGenerateDtoCarriesReferenceMediaIds() {
        AivideoShotVideoGenerateDto dto = new AivideoShotVideoGenerateDto();

        dto.setReferenceMediaIds(List.of(30L, 59L));

        assertEquals(List.of(30L, 59L), dto.getReferenceMediaIds());
    }

    @Test
    void shotAssetExposesTransitionPlanningFields() {
        assertDoesNotThrow(() -> AiVideoShotPo.class.getDeclaredField("transitionBeforeType"));
        assertDoesNotThrow(() -> AiVideoShotPo.class.getDeclaredField("transitionBeforeDesc"));
        assertDoesNotThrow(() -> AiVideoShotPo.class.getDeclaredField("transitionEffect"));
        assertDoesNotThrow(() -> AiVideoShotPo.class.getDeclaredField("stitchGroupNo"));
    }

    @Test
    void characterDesignInstructionSeparates3dAnd2dAnimeAnchors() {
        TestSupport support = new TestSupport();

        String cg3d = support.characterDesignInstruction("THREE_D_ANIME_CG");
        String anime2d = support.characterDesignInstruction("TWO_D_ANIME");

        assertTrue(cg3d.contains("3D"));
        assertTrue(cg3d.contains("全身"));
        assertTrue(cg3d.contains("禁止真人照片"));
        assertTrue(cg3d.contains("禁止2D平面漫画"));
        assertTrue(anime2d.contains("2D"));
        assertTrue(anime2d.contains("线稿"));
        assertTrue(anime2d.contains("禁止3D渲染"));
        assertTrue(anime2d.contains("禁止真人照片"));
    }

    @Test
    void characterDesignInstructionInfersAnimeTypeFromVisualStyleWhenAuto() {
        TestSupport support = new TestSupport();

        String cg3d = support.characterDesignInstruction("AUTO", "3D 国漫 CG");
        String anime2d = support.characterDesignInstruction("AUTO", "2D 日漫");

        assertTrue(cg3d.contains("3D"));
        assertTrue(cg3d.contains("禁止2D平面漫画"));
        assertTrue(anime2d.contains("2D"));
        assertTrue(anime2d.contains("禁止3D渲染"));
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
    void assetPromptRequiresExplicitVoiceOverSpeakerContinuity() throws Exception {
        AivideoTextServiceImpl service = new AivideoTextServiceImpl(
                null, null, null, null, null, null, null, null, null, null, null, null);
        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectName("喵小萌阳光账本");
        project.setTargetPlatform("短剧");
        project.setDefaultRatio("9:16");
        project.setDefaultStyle("Q版 3D 卡通");
        AiVideoProjectSettingPo setting = new AiVideoProjectSettingPo();
        setting.setDefaultShotDuration(5);
        Method method = AivideoTextServiceImpl.class.getDeclaredMethod(
                "buildAssetPrompt", AiVideoProjectPo.class, AiVideoProjectSettingPo.class, String.class);
        method.setAccessible(true);

        String prompt = (String) method.invoke(service, project, setting,
                "甜玉米：廉洁不是做给别人看的。下一镜切到喵小萌。");

        assertTrue(prompt.contains("voiceOver 必须显式标注说话人"));
        assertTrue(prompt.contains("角色名（画外音）：内容"));
        assertTrue(prompt.contains("跨镜头延续同一句话"));
        assertTrue(prompt.contains("甜玉米（画外音）：而是即使无人知晓，也选择对集体负责"));
        assertTrue(prompt.contains("账本文字"));
    }

    @Test
    void assetPromptRequiresTransitionPlanForEveryShot() throws Exception {
        AivideoTextServiceImpl service = new AivideoTextServiceImpl(
                null, null, null, null, null, null, null, null, null, null, null, null);
        AiVideoProjectPo project = new AiVideoProjectPo();
        project.setProjectName("喵小萌阳光账本");
        project.setTargetPlatform("短剧");
        project.setDefaultRatio("9:16");
        project.setDefaultStyle("Q版 3D 卡通");
        AiVideoProjectSettingPo setting = new AiVideoProjectSettingPo();
        setting.setDefaultShotDuration(5);
        Method method = AivideoTextServiceImpl.class.getDeclaredMethod(
                "buildAssetPrompt", AiVideoProjectPo.class, AiVideoProjectSettingPo.class, String.class);
        method.setAccessible(true);

        String prompt = (String) method.invoke(service, project, setting,
                "第3镜在教室准备，第4镜切到文具店采购。");

        assertTrue(prompt.contains("transitionBeforeType"));
        assertTrue(prompt.contains("transitionBeforeDesc"));
        assertTrue(prompt.contains("transitionEffect"));
        assertTrue(prompt.contains("stitchGroupNo"));
        assertTrue(prompt.contains("SCENE_CUT"));
        assertTrue(prompt.contains("只有 CONTINUE 才强制使用上一镜尾帧"));
        assertTrue(prompt.contains("episodeNo 固定为 1"));
    }

    @Test
    void transitionInferenceTreatsSameSceneDifferentCharactersAsInsert() {
        AiVideoShotPo previousShot = new AiVideoShotPo();
        previousShot.setSceneId(12L);
        previousShot.setCharacterIds("6");
        AiVideoShotPo currentShot = new AiVideoShotPo();
        currentShot.setSceneId(12L);
        currentShot.setCharacterIds("7");

        String transitionType = AivideoTextServiceImpl.normalizeTransitionBeforeType(null, currentShot, previousShot);

        assertEquals("INSERT", transitionType);
    }

    @Test
    void shotVideoPromptKeepsRoleVoiceWhenVisualCutsAway() throws Exception {
        Field field = AivideoShotVideoServiceImpl.class.getDeclaredField("SHOT_VIDEO_SYSTEM_PROMPT");
        field.setAccessible(true);
        String systemPrompt = (String) field.get(null);

        assertTrue(systemPrompt.contains("带“角色名（画外音）”的旁白必须继承该角色声线"));

        AivideoShotVideoServiceImpl service = new AivideoShotVideoServiceImpl(
                null, null, null, null, null, null, null, null, null, null);
        AiVideoShotPo shot = new AiVideoShotPo();
        shot.setDialogue("");
        shot.setVoiceOver("甜玉米（画外音）：而是即使无人知晓，也选择对集体负责。");
        Class<?> strategyClass = Class.forName(
                "com.han.aivideo.service.impl.AivideoShotVideoServiceImpl$StrategyContext");
        Constructor<?> constructor = strategyClass.getDeclaredConstructor(
                String.class, String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class);
        constructor.setAccessible(true);
        Object strategy = constructor.newInstance(
                "Q版 3D 卡通", "AUTO", "NATIVE_AUDIO", "NONE", "角色 + 场景",
                "普通动作", "严格", "单角色优先", "Q版萌系全身");
        Method method = AivideoShotVideoServiceImpl.class.getDeclaredMethod(
                "buildAudioVisualProtocol", AiVideoShotPo.class, strategyClass);
        method.setAccessible(true);

        String protocol = (String) method.invoke(service, shot, strategy);

        assertTrue(protocol.contains("甜玉米（画外音）：而是即使无人知晓"));
        assertTrue(protocol.contains("必须沿用该角色声线"));
        assertTrue(protocol.contains("即使画面切到其他角色也不能换声线"));
        assertTrue(protocol.contains("账本文字"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shotVideoReferencesUseTailOnlyForSameSceneWithoutNewCharacters() throws Exception {
        AivideoShotVideoServiceImpl service = new AivideoShotVideoServiceImpl(
                null, null, null, null, null, null, null, null, null, null);
        AiVideoShotPo shot = new AiVideoShotPo();
        shot.setSceneId(12L);
        shot.setCharacterIds("5");
        AiVideoShotPo previousShot = new AiVideoShotPo();
        previousShot.setSceneId(12L);
        previousShot.setCharacterIds("5");
        AiVideoMediaAssetPo scene = media(30L, "SCENE_IMAGE");
        AiVideoMediaAssetPo tailFrame = media(47L, "SHOT_TAIL_FRAME");
        Method method = AivideoShotVideoServiceImpl.class.getDeclaredMethod(
                "buildShotVideoReferenceMedias", Long.class, AiVideoShotPo.class, AiVideoShotPo.class,
                AiVideoMediaAssetPo.class, AiVideoMediaAssetPo.class, List.class);
        method.setAccessible(true);

        List<AiVideoMediaAssetPo> references = (List<AiVideoMediaAssetPo>) method.invoke(
                service, 3L, shot, previousShot, scene, tailFrame, List.of());

        assertEquals(1, references.size());
        assertEquals(47L, references.get(0).getMediaId());
        assertEquals("SHOT_TAIL_FRAME", references.get(0).getAssetType());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shotVideoReferencesSkipPreviousTailWhenSceneChanges() throws Exception {
        AivideoShotVideoServiceImpl service = new AivideoShotVideoServiceImpl(
                null, null, null, null, null, null, null, null, null, null);
        AiVideoShotPo shot = new AiVideoShotPo();
        shot.setSceneId(13L);
        shot.setCharacterIds("");
        AiVideoShotPo previousShot = new AiVideoShotPo();
        previousShot.setSceneId(12L);
        previousShot.setCharacterIds("");
        AiVideoMediaAssetPo scene = media(30L, "SCENE_IMAGE");
        AiVideoMediaAssetPo tailFrame = media(47L, "SHOT_TAIL_FRAME");
        Method method = AivideoShotVideoServiceImpl.class.getDeclaredMethod(
                "buildShotVideoReferenceMedias", Long.class, AiVideoShotPo.class, AiVideoShotPo.class,
                AiVideoMediaAssetPo.class, AiVideoMediaAssetPo.class, List.class);
        method.setAccessible(true);

        List<AiVideoMediaAssetPo> references = (List<AiVideoMediaAssetPo>) method.invoke(
                service, 3L, shot, previousShot, scene, tailFrame, List.of());

        assertEquals(1, references.size());
        assertEquals(30L, references.get(0).getMediaId());
        assertEquals("SCENE_IMAGE", references.get(0).getAssetType());
    }

    @Test
    void shotVideoOnlyRequiresPreviousVideoForContinueTransition() throws Exception {
        AivideoShotVideoServiceImpl service = new AivideoShotVideoServiceImpl(
                null, null, null, null, null, null, null, null, null, null);
        AiVideoShotPo sameSceneShot = new AiVideoShotPo();
        sameSceneShot.setSceneId(12L);
        sameSceneShot.setTransitionBeforeType("CONTINUE");
        AiVideoShotPo sceneCutShot = new AiVideoShotPo();
        sceneCutShot.setSceneId(13L);
        sceneCutShot.setTransitionBeforeType("SCENE_CUT");
        AiVideoShotPo insertShot = new AiVideoShotPo();
        insertShot.setSceneId(12L);
        insertShot.setCharacterIds("5,6");
        AiVideoShotPo previousShot = new AiVideoShotPo();
        previousShot.setSceneId(12L);
        previousShot.setCharacterIds("5");
        Method method = AivideoShotVideoServiceImpl.class.getDeclaredMethod(
                "shouldRequirePreviousShotVideo", AiVideoShotPo.class, AiVideoShotPo.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(service, sameSceneShot, previousShot));
        assertFalse((Boolean) method.invoke(service, sceneCutShot, previousShot));
        assertFalse((Boolean) method.invoke(service, insertShot, previousShot));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shotVideoReferencesDoNotMixTailFrameWhenNewCharacterAppearsInSameScene() throws Exception {
        AiVideoCharacterMapper characterMapper = (AiVideoCharacterMapper) Proxy.newProxyInstance(
                AiVideoCharacterMapper.class.getClassLoader(),
                new Class<?>[]{AiVideoCharacterMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName()) && (Long.valueOf(5L).equals(args[0]) || Long.valueOf(6L).equals(args[0]))) {
                        var character = new com.han.aivideo.domain.po.AiVideoCharacterPo();
                        Long characterId = (Long) args[0];
                        character.setCharacterId(characterId);
                        character.setProjectId(3L);
                        character.setLockedMediaId(characterId + 60L);
                        character.setDelFlag(0);
                        return character;
                    }
                    return null;
                });
        AiVideoMediaAssetMapper mediaMapper = (AiVideoMediaAssetMapper) Proxy.newProxyInstance(
                AiVideoMediaAssetMapper.class.getClassLoader(),
                new Class<?>[]{AiVideoMediaAssetMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())
                            && (Long.valueOf(65L).equals(args[0]) || Long.valueOf(66L).equals(args[0]))) {
                        return media((Long) args[0], "CHARACTER_IMAGE");
                    }
                    return null;
                });
        AivideoShotVideoServiceImpl service = new AivideoShotVideoServiceImpl(
                null, null, null, null, characterMapper, null, mediaMapper, null, null, null);
        AiVideoShotPo shot = new AiVideoShotPo();
        shot.setSceneId(12L);
        shot.setCharacterIds("5,6");
        AiVideoShotPo previousShot = new AiVideoShotPo();
        previousShot.setSceneId(12L);
        previousShot.setCharacterIds("5");
        AiVideoMediaAssetPo scene = media(30L, "SCENE_IMAGE");
        AiVideoMediaAssetPo tailFrame = media(47L, "SHOT_TAIL_FRAME");
        Method method = AivideoShotVideoServiceImpl.class.getDeclaredMethod(
                "buildShotVideoReferenceMedias", Long.class, AiVideoShotPo.class, AiVideoShotPo.class,
                AiVideoMediaAssetPo.class, AiVideoMediaAssetPo.class, List.class);
        method.setAccessible(true);

        List<AiVideoMediaAssetPo> references = (List<AiVideoMediaAssetPo>) method.invoke(
                service, 3L, shot, previousShot, scene, tailFrame, List.of());

        assertEquals(List.of("SCENE_IMAGE", "CHARACTER_IMAGE", "CHARACTER_IMAGE"),
                references.stream().map(AiVideoMediaAssetPo::getAssetType).toList());
    }

    @Test
    void sendSseSafelyReturnsFalseAfterEmitterCompleted() {
        SseEmitter emitter = new SseEmitter();
        emitter.complete();

        boolean sent = AivideoTextServiceImpl.sendSseSafely(emitter, "delta", "chunk");

        assertFalse(sent);
    }

    private AiVideoMediaAssetPo media(Long mediaId, String assetType) {
        AiVideoMediaAssetPo media = new AiVideoMediaAssetPo();
        media.setMediaId(mediaId);
        media.setAssetType(assetType);
        media.setProjectId(3L);
        media.setFileUrl("/file/public/test.png");
        media.setSelected("1");
        media.setAssetStatus("SELECTED");
        media.setDelFlag(0);
        return media;
    }

    private static final class TestSupport extends AivideoServiceSupport {
        @Override
        protected String characterDesignInstruction(String value) {
            return super.characterDesignInstruction(value);
        }

        protected String characterDesignInstruction(String value, String visualStyle) {
            return super.characterDesignInstruction(value, visualStyle);
        }
    }
}
