package com.lanxi.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.common.BusinessException;
import com.lanxi.wms.entity.*;
import com.lanxi.wms.mapper.CustomerMapper;
import com.lanxi.wms.mapper.ProductMapper;
import com.lanxi.wms.mapper.ReturnItemMapper;
import com.lanxi.wms.mapper.ReturnOrderMapper;
import com.lanxi.wms.mapper.WarehouseMapper;
import com.lanxi.wms.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 退货服务（逆向物流）：建单 → 收货 → 质检分级 → 可售回库/报废隔离
 */
@Service
@RequiredArgsConstructor
public class ReturnService extends ServiceImpl<ReturnOrderMapper, ReturnOrder> {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();

    private final ReturnItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final TaskService taskService;
    private final LocationService locationService;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final CustomerMapper customerMapper;

    public static String statusName(Integer status) {
        if (status == null) return "-";
        return switch (status) {
            case 0 -> "待收货";
            case 1 -> "待质检";
            case 2 -> "已完成";
            case 3 -> "已取消";
            default -> "未知";
        };
    }

    public static String gradeName(Integer grade) {
        if (grade == null) return "未质检";
        return switch (grade) {
            case 1 -> "可二次销售";
            case 2 -> "需翻新";
            case 3 -> "报废";
            default -> "未知";
        };
    }

    /**
     * 创建退货单（待收货）
     * form: warehouseId, customerId, remark, items:[{productId, quantity}]
     */
    @Transactional
    public Long create(LoginUser user, Map<String, Object> form) {
        Long warehouseId = Long.valueOf(form.get("warehouseId").toString());
        Long customerId = form.get("customerId") == null ? null : Long.valueOf(form.get("customerId").toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) form.get("items");
        if (items == null || items.isEmpty()) {
            throw new BusinessException("退货明细不能为空");
        }

        ReturnOrder order = new ReturnOrder();
        order.setOrderNo("RT" + LocalDateTime.now().format(NO_FMT) + String.format("%03d", RANDOM.nextInt(1000)));
        order.setWarehouseId(warehouseId);
        order.setCustomerId(customerId);
        order.setStatus(0);
        order.setRemark(form.get("remark") == null ? null : form.get("remark").toString());
        order.setCreateBy(user.getUsername());
        int total = 0;
        this.save(order);

        for (Map<String, Object> f : items) {
            Long productId = Long.valueOf(f.get("productId").toString());
            Integer qty = Integer.valueOf(f.get("quantity").toString());
            if (qty <= 0) {
                throw new BusinessException("退货数量必须大于0");
            }
            ReturnItem item = new ReturnItem();
            item.setReturnId(order.getId());
            item.setProductId(productId);
            item.setQuantity(qty);
            itemMapper.insert(item);
            total += qty;
        }
        order.setTotalQty(total);
        this.updateById(order);
        return order.getId();
    }

    /**
     * 分页查询
     */
    public Map<String, Object> pageData(long current, long size, String orderNo, Integer status) {
        Page<ReturnOrder> page = this.lambdaQuery()
                .like(orderNo != null && !orderNo.isBlank(), ReturnOrder::getOrderNo, orderNo)
                .eq(status != null, ReturnOrder::getStatus, status)
                .orderByDesc(ReturnOrder::getId)
                .page(new Page<>(current, size));
        Map<Long, String> wMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        Map<Long, String> cMap = customerMapper.selectList(null).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ReturnOrder o : page.getRecords()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", o.getId());
            row.put("orderNo", o.getOrderNo());
            row.put("warehouseId", o.getWarehouseId());
            row.put("warehouseName", wMap.getOrDefault(o.getWarehouseId(), "-"));
            row.put("customerId", o.getCustomerId());
            row.put("customerName", o.getCustomerId() == null ? "-" : cMap.getOrDefault(o.getCustomerId(), "-"));
            row.put("status", o.getStatus());
            row.put("statusName", statusName(o.getStatus()));
            row.put("totalQty", o.getTotalQty());
            row.put("resaleQty", o.getResaleQty());
            row.put("scrapQty", o.getScrapQty());
            row.put("remark", o.getRemark());
            row.put("createBy", o.getCreateBy());
            row.put("auditBy", o.getAuditBy());
            row.put("auditTime", o.getAuditTime());
            row.put("createTime", o.getCreateTime());
            rows.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("records", rows);
        data.put("total", page.getTotal());
        return data;
    }

