package com.nexus.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.agent.entity.AgentRun;
import com.nexus.agent.mapper.AgentRunMapper;
import com.nexus.dashboard.dto.DashboardSummaryResponse;
import com.nexus.dashboard.dto.HealthScoreResponse;
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

    /** 健康度评分：数据新鲜度 40 + 风险状况 40 + 任务完成 20（纯规则可解释） */
    @Override
    public HealthScoreResponse healthScore(Long userId) {
        // 1) 数据新鲜度：最近一条事实距今天数，7 天内满分，30 天外 0 分，之间线性衰减
        List<WorldFact> facts = factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userId)
                .orderByDesc(WorldFact::getCreatedAt)
                .last("LIMIT 1"));
        int freshnessScore;
        String freshnessDetail;
        if (facts.isEmpty()) {
            freshnessScore = 0;
            freshnessDetail = "尚无事实数据，上传资料后开始积累";
        } else {
            long days = java.time.Duration.between(facts.get(0).getCreatedAt(), Instant.now()).toDays();
            if (days <= 7) {
                freshnessScore = 40;
                freshnessDetail = "最近 " + days + " 天内有新事实沉淀";
            } else if (days >= 30) {
                freshnessScore = 0;
                freshnessDetail = "最近一条事实已 " + days + " 天，数据可能过时";
            } else {
                freshnessScore = (int) Math.round(40.0 * (30 - days) / 23);
                freshnessDetail = "最近一条事实距 " + days + " 天，建议补充新资料";
            }
        }

        // 2) 风险状况：无未解决满分；high 扣 10 / medium 扣 5 / low 扣 2
        List<WorldRisk> unresolved = riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .ne(WorldRisk::getStatus, "resolved"));
        int penalty = 0;
        int high = 0;
        int medium = 0;
        int low = 0;
        for (WorldRisk risk : unresolved) {
            switch (risk.getLevel()) {
                case "high" -> { penalty += 10; high++; }
                case "medium" -> { penalty += 5; medium++; }
                default -> { penalty += 2; low++; }
            }
        }
        int riskScore = Math.max(0, 40 - penalty);
        String riskDetail = unresolved.isEmpty()
                ? "无未解决风险"
                : "未解决风险 " + unresolved.size() + " 条（高 " + high + " / 中 " + medium + " / 低 " + low + "）";

        // 3) 任务完成：近 30 天创建的任务 done 占比（无任务给一半基准分，避免空数据惩罚）
        Instant since = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        List<Task> recentTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId)
                .ge(Task::getCreatedAt, since));
        int taskScore;
        String taskDetail;
        if (recentTasks.isEmpty()) {
            taskScore = 10;
            taskDetail = "近 30 天无任务记录";
        } else {
            long done = recentTasks.stream().filter(task -> "done".equals(task.getStatus())).count();
            taskScore = (int) Math.round(20.0 * done / recentTasks.size());
            taskDetail = "近 30 天任务完成 " + done + "/" + recentTasks.size();
        }

        int score = freshnessScore + riskScore + taskScore;
        String level = score >= 85 ? "excellent" : score >= 70 ? "good" : score >= 50 ? "attention" : "critical";
        return new HealthScoreResponse(score, level, List.of(
                new HealthScoreResponse.Dimension("freshness", "数据新鲜度", freshnessScore, 40, freshnessDetail),
                new HealthScoreResponse.Dimension("risk", "风险状况", riskScore, 40, riskDetail),
                new HealthScoreResponse.Dimension("task", "任务完成", taskScore, 20, taskDetail)));
    }
}
