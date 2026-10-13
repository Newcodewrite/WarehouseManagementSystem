package com.lanxi.wms.controller;

import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.entity.Location;
import com.lanxi.wms.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 库位管理接口
 */
@RestController
@RequestMapping("/api/base/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) Long warehouseId,
                                            @RequestParam(required = false) String zone,
                                            @RequestParam(required = false) String keyword) {
        return Result.ok(locationService.pageData(current, size, warehouseId, zone, keyword));
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam Long warehouseId) {
        return Result.ok(locationService.listByWarehouse(warehouseId));
    }

    @PostMapping
    @OperLog(module = "基础数据", value = "新增库位")
    @PreAuthorize("hasAuthority('base:location')")
    public Result<Void> create(@RequestBody Location form) {
        locationService.create(form);
        return Result.ok();
    }

    @PutMapping
    @OperLog(module = "基础数据", value = "修改库位")
    @PreAuthorize("hasAuthority('base:location')")
    public Result<Void> update(@RequestBody Location form) {
        locationService.update(form);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @OperLog(module = "基础数据", value = "删除库位")
    @PreAuthorize("hasAuthority('base:location')")
    public Result<Void> delete(@PathVariable Long id) {
        locationService.delete(id);
        return Result.ok();
    }
}
