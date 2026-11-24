package com.nexus.asset.controller;

import com.nexus.asset.dto.AssetResponse;
import com.nexus.asset.dto.AssetSummaryResponse;
import com.nexus.asset.service.AssetService;
import com.nexus.auth.UserContext;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资产中心接口：世界模型资产台账的只读聚合视图。
 */
@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public Result<PageResult<AssetResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String keyword) {
        return Result.ok(assetService.list(UserContext.get(), entityType, keyword, page, pageSize));
    }

    @GetMapping("/summary")
    public Result<AssetSummaryResponse> summary() {
        return Result.ok(assetService.summary(UserContext.get()));
    }
}
