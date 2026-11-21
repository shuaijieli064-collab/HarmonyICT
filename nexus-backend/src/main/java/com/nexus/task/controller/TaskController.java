package com.nexus.task.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.Result;
import com.nexus.task.dto.TaskCreateRequest;
import com.nexus.task.dto.TaskResponse;
import com.nexus.task.dto.TaskStatusRequest;
import com.nexus.task.dto.TaskUpdateRequest;
import com.nexus.task.service.TaskService;
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
 * 任务接口，契约见 api-specification.md 7 节。
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public Result<TaskResponse> create(@Valid @RequestBody TaskCreateRequest request) {
        return Result.ok(taskService.create(UserContext.get(), request));
    }

    @GetMapping
    public Result<PageResult<TaskResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType) {
        return Result.ok(taskService.list(UserContext.get(), status, sourceType, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<TaskResponse> get(@PathVariable Long id) {
        return Result.ok(taskService.get(UserContext.get(), id));
    }

    @PutMapping("/{id}")
    public Result<TaskResponse> update(@PathVariable Long id, @Valid @RequestBody TaskUpdateRequest request) {
        return Result.ok(taskService.update(UserContext.get(), id, request));
    }

    @PutMapping("/{id}/status")
    public Result<TaskResponse> changeStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusRequest request) {
        return Result.ok(taskService.changeStatus(UserContext.get(), id, request.status()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(UserContext.get(), id);
        return Result.ok();
    }
}