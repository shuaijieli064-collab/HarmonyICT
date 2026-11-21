package com.nexus.world.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.Result;
import com.nexus.world.dto.EventCreateRequest;
import com.nexus.world.dto.EventResponse;
import com.nexus.world.service.WorldEventService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 世界事件接口，契约见 api-specification.md 5.3 节。
 */
@RestController
@RequestMapping("/api/v1/events")
public class WorldEventController {

    private final WorldEventService eventService;

    public WorldEventController(WorldEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public Result<EventResponse> create(@Valid @RequestBody EventCreateRequest request) {
        return Result.ok(eventService.create(UserContext.get(), request));
    }

    @GetMapping
    public Result<PageResult<EventResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Long entityId) {
        return Result.ok(eventService.list(UserContext.get(), eventType, entityId, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<EventResponse> get(@PathVariable Long id) {
        return Result.ok(eventService.get(UserContext.get(), id));
    }
}