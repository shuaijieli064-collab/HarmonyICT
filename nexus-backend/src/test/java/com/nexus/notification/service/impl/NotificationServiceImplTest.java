package com.nexus.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.agent.entity.AgentActionLog;
import com.nexus.agent.mapper.AgentActionLogMapper;
import com.nexus.common.exception.BizException;
import com.nexus.notification.entity.Notification;
import com.nexus.notification.mapper.NotificationMapper;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知同步与已读流转验证：
 * 1. 风险同步幂等（同一 risk 只通知一次）；
 * 2. Agent create_task 动作按目标 task 去重；
 * 3. 已读流转带归属校验（隔离红线）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private WorldRiskMapper riskMapper;

    @Mock
    private AgentActionLogMapper actionLogMapper;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationMapper, riskMapper, actionLogMapper);
        when(notificationMapper.selectCount(any())).thenReturn(0L);
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
    }

    private WorldRisk risk(Long id, Long userId, String level) {
        WorldRisk risk = new WorldRisk();
        risk.setId(id);
        risk.setUserId(userId);
        risk.setLevel(level);
        risk.setTitle("订阅即将续费");
        risk.setDescription("云盘订阅 11-28 扣费 30 元/月");
        risk.setStatus("open");
        risk.setCreatedAt(Instant.now());
        return risk;
    }

    private AgentActionLog action(Long userId, Long targetId, String payload) {
        AgentActionLog action = new AgentActionLog();
        action.setId(9001L);
        action.setUserId(userId);
        action.setRunId(1L);
        action.setActionType("create_task");
        action.setResult("success");
        action.setTargetType("task");
        action.setTargetId(targetId);
        action.setPayload(payload);
        action.setCreatedAt(Instant.now());
        return action;
    }

    @Test
    void syncRiskNotificationsCreatesAndSkipsExisting() {
        when(riskMapper.selectList(any())).thenReturn(List.of(risk(501L, 1L, "high")));

        assertEquals(1, service.syncRiskNotifications());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals("risk", captor.getValue().getCategory());
        assertEquals("[高风险] 订阅即将续费", captor.getValue().getTitle());
        assertEquals("risk", captor.getValue().getRefType());
        assertEquals(501L, captor.getValue().getRefId());

        // 幂等：已存在同来源通知时不再插入
        when(notificationMapper.selectCount(any())).thenReturn(1L);
        assertEquals(0, service.syncRiskNotifications());
        verify(notificationMapper).insert(any(Notification.class)); // 仍只有第一次那一条
    }

    @Test
    void syncAgentActionNotificationsDedupesByTargetTask() {
        when(actionLogMapper.selectList(any())).thenReturn(List.of(
                action(1L, 7001L, "{\"title\":\"确认续费\"}"),
                action(1L, 7001L, "{\"title\":\"确认续费\"}")));

        assertEquals(1, service.syncAgentActionNotifications());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        assertEquals("task", captor.getValue().getRefType());
        assertEquals(7001L, captor.getValue().getRefId());
        assertTrue(captor.getValue().getTitle().contains("确认续费"));
    }

    @Test
    void markReadRejectsForeignNotification() {
        Notification foreign = new Notification();
        foreign.setId(1L);
        foreign.setUserId(2L);
        when(notificationMapper.selectById(1L)).thenReturn(foreign);

        assertThrows(BizException.class, () -> service.markRead(1L, 1L));
        verify(notificationMapper, never()).updateById(any(Notification.class));
    }

    @Test
    void markAllReadOnlyTouchesUnreadOfOwner() {
        Notification unread = new Notification();
        unread.setId(1L);
        unread.setUserId(1L);
        when(notificationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(unread));

        assertEquals(1, service.markAllRead(1L));
        verify(notificationMapper).updateById(unread);
        assertTrue(unread.getReadAt() != null);
    }
}
