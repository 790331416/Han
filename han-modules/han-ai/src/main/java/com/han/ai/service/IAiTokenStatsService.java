package com.han.ai.service;

import java.util.List;
import java.util.Map;

/**
 * Token statistics service.
 */
public interface IAiTokenStatsService {

    /**
     * Aggregate token usage by model.
     *
     * @param startTime start time text
     * @param endTime end time text
     * @return statistics rows
     */
    List<Map<String, Object>> statsByModel(String startTime, String endTime);

    /**
     * Aggregate token usage by user.
     *
     * @param startTime start time text
     * @param endTime end time text
     * @return statistics rows
     */
    List<Map<String, Object>> statsByUser(String startTime, String endTime);

    /**
     * Aggregate token usage by day.
     *
     * @param startTime start time text
     * @param endTime end time text
     * @return statistics rows
     */
    List<Map<String, Object>> statsByDay(String startTime, String endTime);
}
