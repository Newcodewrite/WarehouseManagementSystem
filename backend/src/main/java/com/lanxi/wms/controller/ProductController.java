package com.lanxi.wms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.entity.Product;
import com.lanxi.wms.service.ProductService;
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
 * 商品管理接口
 */
@RestController
@RequestMapping("/api/base/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/page")
    public Result<Page<Product>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer status) {
        return Result.ok(productService.page(current, size, keyword, status));
    }

    @GetMapping("/list")
    public Result<List<Product>> list() {
        return Result.ok(productService.lambdaQuery()
                .eq(Product::getStatus, 1)
                .orderByAsc(Product::getId)
                .list());
    }

    @PostMapping
    @OperLog(module = "基础数据", value = "新增商品")
    @PreAuthorize("hasAuthority('base:product')")
    public Result<Void> save(@RequestBody Product product) {
        productService.save(product);
        return Result.ok();
    }

    @PutMapping
    @OperLog(module = "基础数据", value = "修改商品")
    @PreAuthorize("hasAuthority('base:product')")
    public Result<Void> update(@RequestBody Product product) {
        productService.updateById(product);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @OperLog(module = "基础数据", value = "删除商品")
    @PreAuthorize("hasAuthority('base:product')")
    public Result<Void> delete(@PathVariable Long id) {
        productService.removeById(id);
        return Result.ok();
    }
}
