package com.nexus.ai.risk;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import com.nexus.ai.dto.EventSnapshot;
import com.nexus.ai.dto.FactSnapshot;

/**
 * 规则评估上下文：封装事实/事件快照与基准时间，统一日期解析与等级判定。
 * 为什么把日期处理集中：世界模型里的日期可能来自日期、ISO 时间、带时区时间，
 * 规则层必须先归一才能算剩余天数，避免各规则各写一套造成口径不一致。
 */
public final class RiskEvaluationContext {

    private static final DateTimeFormatter SPACE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<FactSnapshot> facts;
    private final List<EventSnapshot> events;
    private final Instant analysisAt;

    public RiskEvaluationContext(List<FactSnapshot> facts, List<EventSnapshot> events, Instant analysisAt) {
        this.facts = facts == null ? List.of() : facts;
        this.events = events == null ? List.of() : events;
        this.analysisAt = analysisAt == null ? Instant.now() : analysisAt;
    }

    public List<FactSnapshot> facts() {
        return facts;
    }

    public List<EventSnapshot> events() {
        return events;
    }

    public Instant analysisAt() {
        return analysisAt;
    }

    /** 事实是否仍在有效期内；validUntil 为空表示当前有效。 */
    public boolean isActive(FactSnapshot fact) {
        return fact.validUntil() == null || fact.validUntil().isAfter(analysisAt);
    }

    /** 到期时间优先取 validUntil，其次解析 objectValue。 */
    public Optional<Instant> dueOf(FactSnapshot fact) {
        if (fact.validUntil() != null) {
            return Optional.of(fact.validUntil());
        }
        return parseInstant(fact.objectValue());
    }

    public static Optional<Instant> parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String text = value.trim();
        try {
            return Optional.of(Instant.parse(text));
        } catch (Exception ignored) {
            // 继续尝试其他格式
        }
        try {
            return Optional.of(OffsetDateTime.parse(text).toInstant());
        } catch (Exception ignored) {
            // 继续尝试其他格式
        }
        try {
            return Optional.of(LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (Exception ignored) {
            // 继续尝试其他格式
        }
        try {
            return Optional.of(LocalDateTime.parse(text).toInstant(ZoneOffset.UTC));
        } catch (Exception ignored) {
            // 继续尝试其他格式
        }
        try {
            return Optional.of(LocalDateTime.parse(text, SPACE_DATE_TIME).toInstant(ZoneOffset.UTC));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /** 剩余天数向下取整；当天到期为 0，已过期为负数。 */
    public static long daysUntil(Instant analysisAt, Instant due) {
        return Duration.between(analysisAt, due).toDays();
    }

    /** 30 天内 low、7 天内 mid、1 天内 high；超过 30 天不产生风险。 */
    public static String levelForDays(long days) {
        if (days < 0) {
            return null;
        }
        if (days <= 1) {
            return "high";
        }
        if (days <= 7) {
            return "mid";
        }
        if (days <= 30) {
            return "low";
        }
        return null;
    }

    public static String ruleSuffix(long days) {
        if (days <= 1) {
            return "1D";
        }
        if (days <= 7) {
            return "7D";
        }
        return "30D";
    }

    public static String formatDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
    }
}