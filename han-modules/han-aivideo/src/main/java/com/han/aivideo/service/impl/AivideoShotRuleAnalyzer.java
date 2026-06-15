package com.han.aivideo.service.impl;

import com.han.aivideo.domain.po.AiVideoPropPo;
import com.han.common.core.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 分镜动作预算和关键道具关联校验器。
 */
final class AivideoShotRuleAnalyzer {

    private static final List<String> STRONG_ACTION_KEYWORDS = List.of(
            "拔出", "拔剑", "出鞘", "挥剑", "挥砍", "斩", "刺向", "冲刺", "冲向", "跳起",
            "飞跃", "变身", "爆炸", "倒地", "起身", "悬浮", "坠落", "落水", "救援", "打斗",
            "搏斗", "俯冲", "释放", "施法", "掰弯", "击中", "劈中", "抽搐", "电流包裹", "飞起", "落下");

    private static final List<String> MAIN_ACTION_KEYWORDS = List.of(
            "拿起", "抬起", "低头", "抬头", "转身", "转向", "看向", "靠近", "走向", "递给",
            "接过", "交给", "传给", "展示", "指向", "站起", "坐下", "放入", "拉开", "推开",
            "合上", "打开", "写", "读", "跑", "跳", "抽出", "举起", "握住", "挥动", "靠拢");

    private static final List<String> REACTION_KEYWORDS = List.of(
            "嘴角", "微笑", "笑", "表情", "眼神", "露出", "发现", "惊讶", "困惑", "点头", "认可", "凝视", "深吸");

    private static final List<String> END_STATE_KEYWORDS = List.of(
            "结尾", "最后", "停在", "定格", "保持", "站定", "结束", "收束");

    private static final List<String> REQUIRED_PROP_KEYWORDS = List.of(
            "蓝色透明收纳盒", "寒光剑", "价格标签", "收纳盒", "试卷", "账本", "存折", "票据",
            "皮球", "钥匙", "手机", "书包", "笔记本", "铅笔", "卡片", "长剑", "光剑", "法杖",
            "魔杖", "盾牌", "盾", "弓箭", "弓", "匕首", "刀", "剑", "枪");

    private AivideoShotRuleAnalyzer() {
    }

    static void validateActionBudgetOrThrow(Integer shotNo, Integer durationSec, String actionDesc, String promptText) {
        ActionBudgetResult result = analyzeActionBudget(durationSec, actionDesc, promptText);
        if (!result.requiresSplit()) {
            return;
        }
        throw new BusinessException("动作预算过载：第" + safeShotNo(shotNo) + "镜为" + result.durationSec()
                + "秒，当前包含约" + result.mainActionCount() + "个主动作、" + result.strongActionCount()
                + "个强动作，已超过本时长可稳定生成范围。建议拆成多个分镜："
                + result.splitSuggestion());
    }

    static ActionBudgetResult analyzeActionBudget(Integer durationSec, String actionDesc, String promptText) {
        int normalizedDuration = normalizeDuration(durationSec);
        List<String> beats = splitBeats(actionDesc, promptText);
        double cost = 0D;
        int mainActionCount = 0;
        int strongActionCount = 0;
        int reactionCount = 0;
        for (String beat : beats) {
            if (!StringUtils.hasText(beat)) {
                continue;
            }
            if (containsAny(beat, STRONG_ACTION_KEYWORDS)) {
                cost += 2D;
                mainActionCount++;
                strongActionCount++;
                continue;
            }
            boolean endState = containsAny(beat, END_STATE_KEYWORDS);
            if (containsAny(beat, MAIN_ACTION_KEYWORDS) && !endState) {
                cost += 1D;
                mainActionCount++;
                continue;
            }
            if (containsAny(beat, REACTION_KEYWORDS)) {
                cost += 0.25D;
                reactionCount++;
            }
        }
        Budget budget = budgetFor(normalizedDuration);
        boolean requiresSplit = mainActionCount > budget.maxMainActions()
                || strongActionCount > budget.maxStrongActions()
                || cost > budget.maxCost();
        return new ActionBudgetResult(normalizedDuration, cost, mainActionCount, strongActionCount,
                reactionCount, requiresSplit, buildSplitSuggestion(beats, normalizedDuration));
    }

