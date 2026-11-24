package com.nexus.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.asset.dto.AssetFactResponse;
import com.nexus.asset.dto.AssetResponse;
import com.nexus.asset.dto.AssetSummaryResponse;
import com.nexus.asset.service.AssetService;
import com.nexus.common.result.PageResult;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资产中心实现：对世界模型实体/事实/风险的只读聚合。
 * 红线：资产视图不产生新数据，仅重排既有事实；价格汇总只取数值型事实，
 * 解析失败的跳过（规则支撑智能，不靠大模型给结论）。
 */
@Service
public class AssetServiceImpl implements AssetService {

    /** 视为「价值类」的谓词集合，小写匹配（与 extraction-schema.md 的谓词命名对齐）。 */
    private static final Set<String> VALUE_PREDICATES =
            Set.of("price", "cost", "amount", "budget", "total", "monthly_price", "renewal_price", "unit_price");

    private final WorldEntityMapper entityMapper;
    private final WorldFactMapper factMapper;
    private final WorldRiskMapper riskMapper;

    public AssetServiceImpl(WorldEntityMapper entityMapper, WorldFactMapper factMapper, WorldRiskMapper riskMapper) {
        this.entityMapper = entityMapper;
        this.factMapper = factMapper;
        this.riskMapper = riskMapper;
    }

    @Override
    public com.nexus.common.result.PageResult<AssetResponse> list(Long userId, String entityType, String keyword,
                                                                  int page, int pageSize) {
        LambdaQueryWrapper<WorldEntity> wrapper = new LambdaQueryWrapper<WorldEntity>()
                .eq(WorldEntity::getUserId, userId)
                .eq(WorldEntity::getStatus, 1)
                .eq(entityType != null && !entityType.isBlank(), WorldEntity::getEntityType, entityType)
                .like(keyword != null && !keyword.isBlank(), WorldEntity::getName, keyword)
                .orderByDesc(WorldEntity::getCreatedAt);
        Page<WorldEntity> result = entityMapper.selectPage(new Page<>(page, pageSize), wrapper);

        List<Long> entityIds = result.getRecords().stream().map(WorldEntity::getId).toList();
        Map<Long, List<AssetFactResponse>> factsBySubject = loadActiveFacts(userId, entityIds);

        List<AssetResponse> records = result.getRecords().stream()
                .map(entity -> AssetResponse.from(entity, factsBySubject.getOrDefault(entity.getId(), List.of())))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AssetSummaryResponse summary(Long userId) {
        List<WorldEntity> entities = entityMapper.selectList(new LambdaQueryWrapper<WorldEntity>()
                .eq(WorldEntity::getUserId, userId)
                .eq(WorldEntity::getStatus, 1));

        Map<String, Long> byType = entities.stream()
                .collect(Collectors.groupingBy(WorldEntity::getEntityType, Collectors.counting()));

        List<WorldFact> facts = loadAllActiveFacts(userId);
        List<WorldFact> valueFacts = facts.stream()
                .filter(fact -> fact.getPredicate() != null
                        && VALUE_PREDICATES.contains(fact.getPredicate().toLowerCase(Locale.ROOT)))
                .toList();

        BigDecimal totalValue = valueFacts.stream()
                .map(this::numericValue)
                .filter(value -> value.signum() >= 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String valueUnit = majorityUnit(valueFacts);

        Long openRiskCount = riskMapper.selectCount(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .eq(WorldRisk::getStatus, "open"));

        return new AssetSummaryResponse(entities.size(), byType, totalValue, valueUnit,
                openRiskCount == null ? 0 : openRiskCount);
    }

    /** 批量加载实体关联的 active 事实并按主体分组。 */
    private Map<Long, List<AssetFactResponse>> loadActiveFacts(Long userId, List<Long> subjectIds) {
        if (subjectIds.isEmpty()) {
            return Map.of();
        }
        List<WorldFact> facts = factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userId)
                .eq(WorldFact::getStatus, "active")
                .in(WorldFact::getSubjectId, subjectIds));
        return facts.stream().collect(Collectors.groupingBy(WorldFact::getSubjectId,
                Collectors.mapping(this::toFactResponse, Collectors.toList())));
    }

    private List<WorldFact> loadAllActiveFacts(Long userId) {
        return factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userId)
                .eq(WorldFact::getStatus, "active"));
    }

    private AssetFactResponse toFactResponse(WorldFact fact) {
        return new AssetFactResponse(fact.getId(), fact.getPredicate(), fact.getObjectValue(),
                fact.getValueType(), fact.getUnit(), fact.getNormalizedValue(), fact.getValidUntil());
    }

    /** 取事实数值：优先 normalizedValue，退化 objectValue；解析失败按 0 跳过。 */
    private BigDecimal numericValue(WorldFact fact) {
        String raw = fact.getNormalizedValue() != null && !fact.getNormalizedValue().isBlank()
                ? fact.getNormalizedValue()
                : fact.getObjectValue();
        try {
            return raw == null ? BigDecimal.ZERO : new BigDecimal(raw.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    /** 单位众数：出现最多的非空单位，稳定取字典序第一避免并发顺序抖动。 */
    private String majorityUnit(List<WorldFact> valueFacts) {
        Map<String, Long> counts = valueFacts.stream()
                .map(WorldFact::getUnit)
                .filter(unit -> unit != null && !unit.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry<String, Long>::getValue)
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
