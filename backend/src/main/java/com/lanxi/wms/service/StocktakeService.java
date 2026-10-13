package com.lanxi.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.common.BusinessException;
import com.lanxi.wms.entity.*;
import com.lanxi.wms.mapper.ProductMapper;
import com.lanxi.wms.mapper.StocktakeItemMapper;
import com.lanxi.wms.mapper.StocktakeMapper;
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
 * 盘点服务：创建快照 → 录入实盘 → 审核（盘盈盘亏自动调库存）
 */
@Service
@RequiredArgsConstructor
public class StocktakeService extends ServiceImpl<StocktakeMapper, Stocktake> {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();

    private final StocktakeItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final TaskService taskService;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;

    /**
     * 创建盘点单（快照当前库存为账面数量）
     */
    @Transactional
    public Long create(LoginUser user, Long warehouseId, Integer type, String remark) {
        if (warehouseId == null) {
            throw new BusinessException("请选择盘点仓库");
        }
        List<Inventory> invs = inventoryService.lambdaQuery()
                .eq(Inventory::getWarehouseId, warehouseId).list();

        Stocktake order = new Stocktake();
        order.setOrderNo("PD" + LocalDateTime.now().format(NO_FMT) + String.format("%03d", RANDOM.nextInt(1000)));
        order.setWarehouseId(warehouseId);
        order.setType(type == null ? 1 : type);
        order.setStatus(0);
        order.setRemark(remark);
        order.setCreateBy(user.getUsername());
        order.setTotalCount(invs.size());
        this.save(order);

        for (Inventory inv : invs) {
            StocktakeItem item = new StocktakeItem();
            item.setStocktakeId(order.getId());
            item.setProductId(inv.getProductId());
            item.setLocationId(inv.getLocationId());
            item.setBookQty(inv.getQuantity());
            item.setActualQty(null);
            item.setDiffQty(0);
            itemMapper.insert(item);
        }

        // 生成盘点任务
        taskService.create(3, "STOCKTAKE", order.getId(), warehouseId, null,
                "盘点任务：" + (order.getType() == 2 ? "循环盘点" : "全面盘点") + "，共 " + invs.size() + " 项",
                user.getUsername());
        return order.getId();
    }

    /**
     * 分页查询
     */
    public Map<String, Object> pageData(long current, long size, String orderNo, Integer status) {
        Page<Stocktake> page = this.lambdaQuery()
                .like(orderNo != null && !orderNo.isBlank(), Stocktake::getOrderNo, orderNo)
                .eq(status != null, Stocktake::getStatus, status)
                .orderByDesc(Stocktake::getId)
                .page(new Page<>(current, size));
        Map<Long, String> wMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Stocktake o : page.getRecords()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", o.getId());
            row.put("orderNo", o.getOrderNo());
            row.put("warehouseId", o.getWarehouseId());
            row.put("warehouseName", wMap.getOrDefault(o.getWarehouseId(), "-"));
            row.put("type", o.getType());
            row.put("typeName", o.getType() == 2 ? "循环盘点" : "全面盘点");
            row.put("status", o.getStatus());
            row.put("statusName", statusName(o.getStatus()));
            row.put("totalCount", o.getTotalCount());
            row.put("gainQty", o.getGainQty());
            row.put("lossQty", o.getLossQty());
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

    public static String statusName(Integer status) {
        if (status == null) return "-";
        return switch (status) {
            case 0 -> "待盘点";
            case 1 -> "盘点中";
            case 2 -> "待审核";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "未知";
        };
    }

    /**
     * 盘点单详情（含明细）
     */
    public Map<String, Object> detail(Long id) {
        Stocktake o = this.getById(id);
        if (o == null) {
            throw new BusinessException("盘点单不存在");
        }
        Warehouse w = warehouseMapper.selectById(o.getWarehouseId());
        Map<Long, Product> pMap = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<String, Object> data = new HashMap<>();
        data.put("id", o.getId());
        data.put("orderNo", o.getOrderNo());
        data.put("warehouseId", o.getWarehouseId());
        data.put("warehouseName", w == null ? "-" : w.getName());
        data.put("type", o.getType());
        data.put("status", o.getStatus());
        data.put("statusName", statusName(o.getStatus()));
        data.put("totalCount", o.getTotalCount());
        data.put("gainQty", o.getGainQty());
        data.put("lossQty", o.getLossQty());
        data.put("remark", o.getRemark());
        data.put("createBy", o.getCreateBy());
        data.put("createTime", o.getCreateTime());

        List<Map<String, Object>> items = new ArrayList<>();
        for (StocktakeItem it : itemMapper.selectList(
                new LambdaQueryWrapper<StocktakeItem>().eq(StocktakeItem::getStocktakeId, id))) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", it.getId());
            row.put("productId", it.getProductId());
            Product p = pMap.get(it.getProductId());
            row.put("productCode", p == null ? "-" : p.getCode());
            row.put("productName", p == null ? "-" : p.getName());
            row.put("unit", p == null ? "" : p.getUnit());
            row.put("bookQty", it.getBookQty());
            row.put("actualQty", it.getActualQty());
            row.put("diffQty", it.getDiffQty());
            items.add(row);
        }
        data.put("items", items);
        return data;
    }

