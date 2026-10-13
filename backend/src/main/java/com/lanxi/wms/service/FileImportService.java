package com.lanxi.wms.service;

import com.lanxi.wms.common.BusinessException;
import com.lanxi.wms.dto.InboundForm;
import com.lanxi.wms.dto.ItemForm;
import com.lanxi.wms.entity.InboundOrder;
import com.lanxi.wms.entity.Product;
import com.lanxi.wms.entity.Warehouse;
import com.lanxi.wms.security.LoginUser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 文件导入服务：
 * 1) 解析用户上传的 Excel(xlsx/xls) / CSV 文件为「表头 + 行数据」结构，暂存 30 分钟（fileToken 索引）；
 * 2) 供 AI 工具调用，执行两类批量导入：
 *    - 商品档案导入（新增/更新商品）
 *    - 库存入库导入（生成入库单，可自动审核增加库存）
 * 全部写入复用既有业务服务（含校验、流水），失败行逐条记录原因，不影响其他行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileImportService {

    public static final String TYPE_PRODUCT = "PRODUCT";
    public static final String TYPE_STOCK = "STOCK";

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_ROWS = 5000;

    /** 暂存的解析结果 */
    @Data
    public static class StagedFile {
        private String token;
        private String fileName;
        private List<String> headers;
        private List<Map<String, String>> rows;
        private LocalDateTime uploadTime;
        private String detectedType;
    }

    private final ProductService productService;
    private final WarehouseService warehouseService;
    private final InboundService inboundService;
    private final com.lanxi.wms.mapper.ProductMapper productMapper;

    private final Map<String, StagedFile> staging = new ConcurrentHashMap<>();

    // ============================ 解析与暂存 ============================

    /** 解析文件并暂存，返回 fileToken */
    public StagedFile stage(String fileName, byte[] bytes) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        List<String> headers = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();
        if (lower.endsWith(".csv")) {
            parseCsv(fileName, bytes, headers, rows);
        } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            parseExcel(bytes, headers, rows);
        } else {
            throw new BusinessException("仅支持 CSV / XLSX / XLS 文件");
        }
        if (headers.isEmpty()) {
            throw new BusinessException("文件未解析到表头，请确认第一行为列名");
        }
        // 清理过期暂存
        staging.values().removeIf(f -> f.getUploadTime() != null
                && f.getUploadTime().isBefore(LocalDateTime.now().minus(TTL)));

        StagedFile sf = new StagedFile();
        sf.setToken(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        sf.setFileName(fileName);
        sf.setHeaders(headers);
        sf.setRows(rows);
        sf.setUploadTime(LocalDateTime.now());
        sf.setDetectedType(detectType(headers));
        staging.put(sf.getToken(), sf);
        log.info("AI文件上传解析成功：{}，{} 行，识别类型 {}", fileName, rows.size(), sf.getDetectedType());
        return sf;
    }

    /** 获取暂存文件（过期返回 null） */
    public StagedFile get(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        StagedFile f = staging.get(token.trim());
        if (f == null) {
            return null;
        }
        if (f.getUploadTime().isBefore(LocalDateTime.now().minus(TTL))) {
            staging.remove(token.trim());
            return null;
        }
        return f;
    }

    // ============================ 商品档案导入 ============================

    /**
     * 将暂存文件导入为商品档案。
     * 必需列：商品名称；可选列：商品编码、分类、单位、单价、预警值。
     * 有编码且已存在 → 按 updateExisting 决定覆盖更新或跳过；编码为空则自动生成编码新增。
     */
    public Map<String, Object> commitProducts(StagedFile f, boolean updateExisting) {
        List<String> headers = f.getHeaders();
        String colCode = pickHeader(headers, "商品编码", "编码", "产品编码", "商品编号", "货品编码", "code", "sku");
        String colName = pickHeader(headers, "商品名称", "名称", "品名", "产品名称", "货品名称", "name");
        String colCategory = pickHeader(headers, "分类", "类别", "商品分类", "品类", "类目", "category");
        String colUnit = pickHeader(headers, "单位", "计量单位", "unit");
        String colPrice = pickHeader(headers, "单价", "价格", "进货价", "售价", "采购价", "price");
        String colWarn = pickHeader(headers, "预警值", "库存预警值", "预警库存", "预警下限", "warnstock", "warn_stock");
        if (colName == null) {
            throw new BusinessException("文件缺少「商品名称」列，无法导入商品档案");
        }
        int created = 0, updated = 0, skipped = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        int rowNum = 1;
        for (Map<String, String> row : f.getRows()) {
            rowNum++;
            try {
                String name = cell(row, colName);
                if (name.isEmpty()) {
                    errors.add(Map.of("row", rowNum, "reason", "商品名称为空"));
                    continue;
                }
                BigDecimal price = toDecimal(cell(row, colPrice));
                Integer warn = toInt(cell(row, colWarn));
                String unitRaw = cell(row, colUnit);
                String category = cell(row, colCategory).isEmpty() ? null : cell(row, colCategory);
                String code = cell(row, colCode);

                Product exist = null;
                if (!code.isEmpty()) {
                    exist = productService.lambdaQuery().eq(Product::getCode, code).last("LIMIT 1").one();
                    // 绕过 @TableLogic 查已逻辑删除的同编码商品，避免唯一键冲突
                    if (exist == null) {
                        exist = productMapper.selectByCodeIncludeDeleted(code);
                        if (exist != null) {
                            // 恢复逻辑删除的商品
                            productMapper.restoreDeleted(exist.getId());
                        }
                    }
                }
                if (exist == null) {
                    // 编码为空或编码未命中时，按名称兜底查重（含已删除），避免重复建档
                    exist = productService.lambdaQuery().eq(Product::getName, name).last("LIMIT 1").one();
                    if (exist == null) {
                        exist = productMapper.selectByNameIncludeDeleted(name);
                        if (exist != null) {
                            productMapper.restoreDeleted(exist.getId());
                        }
                    }
                }
                if (exist != null) {
                    if (!updateExisting) {
                        skipped++;
                        continue;
                    }
                    exist.setName(name);
                    // 文件未提供的列保留原值，不覆盖
                    if (category != null) {
                        exist.setCategory(category);
                    }
                    if (!unitRaw.isEmpty()) {
                        exist.setUnit(unitRaw);
                    }
                    if (price != null) {
                        exist.setPrice(price);
                    }
                    if (warn != null) {
                        exist.setWarnStock(warn);
                    }
                    productService.updateById(exist);
                    updated++;
                } else {
                    Product p = new Product();
                    p.setCode(code.isEmpty() ? genProductCode() : code);
                    p.setName(name);
                    p.setCategory(category);
                    p.setUnit(unitRaw.isEmpty() ? "个" : unitRaw);
                    p.setPrice(price != null ? price : BigDecimal.ZERO);
                    p.setWarnStock(warn != null ? warn : 10);
                    p.setStatus(1);
                    productService.save(p);
                    created++;
                }
            } catch (Exception e) {
                errors.add(Map.of("row", rowNum, "reason", e.getMessage() == null ? "未知错误" : e.getMessage()));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRows", f.getRows().size());
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("failed", errors.size());
        if (!errors.isEmpty()) {
            result.put("errors", errors.subList(0, Math.min(errors.size(), 20)));
        }
        return result;
    }

    // ============================ 库存入库导入 ============================

    /**
     * 将暂存文件导入为入库单（批量库存导入）。
     * 必需列：数量、（商品编码 或 商品名称）；可选列：单价、仓库（编码/名称/ID）、备注。
     * 按仓库分组生成入库单；autoAudit=true 时导入后立即审核（库存增加）。
     */
    public Map<String, Object> commitStock(StagedFile f, LoginUser operator, Long warehouseId, Long supplierId,
                                           boolean autoAudit, boolean autoCreateProduct) {
        List<String> headers = f.getHeaders();
        String colCode = pickHeader(headers, "商品编码", "编码", "产品编码", "商品编号", "code");
        String colName = pickHeader(headers, "商品名称", "名称", "品名", "产品名称", "name");
        String colQty = pickHeader(headers, "数量", "入库数量", "库存数量", "采购数量", "quantity", "qty");
        String colPrice = pickHeader(headers, "单价", "价格", "进货价", "售价", "price");
        String colWh = pickHeader(headers, "仓库", "仓库编码", "仓库编号", "仓库名称", "仓库id", "warehouse");
        if (colQty == null) {
            throw new BusinessException("文件缺少「数量」列，无法导入库存");
        }
        if (colCode == null && colName == null) {
            throw new BusinessException("文件缺少「商品编码 / 商品名称」列，无法识别商品");
        }
        Map<Long, List<ItemForm>> groups = new LinkedHashMap<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int okRows = 0;
        int rowNum = 1;
        for (Map<String, String> row : f.getRows()) {
            rowNum++;
            try {
                Integer qty = toInt(cell(row, colQty));
                if (qty == null || qty <= 0) {
                    errors.add(Map.of("row", rowNum, "reason", "数量无效（须为正整数）"));
                    continue;
                }
                Product p = resolveProduct(cell(row, colCode), cell(row, colName), autoCreateProduct);
                if (p == null) {
                    errors.add(Map.of("row", rowNum, "reason", "商品不存在" + (autoCreateProduct ? "" : "（如需自动建档请开启自动创建商品）")));
                    continue;
                }
                Long whId = warehouseId != null ? warehouseId : resolveWarehouse(cell(row, colWh));
                if (whId == null) {
                    errors.add(Map.of("row", rowNum, "reason", "未指定目标仓库，且行内无有效仓库信息"));
                    continue;
                }
                BigDecimal price = toDecimal(cell(row, colPrice));
                if (price == null && p.getPrice() != null) {
                    price = p.getPrice();
                }
                if (price == null) {
                    price = BigDecimal.ZERO;
                }
                ItemForm item = new ItemForm();
                item.setProductId(p.getId());
                item.setQuantity(qty);
                item.setPrice(price);
                groups.computeIfAbsent(whId, k -> new ArrayList<>()).add(item);
                okRows++;
            } catch (Exception e) {
                errors.add(Map.of("row", rowNum, "reason", e.getMessage() == null ? "未知错误" : e.getMessage()));
            }
        }
        if (groups.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("totalRows", f.getRows().size());
            result.put("importedRows", 0);
            result.put("failedRows", errors.size());
            result.put("message", "没有可导入的有效数据行");
            result.put("errors", errors.subList(0, Math.min(errors.size(), 20)));
            return result;
        }
        List<Map<String, Object>> orders = new ArrayList<>();
        for (Map.Entry<Long, List<ItemForm>> en : groups.entrySet()) {
            InboundForm form = new InboundForm();
            form.setWarehouseId(en.getKey());
            form.setSupplierId(supplierId);
            form.setRemark("AI文件导入：" + f.getFileName());
            form.setItems(en.getValue());
            inboundService.create(operator, form);
            InboundOrder latest = inboundService.lambdaQuery()
                    .eq(InboundOrder::getWarehouseId, en.getKey())
                    .orderByDesc(InboundOrder::getId).last("LIMIT 1").one();
            boolean audited = false;
            if (autoAudit && latest != null) {
                inboundService.audit(latest.getId(), true, operator);
                audited = true;
            }
            Map<String, Object> om = new LinkedHashMap<>();
            om.put("inboundId", latest != null ? latest.getId() : null);
            om.put("orderNo", latest != null ? latest.getOrderNo() : null);
            om.put("warehouseId", en.getKey());
            om.put("itemCount", en.getValue().size());
            om.put("audited", audited);
            orders.add(om);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("totalRows", f.getRows().size());
        result.put("importedRows", okRows);
        result.put("failedRows", f.getRows().size() - okRows);
        result.put("orderCount", orders.size());
        result.put("autoAudited", autoAudit);
        result.put("orders", orders);
        if (!errors.isEmpty()) {
            result.put("errors", errors.subList(0, Math.min(errors.size(), 20)));
        }
        return result;
    }

    // ============================ 内部辅助 ============================

    /** 按候选名匹配表头：先精确匹配（归一化后），再包含匹配 */
    private String pickHeader(List<String> headers, String... candidates) {
        for (String cand : candidates) {
            for (String h : headers) {
                if (!h.isEmpty() && norm(h).equals(norm(cand))) {
                    return h;
                }
            }
        }
        for (String cand : candidates) {
            for (String h : headers) {
                if (!h.isEmpty() && norm(h).contains(norm(cand))) {
                    return h;
                }
            }
        }
        return null;
    }

    private String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }

    /** 取单元格值（去首尾空白，null 安全） */
    private String cell(Map<String, String> row, String header) {
        if (header == null) {
            return "";
        }
        String v = row.get(header);
        return v == null ? "" : v.trim();
    }

    /** 数字清洗：去除千分位逗号、货币符号、空格 */
    private String cleanNum(String s) {
        return s.replace(",", "").replace("，", "").replace("¥", "").replace("￥", "").replace(" ", "");
    }

    private BigDecimal toDecimal(String s) {
        String v = cleanNum(s);
        if (v.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(String s) {
        String v = cleanNum(s);
        if (v.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(v).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 生成不重复商品编码 P+3位序号 */
    private String genProductCode() {
        long n = productService.count() + 1;
        String code;
        do {
            code = "P" + String.format("%03d", n++);
        } while (productService.lambdaQuery().eq(Product::getCode, code).exists());
        return code;
    }

    /** 解析商品：编码优先，其次名称精确匹配；autoCreate 时按名称自动建档 */
    private Product resolveProduct(String code, String name, boolean autoCreate) {
        if (!code.isEmpty()) {
            Product p = productService.lambdaQuery().eq(Product::getCode, code).last("LIMIT 1").one();
            if (p != null) {
                return p;
            }
        }
        if (!name.isEmpty()) {
            Product p = productService.lambdaQuery().eq(Product::getName, name).last("LIMIT 1").one();
            if (p != null) {
                return p;
            }
            if (autoCreate) {
                Product np = new Product();
                np.setCode(genProductCode());
                np.setName(name);
                np.setUnit("个");
                np.setPrice(BigDecimal.ZERO);
                np.setWarnStock(10);
                np.setStatus(1);
                productService.save(np);
                return np;
            }
        }
        return null;
    }

    /** 解析仓库：数字按 ID，其次按编码，再次按名称 */
    private Long resolveWarehouse(String v) {
        if (v.isEmpty()) {
            return null;
        }
        if (v.matches("\\d+")) {
            Warehouse w = warehouseService.getById(Long.parseLong(v));
            return w != null ? w.getId() : null;
        }
        Warehouse w = warehouseService.lambdaQuery().eq(Warehouse::getCode, v).last("LIMIT 1").one();
        if (w == null) {
            w = warehouseService.lambdaQuery().eq(Warehouse::getName, v).last("LIMIT 1").one();
        }
        return w != null ? w.getId() : null;
    }

    /** 类型识别：有数量列 → 库存入库导入；否则按名称列判断为商品档案导入 */
    private String detectType(List<String> headers) {
        if (pickHeader(headers, "数量", "入库数量", "库存数量", "采购数量", "quantity", "qty") != null) {
            return TYPE_STOCK;
        }
        if (pickHeader(headers, "商品名称", "名称", "品名", "产品名称", "name") != null) {
            return TYPE_PRODUCT;
        }
        return "UNKNOWN";
    }

    // ============================ 文件解析 ============================

    private void parseCsv(String fileName, byte[] bytes, List<String> headers, List<Map<String, String>> rows) {
        String text = decode(bytes);
        String[] lines = text.split("\\r?\\n");
        boolean headerDone = false;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            List<String> cols = parseCsvLine(line);
            if (!headerDone) {
                for (String c : cols) {
                    headers.add(c == null ? "" : c.trim());
                }
                headerDone = true;
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            boolean hasValue = false;
            for (int i = 0; i < headers.size(); i++) {
                String v = i < cols.size() ? cols.get(i) : "";
                row.put(headers.get(i), v);
                if (!v.isBlank()) {
                    hasValue = true;
                }
            }
            if (hasValue) {
                rows.add(row);
            }
            if (rows.size() > MAX_ROWS) {
                throw new BusinessException("数据行超过 " + MAX_ROWS + " 行上限，请拆分文件后导入");
            }
        }
    }

    /** 单行 CSV 解析（支持双引号包裹与转义） */
    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private void parseExcel(byte[] bytes, List<String> headers, List<Map<String, String>> rows) {
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            boolean headerDone = false;
            for (Row r : sheet) {
                if (r == null) {
                    continue;
                }
                if (!headerDone) {
                    for (int i = 0; i < r.getLastCellNum(); i++) {
                        headers.add(fmt.formatCellValue(r.getCell(i)).trim());
                    }
                    headerDone = true;
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int i = 0; i < headers.size(); i++) {
                    String v = fmt.formatCellValue(r.getCell(i)).trim();
                    row.put(headers.get(i), v);
                    if (!v.isBlank()) {
                        hasValue = true;
                    }
                }
                if (hasValue) {
                    rows.add(row);
                }
                if (rows.size() > MAX_ROWS) {
                    throw new BusinessException("数据行超过 " + MAX_ROWS + " 行上限，请拆分文件后导入");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Excel 解析失败：" + e.getMessage());
        }
    }

    /** 字节解码：BOM 识别 UTF-8，严格 UTF-8 尝试失败后回退 GBK（兼容中文 Excel 导出） */
    private String decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        try {
            CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return dec.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            try {
                return new String(bytes, "GBK");
            } catch (Exception ex) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
    }
}
