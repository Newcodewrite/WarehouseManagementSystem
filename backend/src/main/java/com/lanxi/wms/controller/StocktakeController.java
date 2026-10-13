package com.lanxi.wms.controller;

import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.security.SecurityUtil;
import com.lanxi.wms.service.StocktakeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 盘点管理接口
 */
@RestController
@RequestMapping("/api/wms/stocktake")
@RequiredArgsConstructor
public class StocktakeController {

    private final StocktakeService stocktakeService;

    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String orderNo,
                                            @RequestParam(required = false) Integer status) {
        return Result.ok(stocktakeService.pageData(current, size, orderNo, status));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(stocktakeService.detail(id));
    }

    @PostMapping
    @OperLog(module = "仓储作业", value = "创建盘点单")
    @PreAuthorize("hasAuthority('wms:stocktake')")
    public Result<Long> create(@RequestBody Map<String, Object> form) {
        Long warehouseId = Long.valueOf(form.get("warehouseId").toString());
        Integer type = form.get("type") == null ? 1 : Integer.valueOf(form.get("type").toString());
        String remark = form.get("remark") == null ? null : form.get("remark").toString();
        return Result.ok(stocktakeService.create(SecurityUtil.getLoginUser(), warehouseId, type, remark));
    }

    /** 录入实盘数量并提交审核：body: {counts:[{itemId, actualQty}]} */
    @PutMapping("/{id}/submit")
    @OperLog(module = "仓储作业", value = "录入盘点结果")
    @PreAuthorize("hasAuthority('wms:stocktake')")
    public Result<Void> submit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> counts = (List<Map<String, Object>>) body.get("counts");
        stocktakeService.submitCounts(id, counts);
        return Result.ok();
    }

    @PutMapping("/{id}/audit")
    @OperLog(module = "仓储作业", value = "审核盘点单")
    @PreAuthorize("hasAuthority('wms:stocktake')")
    public Result<Void> audit(@PathVariable Long id, @RequestParam boolean approved) {
        stocktakeService.audit(id, approved, SecurityUtil.getLoginUser());
        return Result.ok();
    }

    @PutMapping("/{id}/cancel")
    @OperLog(module = "仓储作业", value = "取消盘点单")
    @PreAuthorize("hasAuthority('wms:stocktake')")
    public Result<Void> cancel(@PathVariable Long id) {
        stocktakeService.cancel(id);
        return Result.ok();
    }
}
