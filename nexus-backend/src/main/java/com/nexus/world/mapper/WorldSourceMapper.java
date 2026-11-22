package com.nexus.world.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.world.entity.WorldSource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资料来源 Mapper，供 ingestion 上传服务与世界模型查询共用。
 */
@Mapper
public interface WorldSourceMapper extends BaseMapper<WorldSource> {
}