package com.nexus.task.service;

import com.nexus.common.result.PageResult;
import com.nexus.task.dto.TaskCreateRequest;
import com.nexus.task.dto.TaskResponse;
import com.nexus.task.dto.TaskUpdateRequest;

/**
 * 任务服务：行动项闭环。状态流转规则见 api-specification.md 7 节。
 */
public interface TaskService {

    /** 创建任务，riskId 传入时校验归属当前用户 */
    TaskResponse create(Long userId, TaskCreateRequest request);

    /** 分页查询：status/sourceType 可选过滤，优先按 dueDate 升序 */
    PageResult<TaskResponse> list(Long userId, String status, String sourceType, int page, int pageSize);

    /** 详情 */
    TaskResponse get(Long userId, Long id);

    /** 更新 title/description/priority/dueDate */
    TaskResponse update(Long userId, Long id, TaskUpdateRequest request);

    /**
     * 状态流转：todo → in_progress → done；todo/in_progress → cancelled；
     * 终态（done/cancelled）不可再流转，非法流转抛 TASK_STATE_INVALID（50102）。
     */
    TaskResponse changeStatus(Long userId, Long id, String targetStatus);

    /** 删除任务 */
    void delete(Long userId, Long id);
}