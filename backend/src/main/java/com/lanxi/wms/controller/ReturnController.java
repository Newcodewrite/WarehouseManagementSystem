package com.lanxi.wms.controller;

import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.security.SecurityUtil;
import com.lanxi.wms.service.ReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 退货（逆向物流）接口
 */
@RestController
@RequestMapping("/api/wms/return")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String orderNo,
                                            @RequestParam(required = false) Integer status) {
        return Result.ok(returnService.pageData(current, size, orderNo, status));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(returnService.detail(id));
    }

    @PostMapping
    @OperLog(module = "仓储作业", value = "创建退货单")
    @PreAuthorize("hasAuthority('wms:return')")
    public Result<Long> create(@RequestBody Map<String, Object> form) {
        return Result.ok(returnService.create(SecurityUtil.getLoginUser(), form));
    }

    @PutMapping("/{id}/receive")
    @OperLog(module = "仓储作业", value = "退货收货")
    @PreAuthorize("hasAuthority('wms:return')")
    public Result<Void> receive(@PathVariable Long id) {
        returnService.receive(id, SecurityUtil.getLoginUser());
        return Result.ok();
    }

    /** 质检分级并完成：body: {grades:[{itemId, qcGrade(1可售/2翻新/3报废), qcRemark}]} */
    @PutMapping("/{id}/qc")
    @OperLog(module = "仓储作业", value = "退货质检分级")
    @PreAuthorize("hasAuthority('wms:return')")
    public Result<Void> qc(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> grades = (List<Map<String, Object>>) body.get("grades");
        returnService.qcAndComplete(id, grades, SecurityUtil.getLoginUser());
        return Result.ok();
    }

    @PutMapping("/{id}/cancel")
    @OperLog(module = "仓储作业", value = "取消退货单")
    @PreAuthorize("hasAuthority('wms:return')")
    public Result<Void> cancel(@PathVariable Long id) {
        returnService.cancel(id);
        return Result.ok();
    }
}
