package com.han.ai.controller;

import com.han.ai.service.IAiTokenStatsService;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Token 用量统计控制器。
 */
@AdminAuth
@RestController
@RequestMapping("/ai/token")
@RequiredArgsConstructor
public class AiTokenStatsController {

    private final IAiTokenStatsService aiTokenStatsService;

    /**
     * 按模型维度查询 Token 用量。
     *
     * @param startTime start time
     * @param endTime end time
     * @return statistics rows
     */
    @GetMapping("/stats/model")
    @PreAuthorize("@ss.hasAuthority('ai:token:stats')")
    public R<List<Map<String, Object>>> statsByModel(String startTime, String endTime) {
        return R.ok(aiTokenStatsService.statsByModel(startTime, endTime));
    }

    /**
     * 按用户维度查询 Token 用量。
     *
     * @param startTime start time
     * @param endTime end time
     * @return statistics rows
     */
    @GetMapping("/stats/user")
    @PreAuthorize("@ss.hasAuthority('ai:token:stats')")
    public R<List<Map<String, Object>>> statsByUser(String startTime, String endTime) {
        return R.ok(aiTokenStatsService.statsByUser(startTime, endTime));
    }

    /**
     * 按天维度查询 Token 用量。
     *
     * @param startTime start time
     * @param endTime end time
     * @return statistics rows
     */
    @GetMapping("/stats/daily")
    @PreAuthorize("@ss.hasAuthority('ai:token:stats')")
    public R<List<Map<String, Object>>> statsByDay(String startTime, String endTime) {
        return R.ok(aiTokenStatsService.statsByDay(startTime, endTime));
    }
}
