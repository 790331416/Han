package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI model config.
 */
@Data
@TableName("ai_model")
public class AiModelPo {

    @TableId(value = "model_id", type = IdType.AUTO)
    private Long modelId;

    private String modelName;

    private String modelType;

    private String provider;

    private String modelCode;

    private String baseUrl;

    private String apiKey;

    private Integer maxTokens;

    private BigDecimal temperature;

    private String status;

    private String remark;

    private Long tenantId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
