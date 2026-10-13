package com.lanxi.wms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.entity.Warehouse;
import com.lanxi.wms.mapper.WarehouseMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 仓库服务
 */
@Service
public class WarehouseService extends ServiceImpl<WarehouseMapper, Warehouse> {

    public Page<Warehouse> page(long current, long size, String keyword, Integer status) {
        return this.lambdaQuery()
                .and(StringUtils.hasText(keyword),
                        w -> w.like(Warehouse::getCode, keyword).or().like(Warehouse::getName, keyword))
                .eq(status != null, Warehouse::getStatus, status)
                .orderByDesc(Warehouse::getId)
                .page(new Page<>(current, size));
    }
}
