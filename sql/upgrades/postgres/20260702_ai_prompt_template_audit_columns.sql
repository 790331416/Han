-- 20260702: 补齐 ai_prompt_template 审计列
-- 背景: 95 环境 ai_prompt_template 表由旧版本建表, 缺 create_by/update_by 两列,
--       但 AiPromptTemplatePo 含 createBy/updateBy 字段 + full-init.sql 已定义这两列,
--       导致 MyBatis-Plus 自动 SELECT 全字段时报 column "create_by" does not exist, Prompt 模板列表/保存 500。
-- 修复: 幂等补列, 与 full-init.sql 对齐 (VARCHAR(64) DEFAULT '')。
-- 说明: 本脚本是 95 现场热修入口, 可单独执行; 在标准升级顺序里这两列已由
--       phase8_prompt_template_alignment.sql 补过, 此处是空操作。
-- 20260812 修正: 列存在性判断补 table_schema = 'public', 与仓内其他同类判断保持一致,
--       避免 search_path 上有同名表时判定出错。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'ai_prompt_template' AND column_name = 'create_by'
    ) THEN
        ALTER TABLE ai_prompt_template ADD COLUMN create_by VARCHAR(64) DEFAULT '';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'ai_prompt_template' AND column_name = 'update_by'
    ) THEN
        ALTER TABLE ai_prompt_template ADD COLUMN update_by VARCHAR(64) DEFAULT '';
    END IF;
END $$;
