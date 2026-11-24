package com.nexus.world.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.Result;
import com.nexus.world.dto.FactCreateRequest;
import com.nexus.world.dto.FactResponse;
import com.nexus.world.dto.FactUpdateRequest;
import com.nexus.world.service.WorldFactService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 世界事实接口，契约见 api-specification.md 5.2 节。
 */
@RestController
@RequestMapping("/api/v1/facts")
public class WorldFactController {

    private final WorldFactService factService;

    public WorldFactController(WorldFactService factService) {
        this.factService = factService;
    }

    @PostMapping
    public Result<FactResponse> create(@Valid @RequestBody FactCreateRequest request) {
        return Result.ok(factService.create(UserContext.get(), request));
    }

    @GetMapping
    public Result<PageResult<FactResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String predicate,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String status) {
        return Result.ok(factService.list(UserContext.get(), subjectId, predicate, sourceId, status, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<FactResponse> get(@PathVariable Long id) {
        return Result.ok(factService.get(UserContext.get(), id));
    }

    /** 事实历史版本：链式失效保留的全部版本按时间倒序（扩展 B 组） */
    @GetMapping("/{id}/history")
    public Result<java.util.List<FactResponse>> history(@PathVariable Long id) {
        return Result.ok(factService.history(UserContext.get(), id));
    }

    @PutMapping("/{id}")
    public Result<FactResponse> update(@PathVariable Long id, @Valid @RequestBody FactUpdateRequest request) {
        return Result.ok(factService.update(UserContext.get(), id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        factService.delete(UserContext.get(), id);
        return Result.ok();
    }
}