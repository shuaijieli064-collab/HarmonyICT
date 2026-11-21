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
import com.nexus.common.exception.BizException;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.ResultCode;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Agent 编排服务实现。
 * execute 通过自注入代理调用异步执行器（同类内部调用 @Async 不生效，见 AgentOrchestrationRunner）。
 */
@Service
public class AgentRunServiceImpl implements AgentRunService {

    private final AgentRunMapper runMapper;
    private final AgentActionLogMapper logMapper;
    private final AgentOrchestrationRunner orchestrationRunner;
    private final AgentRunService selfProxy;

    public AgentRunServiceImpl(AgentRunMapper runMapper, AgentActionLogMapper logMapper,
                               AgentOrchestrationRunner orchestrationRunner,
                               @Lazy AgentRunService selfProxy) {
        this.runMapper = runMapper;
        this.logMapper = logMapper;
        this.orchestrationRunner = orchestrationRunner;
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
        List<Long> logIds = request.logIds();
        if (Boolean.TRUE.equals(request.approve())) {
            // 授权留痕：authorized=true 后执行器才允许真正执行 L2 操作（阶段四联调）
            logMapper.update(null, new LambdaUpdateWrapper<AgentActionLog>()
                    .eq(AgentActionLog::getUserId, userId)
                    .eq(AgentActionLog::getRunId, id)
                    .in(AgentActionLog::getId, logIds)
                    .set(AgentActionLog::getAuthorized, true)
                    .set(AgentActionLog::getUpdatedAt, Instant.now()));
        } else {
            logMapper.update(null, new LambdaUpdateWrapper<AgentActionLog>()
                    .eq(AgentActionLog::getUserId, userId)
                    .eq(AgentActionLog::getRunId, id)
                    .in(AgentActionLog::getId, logIds)
                    .set(AgentActionLog::getResult, "denied")
                    .set(AgentActionLog::getUpdatedAt, Instant.now()));
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