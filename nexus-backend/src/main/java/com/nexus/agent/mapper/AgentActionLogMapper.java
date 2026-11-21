package com.nexus.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.agent.entity.AgentActionLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 操作日志 Mapper。
 */
@Mapper
public interface AgentActionLogMapper extends BaseMapper<AgentActionLog> {
}