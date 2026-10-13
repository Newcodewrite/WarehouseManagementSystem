package com.lanxi.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.common.BusinessException;
import com.lanxi.wms.dto.InboundForm;
import com.lanxi.wms.dto.ItemForm;
import com.lanxi.wms.entity.InboundItem;
import com.lanxi.wms.entity.InboundOrder;
import com.lanxi.wms.entity.Location;
import com.lanxi.wms.entity.Product;
import com.lanxi.wms.entity.Supplier;
import com.lanxi.wms.entity.Warehouse;
import com.lanxi.wms.mapper.InboundItemMapper;
import com.lanxi.wms.mapper.InboundOrderMapper;
import com.lanxi.wms.mapper.ProductMapper;
import com.lanxi.wms.mapper.SupplierMapper;
import com.lanxi.wms.mapper.WarehouseMapper;
import com.lanxi.wms.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 入库服务
 */
@Service
@RequiredArgsConstructor
public class InboundService extends ServiceImpl<InboundOrderMapper, InboundOrder> {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();

    private final InboundItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final TaskService taskService;
    private final LocationService locationService;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final SupplierMapper supplierMapper;

    private String genOrderNo(String prefix) {
        return prefix + LocalDateTime.now().format(NO_FMT) + String.format("%03d", RANDOM.nextInt(1000));
    }

