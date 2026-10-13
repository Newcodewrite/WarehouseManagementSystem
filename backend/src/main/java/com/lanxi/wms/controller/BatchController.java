package com.lanxi.wms.controller;

import com.lanxi.wms.common.Result;
import com.lanxi.wms.service.BatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 批次与效期接口
 */
@RestController
@RequestMapping("/api/wms/batch")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) Long warehouseId,
                                                  @RequestParam(required = false) Boolean nearExpiryOnly) {
        return Result.ok(batchService.batchList(warehouseId, nearExpiryOnly));
    }
}
