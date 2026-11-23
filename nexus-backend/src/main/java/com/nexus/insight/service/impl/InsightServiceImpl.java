package com.nexus.insight.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.insight.dto.EngineFamilySummary;
import com.nexus.insight.dto.InsightOverviewResponse;
import com.nexus.insight.dto.TimelineItem;
import com.nexus.insight.dto.TimelineResponse;
import com.nexus.insight.engine.EngineFamily;
import com.nexus.insight.service.InsightService;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldEvent;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 洞察服务实现：引擎族概览 + 未来时间线投影。
 * 纯只读聚合，无写操作；跨用户隔离依赖查询条件携带 user_id。
 */
@Service
public class InsightServiceImpl implements InsightService {

    private static final int MAX_TIMELINE_ITEMS = 100;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final WorldRiskMapper riskMapper;
    private final WorldFactMapper factMapper;
    private final WorldEventMapper eventMapper;
    private final WorldEntityMapper entityMapper;

    public InsightServiceImpl(WorldRiskMapper riskMapper, WorldFactMapper factMapper,
                              WorldEventMapper eventMapper, WorldEntityMapper entityMapper) {
        this.riskMapper = riskMapper;
        this.factMapper = factMapper;
        this.eventMapper = eventMapper;
        this.entityMapper = entityMapper;
    }

    @Override
    public InsightOverviewResponse overview(Long userId) {
        List<WorldRisk> risks = riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .ne(WorldRisk::getStatus, "resolved"));

        Map<EngineFamily, long[]> grouped = new EnumMap<>(EngineFamily.class);
        for (WorldRisk risk : risks) {
            EngineFamily family = EngineFamily.of(risk.getRuleCode());
            long[] bucket = grouped.computeIfAbsent(family, k -> new long[4]);
            bucket[0]++;
            switch (risk.getLevel() == null ? "" : risk.getLevel()) {
                case "high" -> bucket[1]++;
                case "medium" -> bucket[2]++;
                default -> bucket[3]++;
            }
        }

        List<EngineFamilySummary> families = new ArrayList<>();
        for (EngineFamily family : EngineFamily.values()) {
            long[] bucket = grouped.get(family);
            if (bucket != null) {
                families.add(EngineFamilySummary.of(family, bucket[0], bucket[1], bucket[2], bucket[3]));
            }
        }
        return new InsightOverviewResponse(risks.size(), List.copyOf(families));
    }

    @Override
    public TimelineResponse timeline(Long userId, int days) {
        Instant now = Instant.now();
        Instant windowEnd = now.plusSeconds((long) days * 24 * 3600);

        List<TimelineItem> items = new ArrayList<>();

        List<WorldFact> facts = factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userId)
                .eq(WorldFact::getStatus, "active")
                .isNotNull(WorldFact::getValidUntil)
                .between(WorldFact::getValidUntil, now, windowEnd));
        if (!facts.isEmpty()) {
            Map<Long, String> entityNames = loadEntityNames(userId,
                    facts.stream().map(WorldFact::getSubjectId).toList());
            for (WorldFact fact : facts) {
                String name = entityNames.getOrDefault(fact.getSubjectId(), "未知实体");
                String predicate = fact.getPredicate();
                String kind = isDeadlinePredicate(predicate) ? "deadline" : "expiry";
                long remain = daysUntil(now, fact.getValidUntil());
                items.add(new TimelineItem(
                        DATE_FMT.format(LocalDate.ofInstant(fact.getValidUntil(), ZoneId.systemDefault())),
                        kind,
                        name + " · " + predicateLabel(predicate),
                        levelForDays(remain),
                        fact.getSubjectId(),
                        "fact",
                        String.valueOf(fact.getId())));
            }
        }

        List<WorldEvent> events = eventMapper.selectList(new LambdaQueryWrapper<WorldEvent>()
                .eq(WorldEvent::getUserId, userId)
                .between(WorldEvent::getOccurredAt, now, windowEnd));
        for (WorldEvent event : events) {
            items.add(new TimelineItem(
                    DATE_FMT.format(LocalDate.ofInstant(event.getOccurredAt(), ZoneId.systemDefault())),
                    "event",
                    event.getTitle(),
                    null,
                    event.getEntityId(),
                    "event",
                    String.valueOf(event.getId())));
        }

        items.sort(Comparator.comparing(TimelineItem::date).thenComparing(TimelineItem::title));
        List<TimelineItem> limited = items.size() > MAX_TIMELINE_ITEMS
                ? items.subList(0, MAX_TIMELINE_ITEMS) : items;
        return new TimelineResponse(days, limited.size(), List.copyOf(limited));
    }

    private Map<Long, String> loadEntityNames(Long userId, List<Long> subjectIds) {
        Map<Long, String> names = new HashMap<>();
        Set<Long> distinctIds = new HashSet<>(subjectIds);
        if (distinctIds.isEmpty()) {
            return names;
        }
        List<WorldEntity> entities = entityMapper.selectList(new LambdaQueryWrapper<WorldEntity>()
                .eq(WorldEntity::getUserId, userId)
                .in(WorldEntity::getId, distinctIds));
        for (WorldEntity entity : entities) {
            names.put(entity.getId(), entity.getName());
        }
        return names;
    }

    private static boolean isDeadlinePredicate(String predicate) {
        if (predicate == null) {
            return false;
        }
        return predicate.endsWith("_deadline") || predicate.endsWith("_due_at");
    }

    private static String predicateLabel(String predicate) {
        if (predicate == null) {
            return "未知事项";
        }
        return switch (predicate) {
            case "subscription_renewal_at" -> "订阅续费日";
            case "warranty_until", "cert_expiry_at" -> "保修/证书到期";
            case "task_due_at" -> "任务截止";
            case "goal_deadline" -> "目标期限";
            case "project_deadline" -> "项目期限";
            default -> predicate;
        };
    }

    /** 与规则引擎同源分级口径：≤1 天 high、≤7 天 medium、其余 low。 */
    private static String levelForDays(long days) {
        if (days <= 1) {
            return "high";
        }
        return days <= 7 ? "medium" : "low";
    }

    private static long daysUntil(Instant now, Instant due) {
        return Math.max(0, java.time.Duration.between(now, due).toDays());
    }
}
