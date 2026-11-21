package com.nexus.ai.risk.rules;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.nexus.ai.dto.EventSnapshot;
import com.nexus.ai.dto.RiskAnalysisRequest;
import com.nexus.ai.risk.RiskEvaluationContext;
import com.nexus.ai.risk.RiskRule;

/**
 * 日程冲突规则：同一天出现 2 项及以上事件即提示；若其中两项落在同一小时，升级为 mid。
 * 事件快照只有发生时间（occurredAt），没有结束时间，因此"冲突"按同日/同小时判定，
 * 不臆造时长（避免假阳性）。
 */
@Component
public class ScheduleConflictRule implements RiskRule {

    @Override
    public List<RiskAnalysisRequest.DetectedRisk> evaluate(RiskEvaluationContext context) {
        Map<LocalDate, List<EventSnapshot>> byDay = context.events().stream()
                .filter(event -> event.occurredAt() != null)
                .collect(Collectors.groupingBy(event -> event.occurredAt().atZone(ZoneOffset.UTC).toLocalDate()));

        List<RiskAnalysisRequest.DetectedRisk> risks = new ArrayList<>();
        for (Map.Entry<LocalDate, List<EventSnapshot>> entry : byDay.entrySet()) {
            List<EventSnapshot> events = entry.getValue();
            if (events.size() < 2) {
                continue;
            }
            boolean sameHour = hasOverlappingHour(events);
            String level = sameHour ? "mid" : "low";
            String ruleCode = sameHour ? "SCHEDULE_CONFLICT_HOUR" : "SCHEDULE_CONFLICT_DAY";
            String title = entry.getKey() + " 有 " + events.size() + " 项日程"
                    + (sameHour ? "存在时间重叠" : "安排集中");
            String description = "同日事件：" + events.stream()
                    .sorted(Comparator.comparing(EventSnapshot::occurredAt))
                    .map(event -> event.title() + "（"
                            + event.occurredAt().atZone(ZoneOffset.UTC).toLocalTime() + "）")
                    .collect(Collectors.joining("、"));
            Long entityId = events.stream()
                    .map(EventSnapshot::entityId)
                    .filter(id -> id != null)
                    .findFirst()
                    .orElse(null);
            risks.add(new RiskAnalysisRequest.DetectedRisk(
                    "other", level, ruleCode, title, description, entityId, List.of()));
        }
        return risks;
    }

    private boolean hasOverlappingHour(List<EventSnapshot> events) {
        Set<Long> hours = new HashSet<>();
        for (EventSnapshot event : events) {
            long epochHour = event.occurredAt().getEpochSecond() / 3600;
            if (!hours.add(epochHour)) {
                return true;
            }
        }
        return false;
    }
}