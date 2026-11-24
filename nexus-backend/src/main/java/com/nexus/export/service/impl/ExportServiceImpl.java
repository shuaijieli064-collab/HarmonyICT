package com.nexus.export.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;
import com.nexus.export.service.ExportService;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.task.entity.Task;
import com.nexus.task.mapper.TaskMapper;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * CSV 导出实现。
 * 为什么不用流式逐行写：单用户数据量在演示场景下为小集合（千级以内），
 * 一次拼装文本更简单可控；CSV 转义集中在一处，避免各数据集口径不一致。
 */
@Service
public class ExportServiceImpl implements ExportService {

    private static final Set<String> DATASETS = Set.of("facts", "risks", "tasks", "entities");
    private static final String BOM = "\uFEFF";
    private static final DateTimeFormatter TS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final WorldFactMapper factMapper;
    private final WorldEntityMapper entityMapper;
    private final WorldRiskMapper riskMapper;
    private final TaskMapper taskMapper;

    public ExportServiceImpl(WorldFactMapper factMapper, WorldEntityMapper entityMapper,
                             WorldRiskMapper riskMapper, TaskMapper taskMapper) {
        this.factMapper = factMapper;
        this.entityMapper = entityMapper;
        this.riskMapper = riskMapper;
        this.taskMapper = taskMapper;
    }

    @Override
    public String exportCsv(Long userId, String dataset) {
        if (!DATASETS.contains(dataset)) {
            throw new BizException(ResultCode.PARAM_INVALID, "未知导出数据集: " + dataset);
        }
        return switch (dataset) {
            case "facts" -> facts(userId);
            case "risks" -> risks(userId);
            case "tasks" -> tasks(userId);
            case "entities" -> entities(userId);
            default -> throw new BizException(ResultCode.PARAM_INVALID, "未知导出数据集: " + dataset);
        };
    }

    private String facts(Long userId) {
        List<WorldFact> facts = factMapper.selectList(new LambdaQueryWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userId)
                .orderByDesc(WorldFact::getCreatedAt));
        StringBuilder csv = new StringBuilder(BOM)
                .append("id,subject_id,predicate,object_value,value_type,confidence,valid_from,valid_until,status,fact_status,created_at\n");
        for (WorldFact fact : facts) {
            csv.append(row(
                    String.valueOf(fact.getId()),
                    String.valueOf(fact.getSubjectId()),
                    fact.getPredicate(),
                    fact.getObjectValue(),
                    fact.getValueType(),
                    fact.getConfidence() == null ? "" : fact.getConfidence().toPlainString(),
                    format(fact.getValidFrom()),
                    format(fact.getValidUntil()),
                    fact.getStatus(),
                    fact.getFactStatus(),
                    format(fact.getCreatedAt())));
        }
        return csv.toString();
    }

    private String entities(Long userId) {
        List<WorldEntity> entities = entityMapper.selectList(new LambdaQueryWrapper<WorldEntity>()
                .eq(WorldEntity::getUserId, userId)
                .orderByDesc(WorldEntity::getCreatedAt));
        StringBuilder csv = new StringBuilder(BOM)
                .append("id,entity_type,name,attributes,status,created_at,updated_at\n");
        for (WorldEntity entity : entities) {
            csv.append(row(
                    String.valueOf(entity.getId()),
                    entity.getEntityType(),
                    entity.getName(),
                    entity.getAttributes(),
                    String.valueOf(entity.getStatus()),
                    format(entity.getCreatedAt()),
                    format(entity.getUpdatedAt())));
        }
        return csv.toString();
    }

    private String risks(Long userId) {
        List<WorldRisk> risks = riskMapper.selectList(new LambdaQueryWrapper<WorldRisk>()
                .eq(WorldRisk::getUserId, userId)
                .orderByDesc(WorldRisk::getCreatedAt));
        StringBuilder csv = new StringBuilder(BOM)
                .append("id,risk_type,level,title,description,entity_id,rule_code,status,created_at\n");
        for (WorldRisk risk : risks) {
            csv.append(row(
                    String.valueOf(risk.getId()),
                    risk.getRiskType(),
                    risk.getLevel(),
                    risk.getTitle(),
                    risk.getDescription(),
                    risk.getEntityId() == null ? "" : String.valueOf(risk.getEntityId()),
                    risk.getRuleCode(),
                    risk.getStatus(),
                    format(risk.getCreatedAt())));
        }
        return csv.toString();
    }

    private String tasks(Long userId) {
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId)
                .orderByDesc(Task::getCreatedAt));
        StringBuilder csv = new StringBuilder(BOM)
                .append("id,title,description,status,priority,due_date,source_type,risk_id,related_fact_id,created_at\n");
        for (Task task : tasks) {
            csv.append(row(
                    String.valueOf(task.getId()),
                    task.getTitle(),
                    task.getDescription(),
                    task.getStatus(),
                    task.getPriority() == null ? "" : String.valueOf(task.getPriority()),
                    task.getDueDate() == null ? "" : task.getDueDate().toString(),
                    task.getSourceType(),
                    task.getRiskId() == null ? "" : String.valueOf(task.getRiskId()),
                    task.getRelatedFactId() == null ? "" : String.valueOf(task.getRelatedFactId()),
                    format(task.getCreatedAt())));
        }
        return csv.toString();
    }

    /** 单行拼接：字段统一做 CSV 转义（含逗号/引号/换行的值加引号并翻倍内部引号） */
    private String row(String... fields) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(fields[i] == null ? "" : fields[i]));
        }
        return line.append('\n').toString();
    }

    private String escape(String value) {
        String normalized = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        if (normalized.contains(",") || normalized.contains("\"")) {
            return '"' + normalized.replace("\"", "\"\"") + '"';
        }
        return normalized;
    }

    private String format(Instant instant) {
        return instant == null ? "" : TS.format(instant);
    }
}
