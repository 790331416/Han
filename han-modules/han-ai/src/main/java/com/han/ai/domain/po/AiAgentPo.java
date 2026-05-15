package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    private Long modelId;

    private String knowledgeBaseIds;

    private String mcpServerIds;

    private BigDecimal temperature;

    private Integer maxTokens;

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
