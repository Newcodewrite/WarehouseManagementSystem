package com.lanxi.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.common.BusinessException;
import com.lanxi.wms.dto.RoleForm;
import com.lanxi.wms.entity.SysRole;
import com.lanxi.wms.entity.SysUserRole;
import com.lanxi.wms.mapper.SysRoleMapper;
import com.lanxi.wms.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 角色服务
 */
@Service
@RequiredArgsConstructor
public class SysRoleService extends ServiceImpl<SysRoleMapper, SysRole> {

    private final SysUserRoleMapper sysUserRoleMapper;

    public Page<SysRole> page(long current, long size, String keyword) {
        return this.lambdaQuery()
                .and(StringUtils.hasText(keyword),
                        w -> w.like(SysRole::getCode, keyword).or().like(SysRole::getName, keyword))
                .orderByAsc(SysRole::getId)
                .page(new Page<>(current, size));
    }

    public List<SysRole> listAll() {
        return this.lambdaQuery().orderByAsc(SysRole::getId).list();
    }

    public List<SysRole> listRolesByUserId(Long userId) {
        List<Long> roleIds = sysUserRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return this.listByIds(roleIds);
    }

    @Transactional
    public void saveRole(RoleForm form) {
        long count = this.lambdaQuery().eq(SysRole::getCode, form.getCode()).count();
        if (count > 0) {
            throw new BusinessException("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setCode(form.getCode());
        role.setName(form.getName());
        role.setPerms(form.getPerms());
        role.setRemark(form.getRemark());
        this.save(role);
    }

    @Transactional
    public void updateRole(RoleForm form) {
        SysRole role = this.getById(form.getId());
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        long count = this.lambdaQuery()
                .eq(SysRole::getCode, form.getCode())
                .ne(SysRole::getId, form.getId())
                .count();
        if (count > 0) {
            throw new BusinessException("角色编码已存在");
        }
        role.setCode(form.getCode());
        role.setName(form.getName());
        role.setPerms(form.getPerms());
        role.setRemark(form.getRemark());
        this.updateById(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        this.removeById(id);
    }
}
