package com.han.ai.domain.vo;

import lombok.Data;

/**
 * Structured knowledge source for chat message insight.
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
