package com.lanxi.wms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.entity.Vehicle;
import com.lanxi.wms.service.VehicleService;
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
 * 车辆管理接口
 */
@RestController
@RequestMapping("/api/logistics/vehicle")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping("/page")
    public Result<Page<Vehicle>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer status) {
        return Result.ok(vehicleService.page(current, size, keyword, status));
    }

    @GetMapping("/list")
    public Result<List<Vehicle>> list() {
        return Result.ok(vehicleService.lambdaQuery().orderByAsc(Vehicle::getId).list());
    }

    @PostMapping
    @OperLog(module = "物流运输", value = "新增车辆")
    @PreAuthorize("hasAuthority('logistics:vehicle')")
    public Result<Void> save(@RequestBody Vehicle vehicle) {
        vehicleService.save(vehicle);
        return Result.ok();
    }

    @PutMapping
    @OperLog(module = "物流运输", value = "修改车辆")
    @PreAuthorize("hasAuthority('logistics:vehicle')")
    public Result<Void> update(@RequestBody Vehicle vehicle) {
        vehicleService.updateById(vehicle);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @OperLog(module = "物流运输", value = "删除车辆")
    @PreAuthorize("hasAuthority('logistics:vehicle')")
    public Result<Void> delete(@PathVariable Long id) {
        vehicleService.removeById(id);
        return Result.ok();
    }
}
