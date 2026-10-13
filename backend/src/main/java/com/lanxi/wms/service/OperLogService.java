package com.lanxi.wms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lanxi.wms.entity.SysOperLog;
import com.lanxi.wms.mapper.SysOperLogMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作日志服务
 */
@Service
public class OperLogService extends ServiceImpl<SysOperLogMapper, SysOperLog> {

    /**
     * 异步保存操作日志
     */
    @Async
    public void add(SysOperLog log) {
        try {
            this.save(log);
        } catch (Exception ignored) {
        }
    }

    public Page<SysOperLog> page(long current, long size, String keyword) {
        return this.lambdaQuery()
                .and(StringUtils.hasText(keyword),
                        w -> w.like(SysOperLog::getUsername, keyword)
                                .or().like(SysOperLog::getModule, keyword)
                                .or().like(SysOperLog::getDescription, keyword))
                .orderByDesc(SysOperLog::getId)
                .page(new Page<>(current, size));
    }
}
