package com.han.ai.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息图片附件（上传的多模态输入图或对话内生成图）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatImageVo {

    private Long fileId;

    private String url;

    private String name;
}
