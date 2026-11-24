package com.nexus.intel.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.Result;
import com.nexus.intel.dto.IntelFeedItemResponse;
import com.nexus.intel.dto.IntelSummaryResponse;
import com.nexus.intel.service.IntelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 情报中心接口：风险 / 事件 / Agent 执行的只读聚合视图。
 */
@RestController
@RequestMapping("/api/v1/intel")
public class IntelController {

    private final IntelService intelService;

    public IntelController(IntelService intelService) {
        this.intelService = intelService;
    }

    @GetMapping("/summary")
    public Result<IntelSummaryResponse> summary() {
        return Result.ok(intelService.summary(UserContext.get()));
    }

    @GetMapping("/feed")
    public Result<PageResult<IntelFeedItemResponse>> feed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "30") int days) {
        return Result.ok(intelService.feed(UserContext.get(), type, days, page, pageSize));
    }
}
