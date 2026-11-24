package com.nexus.export.service.impl;

import com.nexus.common.exception.BizException;
import com.nexus.risk.entity.WorldRisk;
import com.nexus.risk.mapper.WorldRiskMapper;
import com.nexus.task.mapper.TaskMapper;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CSV 导出验证：表头、字段顺序、含逗号/引号值的转义、未知数据集拒绝。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportServiceImplTest {

    @Mock
    private WorldFactMapper factMapper;
    @Mock
    private WorldEntityMapper entityMapper;
    @Mock
    private WorldRiskMapper riskMapper;
    @Mock
    private TaskMapper taskMapper;

    private ExportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ExportServiceImpl(factMapper, entityMapper, riskMapper, taskMapper);
    }

    private WorldFact fact(String objectValue) {
        WorldFact fact = new WorldFact();
        fact.setId(1L);
        fact.setUserId(1L);
        fact.setSubjectId(10L);
        fact.setPredicate("subscription_price");
        fact.setObjectValue(objectValue);
        fact.setValueType("number");
        fact.setConfidence(new BigDecimal("0.900"));
        fact.setValidFrom(Instant.parse("2026-11-01T00:00:00Z"));
        fact.setStatus("active");
        fact.setFactStatus("confirmed");
        fact.setCreatedAt(Instant.parse("2026-11-20T08:30:00Z"));
        return fact;
    }

    @Test
    void factsExportWritesHeaderAndRows() {
        when(factMapper.selectList(any())).thenReturn(List.of(fact("39.00")));

        String csv = service.exportCsv(1L, "facts");

        assertTrue(csv.startsWith("\uFEFF"), "应带 UTF-8 BOM 便于 Excel 打开");
        String[] lines = csv.split("\n");
        assertEquals("id,subject_id,predicate,object_value,value_type,confidence,valid_from,valid_until,status,fact_status,created_at",
                lines[0].substring(1), "首行去掉 BOM 后应为表头");
        assertTrue(lines[1].startsWith("1,10,subscription_price,39.00,number,0.900,"), "实际行: " + lines[1]);
        assertTrue(lines[1].contains("2026-11-20 08:30"));
    }

    @Test
    void commasAndQuotesAreEscaped() {
        when(riskMapper.selectList(any())).thenReturn(List.of(riskWithTitle("订阅「A,B」续费, 请注意\"扣费\"")));

        String csv = service.exportCsv(1L, "risks");

        String dataLine = csv.split("\n")[1];
        assertTrue(dataLine.contains("\"订阅「A,B」续费, 请注意\"\"扣费\"\"\""), "含逗号与引号应加引号并翻倍: " + dataLine);
    }

    private WorldRisk riskWithTitle(String title) {
        WorldRisk risk = new WorldRisk();
        risk.setId(2L);
        risk.setUserId(1L);
        risk.setRiskType("other");
        risk.setLevel("medium");
        risk.setTitle(title);
        risk.setRuleCode("RULE_X");
        risk.setStatus("open");
        risk.setCreatedAt(Instant.parse("2026-11-21T00:00:00Z"));
        return risk;
    }

    @Test
    void unknownDatasetIsRejected() {
        assertThrows(BizException.class, () -> service.exportCsv(1L, "secrets"));
    }
}
