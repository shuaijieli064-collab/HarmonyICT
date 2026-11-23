package com.nexus.insight.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.Result;
import com.nexus.insight.dto.SimulationRequest;
import com.nexus.insight.dto.SimulationResponse;
import com.nexus.insight.service.DecisionSimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 决策推演接口（P1）。只读沙盘：不落库、不产生真实风险记录。
 */
@RestController
@RequestMapping("/api/v1/decision")
public class DecisionController {

    private final DecisionSimulationService decisionSimulationService;

    public DecisionController(DecisionSimulationService decisionSimulationService) {
        this.decisionSimulationService = decisionSimulationService;
    }

    @PostMapping("/simulate")
    public Result<SimulationResponse> simulate(@Valid @RequestBody SimulationRequest request) {
        return Result.ok(decisionSimulationService.simulate(UserContext.get(), request));
    }
}
