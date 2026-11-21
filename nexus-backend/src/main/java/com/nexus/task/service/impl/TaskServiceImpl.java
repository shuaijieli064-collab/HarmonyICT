package com.nexus.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.ResultCode;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.task.dto.TaskCreateRequest;
import com.nexus.task.dto.TaskResponse;
import com.nexus.task.dto.TaskUpdateRequest;
import com.nexus.task.entity.Task;
import com.nexus.task.mapper.TaskMapper;
import com.nexus.task.service.TaskService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务服务实现。
 * 状态机用转移表表达（可读性优于 if-else 链），终态 done/cancelled 无出边即不可流转。
 */
@Service
public class TaskServiceImpl implements TaskService {

    /** 合法状态流转表：key 当前状态 → value 允许的目标状态集合 */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "todo", Set.of("in_progress", "cancelled"),
            "in_progress", Set.of("done", "cancelled"),
            "done", Set.of(),
            "cancelled", Set.of());

    private final TaskMapper taskMapper;
    private final WorldRiskMapper riskMapper;

    public TaskServiceImpl(TaskMapper taskMapper, WorldRiskMapper riskMapper) {
        this.taskMapper = taskMapper;
        this.riskMapper = riskMapper;
    }

    @Override
    public TaskResponse create(Long userId, TaskCreateRequest request) {
        if (request.riskId() != null) {
            Long count = riskMapper.selectCount(new LambdaQueryWrapper<WorldRisk>()
                    .eq(WorldRisk::getId, request.riskId())
                    .eq(WorldRisk::getUserId, userId));
            if (count == null || count == 0) {
                throw new BizException(ResultCode.RISK_NOT_FOUND);
            }
        }
        Instant now = Instant.now();
        Task task = new Task();
        task.setUserId(userId);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus("todo");
        task.setPriority(request.priority() == null ? 0 : request.priority());
        task.setDueDate(request.dueDate());
        task.setSourceType(request.sourceType() == null ? "manual" : request.sourceType());
        task.setRiskId(request.riskId());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        return TaskResponse.from(task);
    }

    @Override
    public PageResult<TaskResponse> list(Long userId, String status, String sourceType, int page, int pageSize) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId)
                .eq(status != null && !status.isBlank(), Task::getStatus, status)
                .eq(sourceType != null && !sourceType.isBlank(), Task::getSourceType, sourceType)
                // 契约 7：优先按 dueDate 升序；未设截止日期的排其后（次级按创建时间倒序）
                .orderByAsc(Task::getDueDate)
                .orderByDesc(Task::getCreatedAt);
        IPage<Task> result = taskMapper.selectPage(TaskPageHelper.clampPage(page, pageSize), wrapper);
        List<TaskResponse> records = result.getRecords().stream().map(TaskResponse::from).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public TaskResponse get(Long userId, Long id) {
        return TaskResponse.from(requireTask(userId, id));
    }

    @Override
    public TaskResponse update(Long userId, Long id, TaskUpdateRequest request) {
        Task task = requireTask(userId, id);
        if (request.title() != null) {
            task.setTitle(request.title());
        }
        if (request.description() != null) {
            task.setDescription(request.description());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }
        task.setUpdatedAt(Instant.now());
        taskMapper.updateById(task);
        return TaskResponse.from(task);
    }

    @Override
    public TaskResponse changeStatus(Long userId, Long id, String targetStatus) {
        Task task = requireTask(userId, id);
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(task.getStatus(), Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new BizException(ResultCode.TASK_STATE_INVALID,
                    "非法状态流转: " + task.getStatus() + " → " + targetStatus);
        }
        task.setStatus(targetStatus);
        task.setUpdatedAt(Instant.now());
        taskMapper.updateById(task);
        return TaskResponse.from(task);
    }

    @Override
    public void delete(Long userId, Long id) {
        Task task = requireTask(userId, id);
        taskMapper.deleteById(task.getId());
    }

    private Task requireTask(Long userId, Long id) {
        Task task = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getId, id)
                .eq(Task::getUserId, userId));
        if (task == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }
        return task;
    }
}