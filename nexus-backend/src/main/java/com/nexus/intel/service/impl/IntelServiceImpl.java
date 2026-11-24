package com.nexus.intel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.agent.entity.AgentRun;
import com.nexus.agent.mapper.AgentRunMapper;
import com.nexus.common.result.PageResult;
import com.nexus.intel.dto.IntelFeedItemResponse;
import com.nexus.intel.dto.IntelSummaryResponse;
import com.nexus.intel.service.IntelService;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.task.entity.Task;
import com.nexus.task.mapper.TaskMapper;
import com.nexus.world.entity.WorldEvent;
import com.nexus.world.mapper.WorldEventMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 情报中心实现：跨风险 / 事件 / Agent 执行三个模块的只读聚合。
 * 红线：情报流只做重排不做解读，风险条目保留规则引擎给出的 level，
 * 便于前端直接映射严重度样式（规范第 6 节：规则支撑智能）。
 */
@Service
public class IntelServiceImpl implements IntelService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 90;
    /** 单次合并的窗口上限，保护 Demo 数据量下的内存（每源最多 500 条）。 */
    private static final long SOURCE_LIMIT = 500;
    private static final Set<String> FEED_TYPES = Set.of("risk", "event", "agent");
    private static final Set<String> PENDING_TASK_STATUSES = Set.of("todo", "in_progress");

    private final WorldRiskMapper riskMapper;
    private final WorldEventMapper eventMapper;
    private final AgentRunMapper agentRunMapper;
    private final TaskMapper taskMapper;

    public IntelServiceImpl(WorldRiskMapper riskMapper, WorldEventMapper eventMapper,
                            AgentRunMapper agentRunMapper, TaskMapper taskMapper) {
        this.riskMapper = riskMapper;
        this.eventMapper = eventMapper;
        this.agentRunMapper = agentRunMapper;
        this.taskMapper = taskMapper;
    }

    @Override
    public IntelSummaryResponse summary(Long userId) {
        List<WorldRisk> openRisks = riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .eq(WorldRisk::getStatus, "open"));
        Map<String, Long> riskByLevel = openRisks.stream()
                .collect(Collectors.groupingBy(risk -> risk.getLevel() == null ? "unknown" : risk.getLevel(),
                        Collectors.counting()));

        Instant sevenDaysAgo = Instant.now().minus(Duration.ofDays(7));
        Long recentEvents = eventMapper.selectCount(new LambdaQueryWrapper<WorldEvent>()
                .eq(WorldEvent::getUserId, userId)
                .ge(WorldEvent::getOccurredAt, sevenDaysAgo));
        Long pendingTasks = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId)
                .in(Task::getStatus, PENDING_TASK_STATUSES));
        Long recentAgentRuns = agentRunMapper.selectCount(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .ge(AgentRun::getStartedAt, sevenDaysAgo));

        return new IntelSummaryResponse(openRisks.size(), riskByLevel,
                recentEvents == null ? 0 : recentEvents,
                pendingTasks == null ? 0 : pendingTasks,
                recentAgentRuns == null ? 0 : recentAgentRuns);
    }

    @Override
    public PageResult<IntelFeedItemResponse> feed(Long userId, String type, int days, int page, int pageSize) {
        Instant since = Instant.now().minus(Duration.ofDays(normalizeDays(days)));
        Set<String> wantedTypes = normalizeTypes(type);

        List<IntelFeedItemResponse> items = new ArrayList<>();
        if (wantedTypes.contains("risk")) {
            items.addAll(loadRiskItems(userId, since));
        }
        if (wantedTypes.contains("event")) {
            items.addAll(loadEventItems(userId, since));
        }
        if (wantedTypes.contains("agent")) {
            items.addAll(loadAgentItems(userId, since));
        }
        items.sort(Comparator.comparing(IntelFeedItemResponse::occurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        long total = items.size();
        int fromIndex = (int) Math.min((long) Math.max(page - 1, 0) * pageSize, total);
        int toIndex = (int) Math.min((long) fromIndex + pageSize, total);
        List<IntelFeedItemResponse> records = items.subList(fromIndex, toIndex);
        return PageResult.of(records, total, page, pageSize);
    }

    private List<IntelFeedItemResponse> loadRiskItems(Long userId, Instant since) {
        return riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                        .eq(WorldRisk::getUserId, userId)
                        .ne(WorldRisk::getStatus, "resolved")
                        .ge(WorldRisk::getCreatedAt, since)
                        .orderByDesc(WorldRisk::getCreatedAt)
                        .last("LIMIT " + SOURCE_LIMIT))
                .stream()
                .map(risk -> new IntelFeedItemResponse("risk", risk.getId(), risk.getTitle(),
                        risk.getDescription(), risk.getLevel(), risk.getStatus(), risk.getCreatedAt()))
                .toList();
    }

    private List<IntelFeedItemResponse> loadEventItems(Long userId, Instant since) {
        return eventMapper.selectList(new LambdaQueryWrapper<WorldEvent>()
                        .eq(WorldEvent::getUserId, userId)
                        .ge(WorldEvent::getOccurredAt, since)
                        .orderByDesc(WorldEvent::getOccurredAt)
                        .last("LIMIT " + SOURCE_LIMIT))
                .stream()
                .map(event -> new IntelFeedItemResponse("event", event.getId(), event.getTitle(),
                        event.getDescription(), null, "happened", event.getOccurredAt()))
                .toList();
    }

    private List<IntelFeedItemResponse> loadAgentItems(Long userId, Instant since) {
        return agentRunMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getUserId, userId)
                        .ge(AgentRun::getStartedAt, since)
                        .orderByDesc(AgentRun::getStartedAt)
                        .last("LIMIT " + SOURCE_LIMIT))
                .stream()
                .map(run -> new IntelFeedItemResponse("agent", run.getId(), run.getIntent(),
                        run.getSummary(), null, run.getStatus(), run.getStartedAt()))
                .toList();
    }

    private int normalizeDays(int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            return DEFAULT_DAYS;
        }
        return days;
    }

    /** all 或未识别取值返回全类型；识别的取值保持小写语义。 */
    private Set<String> normalizeTypes(String type) {
        if (type == null || type.isBlank()) {
            return FEED_TYPES;
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        return FEED_TYPES.contains(normalized) ? Set.of(normalized) : FEED_TYPES;
    }
}
