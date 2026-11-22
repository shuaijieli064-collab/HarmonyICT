package com.nexus.world.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.world.entity.WorldEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 世界实体 Mapper。通用 CRUD 满足阶段二需求，复杂查询后续按需补 XML。
 */
@Mapper
public interface WorldEntityMapper extends BaseMapper<WorldEntity> {
}