package com.nexus.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.agent.entity.AgentActionLog;
import com.nexus.agent.mapper.AgentActionLogMapper;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.ResultCode;
import com.nexus.notification.dto.NotificationResponse;
import com.nexus.notification.entity.Notification;
import com.nexus.notification.mapper.NotificationMapper;
import com.nexus.notification.service.NotificationService;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知服务实现。
 * 为什么用"同步"而不是管线内直插：风险产生有两处入口（RiskRuleEngine 与 RiskAnalysisPipelineService）、
 * Agent 动作执行在授权后异步完成——同步式聚合让通知模块自成一体，不改动既有管线的构造与测试，
 * 且按 (refType, refId) 幂等，重复扫描不会产生重复通知。
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private static final Map<String, String> LEVEL_LABELS = Map.of(
            "high", "高风险", "medium", "需关注", "low", "提示");

    private final NotificationMapper notificationMapper;
    private final WorldRiskMapper riskMapper;
    private final AgentActionLogMapper actionLogMapper;

    public NotificationServiceImpl(NotificationMapper notificationMapper,
                                   WorldRiskMapper riskMapper,
                                   AgentActionLogMapper actionLogMapper) {
        this.notificationMapper = notificationMapper;
        this.riskMapper = riskMapper;
        this.actionLogMapper = actionLogMapper;
    }

    @Override
    public PageResult<NotificationResponse> list(Long userId, boolean unread, int page, int pageSize) {
        Page<Notification> result = notificationMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(unread, Notification::getReadAt, null)
                        .orderByDesc(Notification::getCreatedAt));
        List<NotificationResponse> records = result.getRecords().stream()
                .map(NotificationResponse::from)
                .toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public long unreadCount(Long userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt));
        return count == null ? 0 : count;
    }

    @Override
    public NotificationResponse markRead(Long userId, Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || !notification.getUserId().equals(userId)) {
            // 归属校验与任务/风险接口一致：他人或不存在一律按资源不存在处理（隔离红线）
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationMapper.updateById(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Override
    public int markAllRead(Long userId) {
        List<Notification> unread = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt));
        Instant now = Instant.now();
        for (Notification notification : unread) {
            notification.setReadAt(now);
            notificationMapper.updateById(notification);
        }
        return unread.size();
    }

    @Override
    public int syncRiskNotifications() {
        List<WorldRisk> openRisks = riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getStatus, "open"));
        int created = 0;
        for (WorldRisk risk : openRisks) {
            if (exists("risk", risk.getId())) {
                continue;
            }
            String level = LEVEL_LABELS.getOrDefault(risk.getLevel(), risk.getLevel());
            created += insert(risk.getUserId(), "risk",
                    "[" + level + "] " + risk.getTitle(),
                    risk.getDescription() == null ? "" : risk.getDescription(),
                    "risk", risk.getId());
        }
        if (created > 0) {
            log.info("风险通知同步新增 {} 条", created);
        }
        return created;
    }

    @Override
    public int syncAgentActionNotifications() {
        List<AgentActionLog> actions = actionLogMapper.selectList(new LambdaQueryWrapper<AgentActionLog>()
                .eq(AgentActionLog::getActionType, "create_task")
                .eq(AgentActionLog::getResult, "success"));
        int created = 0;
        // 同一 task 目标可能被多次 create_task 命中：按 targetId 去重后再建通知
        Map<Long, AgentActionLog> latestByTask = new LinkedHashMap<>();
        for (AgentActionLog action : actions) {
            if (action.getTargetId() != null) {
                latestByTask.putIfAbsent(action.getTargetId(), action);
            }
        }
        for (Map.Entry<Long, AgentActionLog> entry : latestByTask.entrySet()) {
            AgentActionLog action = entry.getValue();
            if (exists("task", entry.getKey())) {
                continue;
            }
            created += insert(action.getUserId(), "agent",
                    "Agent 已创建待办「" + titleOf(action.getPayload()) + "」",
                    "您授权的 Agent 动作已执行成功，待办已写入任务中心（动作日志 #" + action.getId() + "）。",
                    "task", entry.getKey());
        }
        if (created > 0) {
            log.info("Agent 动作通知同步新增 {} 条", created);
        }
        return created;
    }

    private String titleOf(String payloadJson) {
        // payload 形如 {"title":"..."}；解析失败不阻断同步，用占位标题兜底
        int start = payloadJson == null ? -1 : payloadJson.indexOf("\"title\"");
        if (start < 0) {
            return "未命名待办";
        }
        int valueStart = payloadJson.indexOf('"', payloadJson.indexOf(':', start) + 1);
        int valueEnd = payloadJson.indexOf('"', valueStart + 1);
        return valueStart >= 0 && valueEnd > valueStart ? payloadJson.substring(valueStart + 1, valueEnd) : "未命名待办";
    }

    private boolean exists(String refType, Long refId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getRefType, refType)
                .eq(Notification::getRefId, refId));
        return count != null && count > 0;
    }

    private int insert(Long userId, String category, String title, String content,
                       String refType, Long refId) {
        Instant now = Instant.now();
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setCategory(category);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRefType(refType);
        notification.setRefId(refId);
        notification.setCreatedAt(now);
        notificationMapper.insert(notification);
        return 1;
    }
}
