package com.lanxi.wms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.dto.RoleForm;
import com.lanxi.wms.entity.SysRole;
import com.lanxi.wms.service.SysRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口
 */
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:role')")
    public Result<Page<SysRole>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) String keyword) {
        return Result.ok(sysRoleService.page(current, size, keyword));
    }

    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.ok(sysRoleService.listAll());
    }

    @PostMapping
    @OperLog(module = "系统管理", value = "新增角色")
    @PreAuthorize("hasAuthority('system:role')")
    public Result<Void> save(@RequestBody @Valid RoleForm form) {
        sysRoleService.saveRole(form);
        return Result.ok();
    }

    @PutMapping
    @OperLog(module = "系统管理", value = "修改角色")
    @PreAuthorize("hasAuthority('system:role')")
    public Result<Void> update(@RequestBody @Valid RoleForm form) {
        sysRoleService.updateRole(form);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @OperLog(module = "系统管理", value = "删除角色")
    @PreAuthorize("hasAuthority('system:role')")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.deleteRole(id);
        return Result.ok();
    }
}
