package com.nexus.world.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.world.entity.WorldFact;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事实 Mapper。链式失效（superseded）在服务层用两次条件更新完成，不在此定义自定义 SQL。
 */
@Mapper
public interface WorldFactMapper extends BaseMapper<WorldFact> {
}