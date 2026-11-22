package com.nexus.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper。MyBatis-Plus 通用 CRUD 即可满足认证模块，复杂查询后续按需补 XML。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}