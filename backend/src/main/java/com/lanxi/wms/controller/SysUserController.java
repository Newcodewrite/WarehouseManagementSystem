package com.lanxi.wms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.dto.PasswordForm;
import com.lanxi.wms.dto.UserForm;
import com.lanxi.wms.entity.SysUser;
import com.lanxi.wms.service.SysUserService;
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

/**
 * 用户管理接口
 */
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:user')")
    public Result<Page<SysUser>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) String keyword) {
        return Result.ok(sysUserService.page(current, size, keyword));
    }

    @PostMapping
    @OperLog(module = "系统管理", value = "新增用户")
    @PreAuthorize("hasAuthority('system:user')")
    public Result<Void> save(@RequestBody @Valid UserForm form) {
        sysUserService.saveUser(form);
        return Result.ok();
    }

    @PutMapping
    @OperLog(module = "系统管理", value = "修改用户")
    @PreAuthorize("hasAuthority('system:user')")
    public Result<Void> update(@RequestBody @Valid UserForm form) {
        sysUserService.updateUser(form);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @OperLog(module = "系统管理", value = "删除用户")
    @PreAuthorize("hasAuthority('system:user')")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.deleteUser(id);
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    @OperLog(module = "系统管理", value = "重置密码")
    @PreAuthorize("hasAuthority('system:user')")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody @Valid PasswordForm form) {
        sysUserService.resetPassword(id, form.getPassword());
        return Result.ok();
    }
}
