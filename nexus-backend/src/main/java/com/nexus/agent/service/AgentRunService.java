package com.nexus.agent.service;

import com.nexus.agent.dto.RunCreateRequest;
import com.nexus.agent.dto.RunDetailResponse;
import com.nexus.agent.dto.RunAuthorizeRequest;
import com.nexus.common.result.PageResult;
import com.nexus.agent.dto.AgentRunResponse;

/**
 * Agent 编排服务（A 负责侧）：执行记录管理与 L2 授权控制。
 * 计划产出依赖 com.nexus.ai 的 AgentOrchestratorProvider（B 实现），本服务只落库与控制授权。
 */
public interface AgentRunService {

    /** 发起执行：创建 run 记录（status=running），触发异步编排，立即返回当前快照 */
    AgentRunResponse create(Long userId, RunCreateRequest request);

    /** 分页查询执行记录 */
    PageResult<AgentRunResponse> list(Long userId, String status, int page, int pageSize);

    /** 详情：含操作日志列表 */
    RunDetailResponse get(Long userId, Long id);

    /** 触发异步编排（重复触发 running 状态的 run 幂等返回快照；已结束的 run 拒绝） */
    AgentRunResponse execute(Long userId, Long id);

    /**
     * L2 操作授权：approve=true 时 logIds 置 authorized=true；approve=false 置 result=denied。
     * 校验日志归属当前用户与该 run。
     */
    RunDetailResponse authorize(Long userId, Long id, RunAuthorizeRequest request);
}