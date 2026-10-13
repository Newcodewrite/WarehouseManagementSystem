package com.lanxi.wms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.entity.Customer;
import com.lanxi.wms.mapper.CustomerMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 客户服务
 */
@Service
public class CustomerService extends ServiceImpl<CustomerMapper, Customer> {

    public Page<Customer> page(long current, long size, String keyword) {
        return this.lambdaQuery()
                .and(StringUtils.hasText(keyword),
                        w -> w.like(Customer::getCode, keyword).or().like(Customer::getName, keyword))
                .orderByDesc(Customer::getId)
                .page(new Page<>(current, size));
    }
}
