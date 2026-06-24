package com.han.aivideo.service.impl;

import com.han.aivideo.domain.po.AiVideoPropPo;
import com.han.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AivideoShotRuleAnalyzerTest {

    @Test
    void fiveSecondShotRejectsStrongActionChain() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                AivideoShotRuleAnalyzer.validateActionBudgetOrThrow(
                        1,
                        5,
                        "剑魂右手拔出寒光剑，剑尖指向深渊柱，嘴角勾起笑，结尾持剑站在柱前。",
                        ""));

        assertTrue(exception.getMessage().contains("动作预算过载"), exception::getMessage);
        assertTrue(exception.getMessage().contains("拆成"), exception::getMessage);
    }

    @Test
    void sixSecondShotAllowsTwoSimpleSequentialActions() {
        assertDoesNotThrow(() ->
                AivideoShotRuleAnalyzer.validateActionBudgetOrThrow(
                        2,
                        6,
                        "喵小萌缓慢抬头，低声读出账本数字，结尾停在困惑表情。",
                        ""));
    }

    @Test
    void fiveSecondShotDoesNotDoubleCountPromptRepeatingActionDesc() {
        assertDoesNotThrow(() ->
                AivideoShotRuleAnalyzer.validateActionBudgetOrThrow(
                        2,
                        5,
                        "剑魂右手拔出寒光剑，剑身显出冷白光，嘴角勾起拽笑，结尾停在寒光剑完全握在右手、剑身横在身前的姿态。",
                        "9:16竖屏3DQ萌国漫，剑魂单人中景，右手拔出寒光剑，剑身显出冷白光，嘴角拽笑，结尾寒光剑完全握在右手、剑身横在身前。"));
    }

    @Test
    void detectsNamedWeaponAsRequiredProp() {
        List<String> names = AivideoShotRuleAnalyzer.detectRequiredPropNames(
                "剑魂右手拔出寒光剑，剑尖指向深渊柱。");

        assertEquals(List.of("寒光剑"), names);
    }

    @Test
    void rejectsWeaponActionWithoutLinkedPropAsset() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                AivideoShotRuleAnalyzer.validateRequiredPropsOrThrow(
                        "剑魂右手拔出寒光剑，结尾持剑站定。",
                        List.of(),
                        false));

        assertTrue(exception.getMessage().contains("道具未关联"), exception::getMessage);
        assertTrue(exception.getMessage().contains("寒光剑"), exception::getMessage);
    }

    @Test
    void rejectsWeaponActionWithoutLockedPropImageWhenVideoNeedsAnchor() {
        AiVideoPropPo prop = new AiVideoPropPo();
        prop.setPropName("寒光剑");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                AivideoShotRuleAnalyzer.validateRequiredPropsOrThrow(
                        "剑魂右手拔出寒光剑，结尾持剑站定。",
                        List.of(prop),
                        true));

        assertTrue(exception.getMessage().contains("道具未锁定参考图"), exception::getMessage);
        assertTrue(exception.getMessage().contains("寒光剑"), exception::getMessage);
    }

    @Test
    void matchesRequiredPropByTypeOrVisualDescriptionWhenNameIsSpecific() {
        AiVideoPropPo prop = new AiVideoPropPo();
        prop.setPropName("迪宝生日快乐发光牌");
        prop.setPropType("剧情卡片");
        prop.setVisualDesc("黑色亚克力卡片，粉色霓虹字，作为当前镜头手持祝福道具。");
        prop.setLockedMediaId(9001L);

        assertDoesNotThrow(() ->
                AivideoShotRuleAnalyzer.validateRequiredPropsOrThrow(
                        "Q版西格玛男人左手握住卡片，正面展示粉色霓虹生日祝福字样。",
                        List.of(prop),
                        true));
    }
}
