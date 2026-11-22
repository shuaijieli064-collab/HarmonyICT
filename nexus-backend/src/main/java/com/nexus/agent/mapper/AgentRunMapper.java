package com.nexus.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.agent.entity.AgentRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 执行记录 Mapper。
 */
@Mapper
public interface AgentRunMapper extends BaseMapper<AgentRun> {
}