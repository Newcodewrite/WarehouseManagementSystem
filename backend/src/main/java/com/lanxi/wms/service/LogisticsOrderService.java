package com.lanxi.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.common.BusinessException;
import com.lanxi.wms.dto.DispatchForm;
import com.lanxi.wms.dto.LogisticsForm;
import com.lanxi.wms.dto.TrackForm;
import com.lanxi.wms.entity.LogTrack;
import com.lanxi.wms.entity.LogisticsOrder;
import com.lanxi.wms.entity.OutboundOrder;
import com.lanxi.wms.entity.Vehicle;
import com.lanxi.wms.mapper.LogTrackMapper;
import com.lanxi.wms.mapper.LogisticsOrderMapper;
import com.lanxi.wms.mapper.OutboundOrderMapper;
import com.lanxi.wms.mapper.VehicleMapper;
import com.lanxi.wms.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 物流订单服务
 */
@Service
@RequiredArgsConstructor
public class LogisticsOrderService extends ServiceImpl<LogisticsOrderMapper, LogisticsOrder> {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();

    private final LogTrackMapper trackMapper;
    private final VehicleMapper vehicleMapper;
    private final OutboundOrderMapper outboundOrderMapper;

    /**
     * 分页查询物流订单
     */
    public Map<String, Object> pageData(long current, long size, String orderNo, Integer status) {
        Page<LogisticsOrder> page = this.lambdaQuery()
                .like(StringUtils.hasText(orderNo), LogisticsOrder::getOrderNo, orderNo)
                .eq(status != null, LogisticsOrder::getStatus, status)
                .orderByDesc(LogisticsOrder::getId)
                .page(new Page<>(current, size));

        Map<Long, Vehicle> vMap = vehicleMapper.selectList(null).stream()
                .collect(java.util.stream.Collectors.toMap(Vehicle::getId, v -> v));
        Map<Long, OutboundOrder> oMap = outboundOrderMapper.selectList(null).stream()
                .collect(java.util.stream.Collectors.toMap(OutboundOrder::getId, o -> o));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (LogisticsOrder o : page.getRecords()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", o.getId());
            row.put("orderNo", o.getOrderNo());
            row.put("outboundOrderId", o.getOutboundOrderId());
            if (o.getOutboundOrderId() != null) {
                OutboundOrder ob = oMap.get(o.getOutboundOrderId());
                row.put("outboundOrderNo", ob == null ? "-" : ob.getOrderNo());
            }
            row.put("receiverName", o.getReceiverName());
            row.put("receiverPhone", o.getReceiverPhone());
            row.put("receiverAddress", o.getReceiverAddress());
            row.put("vehicleId", o.getVehicleId());
            Vehicle v = o.getVehicleId() == null ? null : vMap.get(o.getVehicleId());
            row.put("vehicleCode", v == null ? "-" : v.getCode());
            row.put("driverName", o.getDriverName());
            row.put("status", o.getStatus());
            row.put("remark", o.getRemark());
            row.put("createTime", o.getCreateTime());
            rows.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("records", rows);
        data.put("total", page.getTotal());
        return data;
    }

    /**
     * 创建物流订单（待调度）
     */
    @Transactional
    public void create(LogisticsForm form) {
        LogisticsOrder order = new LogisticsOrder();
        order.setOrderNo("LO" + LocalDateTime.now().format(NO_FMT) + String.format("%03d", RANDOM.nextInt(1000)));
        order.setOutboundOrderId(form.getOutboundOrderId());
        order.setReceiverName(form.getReceiverName());
        order.setReceiverPhone(form.getReceiverPhone());
        order.setReceiverAddress(form.getReceiverAddress());
        order.setRemark(form.getRemark());
        order.setStatus(0);
        this.save(order);
        addTrack(order, "", "物流订单创建成功，等待调度");
    }

    /**
     * 调度车辆
     */
    @Transactional
    public void dispatch(Long id, DispatchForm form) {
        LogisticsOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("物流订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new BusinessException("当前状态不允许调度");
        }
        Vehicle vehicle = vehicleMapper.selectById(form.getVehicleId());
        if (vehicle == null) {
            throw new BusinessException("车辆不存在");
        }
        if (vehicle.getStatus() != 0) {
            throw new BusinessException("车辆当前不可用");
        }
        vehicle.setStatus(1);
        vehicleMapper.updateById(vehicle);

        order.setVehicleId(vehicle.getId());
        order.setDriverName(vehicle.getDriverName());
        order.setStatus(1);
        this.updateById(order);
        addTrack(order, "调度中心", "已调度车辆[" + vehicle.getCode() + "]，司机[" + vehicle.getDriverName() + "]，货物开始运输");
    }

    /**
     * 签收
     */
    @Transactional
    public void deliver(Long id) {
        LogisticsOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("物流订单不存在");
        }
        if (order.getStatus() != 1) {
            throw new BusinessException("仅运输中的订单可签收");
        }
        order.setStatus(2);
        this.updateById(order);
        if (order.getVehicleId() != null) {
            Vehicle vehicle = vehicleMapper.selectById(order.getVehicleId());
            if (vehicle != null) {
                vehicle.setStatus(0);
                vehicleMapper.updateById(vehicle);
            }
        }
        addTrack(order, "", "货物已送达并签收，运输完成");
    }

    /**
     * 取消订单
     */
    @Transactional
    public void cancel(Long id) {
        LogisticsOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("物流订单不存在");
        }
        if (order.getStatus() != 0 && order.getStatus() != 1) {
            throw new BusinessException("当前状态不允许取消");
        }
        order.setStatus(3);
        this.updateById(order);
        if (order.getVehicleId() != null) {
            Vehicle vehicle = vehicleMapper.selectById(order.getVehicleId());
            if (vehicle != null) {
                vehicle.setStatus(0);
                vehicleMapper.updateById(vehicle);
            }
        }
        addTrack(order, "", "订单已取消");
    }

    /**
     * 添加轨迹
     */
    @Transactional
    public void addTrack(Long id, TrackForm form) {
        LogisticsOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("物流订单不存在");
        }
        if (order.getStatus() != 1) {
            throw new BusinessException("仅运输中的订单可添加轨迹");
        }
        LogTrack track = new LogTrack();
        track.setOrderId(id);
        track.setLocation(form.getLocation());
        track.setContent(form.getContent());
        track.setTrackTime(LocalDateTime.now());
        trackMapper.insert(track);
    }

    /**
     * 查询轨迹
     */
    public List<LogTrack> tracks(Long orderId) {
        return trackMapper.selectList(new LambdaQueryWrapper<LogTrack>()
                .eq(LogTrack::getOrderId, orderId)
                .orderByAsc(LogTrack::getTrackTime)
                .orderByAsc(LogTrack::getId));
    }

    private void addTrack(LogisticsOrder order, String location, String content) {
        LogTrack track = new LogTrack();
        track.setOrderId(order.getId());
        track.setLocation(location);
        track.setContent(content);
        track.setTrackTime(LocalDateTime.now());
        trackMapper.insert(track);
    }
}
