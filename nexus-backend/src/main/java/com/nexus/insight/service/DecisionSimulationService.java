package com.nexus.insight.service;

import com.nexus.insight.dto.SimulationRequest;
import com.nexus.insight.dto.SimulationResponse;

/**
 * 假设推演服务（P1）。纯只读沙盘：基于实体事实做 12 个月投影，
 * 不落库、不产生真实风险记录（红线：风险结论只由规则引擎产生）。
 */
public interface DecisionSimulationService {

    SimulationResponse simulate(Long userId, SimulationRequest request);
}
