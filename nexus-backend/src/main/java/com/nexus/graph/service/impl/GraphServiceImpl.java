package com.nexus.graph.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.graph.dto.WorldGraphResponse;
import com.nexus.graph.service.GraphService;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldEvent;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图谱聚合实现。
 * 四类边的口径：
 * 1. fact_of  —— 实体 → 事实（主体归属，图的主干）
 * 2. event_of —— 实体 → 事件（时间线挂载）
 * 3. co_source —— 实体 ↔ 实体：两个实体的事实出自同一份资料（同源关联，演示"一份发票牵出设备+订阅"）
 * 4. risk_on  —— 实体 → 未解决风险（风险在图上直接可见，演示"世界模型主动发现风险"）
 * 数量上限：实体 100 / 事实 200 / 事件 100 / 风险 50，演示与中型数据量下足够且可控。
 */
@Service
public class GraphServiceImpl implements GraphService {

    private static final int ENTITY_LIMIT = 100;
    private static final int FACT_LIMIT = 200;
    private static final int EVENT_LIMIT = 100;
    private static final int RISK_LIMIT = 50;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneOffset.UTC);

    private final WorldEntityMapper entityMapper;
    private final WorldFactMapper factMapper;
    private final WorldEventMapper eventMapper;
    private final WorldRiskMapper riskMapper;

    public GraphServiceImpl(WorldEntityMapper entityMapper, WorldFactMapper factMapper,
                            WorldEventMapper eventMapper, WorldRiskMapper riskMapper) {
        this.entityMapper = entityMapper;
        this.factMapper = factMapper;
        this.eventMapper = eventMapper;
        this.riskMapper = riskMapper;
    }

    @Override
    public WorldGraphResponse graph(Long userId) {
        List<WorldEntity> entities = entityMapper.selectList(new LambdaQueryWrapper<WorldEntity>()
                .eq(WorldEntity::getUserId, userId)
                .orderByDesc(WorldEntity::getCreatedAt)
                .last("LIMIT " + ENTITY_LIMIT));
        List<WorldFact> facts = factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userId)
                .eq(WorldFact::getStatus, "active")
                .orderByDesc(WorldFact::getCreatedAt)
                .last("LIMIT " + FACT_LIMIT));
        List<WorldEvent> events = eventMapper.selectList(new LambdaQueryWrapper<WorldEvent>()
                .eq(WorldEvent::getUserId, userId)
                .orderByDesc(WorldEvent::getOccurredAt)
                .last("LIMIT " + EVENT_LIMIT));
        List<WorldRisk> risks = riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .ne(WorldRisk::getStatus, "resolved")
                .orderByDesc(WorldRisk::getCreatedAt)
                .last("LIMIT " + RISK_LIMIT));

        Set<String> nodeIds = new HashSet<>();
        List<WorldGraphResponse.GraphNode> nodes = new ArrayList<>();
        List<WorldGraphResponse.GraphLink> links = new ArrayList<>();

        Map<Long, String> entityNodeId = new HashMap<>();
        for (WorldEntity entity : entities) {
            String id = "entity:" + entity.getId();
            entityNodeId.put(entity.getId(), id);
            nodes.add(new WorldGraphResponse.GraphNode(id, "entity", entity.getName(),
                    entity.getEntityType(), String.valueOf(entity.getStatus())));
            nodeIds.add(id);
        }

        for (WorldFact fact : facts) {
            String subjectNode = entityNodeId.get(fact.getSubjectId());
            if (subjectNode == null) {
                continue;
            }
            String id = "fact:" + fact.getId();
            nodes.add(new WorldGraphResponse.GraphNode(id, "fact",
                    fact.getPredicate() + " = " + fact.getObjectValue(), "fact",
                    fact.getConfidence() == null ? "" : fact.getConfidence().toPlainString()));
            links.add(new WorldGraphResponse.GraphLink(subjectNode, id, "fact_of", fact.getPredicate()));
            nodeIds.add(id);
        }

        // 同源关联：同一 source 的多个事实分属不同实体 → 实体间建边
        Map<Long, Set<String>> entitiesBySource = new HashMap<>();
        for (WorldFact fact : facts) {
            if (fact.getSourceId() == null) {
                continue;
            }
            String subjectNode = entityNodeId.get(fact.getSubjectId());
            if (subjectNode != null) {
                entitiesBySource.computeIfAbsent(fact.getSourceId(), k -> new HashSet<>()).add(subjectNode);
            }
        }
        Set<String> coSourceAdded = new HashSet<>();
        for (Set<String> group : entitiesBySource.values()) {
            List<String> groupNodes = group.stream().sorted().toList();
            for (int i = 0; i < groupNodes.size(); i++) {
                for (int j = i + 1; j < groupNodes.size(); j++) {
                    String key = groupNodes.get(i) + "|" + groupNodes.get(j);
                    if (coSourceAdded.add(key)) {
                        links.add(new WorldGraphResponse.GraphLink(
                                groupNodes.get(i), groupNodes.get(j), "co_source", "同源资料"));
                    }
                }
            }
        }

        for (WorldEvent event : events) {
            String id = "event:" + event.getId();
            nodes.add(new WorldGraphResponse.GraphNode(id, "event", event.getTitle(), "event",
                    event.getOccurredAt() == null ? "" : TS.format(event.getOccurredAt())));
            nodeIds.add(id);
            String ownerNode = event.getEntityId() == null ? null : entityNodeId.get(event.getEntityId());
            if (ownerNode != null) {
                links.add(new WorldGraphResponse.GraphLink(ownerNode, id, "event_of", "事件归属"));
            }
        }

        for (WorldRisk risk : risks) {
            String id = "risk:" + risk.getId();
            nodes.add(new WorldGraphResponse.GraphNode(id, "risk", "[" + risk.getLevel() + "] "
                    + risk.getTitle(), "risk", risk.getRuleCode()));
            nodeIds.add(id);
            String entityNode = risk.getEntityId() == null ? null : entityNodeId.get(risk.getEntityId());
            if (entityNode != null) {
                links.add(new WorldGraphResponse.GraphLink(entityNode, id, "risk_on", "风险关联"));
            }
        }

        return new WorldGraphResponse(nodes, links);
    }
}
