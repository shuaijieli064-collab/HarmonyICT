package com.nexus.risk.snapshot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 风险趋势快照实体，对应 risk_snapshots（扩展 B 组）。
 * 每日一条：记录当日 open 风险的等级分布，支撑情报中心趋势图；
 * (user_id, snapshot_date) 唯一，同日重复快照 upsert 覆盖。
 */
@TableName("risk_snapshots")
public class RiskSnapshot {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    @TableField("snapshot_date")
    private LocalDate snapshotDate;

    @TableField("open_count")
    private Integer openCount;

    @TableField("high_count")
    private Integer highCount;

    @TableField("medium_count")
    private Integer mediumCount;

    @TableField("low_count")
    private Integer lowCount;

    @TableField("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public Integer getOpenCount() {
        return openCount;
    }

    public void setOpenCount(Integer openCount) {
        this.openCount = openCount;
    }

    public Integer getHighCount() {
        return highCount;
    }

    public void setHighCount(Integer highCount) {
        this.highCount = highCount;
    }

    public Integer getMediumCount() {
        return mediumCount;
    }

    public void setMediumCount(Integer mediumCount) {
        this.mediumCount = mediumCount;
    }

    public Integer getLowCount() {
        return lowCount;
    }

    public void setLowCount(Integer lowCount) {
        this.lowCount = lowCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
