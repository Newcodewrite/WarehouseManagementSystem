package com.lanxi.wms.controller;

import com.lanxi.wms.common.BusinessException;
import com.lanxi.wms.common.Result;
import com.lanxi.wms.service.FileImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * AI 文件导入：上传解析接口（需登录）。
 * 解析结果暂存 30 分钟，fileToken 交给 AI 助手通过工具执行批量导入。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/file")
@RequiredArgsConstructor
public class AiFileController {

    private static final Set<String> ALLOWED_EXT = Set.of("csv", "xlsx", "xls");

    private final FileImportService fileImportService;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要上传的文件");
        }
        String name = file.getOriginalFilename();
        String ext = "";
        if (name != null && name.lastIndexOf('.') >= 0) {
            ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        }
        if (!ALLOWED_EXT.contains(ext)) {
            return Result.fail("仅支持 CSV / XLSX / XLS 文件");
        }
        try {
            FileImportService.StagedFile sf = fileImportService.stage(name, file.getBytes());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fileToken", sf.getToken());
            data.put("fileName", sf.getFileName());
            data.put("rowCount", sf.getRows().size());
            data.put("columns", sf.getHeaders());
            data.put("detectedType", sf.getDetectedType());
            data.put("preview", sf.getRows().stream().limit(5).toList());
            return Result.ok(data);
        } catch (BusinessException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.warn("文件解析失败：{}", e.getMessage());
            return Result.fail("文件解析失败：" + e.getMessage());
        }
    }
}
