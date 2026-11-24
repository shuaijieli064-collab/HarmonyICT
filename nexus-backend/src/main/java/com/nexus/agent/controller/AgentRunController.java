package com.nexus.agent.controller;

import com.nexus.agent.dto.AgentActionLogResponse;
import com.nexus.agent.dto.AgentRunResponse;
import com.nexus.agent.dto.RunAuthorizeRequest;
import com.nexus.agent.dto.RunCreateRequest;
import com.nexus.agent.dto.RunDetailResponse;
import com.nexus.agent.service.AgentRunService;
import com.nexus.auth.UserContext;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 执行接口，契约见 api-specification.md 8 节。
 */
@RestController
@RequestMapping("/api/v1/agent/runs")
public class AgentRunController {

    private final AgentRunService runService;

    public AgentRunController(AgentRunService runService) {
        this.runService = runService;
    }

    @PostMapping
    public Result<AgentRunResponse> create(@Valid @RequestBody RunCreateRequest request) {
        return Result.ok(runService.create(UserContext.get(), request));
    }

    @GetMapping
    public Result<PageResult<AgentRunResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(runService.list(UserContext.get(), status, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<RunDetailResponse> get(@PathVariable Long id) {
        return Result.ok(runService.get(UserContext.get(), id));
    }

    /**
     * V3：执行日志分页查询（Agent Center 时间线），字段与详情内 logs 一致，附审计摘要。
     * 完整字段说明与前端接线示例见 com/nexus/agent/AGENT-LOG-API.md（不修改 api-spec 主契约）。
     */
    @GetMapping("/{id}/logs")
    public Result<PageResult<AgentActionLogResponse>> logs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String result) {
        return Result.ok(runService.listLogs(UserContext.get(), id, level, result, page, pageSize));
    }

    @PostMapping("/{id}/execute")
    public Result<AgentRunResponse> execute(@PathVariable Long id) {
        return Result.ok(runService.execute(UserContext.get(), id));
    }

    @PostMapping("/{id}/authorize")
    public Result<RunDetailResponse> authorize(@PathVariable Long id,
                                               @Valid @RequestBody RunAuthorizeRequest request) {
        return Result.ok(runService.authorize(UserContext.get(), id, request));
    }
}