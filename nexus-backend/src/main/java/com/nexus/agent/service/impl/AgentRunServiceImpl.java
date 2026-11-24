package com.nexus.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexus.agent.dto.AgentActionLogResponse;
import com.nexus.agent.dto.AgentRunResponse;
import com.nexus.agent.dto.RunAuthorizeRequest;
import com.nexus.agent.dto.RunCreateRequest;
import com.nexus.agent.dto.RunDetailResponse;
import com.nexus.agent.entity.AgentActionLog;
import com.nexus.agent.entity.AgentRun;
import com.nexus.agent.mapper.AgentActionLogMapper;
import com.nexus.agent.mapper.AgentRunMapper;
import com.nexus.agent.service.AgentRunService;
import com.nexus.agent.util.AgentAuditSupport;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.ResultCode;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Agent 编排服务实现。
 * execute 通过自注入代理调用异步执行器（同类内部调用 @Async 不生效，见 AgentOrchestrationRunner）。
 */
@Service
public class AgentRunServiceImpl implements AgentRunService {

    private final AgentRunMapper runMapper;
    private final AgentActionLogMapper logMapper;
    private final AgentOrchestrationRunner orchestrationRunner;
    private final AgentActionExecutor actionExecutor;
    private final AgentRunService selfProxy;

    public AgentRunServiceImpl(AgentRunMapper runMapper, AgentActionLogMapper logMapper,
                               AgentOrchestrationRunner orchestrationRunner,
                               AgentActionExecutor actionExecutor,
                               @Lazy AgentRunService selfProxy) {
        this.runMapper = runMapper;
        this.logMapper = logMapper;
        this.orchestrationRunner = orchestrationRunner;
        this.actionExecutor = actionExecutor;
        this.selfProxy = selfProxy;
    }

    @Override
    public AgentRunResponse create(Long userId, RunCreateRequest request) {
        Instant now = Instant.now();
        AgentRun run = new AgentRun();
        run.setUserId(userId);
        run.setTriggerType(request.triggerType() == null ? "manual" : request.triggerType());
        run.setIntent(request.intent());
        run.setStatus("running");
        run.setStartedAt(now);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runMapper.insert(run);
        // 立即返回快照，编排异步进行（契约 8 节）
        selfProxy.execute(userId, run.getId());
        return AgentRunResponse.from(requireRun(userId, run.getId()));
    }

    @Override
    public PageResult<AgentRunResponse> list(Long userId, String status, int page, int pageSize) {
        LambdaQueryWrapper<AgentRun> wrapper = new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .eq(status != null && !status.isBlank(), AgentRun::getStatus, status)
                .orderByDesc(AgentRun::getCreatedAt);
        IPage<AgentRun> result = runMapper.selectPage(AgentPageHelper.clampPage(page, pageSize), wrapper);
        List<AgentRunResponse> records = result.getRecords().stream().map(AgentRunResponse::from).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public RunDetailResponse get(Long userId, Long id) {
        return new RunDetailResponse(AgentRunResponse.from(requireRun(userId, id)), requireLogs(userId, id));
    }

    @Override
    public PageResult<AgentActionLogResponse> listLogs(Long userId, Long runId, String level,
                                                       String result, int page, int pageSize) {
        // 先校验 run 归属，避免通过日志接口探测他人 runId
        requireRun(userId, runId);
        LambdaQueryWrapper<AgentActionLog> wrapper = new LambdaQueryWrapper<AgentActionLog>()
                .eq(AgentActionLog::getUserId, userId)
                .eq(AgentActionLog::getRunId, runId)
                .eq(level != null && !level.isBlank(), AgentActionLog::getLevel, level)
                .eq(result != null && !result.isBlank(), AgentActionLog::getResult, result)
                .orderByAsc(AgentActionLog::getCreatedAt);
        IPage<AgentActionLog> pageResult = logMapper.selectPage(AgentPageHelper.clampPage(page, pageSize), wrapper);
        List<AgentActionLogResponse> records = pageResult.getRecords().stream()
                .map(AgentActionLogResponse::from).toList();
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public AgentRunResponse execute(Long userId, Long id) {
        AgentRun run = requireRun(userId, id);
        if (!"running".equals(run.getStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "该执行已结束（" + run.getStatus() + "），不可重复触发");
        }
        orchestrationRunner.runAsync(run.getId(), userId);
        return AgentRunResponse.from(run);
    }

    @Override
    @Transactional
    public RunDetailResponse authorize(Long userId, Long id, RunAuthorizeRequest request) {
        requireRun(userId, id);
        List<Long> requested = request.logIds();
        if (requested == null || requested.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "logIds 不能为空");
        }
        // 逐条校验归属：批量 update 的 where 条件会静默忽略越权 id，这里必须显式拒绝
        Set<Long> uniqueIds = new HashSet<>(requested);
        List<AgentActionLog> targets = logMapper.selectList(new LambdaQueryWrapper<AgentActionLog>()
                .eq(AgentActionLog::getUserId, userId)
                .eq(AgentActionLog::getRunId, id)
                .in(AgentActionLog::getId, uniqueIds));
        if (targets.size() != uniqueIds.size()) {
            throw new BizException(ResultCode.AGENT_PERMISSION_DENIED, "授权日志不存在或不属于当前用户");
        }

        Instant now = Instant.now();
        boolean approve = Boolean.TRUE.equals(request.approve());
        for (AgentActionLog target : targets) {
            if (!"L2".equals(target.getLevel())) {
                throw new BizException(ResultCode.PARAM_INVALID, "仅 L2 操作需要授权");
            }
            if (!"pending".equals(target.getResult())) {
                // 已处理过的日志幂等返回，不重复执行
                continue;
            }
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("action", target.getActionType());
            audit.put("authorizedBy", String.valueOf(userId));
            audit.put("authorizedAt", now.toString());
            audit.put("decision", approve ? "approved" : "denied");
            target.setPayload(AgentAuditSupport.write(target.getPayload(), audit));
            target.setUpdatedAt(now);
            if (approve) {
                // 授权留痕：authorized=true 表示用户放行该写操作
                target.setAuthorized(true);
            } else {
                target.setResult("denied");
            }
            logMapper.updateById(target);
        }
        if (approve) {
            // 授权后立即执行已授权动作（B review 反馈缺口：此前只改状态不执行）
            actionExecutor.executeAuthorized(userId, id);
        }
        return selfProxy.get(userId, id);
    }

    private AgentRun requireRun(Long userId, Long id) {
        AgentRun run = runMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getId, id)
                .eq(AgentRun::getUserId, userId));
        if (run == null) {
            throw new BizException(ResultCode.AGENT_RUN_NOT_FOUND);
        }
        return run;
    }

    private List<AgentActionLogResponse> requireLogs(Long userId, Long id) {
        return logMapper.selectList(new LambdaQueryWrapper<AgentActionLog>()
                        .eq(AgentActionLog::getUserId, userId)
                        .eq(AgentActionLog::getRunId, id)
                        .orderByAsc(AgentActionLog::getCreatedAt))
                .stream().map(AgentActionLogResponse::from).toList();
    }
}
