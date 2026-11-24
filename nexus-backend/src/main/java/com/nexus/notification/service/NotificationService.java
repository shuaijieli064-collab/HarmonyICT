package com.nexus.notification.service;

import com.nexus.common.result.PageResult;
import com.nexus.notification.dto.NotificationResponse;

/**
 * 通知服务：查询、已读流转与来源同步。
 * 同步（syncRisk/syncAgentActions）是幂等的：同一来源记录只产生一条通知，
 * 由定时调度调用（NotificationSyncScheduler），不侵入风险管线与 Agent 执行器的既有逻辑。
 */
public interface NotificationService {

    /** 分页查询当前用户通知，按创建时间倒序；unread=true 只看未读 */
    PageResult<NotificationResponse> list(Long userId, boolean unread, int page, int pageSize);

    /** 未读数量（驾驶舱铃铛徽标） */
    long unreadCount(Long userId);

    /** 标记单条已读；重复调用幂等（已读返回原状态不报错） */
    NotificationResponse markRead(Long userId, Long id);

    /** 全部标记已读，返回受影响条数 */
    int markAllRead(Long userId);

    /**
     * 同步新产生的 open 风险为通知（category=risk，refType=risk）。
     * 幂等：已有 (refType=risk, refId=riskId) 的通知跳过。
     *
     * @return 新增通知条数
     */
    int syncRiskNotifications();

    /**
     * 同步 Agent 已成功执行的 create_task 动作为通知（category=agent，refType=task）。
     * 幂等：同一 task 目标只通知一次。
     *
     * @return 新增通知条数
     */
    int syncAgentActionNotifications();
}
