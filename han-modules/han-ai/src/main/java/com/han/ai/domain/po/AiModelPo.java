package com.han.ai.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.web.sensitive.Sensitive;
import com.han.common.web.sensitive.SensitiveType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称不能超过 100 字符")
    private String modelName;

    @NotBlank(message = "模型类型不能为空")
    @Size(max = 20, message = "模型类型不能超过 20 字符")
    private String modelType;

    @NotBlank(message = "供应商不能为空")
    @Size(max = 50, message = "供应商不能超过 50 字符")
    private String provider;

    @NotBlank(message = "模型标识不能为空")
    @Size(max = 100, message = "模型标识不能超过 100 字符")
    private String modelCode;

    @NotBlank(message = "API Base URL 不能为空")
    @Size(max = 500, message = "API Base URL 不能超过 500 字符")
    private String baseUrl;

    @Size(max = 500, message = "API Key 不能超过 500 字符")
    @Sensitive(value = SensitiveType.CUSTOM, prefixKeep = 4, suffixKeep = 4)
    private String apiKey;

    @TableField(exist = false)
    private Boolean credentialConfigured;

    @TableField(exist = false)
    private String credentialSource;

    @Min(value = 1, message = "最大Token数必须大于 0")
    @Max(value = 1_000_000, message = "最大Token数不能超过 1000000")
    private Integer maxTokens;

    @DecimalMin(value = "0.0", message = "温度必须在 0 到 2 之间")
    @DecimalMax(value = "2.0", message = "温度必须在 0 到 2 之间")
    private BigDecimal temperature;

    /**
     * 是否支持视觉输入（图片理解）：'1'支持 '0'不支持
     */
    @Pattern(regexp = "^[01]$", message = "视觉能力开关只能是 0 或 1")
    private String supportsVision;

    @Pattern(regexp = "^[01]$", message = "模型状态只能是 0（启用）或 1（停用）")
    private String status;

    @Size(max = 500, message = "备注不能超过 500 字符")
    private String remark;

    private Long tenantId;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
