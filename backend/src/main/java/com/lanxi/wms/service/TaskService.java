package com.lanxi.wms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.common.BusinessException;
import com.lanxi.wms.entity.SysUser;
import com.lanxi.wms.entity.Warehouse;
import com.lanxi.wms.entity.WmsTask;
import com.lanxi.wms.mapper.SysUserMapper;
import com.lanxi.wms.mapper.WarehouseMapper;
import com.lanxi.wms.mapper.WmsTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 作业任务服务（统一任务池：上架/拣选/盘点/移库/补货/退货质检）
 */
@Service
@RequiredArgsConstructor
public class TaskService extends ServiceImpl<WmsTaskMapper, WmsTask> {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();

    private final WarehouseMapper warehouseMapper;
    private final SysUserMapper sysUserMapper;

    public static String typeName(Integer type) {
        if (type == null) return "-";
        return switch (type) {
            case 1 -> "上架";
            case 2 -> "拣选";
            case 3 -> "盘点";
            case 4 -> "移库";
            case 5 -> "补货";
            case 6 -> "退货质检";
            default -> "未知";
        };
    }

    public static String statusName(Integer status) {
        if (status == null) return "-";
        return switch (status) {
            case 0 -> "待分配";
            case 1 -> "待执行";
            case 2 -> "进行中";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "未知";
        };
    }

    private String genTaskNo() {
        return "TK" + LocalDateTime.now().format(NO_FMT) + String.format("%03d", RANDOM.nextInt(1000));
    }

    /**
     * 创建任务（供各业务钩子调用）
     */
    @Transactional
    public WmsTask create(Integer type, String refType, Long refId, Long warehouseId,
                          Long locationId, String content, String operator) {
        WmsTask t = new WmsTask();
        t.setTaskNo(genTaskNo());
        t.setType(type);
        t.setRefType(refType);
        t.setRefId(refId);
        t.setWarehouseId(warehouseId);
        t.setLocationId(locationId);
        t.setContent(content);
        t.setStatus(0);
        t.setCreateBy(operator);
        this.save(t);
        return t;
    }

    /**
     * 分页查询任务
     */
    public Map<String, Object> pageData(long current, long size, Integer type, Integer status, Long warehouseId) {
        Page<WmsTask> page = this.lambdaQuery()
                .eq(type != null, WmsTask::getType, type)
                .eq(status != null, WmsTask::getStatus, status)
                .eq(warehouseId != null, WmsTask::getWarehouseId, warehouseId)
                .orderByDesc(WmsTask::getId)
                .page(new Page<>(current, size));
        Map<Long, String> wMap = warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (WmsTask t : page.getRecords()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", t.getId());
            row.put("taskNo", t.getTaskNo());
            row.put("type", t.getType());
            row.put("typeName", typeName(t.getType()));
            row.put("refType", t.getRefType());
            row.put("refId", t.getRefId());
            row.put("warehouseId", t.getWarehouseId());
            row.put("warehouseName", wMap.getOrDefault(t.getWarehouseId(), "-"));
            row.put("locationId", t.getLocationId());
            row.put("content", t.getContent());
            row.put("assigneeId", t.getAssigneeId());
            row.put("assigneeName", t.getAssigneeName());
            row.put("status", t.getStatus());
            row.put("statusName", statusName(t.getStatus()));
            row.put("finishTime", t.getFinishTime());
            row.put("remark", t.getRemark());
            row.put("createBy", t.getCreateBy());
            row.put("createTime", t.getCreateTime());
            rows.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("records", rows);
        data.put("total", page.getTotal());
        return data;
    }

    /**
     * 可指派人员列表（作业人员）
     */
    public List<Map<String, Object>> assignees() {
        return sysUserMapper.selectList(null).stream()
                .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("name", u.getNickname() != null ? u.getNickname() : u.getUsername());
                    return m;
                }).toList();
    }

    /**
     * 分配任务
     */
    @Transactional
    public void assign(Long id, Long userId) {
        WmsTask t = mustGet(id);
        if (t.getStatus() == 3 || t.getStatus() == 4) {
            throw new BusinessException("已结束的任务不能分配");
        }
        SysUser u = sysUserMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException("指派人员不存在");
        }
        t.setAssigneeId(userId);
        t.setAssigneeName(u.getNickname() != null ? u.getNickname() : u.getUsername());
        if (t.getStatus() == 0) {
            t.setStatus(1);
        }
        this.updateById(t);
    }

    /**
     * 开始执行
     */
    @Transactional
    public void start(Long id) {
        WmsTask t = mustGet(id);
        if (t.getAssigneeId() == null) {
            throw new BusinessException("请先分配人员再开始任务");
        }
        if (t.getStatus() != 1) {
            throw new BusinessException("仅待执行任务可开始");
        }
        t.setStatus(2);
        this.updateById(t);
    }

    /**
     * 完成任务
     */
    @Transactional
    public void complete(Long id) {
        WmsTask t = mustGet(id);
        if (t.getStatus() == 3) {
            throw new BusinessException("任务已完成");
        }
        if (t.getStatus() == 4) {
            throw new BusinessException("任务已取消");
        }
        t.setStatus(3);
        t.setFinishTime(LocalDateTime.now());
        this.updateById(t);
    }

    /**
     * 取消任务
     */
    @Transactional
    public void cancel(Long id) {
        WmsTask t = mustGet(id);
        if (t.getStatus() == 3) {
            throw new BusinessException("已完成任务不能取消");
        }
        t.setStatus(4);
        this.updateById(t);
    }

    private WmsTask mustGet(Long id) {
        WmsTask t = this.getById(id);
        if (t == null) {
            throw new BusinessException("任务不存在");
        }
        return t;
    }
}
