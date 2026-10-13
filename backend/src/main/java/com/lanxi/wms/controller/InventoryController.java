package com.lanxi.wms.controller;

import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.dto.AdjustForm;
import com.lanxi.wms.security.SecurityUtil;
import com.lanxi.wms.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 库存管理接口
 */
@RestController
@RequestMapping("/api/wms/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) Long warehouseId,
                                            @RequestParam(required = false) String keyword) {
        return Result.ok(inventoryService.pageData(current, size, warehouseId, keyword));
    }

    @GetMapping("/alert/page")
    public Result<Map<String, Object>> alertPage() {
        return Result.ok(Map.of("records", inventoryService.alertList()));
    }

    @GetMapping("/record/page")
    public Result<Map<String, Object>> recordPage(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) Long warehouseId,
                                                  @RequestParam(required = false) Long productId,
                                                  @RequestParam(required = false) String type) {
        return Result.ok(inventoryService.recordPage(current, size, warehouseId, productId, type));
    }

    @PostMapping("/adjust")
    @OperLog(module = "仓储作业", value = "库存调整")
    @PreAuthorize("hasAuthority('wms:inventory')")
    public Result<Void> adjust(@RequestBody @Valid AdjustForm form) {
        inventoryService.adjust(SecurityUtil.getLoginUser(), form);
        return Result.ok();
    }
}
