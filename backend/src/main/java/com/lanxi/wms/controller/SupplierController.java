package com.lanxi.wms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.entity.Supplier;
import com.lanxi.wms.service.SupplierService;
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
 * 供应商管理接口
 */
@RestController
@RequestMapping("/api/base/supplier")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping("/page")
    public Result<Page<Supplier>> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) String keyword) {
        return Result.ok(supplierService.page(current, size, keyword));
    }

    @PostMapping
    @OperLog(module = "基础数据", value = "新增供应商")
    @PreAuthorize("hasAuthority('base:supplier')")
    public Result<Void> save(@RequestBody Supplier supplier) {
        supplierService.save(supplier);
        return Result.ok();
    }

    @PutMapping
    @OperLog(module = "基础数据", value = "修改供应商")
    @PreAuthorize("hasAuthority('base:supplier')")
    public Result<Void> update(@RequestBody Supplier supplier) {
        supplierService.updateById(supplier);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @OperLog(module = "基础数据", value = "删除供应商")
    @PreAuthorize("hasAuthority('base:supplier')")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.removeById(id);
        return Result.ok();
    }
}
