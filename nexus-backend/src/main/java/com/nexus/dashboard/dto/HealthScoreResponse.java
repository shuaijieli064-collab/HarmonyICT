package com.nexus.dashboard.dto;

import java.util.List;

/**
 * 世界模型健康度评分响应（扩展 B 组）。
 * 评分模型（0-100，纯规则可解释，答辩可讲清每一分来源）：
 * - 数据新鲜度 40 分：最近 7 天内有新事实满分；按最近事实距今天数线性衰减，超 30 天 0 分
 * - 风险状况 40 分：无未解决风险满分；每条 high 扣 10 / medium 扣 5 / low 扣 2，下限 0
 * - 任务完成 20 分：近 30 天创建的任务中 done 占比
 */
public record HealthScoreResponse(
        int score,
        String level,
        List<Dimension> dimensions) {

    /** level：excellent(≥85) / good(≥70) / attention(≥50) / critical(<50) */
    public record Dimension(String key, String label, int score, int max, String detail) {
    }
}
