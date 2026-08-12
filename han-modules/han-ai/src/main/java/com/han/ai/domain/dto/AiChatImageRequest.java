package com.han.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 对话内文生图入参
 */
@Data
public class AiChatImageRequest {

    private Long conversationId;

    /**
     * IMAGE 类型模型ID，不传则用默认图片模型
     */
    private Long modelId;

    @NotBlank(message = "绘图提示词不能为空")
    @Size(max = 5000, message = "绘图提示词不能超过 5000 字符")
    private String prompt;

    /**
     * 尺寸，如 1024x1024，不传用模型默认
     */
    @Size(max = 20, message = "图片尺寸格式不合法")
    private String size;
}
