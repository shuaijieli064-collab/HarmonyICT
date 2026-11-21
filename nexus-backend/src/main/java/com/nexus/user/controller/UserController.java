package com.nexus.user.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.Result;
import com.nexus.common.result.ResultCode;
import com.nexus.user.dto.UserProfileResponse;
import com.nexus.user.entity.User;
import com.nexus.user.mapper.UserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料接口。
 * 为什么从 UserContext 取用户：规范 2.4 红线，user_id 一律来自 JWT 上下文，禁止信任前端参数。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    public Result<UserProfileResponse> me() {
        Long userId = UserContext.get();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return Result.ok(new UserProfileResponse(
                user.getId(), user.getUsername(), user.getNickname(), user.getEmail(), user.getCreatedAt()));
    }
}