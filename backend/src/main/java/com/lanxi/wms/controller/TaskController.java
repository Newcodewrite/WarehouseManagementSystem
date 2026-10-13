package com.lanxi.wms.controller;

import com.lanxi.wms.common.OperLog;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 作业任务接口
 */
@RestController
@RequestMapping("/api/wms/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) Integer type,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) Long warehouseId) {
        return Result.ok(taskService.pageData(current, size, type, status, warehouseId));
    }

    @GetMapping("/assignees")
    public Result<List<Map<String, Object>>> assignees() {
        return Result.ok(taskService.assignees());
    }

    @PutMapping("/{id}/assign")
    @OperLog(module = "仓储作业", value = "分配任务")
    @PreAuthorize("hasAuthority('wms:task')")
    public Result<Void> assign(@PathVariable Long id, @RequestParam Long userId) {
        taskService.assign(id, userId);
        return Result.ok();
    }

    @PutMapping("/{id}/start")
    @OperLog(module = "仓储作业", value = "开始任务")
    @PreAuthorize("hasAuthority('wms:task')")
    public Result<Void> start(@PathVariable Long id) {
        taskService.start(id);
        return Result.ok();
    }

    @PutMapping("/{id}/complete")
    @OperLog(module = "仓储作业", value = "完成任务")
    @PreAuthorize("hasAuthority('wms:task')")
    public Result<Void> complete(@PathVariable Long id) {
        taskService.complete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/cancel")
    @OperLog(module = "仓储作业", value = "取消任务")
    @PreAuthorize("hasAuthority('wms:task')")
    public Result<Void> cancel(@PathVariable Long id) {
        taskService.cancel(id);
        return Result.ok();
    }
}
