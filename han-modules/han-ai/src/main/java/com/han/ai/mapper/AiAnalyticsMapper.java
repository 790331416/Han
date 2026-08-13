package com.han.ai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI Token 用量统计 Mapper。
 */
@Mapper
public interface AiAnalyticsMapper {

    /**
     * 按模型维度汇总 Token 用量。
     *
     * @param startTime start time
     * @param endTime end time
     * @return statistics rows
     */
    @Select("""
            SELECT COALESCE(m.model_name, 'Unknown') AS model_name,
                   COUNT(msg.message_id) AS call_count,
                   COALESCE(SUM(CASE WHEN msg.role IN ('user', 'system') THEN msg.token_count ELSE 0 END), 0) AS prompt_tokens,
                   COALESCE(SUM(CASE WHEN msg.role = 'assistant' THEN msg.token_count ELSE 0 END), 0) AS completion_tokens,
                   COALESCE(SUM(msg.token_count), 0) AS total_tokens
            FROM ai_chat_message msg
            LEFT JOIN ai_conversation conv ON conv.conversation_id = msg.conversation_id
            LEFT JOIN ai_model m ON m.model_id = conv.model_id
            WHERE msg.create_time BETWEEN #{startTime} AND #{endTime}
            GROUP BY m.model_name
            ORDER BY total_tokens DESC, call_count DESC
            """)
    List<Map<String, Object>> statsByModel(@Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 按用户维度汇总 Token 用量。
     *
     * @param startTime start time
     * @param endTime end time
     * @return statistics rows
     */
    @Select("""
            SELECT conv.user_id AS user_id,
                   COUNT(msg.message_id) AS call_count,
                   COALESCE(SUM(msg.token_count), 0) AS total_tokens
            FROM ai_chat_message msg
            INNER JOIN ai_conversation conv ON conv.conversation_id = msg.conversation_id
            WHERE msg.create_time BETWEEN #{startTime} AND #{endTime}
            GROUP BY conv.user_id
            ORDER BY total_tokens DESC, call_count DESC
            LIMIT 20
            """)
    List<Map<String, Object>> statsByUser(@Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 按天维度汇总 Token 用量。
     *
     * @param startTime start time
     * @param endTime end time
     * @return statistics rows
     */
    @Select("""
            SELECT TO_CHAR(msg.create_time, 'YYYY-MM-DD') AS date,
                   COUNT(msg.message_id) AS call_count,
                   COALESCE(SUM(msg.token_count), 0) AS total_tokens
            FROM ai_chat_message msg
            WHERE msg.create_time BETWEEN #{startTime} AND #{endTime}
            GROUP BY TO_CHAR(msg.create_time, 'YYYY-MM-DD')
            ORDER BY date ASC
            """)
    List<Map<String, Object>> statsByDay(@Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);
}
