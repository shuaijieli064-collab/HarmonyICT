package com.nexus.notification.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.Result;
import com.nexus.notification.dto.NotificationResponse;
import com.nexus.notification.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通知接口（体量扩充新增）：列表、未读数、已读流转。
 * 除查询外全部走 PUT 幂等语义，前端可安全重试。
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<PageResult<NotificationResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "false") boolean unread) {
        return Result.ok(notificationService.list(UserContext.get(), unread, page, pageSize));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(Map.of("count", notificationService.unreadCount(UserContext.get())));
    }

    @PutMapping("/{id}/read")
    public Result<NotificationResponse> markRead(@PathVariable Long id) {
        return Result.ok(notificationService.markRead(UserContext.get(), id));
    }

    @PutMapping("/read-all")
    public Result<Map<String, Integer>> markAllRead() {
        return Result.ok(Map.of("updated", notificationService.markAllRead(UserContext.get())));
    }
}
