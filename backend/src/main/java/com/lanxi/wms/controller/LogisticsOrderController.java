package com.lanxi.wms.controller;

import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.dto.DispatchForm;
import com.lanxi.wms.dto.LogisticsForm;
import com.lanxi.wms.dto.TrackForm;
import com.lanxi.wms.entity.LogTrack;
import com.lanxi.wms.service.LogisticsOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 物流订单接口
 */
@RestController
@RequestMapping("/api/logistics/order")
@RequiredArgsConstructor
public class LogisticsOrderController {

    private final LogisticsOrderService logisticsOrderService;

    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String orderNo,
                                            @RequestParam(required = false) Integer status) {
        return Result.ok(logisticsOrderService.pageData(current, size, orderNo, status));
    }

    @PostMapping
    @OperLog(module = "物流运输", value = "创建物流订单")
    @PreAuthorize("hasAuthority('logistics:order')")
    public Result<Void> create(@RequestBody @Valid LogisticsForm form) {
        logisticsOrderService.create(form);
        return Result.ok();
    }

    @PutMapping("/{id}/dispatch")
    @OperLog(module = "物流运输", value = "调度车辆")
    @PreAuthorize("hasAuthority('logistics:order')")
    public Result<Void> dispatch(@PathVariable Long id, @RequestBody @Valid DispatchForm form) {
        logisticsOrderService.dispatch(id, form);
        return Result.ok();
    }

    @PutMapping("/{id}/deliver")
    @OperLog(module = "物流运输", value = "订单签收")
    @PreAuthorize("hasAuthority('logistics:order')")
    public Result<Void> deliver(@PathVariable Long id) {
        logisticsOrderService.deliver(id);
        return Result.ok();
    }

    @PutMapping("/{id}/cancel")
    @OperLog(module = "物流运输", value = "取消订单")
    @PreAuthorize("hasAuthority('logistics:order')")
    public Result<Void> cancel(@PathVariable Long id) {
        logisticsOrderService.cancel(id);
        return Result.ok();
    }

    @PostMapping("/{id}/track")
    @OperLog(module = "物流运输", value = "添加轨迹")
    @PreAuthorize("hasAuthority('logistics:order')")
    public Result<Void> addTrack(@PathVariable Long id, @RequestBody @Valid TrackForm form) {
        logisticsOrderService.addTrack(id, form);
        return Result.ok();
    }

    @GetMapping("/{id}/tracks")
    public Result<List<LogTrack>> tracks(@PathVariable Long id) {
        return Result.ok(logisticsOrderService.tracks(id));
    }
}
