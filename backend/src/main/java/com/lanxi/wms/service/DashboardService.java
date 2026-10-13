package com.lanxi.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lanxi.wms.entity.InboundOrder;
import com.lanxi.wms.entity.Inventory;
import com.lanxi.wms.entity.InventoryRecord;
import com.lanxi.wms.entity.LogisticsOrder;
import com.lanxi.wms.entity.OutboundOrder;
import com.lanxi.wms.entity.Product;
import com.lanxi.wms.entity.Stocktake;
import com.lanxi.wms.entity.Vehicle;
import com.lanxi.wms.mapper.CustomerMapper;
import com.lanxi.wms.mapper.InboundOrderMapper;
import com.lanxi.wms.mapper.InventoryMapper;
import com.lanxi.wms.mapper.InventoryRecordMapper;
import com.lanxi.wms.mapper.LogisticsOrderMapper;
import com.lanxi.wms.mapper.OutboundOrderMapper;
import com.lanxi.wms.mapper.ProductMapper;
import com.lanxi.wms.mapper.StocktakeMapper;
import com.lanxi.wms.mapper.SupplierMapper;
import com.lanxi.wms.mapper.VehicleMapper;
import com.lanxi.wms.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 驾驶舱统计服务
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final SupplierMapper supplierMapper;
    private final CustomerMapper customerMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final LogisticsOrderMapper logisticsOrderMapper;
    private final VehicleMapper vehicleMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryRecordMapper recordMapper;
    private final StocktakeMapper stocktakeMapper;

    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("productCount", productMapper.selectCount(null));
        m.put("warehouseCount", warehouseMapper.selectCount(null));
        m.put("supplierCount", supplierMapper.selectCount(null));
        m.put("customerCount", customerMapper.selectCount(null));
        m.put("inboundPending", inboundOrderMapper.selectCount(
                new LambdaQueryWrapper<InboundOrder>().eq(InboundOrder::getStatus, 0)));
        m.put("outboundPending", outboundOrderMapper.selectCount(
                new LambdaQueryWrapper<OutboundOrder>().eq(OutboundOrder::getStatus, 0)));
        m.put("orderTransit", logisticsOrderMapper.selectCount(
                new LambdaQueryWrapper<LogisticsOrder>().eq(LogisticsOrder::getStatus, 1)));
        m.put("vehicleIdle", vehicleMapper.selectCount(
                new LambdaQueryWrapper<Vehicle>().eq(Vehicle::getStatus, 0)));

        Map<Long, Product> pMap = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        long totalQty = 0;
        BigDecimal totalValue = BigDecimal.ZERO;
        int alertCount = 0;
        for (Inventory inv : inventoryMapper.selectList(null)) {
            totalQty += inv.getQuantity();
            Product p = pMap.get(inv.getProductId());
            if (p != null && p.getPrice() != null) {
                totalValue = totalValue.add(p.getPrice().multiply(BigDecimal.valueOf(inv.getQuantity())));
            }
            if (p != null && inv.getQuantity() <= p.getWarnStock()) {
                alertCount++;
            }
        }
        m.put("inventoryTotal", totalQty);
        m.put("inventoryValue", totalValue);
        m.put("alertCount", alertCount);
        return m;
    }

    /**
     * 近 N 天出入库趋势
     */
    public Map<String, Object> trend(int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        List<InventoryRecord> records = recordMapper.selectList(new LambdaQueryWrapper<InventoryRecord>()
                .ge(InventoryRecord::getCreateTime, start.atStartOfDay())
                .in(InventoryRecord::getType, List.of("IN", "OUT")));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        List<String> labels = new ArrayList<>();
        List<Long> inData = new ArrayList<>();
        List<Long> outData = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = start.plusDays(i);
            LocalDate next = date.plusDays(1);
            long in = 0;
            long out = 0;
            for (InventoryRecord r : records) {
                if (r.getCreateTime() != null
                        && !r.getCreateTime().toLocalDate().isBefore(date)
                        && r.getCreateTime().toLocalDate().isBefore(next)) {
                    if ("IN".equals(r.getType())) {
                        in += r.getQuantity();
                    } else {
                        out += -r.getQuantity();
                    }
                }
            }
            labels.add(date.format(fmt));
            inData.add(in);
            outData.add(out);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("labels", labels);
        m.put("inData", inData);
        m.put("outData", outData);
        return m;
    }

    /**
     * 仓储 KPI：周转率 / 库龄分布 / 呆滞库存 / 入库及时率 / 盘点准确率
     */
    public Map<String, Object> kpi() {
        Map<String, Object> m = new HashMap<>();
        Map<Long, Product> pMap = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Inventory> inventories = inventoryMapper.selectList(null);

        // 当前总库存
        long stockQty = inventories.stream().mapToLong(Inventory::getQuantity).sum();

        // 近30天出库总量
        LocalDateTime since30 = LocalDateTime.now().minusDays(30);
        long out30 = recordMapper.selectList(new LambdaQueryWrapper<InventoryRecord>()
                        .eq(InventoryRecord::getType, "OUT")
                        .ge(InventoryRecord::getCreateTime, since30)).stream()
                .mapToLong(r -> -r.getQuantity()).sum();
        // 累计出库总量
        long outAll = recordMapper.selectList(new LambdaQueryWrapper<InventoryRecord>()
                        .eq(InventoryRecord::getType, "OUT")).stream()
                .mapToLong(r -> -r.getQuantity()).sum();
        double turnover30 = stockQty > 0 ? (double) out30 / stockQty : 0;
        m.put("turnoverRate30", BigDecimal.valueOf(turnover30).setScale(2, RoundingMode.HALF_UP));
        m.put("outQty30", out30);
        m.put("outQtyAll", outAll);

        // 库龄分布（按库存最近变动时间近似）
        LocalDate now = LocalDate.now();
        long[] ageBuckets = new long[4]; // 0-30 / 31-90 / 91-180 / 180+
        for (Inventory inv : inventories) {
            LocalDateTime t = inv.getUpdateTime() != null ? inv.getUpdateTime() : inv.getCreateTime();
            long days = t == null ? 181 : java.time.Duration.between(t, LocalDateTime.now()).toDays();
            if (days <= 30) ageBuckets[0] += inv.getQuantity();
            else if (days <= 90) ageBuckets[1] += inv.getQuantity();
            else if (days <= 180) ageBuckets[2] += inv.getQuantity();
            else ageBuckets[3] += inv.getQuantity();
        }
        List<Map<String, Object>> ageDist = new ArrayList<>();
        String[] ageLabels = {"0-30天", "31-90天", "91-180天", "180天以上"};
        for (int i = 0; i < 4; i++) {
            Map<String, Object> b = new HashMap<>();
            b.put("name", ageLabels[i]);
            b.put("value", ageBuckets[i]);
            ageDist.add(b);
        }
        m.put("ageDistribution", ageDist);

        // 呆滞库存：近180天无出库记录且有库存的商品
        LocalDateTime since180 = LocalDateTime.now().minusDays(180);
        Set<Long> activeProducts = recordMapper.selectList(new LambdaQueryWrapper<InventoryRecord>()
                        .eq(InventoryRecord::getType, "OUT")
                        .ge(InventoryRecord::getCreateTime, since180)).stream()
                .map(InventoryRecord::getProductId).collect(Collectors.toSet());
        List<Map<String, Object>> dormant = new ArrayList<>();
        for (Inventory inv : inventories) {
            if (inv.getQuantity() <= 0 || activeProducts.contains(inv.getProductId())) {
                continue;
            }
            Product p = pMap.get(inv.getProductId());
            if (p == null) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("productCode", p.getCode());
            row.put("productName", p.getName());
            row.put("quantity", inv.getQuantity());
            BigDecimal value = p.getPrice() == null ? BigDecimal.ZERO
                    : p.getPrice().multiply(BigDecimal.valueOf(inv.getQuantity()));
            row.put("stockValue", value);
            dormant.add(row);
        }
        dormant.sort((a, b) -> ((BigDecimal) b.get("stockValue")).compareTo((BigDecimal) a.get("stockValue")));
        m.put("dormantTop10", dormant.size() > 10 ? dormant.subList(0, 10) : dormant);

        // 入库及时率：审核时间-创建时间 <= 24h 的占比
        List<InboundOrder> inOrders = inboundOrderMapper.selectList(
                new LambdaQueryWrapper<InboundOrder>().eq(InboundOrder::getStatus, 1));
        long onTime = inOrders.stream().filter(o -> o.getAuditTime() != null && o.getCreateTime() != null
                && !o.getAuditTime().isAfter(o.getCreateTime().plusHours(24))).count();
        double onTimeRate = inOrders.isEmpty() ? 100 : (double) onTime / inOrders.size() * 100;
        m.put("inboundOnTimeRate", BigDecimal.valueOf(onTimeRate).setScale(1, RoundingMode.HALF_UP));
        m.put("inboundTotal", inOrders.size());

        // 盘点准确率：1 - 总差异/总账面
        List<Stocktake> finished = stocktakeMapper.selectList(
                new LambdaQueryWrapper<Stocktake>().eq(Stocktake::getStatus, 3));
        long totalDiff = finished.stream().mapToLong(s -> (long) s.getGainQty() + s.getLossQty()).sum();
        long countItems = finished.stream().mapToLong(Stocktake::getTotalCount).sum();
        double accuracy = countItems == 0 ? 100 : Math.max(0, 100 - (double) totalDiff / Math.max(countItems, 1) * 100);
        m.put("stocktakeAccuracy", BigDecimal.valueOf(accuracy).setScale(1, RoundingMode.HALF_UP));
        m.put("stocktakeCount", finished.size());

        return m;
    }
}
