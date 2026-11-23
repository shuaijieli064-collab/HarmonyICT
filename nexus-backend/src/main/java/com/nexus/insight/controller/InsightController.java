package com.nexus.insight.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.Result;
import com.nexus.insight.dto.InsightOverviewResponse;
import com.nexus.insight.dto.TimelineResponse;
import com.nexus.insight.service.InsightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 洞察接口（P2 引擎族概览 + P3 未来时间线）。只读。
 */
@RestController
@RequestMapping("/api/v1/insight")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping("/overview")
    public Result<InsightOverviewResponse> overview() {
        return Result.ok(insightService.overview(UserContext.get()));
    }

    @GetMapping("/timeline")
    public Result<TimelineResponse> timeline(@RequestParam(defaultValue = "30") int days) {
        int clamped = Math.max(1, Math.min(90, days));
        return Result.ok(insightService.timeline(UserContext.get(), clamped));
    }
}
