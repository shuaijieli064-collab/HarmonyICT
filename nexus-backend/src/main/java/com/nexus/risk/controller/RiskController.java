package com.nexus.risk.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.Result;
import com.nexus.risk.dto.RiskDetailResponse;
import com.nexus.risk.dto.RiskResponse;
import com.nexus.risk.service.RiskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 风险接口，契约见 api-specification.md 6 节。风险只能由规则引擎产生，无创建入口；
 * /scan 仅同步触发既有引擎扫描（演示与联调，避免等待 60s 定时周期）。
 */
@RestController
@RequestMapping("/api/v1/risks")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @GetMapping
    public Result<PageResult<RiskResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskType) {
        return Result.ok(riskService.list(UserContext.get(), level, status, riskType, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<RiskDetailResponse> get(@PathVariable Long id) {
        return Result.ok(riskService.get(UserContext.get(), id));
    }

    @PutMapping("/{id}/acknowledge")
    public Result<RiskResponse> acknowledge(@PathVariable Long id) {
        return Result.ok(riskService.acknowledge(UserContext.get(), id));
    }

    @PutMapping("/{id}/resolve")
    public Result<RiskResponse> resolve(@PathVariable Long id) {
        return Result.ok(riskService.resolve(UserContext.get(), id));
    }

    /** 手动触发一次全量风险扫描，返回本轮新增风险数 */
    @PostMapping("/scan")
    public Result<Map<String, Integer>> scan() {
        return Result.ok(Map.of("created", riskService.scanNow()));
    }

    /** 近 N 天风险趋势（快照数据，缺失日期补 0；days 缺省 7，上限 30） */
    @GetMapping("/trend")
    public Result<java.util.List<com.nexus.risk.snapshot.RiskSnapshotService.TrendPoint>> trend(
            @RequestParam(defaultValue = "7") int days) {
        return Result.ok(riskService.trend(UserContext.get(), days));
    }
}