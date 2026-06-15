package com.han.aivideo.service.impl;

import com.han.aivideo.domain.dto.AivideoShotVideoGenerateDto;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.po.AiVideoMediaAssetPo;
import com.han.aivideo.domain.po.AiVideoProjectPo;
import com.han.aivideo.domain.po.AiVideoPropPo;
import com.han.aivideo.domain.po.AiVideoScenePo;
import com.han.aivideo.domain.po.AiVideoShotPo;
import com.han.aivideo.mapper.AiVideoCharacterMapper;
import com.han.aivideo.mapper.AiVideoGenerationTaskMapper;
import com.han.aivideo.mapper.AiVideoMediaAssetMapper;
import com.han.aivideo.mapper.AiVideoProjectMapper;
import com.han.aivideo.mapper.AiVideoProjectSettingMapper;
import com.han.aivideo.mapper.AiVideoPropMapper;
import com.han.aivideo.mapper.AiVideoSceneMapper;
import com.han.aivideo.mapper.AiVideoShotMapper;
import com.han.api.ai.AiServiceClient;
import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AivideoShotVideoServiceImplTest {

    @Test
    void previewRejectsAmbiguousPropHandoffBeforeVideoGeneration() {
        TestFixture fixture = new TestFixture();
        fixture.previousShot.setShotNo(4);
        fixture.previousShot.setCharacterIds("狗小汪");
        fixture.previousShot.setActionDesc("狗小汪站在货架前，拿起蓝色透明收纳盒，眼睛发亮，展示给画外。");
        fixture.currentShot.setShotNo(5);
        fixture.currentShot.setCharacterIds("喵小萌");
        fixture.currentShot.setTransitionBeforeType("INSERT");
        fixture.currentShot.setTransitionBeforeDesc("同场景道具交接插入镜头，不强制继承上一尾帧。");
        fixture.currentShot.setActionDesc("接过收纳盒看了看，点头认可，然后转身仔细查看旁边贴纸的价格标签。");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service.previewShotVideoPrompt(fixture.dto()));

        assertTrue(exception.getMessage().contains("道具交接"), exception::getMessage);
        assertTrue(exception.getMessage().contains("谁递给谁"), exception::getMessage);
    }

    @Test
    void previewRejectsSameSceneCharacterDisappearingWithoutExitOrCropExplanation() {
        TestFixture fixture = new TestFixture();
        fixture.previousShot.setShotNo(9);
        fixture.previousShot.setCharacterIds("喵小萌,狗小汪");
        fixture.previousShot.setActionDesc("喵小萌固定在画面左侧，狗小汪固定在画面右侧，两人一起看账本。");
        fixture.currentShot.setShotNo(10);
        fixture.currentShot.setCharacterIds("喵小萌");
        fixture.currentShot.setTransitionBeforeType("INSERT");
        fixture.currentShot.setTransitionBeforeDesc("同场景切人插入镜头。");
        fixture.currentShot.setActionDesc("喵小萌低头看账本，表情犹豫。");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service.previewShotVideoPrompt(fixture.dto()));

        assertTrue(exception.getMessage().contains("上一镜角色疑似无说明消失"), exception::getMessage);
        assertTrue(exception.getMessage().contains("狗小汪"), exception::getMessage);
    }

    @Test
    void previewRejectsBackFacingCharacterTurningFrontWithoutTurnOrReverseShotExplanation() {
        TestFixture fixture = new TestFixture();
        fixture.previousShot.setShotNo(11);
        fixture.previousShot.setCharacterIds("喵小萌");
        fixture.previousShot.setActionDesc("喵小萌背对镜头站在讲台旁，低头看试卷。");
        fixture.currentShot.setShotNo(12);
        fixture.currentShot.setCharacterIds("喵小萌");
        fixture.currentShot.setTransitionBeforeType("CONTINUE");
        fixture.currentShot.setActionDesc("喵小萌正面对着镜头说话，举起试卷。");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service.previewShotVideoPrompt(fixture.dto()));

        assertTrue(exception.getMessage().contains("朝向衔接"), exception::getMessage);
        assertTrue(exception.getMessage().contains("转身"), exception::getMessage);
    }

    @Test
    void previewAcceptsPropImageAsVideoReferenceAnchor() {
        TestFixture fixture = new TestFixture();
        fixture.currentShot.setTransitionBeforeType("INSERT");
        fixture.currentShot.setTransitionBeforeDesc("同场景道具交接插入镜头，不强制继承上一尾帧。");
        fixture.currentShot.setActionDesc("狗小汪从画面左侧把蓝色透明收纳盒递给画面右侧的喵小萌，最后喵小萌双手拿着收纳盒。");
        AiVideoMediaAssetPo propImage = TestFixture.media(500L, "PROP_IMAGE", "/file/public/blue-box.png");
        propImage.setSelected("Y");
        propImage.setAssetStatus("SELECTED");
        AiVideoPropPo prop = new AiVideoPropPo();
        prop.setProjectId(1L);
        prop.setPropName("蓝色透明收纳盒");
        prop.setLockedMediaId(500L);
        prop.setDelFlag(0);
        when(fixture.mediaAssetMapper.selectById(500L)).thenReturn(propImage);
        when(fixture.propMapper.selectList(any())).thenReturn(List.of(prop));
        when(fixture.aiServiceClient.renderTextPrompt(any())).thenAnswer(invocation -> {
            AiTextGenerateRequest request = invocation.getArgument(0);
            return R.ok(request.getUserPrompt());
        });

        AivideoShotVideoGenerateDto dto = fixture.dto();
        dto.setReferenceMediaIds(List.of(500L));

        String prompt = fixture.service.previewShotVideoPrompt(dto).getUserPrompt();

        assertTrue(prompt.contains("reference_image/prop_anchor"), prompt);
        assertTrue(prompt.contains("道具锚定图"), prompt);
        assertTrue(prompt.contains("蓝色透明收纳盒"), prompt);
    }

    @Test
    void previewAutomaticallyAddsLockedPropImageAsVideoReferenceAnchor() {
        TestFixture fixture = new TestFixture();
        fixture.currentShot.setDurationSec(6);
        fixture.currentShot.setActionDesc("剑魂右手拔出寒光剑，结尾持剑站定。");
        AiVideoMediaAssetPo propImage = TestFixture.media(501L, "PROP_IMAGE", "/file/public/cold-sword.png");
        propImage.setSelected("Y");
        propImage.setAssetStatus("SELECTED");
        AiVideoPropPo prop = new AiVideoPropPo();
        prop.setProjectId(1L);
        prop.setPropName("寒光剑");
        prop.setLockedMediaId(501L);
        prop.setDelFlag(0);
        when(fixture.mediaAssetMapper.selectById(501L)).thenReturn(propImage);
        when(fixture.propMapper.selectList(any())).thenReturn(List.of(prop));
        when(fixture.aiServiceClient.renderTextPrompt(any())).thenAnswer(invocation -> {
            AiTextGenerateRequest request = invocation.getArgument(0);
            return R.ok(request.getUserPrompt());
        });

        String prompt = fixture.service.previewShotVideoPrompt(fixture.dto()).getUserPrompt();

        assertTrue(prompt.contains("reference_image/prop_anchor"), prompt);
        assertTrue(prompt.contains("寒光剑"), prompt);
    }

    @Test
    void previewRejectsOverBudgetStrongActionChainBeforeVideoGeneration() {
        TestFixture fixture = new TestFixture();
        fixture.currentShot.setDurationSec(5);
        fixture.currentShot.setActionDesc("剑魂右手拔出寒光剑，剑尖指向深渊柱，嘴角勾起笑，结尾持剑站在柱前。");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service.previewShotVideoPrompt(fixture.dto()));

        assertTrue(exception.getMessage().contains("动作预算过载"), exception::getMessage);
        assertTrue(exception.getMessage().contains("拆成"), exception::getMessage);
    }

    @Test
    void previewRejectsWeaponActionWithoutLinkedPropAsset() {
        TestFixture fixture = new TestFixture();
        fixture.currentShot.setDurationSec(6);
        fixture.currentShot.setActionDesc("剑魂右手拔出寒光剑，结尾持剑站定。");
        when(fixture.propMapper.selectList(any())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service.previewShotVideoPrompt(fixture.dto()));

        assertTrue(exception.getMessage().contains("道具未关联"), exception::getMessage);
        assertTrue(exception.getMessage().contains("寒光剑"), exception::getMessage);
    }

    @Test
    void previewRejectsWeaponActionWithoutLockedPropImage() {
        TestFixture fixture = new TestFixture();
        fixture.currentShot.setDurationSec(6);
        fixture.currentShot.setActionDesc("剑魂右手拔出寒光剑，结尾持剑站定。");
        AiVideoPropPo prop = new AiVideoPropPo();
        prop.setProjectId(1L);
        prop.setPropName("寒光剑");
        prop.setDelFlag(0);
        when(fixture.propMapper.selectList(any())).thenReturn(List.of(prop));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service.previewShotVideoPrompt(fixture.dto()));

        assertTrue(exception.getMessage().contains("道具未锁定参考图"), exception::getMessage);
        assertTrue(exception.getMessage().contains("寒光剑"), exception::getMessage);
    }

    private static class TestFixture {
        private final AiVideoProjectMapper projectMapper = mock(AiVideoProjectMapper.class);
        private final AiVideoProjectSettingMapper settingMapper = mock(AiVideoProjectSettingMapper.class);
        private final AiVideoSceneMapper sceneMapper = mock(AiVideoSceneMapper.class);
        private final AiVideoShotMapper shotMapper = mock(AiVideoShotMapper.class);
        private final AiVideoPropMapper propMapper = mock(AiVideoPropMapper.class);
        private final AiVideoCharacterMapper characterMapper = mock(AiVideoCharacterMapper.class);
        private final AiVideoGenerationTaskMapper taskMapper = mock(AiVideoGenerationTaskMapper.class);
        private final AiVideoMediaAssetMapper mediaAssetMapper = mock(AiVideoMediaAssetMapper.class);
        private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
        private final AivideoShotVideoServiceImpl service = new AivideoShotVideoServiceImpl(
                projectMapper, settingMapper, sceneMapper, shotMapper, characterMapper,
                taskMapper, mediaAssetMapper, aiServiceClient, null, null);

        private final AiVideoProjectPo project = new AiVideoProjectPo();
        private final AiVideoScenePo scene = new AiVideoScenePo();
        private final AiVideoShotPo previousShot = shot(100L, 1);
        private final AiVideoShotPo currentShot = shot(101L, 2);

        private TestFixture() {
            setField(service, "publicFileOrigin", "https://han.scavengers.cn");
            setField(service, "propMapper", propMapper);
            project.setProjectId(1L);
            project.setTenantId(9L);
            project.setProjectName("喵小萌阳光账本");
            project.setDefaultRatio("9:16");
            project.setDelFlag(0);

            scene.setSceneId(20L);
            scene.setProjectId(1L);
            scene.setSceneName("整洁明亮文具店");
            scene.setLockedMediaId(300L);
            scene.setDelFlag(0);

            previousShot.setSceneId(20L);
            currentShot.setSceneId(20L);

            AiVideoMediaAssetPo sceneImage = media(300L, "SCENE_IMAGE", "/file/public/scene.png");
            sceneImage.setSelected("Y");
            sceneImage.setAssetStatus("SELECTED");

            when(projectMapper.selectById(1L)).thenReturn(project);
            when(sceneMapper.selectById(20L)).thenReturn(scene);
            when(shotMapper.selectById(101L)).thenReturn(currentShot);
            when(shotMapper.selectOne(any())).thenReturn(previousShot);
            when(mediaAssetMapper.selectById(300L)).thenReturn(sceneImage);
            when(mediaAssetMapper.selectList(any())).thenReturn(List.of());
            when(propMapper.selectList(any())).thenReturn(List.of());
            when(characterMapper.selectList(any())).thenReturn(List.of());
            when(settingMapper.selectOne(any())).thenReturn(null);
            when(aiServiceClient.renderTextPrompt(any())).thenReturn(R.ok("rendered prompt"));
        }

        private AivideoShotVideoGenerateDto dto() {
            AivideoShotVideoGenerateDto dto = new AivideoShotVideoGenerateDto();
            dto.setProjectId(1L);
            dto.setShotId(101L);
            dto.setAudioMode("REFERENCE_AUDIO");
            dto.setContinuityLevel("STRICT");
            return dto;
        }

        private static AiVideoShotPo shot(Long shotId, int shotNo) {
            AiVideoShotPo shot = new AiVideoShotPo();
            shot.setShotId(shotId);
            shot.setProjectId(1L);
            shot.setEpisodeNo(1);
            shot.setShotNo(shotNo);
            shot.setDurationSec(5);
            shot.setDelFlag(0);
            return shot;
        }

        private static AiVideoMediaAssetPo media(Long mediaId, String assetType, String fileUrl) {
            AiVideoMediaAssetPo media = new AiVideoMediaAssetPo();
            media.setMediaId(mediaId);
            media.setProjectId(1L);
            media.setAssetType(assetType);
            media.setFileUrl(fileUrl);
            media.setDelFlag(0);
            return media;
        }

        private static void setField(Object target, String fieldName, Object value) {
            try {
                Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }
}
