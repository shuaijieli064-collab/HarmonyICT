package com.nexus.risk.snapshot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.auth.UserContext;
import com.nexus.common.result.Result;
import com.nexus.risk.mapper.RiskSnapshotMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示数据注入端点（仅 local profile / H2 内存库存在，prod 不编译进运行面）。
 * 为什么存在：H2 内存库重启即清空，趋势图若只有当天 1 个点演示效果差；
 * 通过此端点在演示前为当前用户合成确定性的历史快照曲线（风险逐步收敛），
 * 让 /risks/trend 有 14 天可看的走势。幂等：已有快照的日期跳过，不覆盖真实数据。
 */
@RestController
@RequestMapping("/api/v1/risks/snapshots")
@Profile("local")
public class DemoSnapshotController {

    private final RiskSnapshotMapper snapshotMapper;
    private final RiskSnapshotService snapshotService;

    public DemoSnapshotController(RiskSnapshotMapper snapshotMapper, RiskSnapshotService snapshotService) {
        this.snapshotMapper = snapshotMapper;
        this.snapshotService = snapshotService;
    }

    /**
     * 为当前用户合成近 days 天（默认 14，上限 30）历史快照。
     * 曲线形态：由峰值线性收敛到当日真实值（当日以真实扫描落库为准），
     * 叠加确定性小波动避免直线过于失真；不引入 Random 保证每次演示一致。
     */
    @PostMapping("/demo-history")
    public Result<Map<String, Object>> seedDemoHistory(@RequestParam(defaultValue = "14") int days) {
        Long userId = UserContext.get();
        int period = days <= 0 ? 14 : Math.min(days, 30);

        // 当日快照以真实风险状态为准（upsert 语义）
        snapshotService.snapshotForUser(userId);
        RiskSnapshot today = snapshotMapper.selectOne(new LambdaQueryWrapper<RiskSnapshot>()
                .eq(RiskSnapshot::getUserId, userId)
                .eq(RiskSnapshot::getSnapshotDate, LocalDate.now(ZoneOffset.UTC)));
        int baseOpen = today != null ? today.getOpenCount() : 0;
        int baseHigh = today != null ? today.getHighCount() : 0;
        int baseMedium = today != null ? today.getMediumCount() : 0;
        int baseLow = today != null ? today.getLowCount() : 0;

        // 峰值 = 当日 + 演示增量，历史逐步下降到当日值（讲"风险治理见效"的故事线）
        int peakOpen = baseOpen + 5;
        int peakHigh = baseHigh + 3;
        int peakMedium = baseMedium + 1;

        LocalDate todayDate = LocalDate.now(ZoneOffset.UTC);
        int inserted = 0;
        int skipped = 0;
        for (int i = period - 1; i >= 1; i--) {
            LocalDate date = todayDate.minusDays(i);
            if (snapshotMapper.selectOne(new LambdaQueryWrapper<RiskSnapshot>()
                    .eq(RiskSnapshot::getUserId, userId)
                    .eq(RiskSnapshot::getSnapshotDate, date)) != null) {
                skipped++;
                continue;
            }
            // t ∈ (0,1)：1=最旧（峰值），→0 接近当日；线性收敛 + 确定性波动
            double t = i / (double) period;
            int wobble = (i % 3 == 0) ? 1 : 0;
            RiskSnapshot snap = new RiskSnapshot();
            snap.setUserId(userId);
            snap.setSnapshotDate(date);
            snap.setOpenCount(Math.max(baseOpen, (int) Math.round(peakOpen * t + baseOpen * (1 - t)) - wobble));
            snap.setHighCount(Math.max(baseHigh, (int) Math.round(peakHigh * t + baseHigh * (1 - t)) - wobble));
            snap.setMediumCount(Math.max(0, (int) Math.round(peakMedium * t + baseMedium * (1 - t)) - wobble));
            snap.setLowCount(Math.max(0, baseLow + wobble));
            snap.setCreatedAt(Instant.now());
            snapshotMapper.insert(snap);
            inserted++;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", period);
        data.put("inserted", inserted);
        data.put("skippedExisting", skipped);
        data.put("baseOpen", baseOpen);
        return Result.ok(data);
    }
}
