package com.han.ai.service.impl;

import java.util.List;

/**
 * 内置 Prompt 模板注册表。
 *
 * <p>用于在升级 SQL 漏执行或新租户缺少模板时提供运行期兜底，保证
 * Java fallback、SQL 初始化和管理端模板列表三套来源尽量保持一致。
 *
 * <p>此处仅保留与具体业务无关的通用示例模板；行业 / 业务专属模板应由对应业务模块
 * 或业务升级 SQL 注入，不在通用 AI 底座硬编码。
 */
final class AiPromptTemplateBuiltinRegistry {

    private AiPromptTemplateBuiltinRegistry() {
    }

    static List<Seed> all() {
        return List.of(
                new Seed("通用文本生成示例", "general_text", """
                        # 通用文本生成示例模板
                        你是通用文本写作助手。请根据用户输入生成结构清晰、表达准确的文本。
                        要求：
                        1. 保持事实准确，不臆造信息。
                        2. 按用户意图组织段落，必要时使用小标题或列表。
                        3. 语言简洁，避免冗余。
                        4. 输出 Markdown，不要解释流程。

                        用户输入：
                        {{input}}
                        """, "[\"input\"]", "通用文本生成示例模板"),
                new Seed("通用内容总结示例", "general_summary", """
                        # 通用内容总结示例模板
                        你是通用内容总结助手。请把输入内容压缩为要点清晰的摘要。
                        要求：
                        1. 提炼核心结论与关键信息，不遗漏要点。
                        2. 用有序或无序列表呈现要点。
                        3. 保留原意，不加入主观评价。
                        4. 输出 Markdown，不要解释流程。

                        待总结内容：
                        {{content}}
                        """, "[\"content\"]", "通用内容总结示例模板")
        );
    }

    /**
     * 内置模板种子数据。
     *
     * @param templateName 模板名称
     * @param category 模板分类
     * @param content 模板正文
     * @param variables 模板变量 JSON
     * @param description 模板说明
     */
    record Seed(String templateName, String category, String content, String variables, String description) {
    }
}
