package com.nexus.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.risk.snapshot.RiskSnapshot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 风险趋势快照 Mapper（MyBatis-Plus 通用 CRUD）。
 */
@Mapper
public interface RiskSnapshotMapper extends BaseMapper<RiskSnapshot> {
}
