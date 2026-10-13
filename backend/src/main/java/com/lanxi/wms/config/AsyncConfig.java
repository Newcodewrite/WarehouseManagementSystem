package com.lanxi.wms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 开启异步支持（操作日志异步落库等）
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
