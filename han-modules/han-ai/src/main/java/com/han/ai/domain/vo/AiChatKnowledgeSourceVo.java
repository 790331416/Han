package com.han.ai.domain.vo;

import lombok.Data;

/**
 * 对话消息洞察用的结构化知识来源。
 */
@Data
public class AiChatKnowledgeSourceVo {

    private Long kbId;

    private String kbName;

    private String kbType;

    private String kbStatus;

    private Integer documentCount;

    private Integer paragraphCount;

    private Long charCount;

    private Long paragraphId;

    private String paragraphTitle;

    private Integer hitCount;

    private String excerpt;

    /** 相关度（向量=余弦相似度，关键词=启发式），0~1 */
    private Double score;

    /** 检索方式：vector / keyword */
    private String retrievalType;
}
