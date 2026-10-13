package com.lanxi.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.common.BusinessException;
import com.lanxi.wms.entity.Location;
import com.lanxi.wms.entity.Warehouse;
import com.lanxi.wms.mapper.LocationMapper;
import com.lanxi.wms.mapper.WarehouseMapper;
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
 * 库位服务（含智能上架推荐）
 */
@Service
@RequiredArgsConstructor
public class LocationService extends ServiceImpl<LocationMapper, Location> {

    private final WarehouseMapper warehouseMapper;

    /** 库区中文名 */
    public static String zoneName(String zone) {
        return switch (zone == null ? "" : zone) {
            case "RECEIVE" -> "收货区";
            case "STORE" -> "存储区";
            case "PICK" -> "拣选区";
            case "DEFECT" -> "残次区";
            case "SHIP" -> "发货区";
            default -> zone;
        };
    }

    /**
     * 分页查询库位
     */
    public Map<String, Object> pageData(long current, long size, Long warehouseId, String zone, String keyword) {
        Page<Location> page = this.lambdaQuery()
                .eq(warehouseId != null, Location::getWarehouseId, warehouseId)
                .eq(StringUtils.hasText(zone), Location::getZone, zone)
                .like(StringUtils.hasText(keyword), Location::getCode, keyword)
                .orderByAsc(Location::getWarehouseId).orderByAsc(Location::getCode)
                .page(new Page<>(current, size));
        Map<Long, String> wMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Location l : page.getRecords()) {
            Map<String, Object> row = toRow(l, wMap);
            rows.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("records", rows);
        data.put("total", page.getTotal());
        return data;
    }

    public List<Map<String, Object>> listByWarehouse(Long warehouseId) {
        Map<Long, String> wMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        return this.lambdaQuery()
                .eq(Location::getWarehouseId, warehouseId)
                .eq(Location::getStatus, 1)
                .orderByAsc(Location::getCode)
                .list().stream().map(l -> toRow(l, wMap)).toList();
    }

    private Map<String, Object> toRow(Location l, Map<Long, String> wMap) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", l.getId());
        row.put("warehouseId", l.getWarehouseId());
        row.put("warehouseName", wMap.getOrDefault(l.getWarehouseId(), "-"));
        row.put("code", l.getCode());
        row.put("zone", l.getZone());
        row.put("zoneName", zoneName(l.getZone()));
        row.put("capacity", l.getCapacity());
        row.put("used", l.getUsed());
        row.put("status", l.getStatus());
        row.put("remark", l.getRemark());
        row.put("createTime", l.getCreateTime());
        return row;
    }

    @Transactional
    public void create(Location form) {
        if (form.getWarehouseId() == null || !StringUtils.hasText(form.getCode()) || !StringUtils.hasText(form.getZone())) {
            throw new BusinessException("仓库、库位编码、库区必填");
        }
        long dup = this.lambdaQuery()
                .eq(Location::getWarehouseId, form.getWarehouseId())
                .eq(Location::getCode, form.getCode()).count();
        if (dup > 0) {
            throw new BusinessException("该仓库下库位编码已存在：" + form.getCode());
        }
        if (form.getCapacity() == null) {
            form.setCapacity(1000);
        }
        if (form.getUsed() == null) {
            form.setUsed(0);
        }
        if (form.getStatus() == null) {
            form.setStatus(1);
        }
        this.save(form);
    }

    @Transactional
    public void update(Location form) {
        Location l = this.getById(form.getId());
        if (l == null) {
            throw new BusinessException("库位不存在");
        }
        if (StringUtils.hasText(form.getCode())) {
            l.setCode(form.getCode());
        }
        if (StringUtils.hasText(form.getZone())) {
            l.setZone(form.getZone());
        }
        if (form.getCapacity() != null) {
            l.setCapacity(form.getCapacity());
        }
        if (form.getStatus() != null) {
            l.setStatus(form.getStatus());
        }
        l.setRemark(form.getRemark());
        this.updateById(l);
    }

    @Transactional
    public void delete(Long id) {
        Location l = this.getById(id);
        if (l == null) {
            return;
        }
        if ("DEFAULT".equals(l.getCode())) {
            throw new BusinessException("系统默认库位不可删除");
        }
        this.removeById(id);
    }

    /**
     * 智能推荐上架库位
     * 规则：高动销/小件商品优先拣选区（PICK）空位 → 其次存储区（STORE）→ 最后默认库位
     */
    public Location recommend(Long warehouseId) {
        Location pick = this.lambdaQuery()
                .eq(Location::getWarehouseId, warehouseId)
                .eq(Location::getZone, "PICK")
                .eq(Location::getStatus, 1)
                .orderByAsc(Location::getCode).last("LIMIT 1").one();
        if (pick != null) {
            return pick;
        }
        Location store = this.lambdaQuery()
                .eq(Location::getWarehouseId, warehouseId)
                .eq(Location::getZone, "STORE")
                .ne(Location::getCode, "DEFAULT")
                .eq(Location::getStatus, 1)
                .orderByAsc(Location::getCode).last("LIMIT 1").one();
        if (store != null) {
            return store;
        }
        Location def = this.lambdaQuery()
                .eq(Location::getWarehouseId, warehouseId)
                .eq(Location::getCode, "DEFAULT").last("LIMIT 1").one();
        if (def == null) {
            throw new BusinessException("该仓库尚未配置库位，请先在库位管理中创建");
        }
        return def;
    }

    /**
     * 获取残次区库位（退货报废品存放）
     */
    public Location defectLocation(Long warehouseId) {
        Location d = this.lambdaQuery()
                .eq(Location::getWarehouseId, warehouseId)
                .eq(Location::getZone, "DEFECT")
                .eq(Location::getStatus, 1)
                .orderByAsc(Location::getCode).last("LIMIT 1").one();
        if (d != null) {
            return d;
        }
        return this.lambdaQuery()
                .eq(Location::getWarehouseId, warehouseId)
                .eq(Location::getCode, "DEFAULT").last("LIMIT 1").one();
    }
}
