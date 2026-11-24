package com.nexus.dashboard.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.Result;
import com.nexus.dashboard.dto.DashboardSummaryResponse;
import com.nexus.dashboard.dto.HealthScoreResponse;
import com.nexus.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 驾驶舱聚合统计接口（体量扩充新增）。
 * 为什么放在独立模块：dashboard 是跨模块只读聚合，不属于任何单一业务域；
 * 只依赖各模块 Mapper，不引入服务层耦合。
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public Result<DashboardSummaryResponse> summary() {
        return Result.ok(dashboardService.summary(UserContext.get()));
    }

    @GetMapping("/health-score")
    public Result<HealthScoreResponse> healthScore() {
        return Result.ok(dashboardService.healthScore(UserContext.get()));
    }
}
