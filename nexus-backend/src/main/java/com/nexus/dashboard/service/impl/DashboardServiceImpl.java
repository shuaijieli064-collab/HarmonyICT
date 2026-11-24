package com.nexus.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.agent.entity.AgentRun;
import com.nexus.agent.mapper.AgentRunMapper;
import com.nexus.dashboard.dto.DashboardSummaryResponse;
import com.nexus.dashboard.service.DashboardService;
import com.nexus.notification.entity.Notification;
import com.nexus.notification.mapper.NotificationMapper;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.task.entity.Task;
import com.nexus.task.mapper.TaskMapper;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldEvent;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 驾驶舱聚合统计实现：全部为当前用户范围（user_id 隔离红线）的只读计数。
 * 风险等级分布在 Java 侧分组：unresolved 数据量小，避免为一次统计引入自定义 SQL；
 * 事件与风险摘要取最近的几条，排序口径与世界模型/风险中心一致。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final int SUMMARY_SIZE = 5;
    private static final List<String> LEVEL_ORDER = List.of("high", "medium", "low");

    private final WorldEntityMapper entityMapper;
    private final WorldFactMapper factMapper;
    private final WorldEventMapper eventMapper;
    private final WorldRiskMapper riskMapper;
    private final TaskMapper taskMapper;
    private final AgentRunMapper agentRunMapper;
    private final NotificationMapper notificationMapper;

    public DashboardServiceImpl(WorldEntityMapper entityMapper, WorldFactMapper factMapper,
                                WorldEventMapper eventMapper, WorldRiskMapper riskMapper,
                                TaskMapper taskMapper, AgentRunMapper agentRunMapper,
                                NotificationMapper notificationMapper) {
        this.entityMapper = entityMapper;
        this.factMapper = factMapper;
        this.eventMapper = eventMapper;
        this.riskMapper = riskMapper;
        this.taskMapper = taskMapper;
        this.agentRunMapper = agentRunMapper;
        this.notificationMapper = notificationMapper;
    }

    @Override
    public DashboardSummaryResponse summary(Long userId) {
        long entityTotal = entityMapper.selectCount(new LambdaQueryWrapper<WorldEntity>()
                .eq(WorldEntity::getUserId, userId));
        long factTotal = factMapper.selectCount(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userId)
                .eq(WorldFact::getStatus, "active"));
        long eventTotal = eventMapper.selectCount(new LambdaQueryWrapper<WorldEvent>()
                .eq(WorldEvent::getUserId, userId));
        long taskTotal = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId));
        long todoTaskTotal = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId)
                .eq(Task::getStatus, "todo"));
        long agentRunTotal = agentRunMapper.selectCount(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId));
        long unreadNotificationTotal = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt));

        List<WorldRisk> unresolvedRisks = riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .ne(WorldRisk::getStatus, "resolved"));
        long openRiskTotal = unresolvedRisks.stream()
                .filter(risk -> "open".equals(risk.getStatus()))
                .count();

        List<WorldEvent> recentEvents = eventMapper.selectPage(new Page<>(1, SUMMARY_SIZE),
                new LambdaQueryWrapper<WorldEvent>()
                        .eq(WorldEvent::getUserId, userId)
                        .orderByDesc(WorldEvent::getOccurredAt)).getRecords();

        Comparator<WorldRisk> byLevelDescThenNewest = Comparator
                .comparingInt((WorldRisk risk) -> LEVEL_ORDER.indexOf(risk.getLevel()))
                .thenComparing(risk -> risk.getCreatedAt() == null ? Instant.EPOCH : risk.getCreatedAt(),
                        Comparator.reverseOrder());

        List<DashboardSummaryResponse.TopRisk> topRisks = unresolvedRisks.stream()
                .sorted(byLevelDescThenNewest)
                .limit(SUMMARY_SIZE)
                .map(DashboardSummaryResponse.TopRisk::from)
                .toList();

        return new DashboardSummaryResponse(
                entityTotal, factTotal, eventTotal, openRiskTotal, taskTotal, todoTaskTotal,
                agentRunTotal, unreadNotificationTotal,
                riskLevels(unresolvedRisks),
                recentEvents.stream().map(DashboardSummaryResponse.RecentEvent::from).toList(),
                topRisks);
    }

    private List<DashboardSummaryResponse.RiskLevelCount> riskLevels(List<WorldRisk> risks) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String level : LEVEL_ORDER) {
            counts.put(level, 0L);
        }
        for (WorldRisk risk : risks) {
            counts.merge(risk.getLevel(), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new DashboardSummaryResponse.RiskLevelCount(entry.getKey(), entry.getValue()))
                .toList();
    }
}
