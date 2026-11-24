package com.nexus.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知表 Mapper（MyBatis-Plus 通用 CRUD，无自定义 SQL）。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
