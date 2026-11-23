package com.nexus.insight.dto;

import com.nexus.insight.engine.EngineFamily;

/**
 * 单个引擎族的风险分布统计（P2）。
 */
public record EngineFamilySummary(String family, String label, long total, long high, long medium, long low) {

    public static EngineFamilySummary of(EngineFamily family, long total, long high, long medium, long low) {
        return new EngineFamilySummary(family.name(), family.getLabel(), total, high, medium, low);
    }
}
