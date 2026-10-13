package com.lanxi.wms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.entity.Warehouse;
import com.lanxi.wms.service.WarehouseService;
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
 * 仓库管理接口
 */
@RestController
@RequestMapping("/api/base/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping("/page")
    public Result<Page<Warehouse>> page(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Integer status) {
        return Result.ok(warehouseService.page(current, size, keyword, status));
    }

    @GetMapping("/list")
    public Result<List<Warehouse>> list() {
        return Result.ok(warehouseService.lambdaQuery()
                .eq(Warehouse::getStatus, 1)
                .orderByAsc(Warehouse::getId)
                .list());
    }

    @PostMapping
    @OperLog(module = "基础数据", value = "新增仓库")
    @PreAuthorize("hasAuthority('base:warehouse')")
    public Result<Void> save(@RequestBody Warehouse warehouse) {
        warehouseService.save(warehouse);
        return Result.ok();
    }

    @PutMapping
    @OperLog(module = "基础数据", value = "修改仓库")
    @PreAuthorize("hasAuthority('base:warehouse')")
    public Result<Void> update(@RequestBody Warehouse warehouse) {
        warehouseService.updateById(warehouse);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @OperLog(module = "基础数据", value = "删除仓库")
    @PreAuthorize("hasAuthority('base:warehouse')")
    public Result<Void> delete(@PathVariable Long id) {
        warehouseService.removeById(id);
        return Result.ok();
    }
}
