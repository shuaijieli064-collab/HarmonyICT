package com.nexus.risk.snapshot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.RiskSnapshotMapper;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldFactMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 风险趋势快照实现。
 * 为什么用快照表而非实时聚合：趋势要展示"历史每天"的分布，world_risks 只有当前状态，
 * 每日落一份快照才能回放历史；同日 upsert 保证手动扫描后数据即时刷新且不重复。
 */
@Service
public class RiskSnapshotServiceImpl implements RiskSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(RiskSnapshotServiceImpl.class);
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_DAYS = 30;

    private final RiskSnapshotMapper snapshotMapper;
    private final WorldRiskMapper riskMapper;
    private final WorldFactMapper factMapper;

    public RiskSnapshotServiceImpl(RiskSnapshotMapper snapshotMapper,
                                   WorldRiskMapper riskMapper,
                                   WorldFactMapper factMapper) {
        this.snapshotMapper = snapshotMapper;
        this.riskMapper = riskMapper;
        this.factMapper = factMapper;
    }

    /** 每日 01:00 为全部活跃用户落快照（错过可由手动扫描补齐） */
    @Scheduled(cron = "0 0 1 * * *")
    public void scheduledSnapshot() {
        try {
            snapshotAll();
        } catch (Exception e) {
            log.error("风险快照定时任务异常", e);
        }
    }

    @Override
    public void snapshotAll() {
        List<WorldFact> facts = factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getStatus, "active"));
        facts.stream().map(WorldFact::getUserId).distinct().forEach(userId -> {
            try {
                snapshotForUser(userId);
            } catch (Exception e) {
                // 单用户失败不影响其余用户
                log.warn("风险快照失败: userId={}, reason={}", userId, e.getMessage());
            }
        });
    }

    @Override
    public void snapshotForUser(Long userId) {
        List<WorldRisk> open = riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .eq(WorldRisk::getStatus, "open"));
        int high = 0;
        int medium = 0;
        int low = 0;
        for (WorldRisk risk : open) {
            switch (risk.getLevel()) {
                case "high" -> high++;
                case "medium" -> medium++;
                default -> low++;
            }
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        RiskSnapshot existing = snapshotMapper.selectOne(new LambdaQueryWrapper<RiskSnapshot>()
                .eq(RiskSnapshot::getUserId, userId)
                .eq(RiskSnapshot::getSnapshotDate, today));
        if (existing == null) {
            RiskSnapshot snapshot = new RiskSnapshot();
            snapshot.setUserId(userId);
            snapshot.setSnapshotDate(today);
            snapshot.setOpenCount(open.size());
            snapshot.setHighCount(high);
            snapshot.setMediumCount(medium);
            snapshot.setLowCount(low);
            snapshot.setCreatedAt(Instant.now());
            snapshotMapper.insert(snapshot);
        } else {
            existing.setOpenCount(open.size());
            existing.setHighCount(high);
            existing.setMediumCount(medium);
            existing.setLowCount(low);
            snapshotMapper.updateById(existing);
        }
    }

    @Override
    public List<TrendPoint> trend(Long userId, int days) {
        int period = days <= 0 ? 7 : Math.min(days, MAX_DAYS);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(period - 1L);

        List<RiskSnapshot> snapshots = snapshotMapper.selectList(new LambdaQueryWrapper<RiskSnapshot>()
                .eq(RiskSnapshot::getUserId, userId)
                .ge(RiskSnapshot::getSnapshotDate, start)
                .orderByAsc(RiskSnapshot::getSnapshotDate));
        Map<LocalDate, RiskSnapshot> byDate = new LinkedHashMap<>();
        for (RiskSnapshot snapshot : snapshots) {
            byDate.put(snapshot.getSnapshotDate(), snapshot);
        }

        // 缺失日期补 0：折线连续，空数据天不产生断点
        return start.datesUntil(today.plusDays(1))
                .map(date -> {
                    RiskSnapshot snapshot = byDate.get(date);
                    if (snapshot == null) {
                        return new TrendPoint(DAY_FORMAT.format(date), 0, 0, 0, 0);
                    }
                    return new TrendPoint(DAY_FORMAT.format(date),
                            snapshot.getOpenCount(), snapshot.getHighCount(),
                            snapshot.getMediumCount(), snapshot.getLowCount());
                })
                .toList();
    }
}
