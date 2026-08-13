package com.han.ai.service.impl;

import com.han.ai.mapper.AiAnalyticsMapper;
import com.han.ai.service.IAiTokenStatsService;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Token 用量统计服务实现。
 */
@Service
@RequiredArgsConstructor
public class AiTokenStatsServiceImpl implements IAiTokenStatsService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiAnalyticsMapper aiAnalyticsMapper;

    @Override
    public List<Map<String, Object>> statsByModel(String startTime, String endTime) {
        return aiAnalyticsMapper.statsByModel(parseDateTime(startTime, "开始时间"), parseDateTime(endTime, "结束时间"));
    }

    @Override
    public List<Map<String, Object>> statsByUser(String startTime, String endTime) {
        return aiAnalyticsMapper.statsByUser(parseDateTime(startTime, "开始时间"), parseDateTime(endTime, "结束时间"));
    }

    @Override
    public List<Map<String, Object>> statsByDay(String startTime, String endTime) {
        return aiAnalyticsMapper.statsByDay(parseDateTime(startTime, "开始时间"), parseDateTime(endTime, "结束时间"));
    }

    private LocalDateTime parseDateTime(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(fieldName + "不能为空");
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(fieldName + "格式不正确，应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
