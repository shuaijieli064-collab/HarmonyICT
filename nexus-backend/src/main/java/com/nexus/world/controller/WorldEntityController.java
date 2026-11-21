package com.nexus.world.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.Result;
import com.nexus.world.dto.EntityCreateRequest;
import com.nexus.world.dto.EntityResponse;
import com.nexus.world.dto.EntityUpdateRequest;
import com.nexus.world.service.WorldEntityService;
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
 * 世界实体接口，契约见 api-specification.md 5.1 节。
 */
@RestController
@RequestMapping("/api/v1/entities")
public class WorldEntityController {

    private final WorldEntityService entityService;

    public WorldEntityController(WorldEntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public Result<EntityResponse> create(@Valid @RequestBody EntityCreateRequest request) {
        return Result.ok(entityService.create(UserContext.get(), request));
    }

    @GetMapping
    public Result<PageResult<EntityResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String keyword) {
        return Result.ok(entityService.list(UserContext.get(), entityType, keyword, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<EntityResponse> get(@PathVariable Long id) {
        return Result.ok(entityService.get(UserContext.get(), id));
    }

    @PutMapping("/{id}")
    public Result<EntityResponse> update(@PathVariable Long id, @Valid @RequestBody EntityUpdateRequest request) {
        return Result.ok(entityService.update(UserContext.get(), id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        entityService.delete(UserContext.get(), id);
        return Result.ok();
    }
}