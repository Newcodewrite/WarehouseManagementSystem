package com.lanxi.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.common.BusinessException;
import com.lanxi.wms.dto.AdjustForm;
import com.lanxi.wms.entity.Inventory;
import com.lanxi.wms.entity.InventoryRecord;
import com.lanxi.wms.entity.Location;
import com.lanxi.wms.entity.Product;
import com.lanxi.wms.entity.Warehouse;
import com.lanxi.wms.mapper.InventoryMapper;
import com.lanxi.wms.mapper.InventoryRecordMapper;
import com.lanxi.wms.mapper.LocationMapper;
import com.lanxi.wms.mapper.ProductMapper;
import com.lanxi.wms.mapper.WarehouseMapper;
import com.lanxi.wms.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存服务
 */
@Service
@RequiredArgsConstructor
public class InventoryService extends ServiceImpl<InventoryMapper, Inventory> {

    private final InventoryRecordMapper recordMapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final LocationMapper locationMapper;

    /**
     * 变更库存并记录流水（delta 正数为增加，负数为减少）
     */
    @Transactional
    public void changeStock(Long warehouseId, Long productId, int delta, String type, Long orderId,
                            String remark, String operator) {
        Inventory inv = this.lambdaQuery()
                .eq(Inventory::getWarehouseId, warehouseId)
                .eq(Inventory::getProductId, productId)
                .one();
        int newQty;
        if (inv == null) {
            if (delta < 0) {
                throw new BusinessException("库存不足，无法完成出库");
            }
            inv = new Inventory();
            inv.setWarehouseId(warehouseId);
            inv.setProductId(productId);
            inv.setQuantity(delta);
            // 新库存挂到仓库默认库位
            Location def = locationMapper.selectOne(new LambdaQueryWrapper<Location>()
                    .eq(Location::getWarehouseId, warehouseId)
                    .eq(Location::getCode, "DEFAULT").last("LIMIT 1"));
            if (def != null) {
                inv.setLocationId(def.getId());
            }
            this.save(inv);
            newQty = delta;
        } else {
            newQty = inv.getQuantity() + delta;
            if (newQty < 0) {
                throw new BusinessException("库存不足，无法完成出库");
            }
            inv.setQuantity(newQty);
            this.updateById(inv);
        }
        InventoryRecord record = new InventoryRecord();
        record.setWarehouseId(warehouseId);
        record.setProductId(productId);
        record.setType(type);
        record.setQuantity(delta);
        record.setAfterQuantity(newQty);
        record.setOrderId(orderId);
        record.setRemark(remark);
        record.setCreateBy(operator);
        recordMapper.insert(record);
    }

    /**
     * 库存调整
     */
    public void adjust(LoginUser user, AdjustForm form) {
        changeStock(form.getWarehouseId(), form.getProductId(), form.getQuantity(),
                "ADJUST", null, form.getRemark(), user.getUsername());
    }

    /**
     * 分页查询库存（带商品/仓库名称）
     */
    public Map<String, Object> pageData(long current, long size, Long warehouseId, String keyword) {
        List<Long> productIds = null;
        if (StringUtils.hasText(keyword)) {
            productIds = productMapper.selectList(new LambdaQueryWrapper<Product>()
                            .like(Product::getCode, keyword).or().like(Product::getName, keyword))
                    .stream().map(Product::getId).toList();
            if (productIds.isEmpty()) {
                return Map.of("records", List.of(), "total", 0);
            }
        }
        Page<Inventory> page = this.lambdaQuery()
                .eq(warehouseId != null, Inventory::getWarehouseId, warehouseId)
                .in(productIds != null, Inventory::getProductId, productIds)
                .orderByDesc(Inventory::getUpdateTime)
                .page(new Page<>(current, size));

        Map<Long, Product> pMap = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Warehouse> wMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Inventory inv : page.getRecords()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", inv.getId());
            row.put("warehouseId", inv.getWarehouseId());
            row.put("productId", inv.getProductId());
            row.put("quantity", inv.getQuantity());
            row.put("updateTime", inv.getUpdateTime());
            Product p = pMap.get(inv.getProductId());
            if (p != null) {
                row.put("productCode", p.getCode());
                row.put("productName", p.getName());
                row.put("category", p.getCategory());
                row.put("unit", p.getUnit());
                row.put("warnStock", p.getWarnStock());
                row.put("alert", inv.getQuantity() <= p.getWarnStock());
            }
            Warehouse w = wMap.get(inv.getWarehouseId());
            row.put("warehouseName", w == null ? "-" : w.getName());
            rows.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("records", rows);
        data.put("total", page.getTotal());
        return data;
    }

    /**
     * 库存预警列表（库存 <= 商品预警值）
     */
    public List<Map<String, Object>> alertList() {
        Map<Long, Product> pMap = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Warehouse> wMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Inventory inv : this.list()) {
            Product p = pMap.get(inv.getProductId());
            if (p == null || p.getStatus() == null || p.getStatus() != 1) {
                continue;
            }
            if (inv.getQuantity() <= p.getWarnStock()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", inv.getId());
                row.put("warehouseId", inv.getWarehouseId());
                Warehouse w = wMap.get(inv.getWarehouseId());
                row.put("warehouseName", w == null ? "-" : w.getName());
                row.put("productId", inv.getProductId());
                row.put("productCode", p.getCode());
                row.put("productName", p.getName());
                row.put("category", p.getCategory());
                row.put("unit", p.getUnit());
                row.put("quantity", inv.getQuantity());
                row.put("warnStock", p.getWarnStock());
                row.put("suggestQty", Math.max(p.getWarnStock() * 2 - inv.getQuantity(), 0));
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 分页查询库存流水
     */
    public Map<String, Object> recordPage(long current, long size, Long warehouseId, Long productId, String type) {
        Page<InventoryRecord> page = recordMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<InventoryRecord>()
                        .eq(warehouseId != null, InventoryRecord::getWarehouseId, warehouseId)
                        .eq(productId != null, InventoryRecord::getProductId, productId)
                        .eq(StringUtils.hasText(type), InventoryRecord::getType, type)
                        .orderByDesc(InventoryRecord::getId));
        Map<Long, Product> pMap = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Warehouse> wMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InventoryRecord r : page.getRecords()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", r.getId());
            row.put("warehouseId", r.getWarehouseId());
            row.put("productId", r.getProductId());
            row.put("type", r.getType());
            row.put("quantity", r.getQuantity());
            row.put("afterQuantity", r.getAfterQuantity());
            row.put("orderId", r.getOrderId());
            row.put("remark", r.getRemark());
            row.put("createBy", r.getCreateBy());
            row.put("createTime", r.getCreateTime());
            Product p = pMap.get(r.getProductId());
            row.put("productName", p == null ? "-" : p.getName());
            Warehouse w = wMap.get(r.getWarehouseId());
            row.put("warehouseName", w == null ? "-" : w.getName());
            rows.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("records", rows);
        data.put("total", page.getTotal());
        return data;
    }
}
