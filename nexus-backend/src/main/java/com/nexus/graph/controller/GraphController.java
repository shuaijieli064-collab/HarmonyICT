package com.nexus.graph.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.Result;
import com.nexus.graph.dto.WorldGraphResponse;
import com.nexus.graph.service.GraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 世界模型图谱接口（体量扩充第二波）。
 */
@RestController
@RequestMapping("/api/v1/world")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/graph")
    public Result<WorldGraphResponse> graph() {
        return Result.ok(graphService.graph(UserContext.get()));
    }
}
