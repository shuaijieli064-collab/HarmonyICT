package com.nexus.api.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 健康检查接口。
 * 为什么业务级健康检查与 actuator 并存：actuator 检查进程健康，此接口额外验证认证链路与用户上下文可用。
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Long userId;
        try {
            userId = UserContext.get();
        } catch (IllegalStateException e) {
            userId = null;
        }
        return Result.ok(Map.of(
                "status", "UP",
                "service", "nexus-backend",
                "timestamp", Instant.now().toString(),
                "currentUserId", userId == null ? "anonymous" : String.valueOf(userId)));
    }
}