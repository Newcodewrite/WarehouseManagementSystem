package com.lanxi.wms.controller;

import com.lanxi.wms.common.Result;
import com.lanxi.wms.dto.ChatForm;
import com.lanxi.wms.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 智能助手接口
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody @Valid ChatForm form) {
        return Result.ok(aiService.chat(form));
    }

    @GetMapping("/stock-advice")
    public Result<Map<String, Object>> stockAdvice() {
        return Result.ok(aiService.stockAdvice());
    }

    @GetMapping("/track-explain/{orderId}")
    public Result<Map<String, Object>> trackExplain(@PathVariable Long orderId) {
        return Result.ok(aiService.trackExplain(orderId));
    }

    @GetMapping("/schedule-advice")
    public Result<Map<String, Object>> scheduleAdvice() {
        return Result.ok(aiService.scheduleAdvice());
    }
}
