package com.nexus.user.controller;

import com.nexus.auth.UserContext;
import com.nexus.common.exception.BizException;
import com.nexus.common.result.Result;
import com.nexus.common.result.ResultCode;
import com.nexus.user.dto.UserProfileResponse;
import com.nexus.user.dto.UserProfileUpdateRequest;
import com.nexus.user.entity.User;
import com.nexus.user.mapper.UserMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

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
        return Result.ok(toProfile(requireCurrentUser()));
    }

    @PutMapping("/me")
    public Result<UserProfileResponse> updateMe(@Valid @RequestBody UserProfileUpdateRequest request) {
        User user = requireCurrentUser();
        if (request.nickname() != null) {
            user.setNickname(request.nickname());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        user.setUpdatedAt(Instant.now());
        userMapper.updateById(user);
        return Result.ok(toProfile(user));
    }

    private User requireCurrentUser() {
        User user = userMapper.selectById(UserContext.get());
        if (user == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return user;
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(), user.getUsername(), user.getNickname(), user.getEmail(), user.getCreatedAt());
    }
}