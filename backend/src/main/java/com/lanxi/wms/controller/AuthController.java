package com.lanxi.wms.controller;

import com.lanxi.wms.common.Result;
import com.lanxi.wms.dto.LoginDTO;
import com.lanxi.wms.security.LoginUser;
import com.lanxi.wms.security.SecurityUtil;
import com.lanxi.wms.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody @Valid LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        LoginUser user = SecurityUtil.getLoginUser();
        Map<String, Object> info = new HashMap<>();
        if (user != null) {
            info.put("id", user.getId());
            info.put("username", user.getUsername());
            info.put("nickname", user.getNickname());
            info.put("roles", user.getRoleCodes());
            info.put("perms", user.getPerms());
        }
        return Result.ok(info);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }
}
