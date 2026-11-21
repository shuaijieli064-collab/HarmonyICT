package com.nexus.world.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.world.entity.WorldEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事件 Mapper。
 */
@Mapper
public interface WorldEventMapper extends BaseMapper<WorldEvent> {
}