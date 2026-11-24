package com.nexus.insight.dto;

/**
 * 时间线条目（P3）。
 * kind：event=世界事件 / expiry=事实到期 / deadline=任务与目标截止；
 * level 沿用规则引擎同源口径（≤1d high、≤7d medium、其余 low），event 恒为 null。
 */
public record TimelineItem(String date, String kind, String title, String level, Long entityId,
                           String refType, String refId) {
}
