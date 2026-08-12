package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI agent entity.
 */
@Data
@TableName("ai_agent")
public class AiAgentPo {

    @TableId(value = "agent_id", type = IdType.AUTO)
    private Long agentId;

    private String agentName;

    private String description;

    private String avatar;

    private String systemPrompt;

    private String prologue;

    /**
     * 开场推荐问题（JSON 字符串数组，最多 5 条；空数组=不展示）
     */
    private String suggestedQuestions;

    private Long modelId;

    private String knowledgeBaseIds;

    private String mcpServerIds;

    private BigDecimal temperature;

    private Integer maxTokens;

    /**
     * 对话历史注入条数（NULL=默认 12）；更新策略 ALWAYS：清空即恢复默认
     */
    @TableField(value = "history_limit", updateStrategy = FieldStrategy.ALWAYS)
    private Integer historyLimit;

    /**
     * 知识库检索返回条数（NULL=默认 5）
     */
    @TableField(value = "retrieval_top_k", updateStrategy = FieldStrategy.ALWAYS)
    private Integer retrievalTopK;

    /**
     * 向量检索相似度阈值（NULL=默认 0.30）
     */
    @TableField(value = "similarity_threshold", updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal similarityThreshold;

    /**
     * 公开分享链接 key（发布时生成，重置后旧链接失效）
     */
    private String shareKey;

    private String status;

    private Long tenantId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @TableField("published")
    private String publishedRaw;

    @TableField("del_flag")
    private Integer delFlag;

    @TableField(exist = false)
    private String welcomeMessage;

    @JsonProperty("published")
    public boolean getPublished() {
        return "1".equals(publishedRaw);
    }

    @JsonProperty("published")
    public void setPublished(Boolean published) {
        this.publishedRaw = Boolean.TRUE.equals(published) ? "1" : "0";
    }

    @JsonIgnore
    public String getPublishedRaw() {
        return publishedRaw;
    }

    @JsonIgnore
    public void setPublishedRaw(String publishedRaw) {
        this.publishedRaw = publishedRaw;
    }
}
