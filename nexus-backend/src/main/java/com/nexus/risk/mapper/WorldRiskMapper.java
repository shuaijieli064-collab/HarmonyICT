package com.nexus.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.risk.entity.WorldRisk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 风险 Mapper。风险写入仅限 RiskRuleEngine，Controller 只提供查询与状态流转。
 */
@Mapper
public interface WorldRiskMapper extends BaseMapper<WorldRisk> {
}