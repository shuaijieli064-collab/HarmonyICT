package com.nexus.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.risk.entity.WorldRiskFact;
import org.apache.ibatis.annotations.Mapper;

/**
 * 风险-证据事实关联 Mapper（P1-6 拍板）。
 * 写入仅限 RiskRuleEngine 与后续接入的抽取编排链路，Controller 只提供查询。
 */
@Mapper
public interface WorldRiskFactMapper extends BaseMapper<WorldRiskFact> {
}