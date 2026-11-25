package com.nexus.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 风险-证据事实关联实体，对应 world_risk_facts（P1-6 拍板，联调 2026-11-22）。
 * 一条风险可对应多条证据事实（B 的 DetectedRisk.evidenceFactIds），复合主键 (risk_id, fact_id)。
 * 关联行只增不改：事实链式失效（superseded）后关联保留，保证风险结论的历史可追溯。
 */
@TableName("world_risk_facts")
public class WorldRiskFact {

    /** 复合主键之一：显式声明 INPUT 避免 MyBatis-Plus 启动时 "Can not find table primary key" 告警；
     * 关联行由调用方赋值，禁止自动生成。 */
    @TableId(value = "risk_id", type = IdType.INPUT)
    @TableField("risk_id")
    private Long riskId;

    @TableField("fact_id")
    private Long factId;

    public WorldRiskFact() {
    }

    public WorldRiskFact(Long riskId, Long factId) {
        this.riskId = riskId;
        this.factId = factId;
    }

    public Long getRiskId() {
        return riskId;
    }

    public void setRiskId(Long riskId) {
        this.riskId = riskId;
    }

    public Long getFactId() {
        return factId;
    }

    public void setFactId(Long factId) {
        this.factId = factId;
    }
}