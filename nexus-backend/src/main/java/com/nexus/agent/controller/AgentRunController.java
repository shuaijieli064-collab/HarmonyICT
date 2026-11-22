package com.nexus.agent.controller;

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