    /**
     * 录入实盘数量并提交审核
     * counts: [{itemId, actualQty}]
     */
    @Transactional
    public void submitCounts(Long id, List<Map<String, Object>> counts) {
        Stocktake o = mustGet(id);
        if (o.getStatus() != 0 && o.getStatus() != 1) {
            throw new BusinessException("当前状态不允许录入实盘");
        }
        int gain = 0, loss = 0;
        for (Map<String, Object> c : counts) {
            Long itemId = Long.valueOf(c.get("itemId").toString());
            Integer actual = c.get("actualQty") == null ? null : Integer.valueOf(c.get("actualQty").toString());
            StocktakeItem item = itemMapper.selectById(itemId);
            if (item == null || !item.getStocktakeId().equals(id)) {
                continue;
            }
            item.setActualQty(actual);
            int diff = actual == null ? 0 : actual - item.getBookQty();
            item.setDiffQty(diff);
            if (diff > 0) gain += diff;
            else if (diff < 0) loss += -diff;
            itemMapper.updateById(item);
        }
        o.setStatus(2);
        o.setGainQty(gain);
        o.setLossQty(loss);
        this.updateById(o);
    }

    /**
     * 审核：通过则按差异调整库存（盘盈加、盘亏减），写 CHECK 流水
     */
    @Transactional
    public void audit(Long id, boolean approved, LoginUser operator) {
        Stocktake o = mustGet(id);
        if (o.getStatus() != 2) {
            throw new BusinessException("仅待审核盘点单可审核");
        }
        if (approved) {
            List<StocktakeItem> items = itemMapper.selectList(
                    new LambdaQueryWrapper<StocktakeItem>().eq(StocktakeItem::getStocktakeId, id));
            for (StocktakeItem item : items) {
                if (item.getDiffQty() != null && item.getDiffQty() != 0) {
                    String type = item.getDiffQty() > 0 ? "CHECK_GAIN" : "CHECK_LOSS";
                    String remark = (item.getDiffQty() > 0 ? "盘点盘盈：" : "盘点盘亏：") + o.getOrderNo();
                    inventoryService.changeStock(o.getWarehouseId(), item.getProductId(),
                            item.getDiffQty(), type, o.getId(), remark, operator.getUsername());
                }
            }
            o.setStatus(3);
        } else {
            o.setStatus(4);
        }
        o.setAuditBy(operator.getUsername());
        o.setAuditTime(LocalDateTime.now());
        this.updateById(o);

        // 完成关联的盘点任务
        taskService.lambdaUpdate()
                .eq(WmsTask::getRefType, "STOCKTAKE")
                .eq(WmsTask::getRefId, id)
                .ne(WmsTask::getStatus, 4)
                .set(WmsTask::getStatus, 3)
                .set(WmsTask::getFinishTime, LocalDateTime.now())
                .update();
    }

    /**
     * 取消盘点单
     */
    @Transactional
    public void cancel(Long id) {
        Stocktake o = mustGet(id);
        if (o.getStatus() == 3) {
            throw new BusinessException("已完成盘点单不可取消");
        }
        o.setStatus(4);
        this.updateById(o);
        taskService.lambdaUpdate()
                .eq(WmsTask::getRefType, "STOCKTAKE")
                .eq(WmsTask::getRefId, id)
                .eq(WmsTask::getStatus, 0)
                .set(WmsTask::getStatus, 4)
                .update();
    }

    private Stocktake mustGet(Long id) {
        Stocktake o = this.getById(id);
        if (o == null) {
            throw new BusinessException("盘点单不存在");
        }
        return o;
    }
}
