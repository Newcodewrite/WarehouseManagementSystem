package com.lanxi.wms.service;

import com.lanxi.wms.common.BusinessException;
import com.lanxi.wms.dto.LoginDTO;
import com.lanxi.wms.entity.SysUser;
import com.lanxi.wms.security.JwtUtils;
import com.lanxi.wms.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public Map<String, Object> login(LoginDTO dto) {
        SysUser user = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername())
                .one();
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已被停用，请联系管理员");
        }
        LoginUser loginUser = sysUserService.loadLoginUser(user.getUsername());
        String token = jwtUtils.generate(user.getId(), user.getUsername());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("roles", loginUser.getRoleCodes());
        userInfo.put("perms", loginUser.getPerms());

        Map<String, Object> vo = new HashMap<>();
        vo.put("token", token);
        vo.put("user", userInfo);
        return vo;
    }
}
