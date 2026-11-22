package com.nexus.world.service.impl;

import com.nexus.common.exception.BizException;
import com.nexus.common.result.ResultCode;
import com.nexus.world.dto.EntityCreateRequest;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldFactMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1-7 拍板验证：实体判重前按统一规则归一化（全角/大小写/多余空白变体视为同名）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorldEntityServiceImplTest {

    @Mock
    private WorldEntityMapper entityMapper;

    @Mock
    private WorldFactMapper factMapper;

    private WorldEntityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorldEntityServiceImpl(entityMapper, factMapper, new ObjectMapper());
    }

    private WorldEntity existing(String name) {
        WorldEntity entity = new WorldEntity();
        entity.setId(500L);
        entity.setUserId(1L);
        entity.setEntityType("asset");
        entity.setName(name);
        entity.setStatus(1);
        return entity;
    }

    @Test
    void createRejectsNormalizedDuplicate() {
        // 已存在全角大写名，再创建半角小写+尾随空格变体 → 判重命中
        when(entityMapper.selectList(any())).thenReturn(List.of(existing("ＨＵＡＷＥＩ")));

        BizException ex = assertThrows(BizException.class,
                () -> service.create(1L, new EntityCreateRequest("asset", "huawei ", null)));
        assertEquals(ResultCode.WORLD_ENTITY_DUPLICATE, ex.getResultCode());
    }

    @Test
    void createAllowsDifferentNormalizedName() {
        when(entityMapper.selectList(any())).thenReturn(List.of(existing("华为手机")));
        when(entityMapper.insert(any(WorldEntity.class))).thenReturn(1);

        service.create(1L, new EntityCreateRequest("asset", "华为平板", null));

        ArgumentCaptor<WorldEntity> captor = ArgumentCaptor.forClass(WorldEntity.class);
        verify(entityMapper).insert(captor.capture());
        assertEquals("华为平板", captor.getValue().getName());
        assertEquals(1L, captor.getValue().getUserId());
    }
}