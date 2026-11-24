package com.nexus.risk.snapshot;

import java.util.List;

/**
 * 风险趋势快照服务（扩展 B 组）。
 */
public interface RiskSnapshotService {

    /** 为指定用户写入/覆盖今日快照（同日 upsert 幂等） */
    void snapshotForUser(Long userId);

    /** 为全部有事实的用户写今日快照（每日定时 + 手动扫描后调用） */
    void snapshotAll();

    /**
     * 查询近 N 天趋势（缺失日期补 0，保证折线连续）。
     *
     * @return 按日期升序的点列表
     */
    List<TrendPoint> trend(Long userId, int days);

    /** 趋势点：date=yyyy-MM-dd，各等级为当日 open 快照值 */
    record TrendPoint(String date, int openCount, int highCount, int mediumCount, int lowCount) {
    }
}
