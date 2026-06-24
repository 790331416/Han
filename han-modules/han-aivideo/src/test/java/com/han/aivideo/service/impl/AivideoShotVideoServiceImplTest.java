package com.han.aivideo.service.impl;

import com.han.aivideo.domain.dto.AivideoShotScriptOptimizeDto;
import com.han.aivideo.domain.dto.AivideoShotVideoGenerateDto;
import com.han.aivideo.domain.po.AiVideoCharacterPo;
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
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    void previewAcceptsOffscreenExplanationUsingCharacterAliasShortName() {
        TestFixture fixture = new TestFixture();
        fixture.previousShot.setCharacterIds("剑魂,狂战士（红狗）,男散打（乌鸡）");
        fixture.previousShot.setActionDesc("剑魂、狂战士（红狗）、男散打（乌鸡）同处暗黑深渊副本密闭空间。");
        fixture.currentShot.setCharacterIds("剑魂");
        fixture.currentShot.setTransitionBeforeType("CONTINUE");
        fixture.currentShot.setTransitionBeforeDesc("连续镜头：单人镜头，画内主体锁定为剑魂；狂战士、男散打在画外右侧近旁不入画。");
        fixture.currentShot.setActionDesc("剑魂盯住深渊柱，嘴角冷笑，结尾站定。");
        when(fixture.characterMapper.selectList(any())).thenReturn(List.of(
                TestFixture.character(11L, "剑魂"),
                TestFixture.character(12L, "狂战士（红狗）"),
                TestFixture.character(13L, "男散打（乌鸡）")));
        AiVideoPropPo prop = new AiVideoPropPo();
        prop.setProjectId(1L);
        prop.setPropName("剑");
        prop.setLockedMediaId(501L);
        prop.setDelFlag(0);
        when(fixture.propMapper.selectList(any())).thenReturn(List.of(prop));
        AiVideoMediaAssetPo propImage = TestFixture.media(501L, "PROP_IMAGE", "/file/public/sword.png");
        propImage.setSelected("Y");
        propImage.setAssetStatus("SELECTED");
        when(fixture.mediaAssetMapper.selectById(501L)).thenReturn(propImage);

        String prompt = fixture.service.previewShotVideoPrompt(fixture.dto()).getUserPrompt();

        assertEquals("rendered prompt", prompt);
    }

    @Test
    void firstShotStartAndTimingPlanDoNotImplyEnteringFromOffscreen() throws Exception {
        AivideoShotVideoServiceImpl service = new AivideoShotVideoServiceImpl(
                null, null, null, null, null, null, null, null, null, null);
        Method startMethod = AivideoShotVideoServiceImpl.class.getDeclaredMethod(
                "buildCurrentStartState", AiVideoShotPo.class, AiVideoMediaAssetPo.class);
        startMethod.setAccessible(true);
        Method timingMethod = AivideoShotVideoServiceImpl.class.getDeclaredMethod(
                "buildTimingPlan", AiVideoShotPo.class, int.class);
        timingMethod.setAccessible(true);
        AiVideoShotPo firstShot = new AiVideoShotPo();
        firstShot.setActionDesc("起始西格玛玛站C位，左手夹未激活黑卡垂眼；荧光粉字弹出。话说完后卡字稳定定格。");

        String startState = (String) startMethod.invoke(service, null, null);
        String timingPlan = (String) timingMethod.invoke(service, firstShot, 6);

        assertTrue(startState.contains("主体已在位"), startState);
        assertTrue(startState.contains("不得从画外走入"), startState);
        assertFalse(startState.contains("按本镜头分镜描述进入"), startState);
        assertFalse(timingPlan.contains("自然进入"), timingPlan);
        assertFalse(timingPlan.contains("进入本镜头"), timingPlan);
    }

    @Test
    void previewAppendsTailFrameFirstFrameGuardWhenTemplateDoesNotMentionCurrentShot() {
        TestFixture fixture = new TestFixture();
        fixture.previousShot.setTailFrameMediaId(600L);
        fixture.previousShot.setActionDesc("上一镜里小猫保持站立，镜头缓慢推进。");
        fixture.currentShot.setTransitionBeforeType("CONTINUE");
        fixture.currentShot.setTransitionBeforeDesc("连续镜头：从上一镜尾帧起步，但只把尾帧作为首帧锚点。");
        fixture.currentShot.setActionDesc("小猫抬头看向远处，嘴角冷笑，结尾站定。");
        AiVideoMediaAssetPo tailFrame = TestFixture.media(600L, "SHOT_TAIL_FRAME", "/file/public/shot-1-tail.png");
        when(fixture.mediaAssetMapper.selectById(600L)).thenReturn(tailFrame);
        when(fixture.aiServiceClient.renderTextPrompt(any())).thenReturn(R.ok("旧数据库模板输出：仅说明连续上一镜。"));

        String prompt = fixture.service.previewShotVideoPrompt(fixture.dto()).getUserPrompt();

        assertTrue(prompt.contains("上一尾帧首帧执行协议"), prompt);
        assertTrue(prompt.contains("当前生成目标是第2镜头"), prompt);
        assertTrue(prompt.contains("不是第1镜头"), prompt);
        assertTrue(prompt.contains("禁止把上一尾帧或上一镜视频延长成整段"), prompt);
        assertTrue(prompt.contains("小猫抬头看向远处"), prompt);
    }

    @Test
    void previewProvidesShotTextWithLockedSpeechForDatabaseTemplate() {
        TestFixture fixture = new TestFixture();
        fixture.currentShot.setCharacterIds("Q版西格玛男人");
        fixture.currentShot.setActionDesc("Q版西格玛男人站在雨幕霓虹前，面对镜头保持站定。");
        fixture.currentShot.setPromptText("1-5秒开口说出口播生日祝福，口型同步。");
        fixture.currentShot.setDialogue("Q版西格玛男人：我是西格玛，今天祝我的姗宝，生日快乐。");
        fixture.currentShot.setVoiceOver("旁白：黑卡的粉色霓虹字逐渐亮起。");
        when(fixture.aiServiceClient.renderTextPrompt(any())).thenAnswer(invocation -> {
            AiTextGenerateRequest request = invocation.getArgument(0);
            String shotText = request.getVariables().get("shotText");
            assertTrue(shotText.contains("锁定对白"), shotText);
            assertTrue(shotText.contains("Q版西格玛男人：我是西格玛，今天祝我的姗宝，生日快乐。"), shotText);
            assertTrue(shotText.contains("锁定旁白"), shotText);
            assertTrue(shotText.contains("旁白：黑卡的粉色霓虹字逐渐亮起。"), shotText);
            assertTrue(shotText.contains("禁止改写"), shotText);
            assertTrue(shotText.contains("xi ge ma"), shotText);
            assertTrue(shotText.contains("xi ge ta"), shotText);
            String audioVisualProtocol = request.getVariables().get("audioVisualProtocol");
            assertTrue(audioVisualProtocol.contains("xi ge ma"), audioVisualProtocol);
            assertTrue(audioVisualProtocol.contains("xi ge ta"), audioVisualProtocol);
            return R.ok(shotText);
        });

        String prompt = fixture.service.previewShotVideoPrompt(fixture.dto()).getUserPrompt();

        assertTrue(prompt.contains("Q版西格玛男人：我是西格玛，今天祝我的姗宝，生日快乐。"), prompt);
        assertTrue(prompt.contains("禁止改写"), prompt);
        assertFalse(prompt.contains("{{shotText}}"), prompt);
    }
    @Test
    void optimizeShotScriptUpdatesCurrentShotWithAiReturnedFields() {
        TestFixture fixture = new TestFixture();
        fixture.previousShot.setCharacterIds("剑魂,狂战士（红狗）,男散打（乌鸡）");
        fixture.previousShot.setActionDesc("剑魂、狂战士（红狗）、男散打（乌鸡）同处暗黑深渊副本密闭空间。");
        fixture.currentShot.setCharacterIds("剑魂");
        fixture.currentShot.setActionDesc("剑魂右手拔出寒光剑，剑尖显出冷白光。");
        when(fixture.aiServiceClient.renderTextPrompt(any())).thenReturn(R.ok("""
                {
                  "transitionBeforeDesc": "连续镜头，单人镜头：画内主体锁定为剑魂，狂战士（红狗）和男散打（乌鸡）被裁切在画外不入画。",
                  "actionDesc": "剑魂右手拔出寒光剑，剑尖显出冷白光，结尾持剑站定。",
                  "promptText": "只拍剑魂单人半身，其他角色不得自动出现。"
                }
                """));

        AivideoShotScriptOptimizeDto dto = new AivideoShotScriptOptimizeDto();
        dto.setProjectId(1L);
        dto.setShotId(101L);
        dto.setCustomPrompt("保持剑魂单人镜头，不要把其他角色塞回画面。");
        dto.setPreflightFailures(List.of("上一镜角色疑似无说明消失：狂战士（红狗）、男散打（乌鸡）"));

        fixture.service.optimizeShotScript(dto);

        assertTrue(fixture.currentShot.getTransitionBeforeDesc().contains("单人镜头"), fixture.currentShot::getTransitionBeforeDesc);
        assertTrue(fixture.currentShot.getTransitionBeforeDesc().contains("男散打（乌鸡）"), fixture.currentShot::getTransitionBeforeDesc);
        assertTrue(fixture.currentShot.getActionDesc().contains("结尾持剑站定"), fixture.currentShot::getActionDesc);
    }

    @Test
    void optimizeShotScriptKeepsExistingDialogueAndVoiceOverWhenAiReturnsRewrittenSpeech() {
        TestFixture fixture = new TestFixture();
        fixture.currentShot.setCharacterIds("Q版西格玛男人");
        fixture.currentShot.setActionDesc("Q版西格玛男人站在雨幕霓虹前，左手拿着黑卡。");
        fixture.currentShot.setDialogue("Q版西格玛男人：我是西格玛，今天祝我的姗宝，生日快乐。");
        fixture.currentShot.setVoiceOver("旁白：黑卡的粉色霓虹字逐渐亮起。");
        when(fixture.aiServiceClient.renderTextPrompt(any())).thenReturn(R.ok("""
                {
                  "actionDesc": "Q版西格玛男人站在雨幕霓虹前，左手拿着黑卡，粉色字样稳定亮起。",
                  "dialogue": "Q版西格玛男人：生日快乐，这是给你的专属惊喜。",
                  "voiceOver": "旁白：这是给她准备的生日惊喜。"
                }
                """));

        AivideoShotScriptOptimizeDto dto = new AivideoShotScriptOptimizeDto();
        dto.setProjectId(1L);
        dto.setShotId(101L);
        dto.setPreflightFailures(List.of("道具资产缺失：卡片"));

        fixture.service.optimizeShotScript(dto);

        assertTrue(fixture.currentShot.getActionDesc().contains("粉色字样稳定亮起"), fixture.currentShot::getActionDesc);
        assertEquals("Q版西格玛男人：我是西格玛，今天祝我的姗宝，生日快乐。", fixture.currentShot.getDialogue());
        assertEquals("旁白：黑卡的粉色霓虹字逐渐亮起。", fixture.currentShot.getVoiceOver());
    }

    @Test
    void optimizeShotScriptRepairsNaturalLanguageAiResultIntoJson() {
        TestFixture fixture = new TestFixture();
        fixture.previousShot.setCharacterIds("剑魂,狂战士（红狗）,男散打（乌鸡）");
        fixture.currentShot.setCharacterIds("剑魂");
        fixture.currentShot.setActionDesc("剑魂右手拔出寒光剑，剑尖显出冷白光。");
        when(fixture.aiServiceClient.renderTextPrompt(any()))
                .thenReturn(R.ok("""
                        建议这样优化：
                        transitionBeforeDesc 写成单人镜头，明确狂战士（红狗）和男散打（乌鸡）在画外不入画。
                        actionDesc 写成剑魂右手拔出寒光剑，剑身显出冷白光，结尾持剑站定。
                        """))
                .thenReturn(R.ok("""
                        {
                          "transitionBeforeDesc": "连续镜头，单人镜头：画内主体锁定为剑魂，狂战士（红狗）和男散打（乌鸡）被裁切在画外不入画。",
                          "actionDesc": "剑魂右手拔出寒光剑，剑身显出冷白光，结尾持剑站定。",
                          "promptText": "只拍剑魂单人，其他角色不自动入画。",
                          "characterIds": "剑魂"
                        }
                        """));

        AivideoShotScriptOptimizeDto dto = new AivideoShotScriptOptimizeDto();
        dto.setProjectId(1L);
        dto.setShotId(101L);
        dto.setPreflightFailures(List.of("上一镜角色疑似无说明消失：狂战士（红狗）、男散打（乌鸡）"));

        fixture.service.optimizeShotScript(dto);

        verify(fixture.aiServiceClient, times(2)).renderTextPrompt(any());
        assertTrue(fixture.currentShot.getTransitionBeforeDesc().contains("画外不入画"), fixture.currentShot::getTransitionBeforeDesc);
        assertTrue(fixture.currentShot.getActionDesc().contains("结尾持剑站定"), fixture.currentShot::getActionDesc);
        assertTrue(fixture.currentShot.getPromptText().contains("其他角色不自动入画"), fixture.currentShot::getPromptText);
    }

    @Test
    void optimizeShotScriptIgnoresJsonExamplePlaceholderValues() {
        TestFixture fixture = new TestFixture();
        fixture.currentShot.setCharacterIds("剑魂");
        fixture.currentShot.setTransitionBeforeDesc("连续镜头：上一镜剑魂保持拔剑起势。");
        fixture.currentShot.setActionDesc("剑魂右手拔出寒光剑，剑尖显出冷白光。");
        fixture.currentShot.setPromptText("只拍剑魂单人，寒光剑保持在右手。");
        when(fixture.characterMapper.selectList(any()))
                .thenReturn(List.of(TestFixture.character(11L, "剑魂"), TestFixture.character(12L, "奶妈")));
        when(fixture.aiServiceClient.renderTextPrompt(any())).thenReturn(R.ok("""
                {
                  "transitionBeforeDesc": "连续镜头：明确说明上一镜角色/道具/方位如何衔接。",
                  "actionDesc": "只写当前镜头可执行动作，控制在本镜秒数预算内。",
                  "promptText": "给视频模型看的补充执行提示，锁定画内角色、道具、方位和结尾状态。",
                  "characterIds": "只写当前镜头画内角色名称或ID，多个用逗号分隔"
                }
                """));

        AivideoShotScriptOptimizeDto dto = new AivideoShotScriptOptimizeDto();
        dto.setProjectId(1L);
        dto.setShotId(101L);

        fixture.service.optimizeShotScript(dto);

        assertEquals("剑魂", fixture.currentShot.getCharacterIds());
        assertEquals("连续镜头：上一镜剑魂保持拔剑起势。", fixture.currentShot.getTransitionBeforeDesc());
        assertEquals("剑魂右手拔出寒光剑，剑尖显出冷白光。", fixture.currentShot.getActionDesc());
        assertEquals("只拍剑魂单人，寒光剑保持在右手。", fixture.currentShot.getPromptText());
    }

    @Test
    void optimizeShotScriptKeepsCurrentCharactersWhenAiReturnsUnknownCharacter() {
        TestFixture fixture = new TestFixture();
        fixture.currentShot.setCharacterIds("剑魂");
        when(fixture.characterMapper.selectList(any()))
                .thenReturn(List.of(TestFixture.character(11L, "剑魂"), TestFixture.character(12L, "奶妈")));
        when(fixture.aiServiceClient.renderTextPrompt(any())).thenReturn(R.ok("""
                {
                  "actionDesc": "剑魂右手拔出寒光剑，剑身冷白光稳定汇聚。",
                  "characterIds": "不存在角色"
                }
                """));

        AivideoShotScriptOptimizeDto dto = new AivideoShotScriptOptimizeDto();
        dto.setProjectId(1L);
        dto.setShotId(101L);

        fixture.service.optimizeShotScript(dto);

        assertEquals("剑魂", fixture.currentShot.getCharacterIds());
        assertFalse(fixture.currentShot.getCharacterIds().contains("不存在角色"));
        assertTrue(fixture.currentShot.getActionDesc().contains("冷白光稳定汇聚"));
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
    void previewRejectsGroupShotWhenBoundCharactersLessThanTextCount() {
        TestFixture fixture = new TestFixture();
        fixture.currentShot.setShotNo(1);
        fixture.currentShot.setCharacterIds("奶奶,剑魂");
        fixture.currentShot.setActionDesc("奶奶抬右手比1手势，其余三人点燃身前符文。");
        fixture.currentShot.setPromptText("四人副本开场，所有角色都在画内。");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service.previewShotVideoPrompt(fixture.dto()));

        assertTrue(exception.getMessage().contains("画内角色绑定不足"), exception::getMessage);
        assertTrue(exception.getMessage().contains("至少需要4个画内角色"), exception::getMessage);
        assertTrue(exception.getMessage().contains("只绑定了2个"), exception::getMessage);
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

        private static AiVideoCharacterPo character(Long characterId, String characterName) {
            AiVideoCharacterPo character = new AiVideoCharacterPo();
            character.setCharacterId(characterId);
            character.setProjectId(1L);
            character.setCharacterName(characterName);
            character.setDelFlag(0);
            return character;
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
