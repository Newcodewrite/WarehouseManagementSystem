package com.lanxi.wms.controller;

import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.dto.InboundForm;
import com.lanxi.wms.security.SecurityUtil;
import com.lanxi.wms.service.InboundService;
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

import java.util.Map;

/**
 * 入库管理接口
 */
@RestController
@RequestMapping("/api/wms/inbound")
@RequiredArgsConstructor
public class InboundController {

    private final InboundService inboundService;

    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String orderNo,
                                            @RequestParam(required = false) Integer status) {
        return Result.ok(inboundService.pageData(current, size, orderNo, status));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(inboundService.detail(id));
    }

    @PostMapping
    @OperLog(module = "仓储作业", value = "创建入库单")
    @PreAuthorize("hasAuthority('wms:inbound')")
    public Result<Void> create(@RequestBody @Valid InboundForm form) {
        inboundService.create(SecurityUtil.getLoginUser(), form);
        return Result.ok();
    }

    @PutMapping("/{id}/audit")
    @OperLog(module = "仓储作业", value = "审核入库单")
    @PreAuthorize("hasAuthority('wms:inbound')")
    public Result<Void> audit(@PathVariable Long id, @RequestParam boolean approved) {
        inboundService.audit(id, approved, SecurityUtil.getLoginUser());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @OperLog(module = "仓储作业", value = "删除入库单")
    @PreAuthorize("hasAuthority('wms:inbound')")
    public Result<Void> delete(@PathVariable Long id) {
        inboundService.delete(id);
        return Result.ok();
    }
}
