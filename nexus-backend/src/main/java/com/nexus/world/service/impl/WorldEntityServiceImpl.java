package com.nexus.world.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.ResultCode;
import com.nexus.world.dto.EntityCreateRequest;
import com.nexus.world.dto.EntityResponse;
import com.nexus.world.dto.EntityUpdateRequest;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldFact;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldFactMapper;
import com.nexus.world.service.WorldEntityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 世界实体服务实现。
 * attributes 以 JSON 字符串入库（TEXT），写入前用 Jackson 校验合法性，避免脏数据进入世界模型。
 */
@Service
public class WorldEntityServiceImpl implements WorldEntityService {

    private static final long MAX_PAGE_SIZE = 100;

    private final WorldEntityMapper entityMapper;
    private final WorldFactMapper factMapper;
    private final ObjectMapper objectMapper;

    public WorldEntityServiceImpl(WorldEntityMapper entityMapper, WorldFactMapper factMapper,
                                  ObjectMapper objectMapper) {
        this.entityMapper = entityMapper;
        this.factMapper = factMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityResponse create(Long userId, EntityCreateRequest request) {
        validateJson(request.attributes());
        Instant now = Instant.now();
        WorldEntity entity = new WorldEntity();
        entity.setUserId(userId);
        entity.setEntityType(request.entityType());
        entity.setName(request.name());
        entity.setAttributes(request.attributes() == null ? "{}" : request.attributes());
        entity.setStatus(1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entityMapper.insert(entity);
        return EntityResponse.from(entity);
    }

    @Override
    public EntityResponse get(Long userId, Long id) {
        return EntityResponse.from(requireEntity(userId, id));
    }

    @Override
    public PageResult<EntityResponse> list(Long userId, String entityType, String keyword, int page, int pageSize) {
        LambdaQueryWrapper<WorldEntity> wrapper = new LambdaQueryWrapper<WorldEntity>()
                .eq(WorldEntity::getUserId, userId)
                .eq(WorldEntity::getStatus, 1)
                .eq(entityType != null && !entityType.isBlank(), WorldEntity::getEntityType, entityType)
                .like(keyword != null && !keyword.isBlank(), WorldEntity::getName, keyword)
                .orderByDesc(WorldEntity::getCreatedAt);
        IPage<WorldEntity> result = entityMapper.selectPage(clampPage(page, pageSize), wrapper);
        List<EntityResponse> records = result.getRecords().stream().map(EntityResponse::from).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public EntityResponse update(Long userId, Long id, EntityUpdateRequest request) {
        WorldEntity entity = requireEntity(userId, id);
        validateJson(request.attributes());
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.attributes() != null) {
            entity.setAttributes(request.attributes());
        }
        entity.setUpdatedAt(Instant.now());
        entityMapper.updateById(entity);
        return EntityResponse.from(entity);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        WorldEntity entity = requireEntity(userId, id);
        entity.setStatus(0);
        entity.setUpdatedAt(Instant.now());
        entityMapper.updateById(entity);
        // 同步撤回名下 active 事实，避免悬挂数据（见接口注释）
        factMapper.update(null, new LambdaUpdateWrapper<WorldFact>()
                .eq(WorldFact::getUserId, userId)
                .eq(WorldFact::getSubjectId, entity.getId())
                .eq(WorldFact::getStatus, "active")
                .set(WorldFact::getStatus, "retracted")
                .set(WorldFact::getUpdatedAt, Instant.now()));
    }

    private WorldEntity requireEntity(Long userId, Long id) {
        WorldEntity entity = entityMapper.selectOne(new LambdaQueryWrapper<WorldEntity>()
                .eq(WorldEntity::getId, id)
                .eq(WorldEntity::getUserId, userId)
                .eq(WorldEntity::getStatus, 1));
        if (entity == null) {
            // 查询带 user_id（规范 2.4），不存在与无权访问统一报“不存在”，不泄露他人数据存在性
            throw new BizException(ResultCode.WORLD_ENTITY_NOT_FOUND);
        }
        return entity;
    }

    private void validateJson(String attributes) {
        if (attributes == null) {
            return;
        }
        try {
            objectMapper.readTree(attributes);
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_INVALID, "attributes 须为合法 JSON 字符串");
        }
    }

    static <T> Page<T> clampPage(int page, int pageSize) {
        if (page < 1) {
            throw new BizException(ResultCode.PARAM_INVALID, "page 从 1 开始");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BizException(ResultCode.PARAM_INVALID, "pageSize 须在 1-100 之间");
        }
        return new Page<>(page, pageSize);
    }
}