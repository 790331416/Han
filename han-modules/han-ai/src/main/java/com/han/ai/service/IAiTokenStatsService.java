package com.han.ai.service;

import java.util.List;
import java.util.Map;

/**
 * Token 用量统计服务。
 */
public interface IAiTokenStatsService {

    /**
     * 按模型维度汇总 Token 用量。
     *
     * @param startTime start time text
     * @param endTime end time text
     * @return statistics rows
     */
    List<Map<String, Object>> statsByModel(String startTime, String endTime);

    /**
     * 按用户维度汇总 Token 用量。
     *
     * @param startTime start time text
     * @param endTime end time text
     * @return statistics rows
     */
    List<Map<String, Object>> statsByUser(String startTime, String endTime);

    /**
     * 按天维度汇总 Token 用量。
     *
     * @param startTime start time text
     * @param endTime end time text
     * @return statistics rows
     */
    List<Map<String, Object>> statsByDay(String startTime, String endTime);
}
