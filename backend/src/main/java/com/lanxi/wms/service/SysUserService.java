package com.lanxi.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.common.BusinessException;
import com.lanxi.wms.dto.UserForm;
import com.lanxi.wms.entity.SysRole;
import com.lanxi.wms.entity.SysUser;
import com.lanxi.wms.entity.SysUserRole;
import com.lanxi.wms.mapper.SysRoleMapper;
import com.lanxi.wms.mapper.SysUserMapper;
import com.lanxi.wms.mapper.SysUserRoleMapper;
import com.lanxi.wms.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统用户服务
 */
@Service
@RequiredArgsConstructor
public class SysUserService extends ServiceImpl<SysUserMapper, SysUser> {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public Page<SysUser> page(long current, long size, String keyword) {
        Page<SysUser> page = this.lambdaQuery()
                .and(StringUtils.hasText(keyword),
                        w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getNickname, keyword))
                .orderByDesc(SysUser::getId)
                .page(new Page<>(current, size));
        if (!page.getRecords().isEmpty()) {
            Map<Long, List<Long>> roleMap = sysUserRoleMapper.selectList(null).stream()
                    .collect(Collectors.groupingBy(SysUserRole::getUserId,
                            Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())));
            page.getRecords().forEach(u -> u.setRoleIds(roleMap.getOrDefault(u.getId(), List.of())));
        }
        return page;
    }

    public LoginUser loadLoginUser(String username) {
        SysUser user = this.lambdaQuery().eq(SysUser::getUsername, username).one();
        if (user == null) {
            return null;
        }
        LoginUser lu = new LoginUser();
        lu.setId(user.getId());
        lu.setUsername(user.getUsername());
        lu.setNickname(user.getNickname());
        lu.setStatus(user.getStatus());
        lu.setPassword(user.getPassword());

        List<Long> roleIds = sysUserRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()))
                .stream().map(SysUserRole::getRoleId).toList();
        Set<String> roleCodes = new HashSet<>();
        Set<String> perms = new HashSet<>();
        if (!roleIds.isEmpty()) {
            List<SysRole> roles = sysRoleMapper.selectList(
                    new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds));
            roleCodes = roles.stream().map(SysRole::getCode).collect(Collectors.toSet());
            for (SysRole role : roles) {
                if (StringUtils.hasText(role.getPerms())) {
                    perms.addAll(Arrays.asList(role.getPerms().split(",")));
                }
            }
        }
        lu.setRoleCodes(roleCodes);
        lu.setPerms(perms);
        return lu;
    }

    @Transactional
    public void saveUser(UserForm form) {
        long count = this.lambdaQuery().eq(SysUser::getUsername, form.getUsername()).count();
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(form.getUsername());
        user.setPassword(passwordEncoder.encode(
                StringUtils.hasText(form.getPassword()) ? form.getPassword() : "123456"));
        user.setNickname(form.getNickname());
        user.setPhone(form.getPhone());
        user.setEmail(form.getEmail());
        user.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        this.save(user);
        saveRoles(user.getId(), form.getRoleIds());
    }

    @Transactional
    public void updateUser(UserForm form) {
        SysUser user = this.getById(form.getId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        long count = this.lambdaQuery()
                .eq(SysUser::getUsername, form.getUsername())
                .ne(SysUser::getId, form.getId())
                .count();
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        user.setUsername(form.getUsername());
        user.setNickname(form.getNickname());
        user.setPhone(form.getPhone());
        user.setEmail(form.getEmail());
        user.setStatus(form.getStatus());
        this.updateById(user);
        saveRoles(user.getId(), form.getRoleIds());
    }

    private void saveRoles(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                sysUserRoleMapper.insert(ur);
            }
        }
    }

    public void resetPassword(Long id, String password) {
        SysUser user = this.getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(password));
        this.updateById(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        this.removeById(id);
    }
}