    /**
     * 分页查询入库单
     */
    public Map<String, Object> pageData(long current, long size, String orderNo, Integer status) {
        Page<InboundOrder> page = this.lambdaQuery()
                .like(StringUtils.hasText(orderNo), InboundOrder::getOrderNo, orderNo)
                .eq(status != null, InboundOrder::getStatus, status)
                .orderByDesc(InboundOrder::getId)
                .page(new Page<>(current, size));

        Map<Long, Warehouse> wMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        Map<Long, Supplier> sMap = supplierMapper.selectList(null).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));

        List<Long> orderIds = page.getRecords().stream().map(InboundOrder::getId).toList();
        Map<Long, Long> itemCnt = new HashMap<>();
        if (!orderIds.isEmpty()) {
            itemMapper.selectList(new LambdaQueryWrapper<InboundItem>().in(InboundItem::getOrderId, orderIds))
                    .forEach(i -> itemCnt.merge(i.getOrderId(), 1L, Long::sum));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (InboundOrder o : page.getRecords()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", o.getId());
            row.put("orderNo", o.getOrderNo());
            row.put("warehouseId", o.getWarehouseId());
            Warehouse w = wMap.get(o.getWarehouseId());
            row.put("warehouseName", w == null ? "-" : w.getName());
            row.put("supplierId", o.getSupplierId());
            Supplier s = o.getSupplierId() == null ? null : sMap.get(o.getSupplierId());
            row.put("supplierName", s == null ? "-" : s.getName());
            row.put("totalAmount", o.getTotalAmount());
            row.put("status", o.getStatus());
            row.put("remark", o.getRemark());
            row.put("createBy", o.getCreateBy());
            row.put("auditBy", o.getAuditBy());
            row.put("auditTime", o.getAuditTime());
            row.put("createTime", o.getCreateTime());
            row.put("itemCount", itemCnt.getOrDefault(o.getId(), 0L));
            rows.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("records", rows);
        data.put("total", page.getTotal());
        return data;
    }

    /**
     * 创建入库单（待审核）
     */
    @Transactional
    public void create(LoginUser user, InboundForm form) {
        InboundOrder order = new InboundOrder();
        order.setOrderNo(genOrderNo("BI"));
        order.setWarehouseId(form.getWarehouseId());
        order.setSupplierId(form.getSupplierId());
        order.setRemark(form.getRemark());
        order.setStatus(0);
        order.setCreateBy(user.getUsername());

        Map<Long, Product> pMap = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        BigDecimal total = BigDecimal.ZERO;
        List<InboundItem> items = new ArrayList<>();
        for (ItemForm f : form.getItems()) {
            Product p = pMap.get(f.getProductId());
            if (p == null) {
                throw new BusinessException("商品不存在：" + f.getProductId());
            }
            BigDecimal price = f.getPrice() != null ? f.getPrice() : p.getPrice();
            total = total.add(price.multiply(BigDecimal.valueOf(f.getQuantity())));
            InboundItem item = new InboundItem();
            item.setProductId(f.getProductId());
            item.setQuantity(f.getQuantity());
            item.setPrice(price);
            items.add(item);
        }
        order.setTotalAmount(total);
        this.save(order);
        for (InboundItem item : items) {
            item.setOrderId(order.getId());
            itemMapper.insert(item);
        }
    }

    /**
     * 入库单详情（含明细）
     */
    public Map<String, Object> detail(Long id) {
        InboundOrder o = this.getById(id);
        if (o == null) {
            throw new BusinessException("入库单不存在");
        }
        Map<Long, Product> pMap = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Warehouse w = warehouseMapper.selectById(o.getWarehouseId());
        Supplier s = o.getSupplierId() == null ? null : supplierMapper.selectById(o.getSupplierId());

        Map<String, Object> data = new HashMap<>();
        data.put("id", o.getId());
        data.put("orderNo", o.getOrderNo());
        data.put("warehouseId", o.getWarehouseId());
        data.put("warehouseName", w == null ? "-" : w.getName());
        data.put("supplierId", o.getSupplierId());
        data.put("supplierName", s == null ? "-" : s.getName());
        data.put("totalAmount", o.getTotalAmount());
        data.put("status", o.getStatus());
        data.put("remark", o.getRemark());
        data.put("createBy", o.getCreateBy());
        data.put("auditBy", o.getAuditBy());
        data.put("auditTime", o.getAuditTime());
        data.put("createTime", o.getCreateTime());

        List<Map<String, Object>> items = new ArrayList<>();
        for (InboundItem item : itemMapper.selectList(
                new LambdaQueryWrapper<InboundItem>().eq(InboundItem::getOrderId, id))) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", item.getId());
            row.put("productId", item.getProductId());
            Product p = pMap.get(item.getProductId());
            row.put("productCode", p == null ? "-" : p.getCode());
            row.put("productName", p == null ? "-" : p.getName());
            row.put("unit", p == null ? "" : p.getUnit());
            row.put("quantity", item.getQuantity());
            row.put("price", item.getPrice());
            items.add(row);
        }
        data.put("items", items);
        return data;
    }

    /**
     * 审核：通过则自动增加对应仓库商品库存
     */
    @Transactional
    public void audit(Long id, boolean approved, LoginUser operator) {
        InboundOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("入库单不存在");
        }
        if (order.getStatus() != 0) {
            throw new BusinessException("该单据已处理，请勿重复审核");
        }
        if (approved) {
            List<InboundItem> items = itemMapper.selectList(
                    new LambdaQueryWrapper<InboundItem>().eq(InboundItem::getOrderId, id));
            // 智能推荐上架库位（整单使用同一推荐库位）
            Location target = null;
            try {
                target = locationService.recommend(order.getWarehouseId());
            } catch (Exception ignore) {
                // 未配置库位时不阻塞入库
            }
            StringBuilder content = new StringBuilder();
            for (InboundItem item : items) {
                inventoryService.changeStock(order.getWarehouseId(), item.getProductId(), item.getQuantity(),
                        "IN", order.getId(), "入库单：" + order.getOrderNo(), operator.getUsername());
                Product p = productMapper.selectById(item.getProductId());
                if (target != null) {
                    item.setLocationId(target.getId());
                    itemMapper.updateById(item);
                }
                content.append(p == null ? item.getProductId() : p.getName())
                        .append("×").append(item.getQuantity()).append(" ");
            }
            // 自动生成上架任务
            taskService.create(1, "INBOUND", order.getId(), order.getWarehouseId(),
                    target == null ? null : target.getId(),
                    "入库上架：" + order.getOrderNo() + "（推荐库位："
                            + (target == null ? "待分配" : target.getCode()) + "）" + content,
                    operator.getUsername());
            order.setStatus(1);
        } else {
            order.setStatus(2);
        }
        order.setAuditBy(operator.getUsername());
        order.setAuditTime(LocalDateTime.now());
        this.updateById(order);
    }

    /**
     * 删除（仅待审核）
     */
    @Transactional
    public void delete(Long id) {
        InboundOrder order = this.getById(id);
        if (order == null) {
            return;
        }
        if (order.getStatus() != 0) {
            throw new BusinessException("仅待审核单据可删除");
        }
        itemMapper.delete(new LambdaQueryWrapper<InboundItem>().eq(InboundItem::getOrderId, id));
        this.removeById(id);
    }
}
