package com.lanxi.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lanxi.wms.entity.InboundItem;
import com.lanxi.wms.entity.InboundOrder;
import com.lanxi.wms.entity.Product;
import com.lanxi.wms.entity.Warehouse;
import com.lanxi.wms.mapper.InboundItemMapper;
import com.lanxi.wms.mapper.InboundOrderMapper;
import com.lanxi.wms.mapper.ProductMapper;
import com.lanxi.wms.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 批次与效期服务：按入库批次台账，提供近效期预警（FEFO 支撑）
 */
@Service
@RequiredArgsConstructor
public class BatchService {

    private final InboundItemMapper inboundItemMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;

    /**
     * 批次台账列表（仅已入库单据中有批次号或到期日的明细）
     */
    public List<Map<String, Object>> batchList(Long warehouseId, Boolean nearExpiryOnly) {
        // 已审核入库单
        List<InboundOrder> orders = inboundOrderMapper.selectList(
                new LambdaQueryWrapper<InboundOrder>().eq(InboundOrder::getStatus, 1));
        Map<Long, InboundOrder> orderMap = orders.stream()
                .collect(Collectors.toMap(InboundOrder::getId, Function.identity()));
        if (orderMap.isEmpty()) {
            return List.of();
        }
        List<InboundItem> items = inboundItemMapper.selectList(
                new LambdaQueryWrapper<InboundItem>()
                        .in(InboundItem::getOrderId, orderMap.keySet())
                        .and(w -> w.isNotNull(InboundItem::getBatchNo).or().isNotNull(InboundItem::getExpiryDate)));

        Map<Long, Product> pMap = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, String> wMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));

        LocalDate now = LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InboundItem it : items) {
            InboundOrder o = orderMap.get(it.getOrderId());
            if (o == null) continue;
            if (warehouseId != null && !warehouseId.equals(o.getWarehouseId())) continue;
            long daysLeft = it.getExpiryDate() == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(now, it.getExpiryDate());
            if (Boolean.TRUE.equals(nearExpiryOnly) && (it.getExpiryDate() == null || daysLeft > 90)) {
                continue;
            }
            Product p = pMap.get(it.getProductId());
            Map<String, Object> row = new HashMap<>();
            row.put("warehouseName", wMap.getOrDefault(o.getWarehouseId(), "-"));
            row.put("productCode", p == null ? "-" : p.getCode());
            row.put("productName", p == null ? "-" : p.getName());
            row.put("batchNo", StringUtils.hasText(it.getBatchNo()) ? it.getBatchNo() : "无批次");
            row.put("quantity", it.getQuantity());
            row.put("productionDate", it.getProductionDate());
            row.put("expiryDate", it.getExpiryDate());
            row.put("daysLeft", it.getExpiryDate() == null ? null : daysLeft);
            row.put("expired", it.getExpiryDate() != null && daysLeft < 0);
            row.put("nearExpiry", it.getExpiryDate() != null && daysLeft >= 0 && daysLeft <= 90);
            row.put("inboundOrderNo", o.getOrderNo());
            rows.add(row);
        }
        rows.sort(Comparator.comparing(r -> r.get("expiryDate") == null ? LocalDate.MAX : (LocalDate) r.get("expiryDate")));
        return rows;
    }
}
