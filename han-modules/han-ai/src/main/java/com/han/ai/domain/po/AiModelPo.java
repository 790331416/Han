package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.web.sensitive.Sensitive;
import com.han.common.web.sensitive.SensitiveType;
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

    @Sensitive(value = SensitiveType.CUSTOM, prefixKeep = 4, suffixKeep = 4)
    private String apiKey;

    @TableField(exist = false)
    private Boolean credentialConfigured;

    @TableField(exist = false)
    private String credentialSource;

    private Integer maxTokens;

    private BigDecimal temperature;

    /**
     * 是否支持视觉输入（图片理解）：'1'支持 '0'不支持
     */
    private String supportsVision;

    private String status;

    private String remark;

    private Long tenantId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
