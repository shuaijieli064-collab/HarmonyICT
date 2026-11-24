package com.nexus.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 通知表实体，对应 notifications（schema.sql）。
 * 定位：站内轻量提醒——风险产生、Agent 动作落库、系统公告；
 * refType/refId 指向来源记录（risk/task/run），保证通知可溯源。
 */
@TableName("notifications")
public class Notification {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 类别：risk（风险提醒）/ agent（Agent 动作结果）/ system（系统公告） */
    private String category;

    private String title;

    private String content;

    @TableField("ref_type")
    private String refType;

    @TableField("ref_id")
    private Long refId;

    /** 已读时间：NULL 即未读（与契约的布尔语义等价，但保留精确时间便于展示） */
    @TableField("read_at")
    private Instant readAt;

    @TableField("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public Long getRefId() {
        return refId;
    }

    public void setRefId(Long refId) {
        this.refId = refId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