    /**
     * 详情（含明细与质检结果）
     */
    public Map<String, Object> detail(Long id) {
        ReturnOrder o = this.getById(id);
        if (o == null) {
            throw new BusinessException("退货单不存在");
        }
        Warehouse w = warehouseMapper.selectById(o.getWarehouseId());
        Customer c = o.getCustomerId() == null ? null : customerMapper.selectById(o.getCustomerId());
        Map<Long, Product> pMap = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<String, Object> data = new HashMap<>();
        data.put("id", o.getId());
        data.put("orderNo", o.getOrderNo());
        data.put("warehouseId", o.getWarehouseId());
        data.put("warehouseName", w == null ? "-" : w.getName());
        data.put("customerId", o.getCustomerId());
        data.put("customerName", c == null ? "-" : c.getName());
        data.put("status", o.getStatus());
        data.put("statusName", statusName(o.getStatus()));
        data.put("totalQty", o.getTotalQty());
        data.put("resaleQty", o.getResaleQty());
        data.put("scrapQty", o.getScrapQty());
        data.put("remark", o.getRemark());
        data.put("createBy", o.getCreateBy());
        data.put("createTime", o.getCreateTime());

        List<Map<String, Object>> items = new ArrayList<>();
        for (ReturnItem it : itemMapper.selectList(
                new LambdaQueryWrapper<ReturnItem>().eq(ReturnItem::getReturnId, id))) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", it.getId());
            row.put("productId", it.getProductId());
            Product p = pMap.get(it.getProductId());
            row.put("productCode", p == null ? "-" : p.getCode());
            row.put("productName", p == null ? "-" : p.getName());
            row.put("unit", p == null ? "" : p.getUnit());
            row.put("quantity", it.getQuantity());
            row.put("qcGrade", it.getQcGrade());
            row.put("qcGradeName", gradeName(it.getQcGrade()));
            row.put("qcRemark", it.getQcRemark());
            items.add(row);
        }
        data.put("items", items);
        return data;
    }

    /**
     * 收货确认：待收货 → 待质检，并生成退货质检任务
     */
    @Transactional
    public void receive(Long id, LoginUser user) {
        ReturnOrder o = mustGet(id);
        if (o.getStatus() != 0) {
            throw new BusinessException("仅待收货退货单可确认收货");
        }
        o.setStatus(1);
        this.updateById(o);
        taskService.create(6, "RETURN", o.getId(), o.getWarehouseId(),
                locationService.defectLocation(o.getWarehouseId()).getId(),
                "退货质检：" + o.getOrderNo() + "，共 " + o.getTotalQty() + " 件待分级",
                user.getUsername());
    }

    /**
     * 质检分级并完成处理
     * grades: [{itemId, qcGrade(1可售/2翻新/3报废), qcRemark}]
     */
    @Transactional
    public void qcAndComplete(Long id, List<Map<String, Object>> grades, LoginUser user) {
        ReturnOrder o = mustGet(id);
        if (o.getStatus() != 1) {
            throw new BusinessException("仅待质检退货单可录入质检结果");
        }
        List<ReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ReturnItem>().eq(ReturnItem::getReturnId, id));
        Map<Long, ReturnItem> itemMap = items.stream()
                .collect(Collectors.toMap(ReturnItem::getId, Function.identity()));

        int resale = 0, scrap = 0;
        for (Map<String, Object> g : grades) {
            Long itemId = Long.valueOf(g.get("itemId").toString());
            Integer grade = Integer.valueOf(g.get("qcGrade").toString());
            ReturnItem item = itemMap.get(itemId);
            if (item == null) {
                continue;
            }
            item.setQcGrade(grade);
            item.setQcRemark(g.get("qcRemark") == null ? null : g.get("qcRemark").toString());
            itemMapper.updateById(item);
            if (grade == 1) resale += item.getQuantity();
            else if (grade == 3) scrap += item.getQuantity();
        }

        // 存在未质检明细则不允许完成
        boolean allGraded = items.stream().allMatch(i -> i.getQcGrade() != null);
        if (!allGraded) {
            throw new BusinessException("尚有退货明细未完成质检分级");
        }

        // 可二次销售品回库（记退货入流水），报废品移残次区
        Location defect = locationService.defectLocation(o.getWarehouseId());
        StringBuilder putaway = new StringBuilder();
        for (ReturnItem item : items) {
            if (item.getQcGrade() == 1) {
                inventoryService.changeStock(o.getWarehouseId(), item.getProductId(), item.getQuantity(),
                        "RETURN", o.getId(), "退货可售回库：" + o.getOrderNo(), user.getUsername());
                Product p = productMapper.selectById(item.getProductId());
                putaway.append(p == null ? item.getProductId() : p.getName()).append("×").append(item.getQuantity()).append(" ");
            }
        }
        if (resale > 0) {
            taskService.create(1, "RETURN", o.getId(), o.getWarehouseId(),
                    locationService.recommend(o.getWarehouseId()).getId(),
                    "退货可售商品上架：" + putaway, user.getUsername());
        }
        if (scrap > 0) {
            taskService.create(4, "RETURN", o.getId(), o.getWarehouseId(), defect.getId(),
                    "退货报废品移至残次区：" + o.getOrderNo() + "，共 " + scrap + " 件", user.getUsername());
        }

        o.setStatus(2);
        o.setResaleQty(resale);
        o.setScrapQty(scrap);
        o.setAuditBy(user.getUsername());
        o.setAuditTime(LocalDateTime.now());
        this.updateById(o);

        // 完成退货质检任务
        taskService.lambdaUpdate()
                .eq(WmsTask::getRefType, "RETURN")
                .eq(WmsTask::getRefId, id)
                .eq(WmsTask::getType, 6)
                .ne(WmsTask::getStatus, 4)
                .set(WmsTask::getStatus, 3)
                .set(WmsTask::getFinishTime, LocalDateTime.now())
                .update();
    }

    /**
     * 取消
     */
    @Transactional
    public void cancel(Long id) {
        ReturnOrder o = mustGet(id);
        if (o.getStatus() == 2) {
            throw new BusinessException("已完成退货单不可取消");
        }
        o.setStatus(3);
        this.updateById(o);
    }

    private ReturnOrder mustGet(Long id) {
        ReturnOrder o = this.getById(id);
        if (o == null) {
            throw new BusinessException("退货单不存在");
        }
        return o;
    }
}
