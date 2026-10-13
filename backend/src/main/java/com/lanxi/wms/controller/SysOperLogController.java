package com.lanxi.wms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.entity.SysOperLog;
import com.lanxi.wms.service.OperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口
 */
@RestController
@RequestMapping("/api/system/log")
@RequiredArgsConstructor
public class SysOperLogController {

    private final OperLogService operLogService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:log')")
    public Result<Page<SysOperLog>> page(@RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "10") long size,
                                         @RequestParam(required = false) String keyword) {
        return Result.ok(operLogService.page(current, size, keyword));
    }
}
