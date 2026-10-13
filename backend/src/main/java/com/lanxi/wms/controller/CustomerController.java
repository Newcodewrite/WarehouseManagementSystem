package com.lanxi.wms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.entity.Customer;
import com.lanxi.wms.service.CustomerService;
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
 * 客户管理接口
 */
@RestController
@RequestMapping("/api/base/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/page")
    public Result<Page<Customer>> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) String keyword) {
        return Result.ok(customerService.page(current, size, keyword));
    }

    @PostMapping
    @OperLog(module = "基础数据", value = "新增客户")
    @PreAuthorize("hasAuthority('base:customer')")
    public Result<Void> save(@RequestBody Customer customer) {
        customerService.save(customer);
        return Result.ok();
    }

    @PutMapping
    @OperLog(module = "基础数据", value = "修改客户")
    @PreAuthorize("hasAuthority('base:customer')")
    public Result<Void> update(@RequestBody Customer customer) {
        customerService.updateById(customer);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @OperLog(module = "基础数据", value = "删除客户")
    @PreAuthorize("hasAuthority('base:customer')")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.removeById(id);
        return Result.ok();
    }
}
