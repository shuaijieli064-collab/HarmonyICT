package com.nexus.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.task.entity.Task;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务 Mapper。
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}