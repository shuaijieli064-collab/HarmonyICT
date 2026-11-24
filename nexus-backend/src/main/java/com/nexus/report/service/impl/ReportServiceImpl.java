package com.nexus.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.agent.entity.AgentRun;
import com.nexus.agent.mapper.AgentRunMapper;
import com.nexus.notification.entity.Notification;
import com.nexus.notification.mapper.NotificationMapper;
import com.nexus.report.dto.WeeklyReportResponse;
import com.nexus.report.service.ReportService;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.task.entity.Task;
import com.nexus.task.mapper.TaskMapper;
import com.nexus.world.entity.WorldEvent;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 周期报告实现：全部为当前用户范围的只读计数。
 * 为什么在 Java 侧做日分组：期内数据量小，避免为趋势图引入自定义 SQL；
 * 等级分布与代表性风险口径与驾驶舱一致（unresolved）。
 */
@Service
public class ReportServiceImpl implements ReportService {

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 31;
    private static final List<String> LEVEL_ORDER = List.of("high", "medium", "low");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final WorldFactMapper factMapper;
    private final WorldEventMapper eventMapper;
    private final WorldRiskMapper riskMapper;
    private final TaskMapper taskMapper;
    private final AgentRunMapper agentRunMapper;
    private final NotificationMapper notificationMapper;

    public ReportServiceImpl(WorldFactMapper factMapper, WorldEventMapper eventMapper,
                             WorldRiskMapper riskMapper, TaskMapper taskMapper,
                             AgentRunMapper agentRunMapper, NotificationMapper notificationMapper) {
        this.factMapper = factMapper;
        this.eventMapper = eventMapper;
        this.riskMapper = riskMapper;
        this.taskMapper = taskMapper;
        this.agentRunMapper = agentRunMapper;
        this.notificationMapper = notificationMapper;
    }

    @Override
    public WeeklyReportResponse weekly(Long userId, int days) {
        int period = days <= 0 ? DEFAULT_DAYS : Math.min(days, MAX_DAYS);
        Instant end = Instant.now();
        Instant start = end.minus(period, ChronoUnit.DAYS);

        List<WorldFact> periodFacts = factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userId)
                .ge(WorldFact::getCreatedAt, start));
        List<WorldEvent> periodEvents = eventMapper.selectList(new LambdaQueryWrapper<WorldEvent>()
                .eq(WorldEvent::getUserId, userId)
                .ge(WorldEvent::getCreatedAt, start));
        List<WorldRisk> periodRisks = riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .ge(WorldRisk::getCreatedAt, start));
        long taskCompleted = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId)
                .eq(Task::getStatus, "done")
                .ge(Task::getUpdatedAt, start));
        long agentRuns = agentRunMapper.selectCount(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .ge(AgentRun::getStartedAt, start));
        long notifications = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .ge(Notification::getCreatedAt, start));

        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        for (int i = period - 1; i >= 0; i--) {
            byDay.put(LocalDate.ofInstant(end.minus(i, ChronoUnit.DAYS), ZoneOffset.UTC), new long[2]);
        }
        for (WorldFact fact : periodFacts) {
            LocalDate day = LocalDate.ofInstant(fact.getCreatedAt(), ZoneOffset.UTC);
            long[] bucket = byDay.get(day);
            if (bucket != null) {
                bucket[0]++;
            }
        }
        for (WorldEvent event : periodEvents) {
            if (event.getCreatedAt() != null) {
                LocalDate day = LocalDate.ofInstant(event.getCreatedAt(), ZoneOffset.UTC);
                long[] bucket = byDay.get(day);
                if (bucket != null) {
                    bucket[1]++;
                }
            }
        }
        List<WeeklyReportResponse.DailyTrend> trend = byDay.entrySet().stream()
                .map(entry -> new WeeklyReportResponse.DailyTrend(
                        DAY_FORMAT.format(entry.getKey()), entry.getValue()[0], entry.getValue()[1]))
                .toList();

        Map<String, Long> levelCounts = new LinkedHashMap<>();
        for (String level : LEVEL_ORDER) {
            levelCounts.put(level, 0L);
        }
        for (WorldRisk risk : periodRisks) {
            levelCounts.merge(risk.getLevel(), 1L, Long::sum);
        }

        List<WeeklyReportResponse.PeriodRisk> notableRisks = periodRisks.stream()
                .filter(risk -> !"resolved".equals(risk.getStatus()))
                .sorted(Comparator
                        .comparingInt((WorldRisk risk) -> LEVEL_ORDER.indexOf(risk.getLevel()))
                        .thenComparing(risk -> risk.getCreatedAt() == null ? Instant.EPOCH : risk.getCreatedAt(),
                                Comparator.reverseOrder()))
                .limit(3)
                .map(risk -> new WeeklyReportResponse.PeriodRisk(
                        String.valueOf(risk.getId()), risk.getLevel(), risk.getTitle(), risk.getRuleCode()))
                .toList();

        return new WeeklyReportResponse(
                start, end, period,
                periodFacts.size(), periodEvents.size(), periodRisks.size(),
                taskCompleted, agentRuns, notifications,
                levelCounts.entrySet().stream()
                        .map(entry -> new WeeklyReportResponse.RiskLevelCount(entry.getKey(), entry.getValue()))
                        .toList(),
                trend,
                notableRisks);
    }
}