    static List<String> detectRequiredPropNames(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        Set<String> names = new LinkedHashSet<>();
        for (String keyword : REQUIRED_PROP_KEYWORDS) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
            if (normalized.contains(lowerKeyword)) {
                if ("剑".equals(keyword) && hasMoreSpecificSword(names, normalized)) {
                    continue;
                }
                names.add(keyword);
            }
        }
        if (names.contains("寒光剑")) {
            names.remove("剑");
        }
        removeContainedShortNames(names);
        return new ArrayList<>(names);
    }

    static void validateRequiredPropsOrThrow(String text, List<AiVideoPropPo> props, boolean requireLockedImage) {
        for (String requiredName : detectRequiredPropNames(text)) {
            AiVideoPropPo prop = findProp(requiredName, props);
            if (prop == null) {
                throw new BusinessException("道具未关联：" + requiredName
                        + "。出现武器、手持物、发光物或剧情推进物时，必须先在道具资产中建立同名道具，并在分镜中保持颜色、材质、归属和交接关系。");
            }
            if (requireLockedImage && prop.getLockedMediaId() == null && requiresLockedReference(text, requiredName)) {
                throw new BusinessException("道具未锁定参考图：" + requiredName
                        + "。涉及拔出、持有、指向、挥动或交接的关键道具，需要先生成并确认道具图，避免视频里颜色、形状或归属漂移。");
            }
        }
    }

    private static boolean requiresLockedReference(String text, String requiredName) {
        return isWeaponName(requiredName)
                || containsAny(text, List.of("拔出", "拔剑", "出鞘", "持", "握住", "举起", "挥动", "指向",
                "递给", "接过", "交给", "传给", "发光", "亮起"));
    }

    private static boolean isWeaponName(String name) {
        return containsAny(name, List.of("剑", "刀", "枪", "法杖", "魔杖", "盾", "弓", "匕首"));
    }

    private static AiVideoPropPo findProp(String requiredName, List<AiVideoPropPo> props) {
        if (!StringUtils.hasText(requiredName) || props == null) {
            return null;
        }
        for (AiVideoPropPo prop : props) {
            if (prop == null || Integer.valueOf(1).equals(prop.getDelFlag())) {
                continue;
            }
            String propName = prop.getPropName();
            if (!StringUtils.hasText(propName)) {
                continue;
            }
            if (propName.contains(requiredName) || requiredName.contains(propName)) {
                return prop;
            }
        }
        return null;
    }

    private static List<String> splitBeats(String actionDesc, String promptText) {
        String text = String.join("，", safeText(actionDesc), safeText(promptText))
                .replace("然后", "，")
                .replace("接着", "，")
                .replace("随后", "，")
                .replace("再", "，")
                .replace("同时", "，");
        String[] parts = text.split("[，,；;。！？!?\\n]+");
        List<String> beats = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                beats.add(part.trim());
            }
        }
        return beats;
    }

    private static Budget budgetFor(int durationSec) {
        if (durationSec <= 5) {
            return new Budget(1, 1, 2.25D);
        }
        if (durationSec <= 6) {
            return new Budget(2, 1, 3.25D);
        }
        return new Budget(3, 2, 4.25D);
    }

    private static int normalizeDuration(Integer durationSec) {
        if (durationSec == null) {
            return 5;
        }
        if (durationSec <= 5) {
            return 5;
        }
        if (durationSec <= 6) {
            return 6;
        }
        return 8;
    }

    private static String buildSplitSuggestion(List<String> beats, int durationSec) {
        String first = beats == null || beats.isEmpty() ? "起始状态 + 单个核心动作" : beats.get(0);
        String second = beats != null && beats.size() > 1 ? beats.get(1) : "结果状态 + 反应";
        if (durationSec <= 5) {
            return "镜头A只保留“" + first + "”，镜头B再写“" + second + " + 明确结尾状态”。";
        }
        return "保留当前镜头的前两个动作，把后续动作、反应或结尾状态拆到下一镜。";
    }

    private static boolean hasMoreSpecificSword(Set<String> names, String normalizedText) {
        return names.stream().anyMatch(name -> name.contains("剑") && name.length() > 1)
                || normalizedText.contains("寒光剑")
                || normalizedText.contains("长剑")
                || normalizedText.contains("光剑");
    }

    private static void removeContainedShortNames(Set<String> names) {
        List<String> values = new ArrayList<>(names);
        for (String name : values) {
            boolean containedByLonger = values.stream()
                    .anyMatch(other -> other.length() > name.length() && other.contains(name));
            if (containedByLonger) {
                names.remove(name);
            }
        }
    }

    private static boolean containsAny(String text, List<String> keywords) {
        if (!StringUtils.hasText(text) || keywords == null) {
            return false;
        }
        return keywords.stream().anyMatch(keyword -> StringUtils.hasText(keyword) && text.contains(keyword));
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }

    private static int safeShotNo(Integer shotNo) {
        return shotNo == null ? 0 : shotNo;
    }

    record ActionBudgetResult(int durationSec, double cost, int mainActionCount, int strongActionCount,
                              int reactionCount, boolean requiresSplit, String splitSuggestion) {
    }

    private record Budget(int maxMainActions, int maxStrongActions, double maxCost) {
    }
}
