package com.lanxi.wms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.entity.Vehicle;
import com.lanxi.wms.mapper.VehicleMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 车辆服务
 */
@Service
public class VehicleService extends ServiceImpl<VehicleMapper, Vehicle> {

    public Page<Vehicle> page(long current, long size, String keyword, Integer status) {
        return this.lambdaQuery()
                .and(StringUtils.hasText(keyword),
                        w -> w.like(Vehicle::getCode, keyword).or().like(Vehicle::getDriverName, keyword))
                .eq(status != null, Vehicle::getStatus, status)
                .orderByDesc(Vehicle::getId)
                .page(new Page<>(current, size));
    }
}
