package com.nexus.notification.schedule;

import com.nexus.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知同步调度：把新产生的风险与 Agent 成功动作聚合成站内通知。
 * 与风险管线同频（60s），幂等设计保证重复扫描无副作用；
 * 异常吞掉记日志，不允许后台任务中断调度（与既有管线约定一致）。
 */
@Component
public class NotificationSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationSyncScheduler.class);

    private final NotificationService notificationService;

    public NotificationSyncScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void sync() {
        try {
            int risks = notificationService.syncRiskNotifications();
            int actions = notificationService.syncAgentActionNotifications();
            if (risks + actions > 0) {
                log.info("通知同步完成：风险 {} 条，Agent 动作 {} 条", risks, actions);
            }
        } catch (Exception e) {
            log.error("通知同步调度异常", e);
        }
    }
}
