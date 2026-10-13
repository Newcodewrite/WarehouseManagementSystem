package com.lanxi.wms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.entity.Product;
import com.lanxi.wms.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 商品服务
 */
@Service
public class ProductService extends ServiceImpl<ProductMapper, Product> {

    public Page<Product> page(long current, long size, String keyword, Integer status) {
        return this.lambdaQuery()
                .and(StringUtils.hasText(keyword),
                        w -> w.like(Product::getCode, keyword).or().like(Product::getName, keyword))
                .eq(status != null, Product::getStatus, status)
                .orderByDesc(Product::getId)
                .page(new Page<>(current, size));
    }
}
