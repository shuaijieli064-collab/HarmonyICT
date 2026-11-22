package com.nexus.world.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.world.dto.FactCreateRequest;
import com.nexus.world.dto.FactResponse;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldFactMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0 拍板验证：confidence/factStatus 抽取写入透传落库，人工录入缺省兜底 1.0/confirmed；
 * P1-5 拍板验证：带 sourceId 的重抽写入先链式失效旧 active 行（upsert 语义）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorldFactServiceImplTest {

    @Mock
    private WorldFactMapper factMapper;

    @Mock
    private WorldEntityMapper entityMapper;

    private WorldFactServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorldFactServiceImpl(factMapper, entityMapper);
        when(entityMapper.selectCount(any())).thenReturn(1L);
        when(factMapper.insert(any(WorldFact.class))).thenReturn(1);
    }

    @Test
    void createKeepsExtractionConfidenceAndFactStatus() {
        FactCreateRequest request = new FactCreateRequest(
                100L, "warranty_until", "2027-01-01", "date", null, null,
                "保修至 2027-01-01", 9L, Instant.parse("2026-11-22T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"),
                new BigDecimal("0.78"), "inferred");

        FactResponse response = service.create(1L, request);

        ArgumentCaptor<WorldFact> captor = ArgumentCaptor.forClass(WorldFact.class);
        verify(factMapper).insert(captor.capture());
        assertEquals(new BigDecimal("0.78"), captor.getValue().getConfidence());
        assertEquals("inferred", captor.getValue().getFactStatus());
        assertEquals(new BigDecimal("0.78"), response.confidence());
        assertEquals("inferred", response.factStatus());
    }

    @Test
    void createFallsBackToManualDefaultsWhenFieldsAbsent() {
        FactCreateRequest request = new FactCreateRequest(
                100L, "price", "5999", "number", "元", "5999",
                null, null, null, null, null, null);

        service.create(1L, request);

        ArgumentCaptor<WorldFact> captor = ArgumentCaptor.forClass(WorldFact.class);
        verify(factMapper).insert(captor.capture());
        // BigDecimal.ONE scale=0，断言按数值语义比较而非 equals（区分精度）
        assertEquals(0, captor.getValue().getConfidence().compareTo(new BigDecimal("1.0")));
        assertEquals("confirmed", captor.getValue().getFactStatus());
    }

    @Test
    void createWithSourceIdSupersedesPreviousActiveFactFirst() {
        FactCreateRequest request = new FactCreateRequest(
                100L, "warranty_until", "2027-06-01", "date", null, null,
                null, 9L, null, null, null, null);

        service.create(1L, request);

        // 同源同主体同谓词重抽：先封口旧 active 行（upsert 语义），再插入新行
        verify(factMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(factMapper).insert(any(WorldFact.class));
    }

    @Test
    void manualCreateWithoutSourceIdSkipsSupersede() {
        FactCreateRequest request = new FactCreateRequest(
                100L, "price", "5999", "number", null, null,
                null, null, null, null, null, null);

        service.create(1L, request);

        verify(factMapper).insert(any(WorldFact.class));

        verify(factMapper, org.mockito.Mockito.never())
                .update(isNull(), any(LambdaUpdateWrapper.class));
    }
}