package com.nexus.world.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.PageResult;
import com.nexus.common.result.ResultCode;
import com.nexus.world.dto.EventCreateRequest;
import com.nexus.world.dto.EventResponse;
import com.nexus.world.entity.WorldEntity;
import com.nexus.world.entity.WorldEvent;
import com.nexus.world.entity.WorldSource;
import com.nexus.world.mapper.WorldEntityMapper;
import com.nexus.world.mapper.WorldEventMapper;
import com.nexus.world.mapper.WorldSourceMapper;
import com.nexus.world.service.WorldEventService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 事件服务实现。
 */
@Service
public class WorldEventServiceImpl implements WorldEventService {

    private final WorldEventMapper eventMapper;
    private final WorldEntityMapper entityMapper;
    private final WorldSourceMapper sourceMapper;

    public WorldEventServiceImpl(WorldEventMapper eventMapper, WorldEntityMapper entityMapper,
                                 WorldSourceMapper sourceMapper) {
        this.eventMapper = eventMapper;
        this.entityMapper = entityMapper;
        this.sourceMapper = sourceMapper;
    }

    @Override
    public EventResponse create(Long userId, EventCreateRequest request) {
        if (request.entityId() != null) {
            requireOwnership(entityMapper.selectCount(new LambdaQueryWrapper<WorldEntity>()
                    .eq(WorldEntity::getId, request.entityId())
                    .eq(WorldEntity::getUserId, userId)), ResultCode.WORLD_ENTITY_NOT_FOUND);
        }
        if (request.sourceId() != null) {
            requireOwnership(sourceMapper.selectCount(new LambdaQueryWrapper<WorldSource>()
                    .eq(WorldSource::getId, request.sourceId())
                    .eq(WorldSource::getUserId, userId)), ResultCode.RESOURCE_NOT_FOUND);
        }
        Instant now = Instant.now();
        WorldEvent event = new WorldEvent();
        event.setUserId(userId);
        event.setEntityId(request.entityId());
        event.setEventType(request.eventType());
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setOccurredAt(request.occurredAt() == null ? now : request.occurredAt());
        event.setSourceId(request.sourceId());
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        eventMapper.insert(event);
        return EventResponse.from(event);
    }

    @Override
    public EventResponse get(Long userId, Long id) {
        WorldEvent event = eventMapper.selectOne(new LambdaQueryWrapper<WorldEvent>()
                .eq(WorldEvent::getId, id)
                .eq(WorldEvent::getUserId, userId));
        if (event == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return EventResponse.from(event);
    }

    @Override
    public PageResult<EventResponse> list(Long userId, String eventType, Long entityId, int page, int pageSize) {
        LambdaQueryWrapper<WorldEvent> wrapper = new LambdaQueryWrapper<WorldEvent>()
                .eq(WorldEvent::getUserId, userId)
                .eq(eventType != null && !eventType.isBlank(), WorldEvent::getEventType, eventType)
                .eq(entityId != null, WorldEvent::getEntityId, entityId)
                .orderByDesc(WorldEvent::getOccurredAt);
        IPage<WorldEvent> result = eventMapper.selectPage(WorldEntityServiceImpl.clampPage(page, pageSize), wrapper);
        List<EventResponse> records = result.getRecords().stream().map(EventResponse::from).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private void requireOwnership(Long count, ResultCode notFound) {
        if (count == null || count == 0) {
            throw new BizException(notFound);
        }
    }
}