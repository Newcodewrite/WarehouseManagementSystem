package com.lanxi.wms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.entity.Supplier;
import com.lanxi.wms.mapper.SupplierMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 供应商服务
 */
@Service
public class SupplierService extends ServiceImpl<SupplierMapper, Supplier> {

    public Page<Supplier> page(long current, long size, String keyword) {
        return this.lambdaQuery()
                .and(StringUtils.hasText(keyword),
                        w -> w.like(Supplier::getCode, keyword).or().like(Supplier::getName, keyword))
                .orderByDesc(Supplier::getId)
                .page(new Page<>(current, size));
    }
}
