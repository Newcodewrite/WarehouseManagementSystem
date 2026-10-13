package com.lanxi.wms.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanxi.wms.dto.InboundForm;
import com.lanxi.wms.dto.ItemForm;
import com.lanxi.wms.dto.OutboundForm;
import com.lanxi.wms.entity.Customer;
import com.lanxi.wms.entity.InboundOrder;
import com.lanxi.wms.entity.Location;
import com.lanxi.wms.entity.LogisticsOrder;
import com.lanxi.wms.entity.OutboundOrder;
import com.lanxi.wms.entity.Product;
import com.lanxi.wms.entity.Supplier;
import com.lanxi.wms.entity.Warehouse;
import com.lanxi.wms.entity.WmsTask;
import com.lanxi.wms.security.LoginUser;
import com.lanxi.wms.service.FileImportService;
import com.lanxi.wms.service.InboundService;
import com.lanxi.wms.service.InventoryService;
import com.lanxi.wms.service.LogisticsOrderService;
import com.lanxi.wms.service.OutboundService;
import com.lanxi.wms.service.ProductService;
import com.lanxi.wms.service.SupplierService;
import com.lanxi.wms.service.CustomerService;
import com.lanxi.wms.service.WarehouseService;
import com.lanxi.wms.service.LocationService;
import com.lanxi.wms.service.StocktakeService;
import com.lanxi.wms.service.TaskService;
import com.lanxi.wms.service.BatchService;
import com.lanxi.wms.service.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WMS 系统管理工具集（供大模型 Function Calling 调用）。
 * 每次对话创建一个实例，绑定当前登录用户，保证操作身份与权限正确。
 * 所有方法返回字符串（JSON 或文本），异常不抛出，转为错误描述返回给模型。
 */
@Slf4j
public class WmsTools {

    private static final int LIMIT = 20;

    private final LoginUser operator;
    private final ProductService productService;
    private final WarehouseService warehouseService;
    private final SupplierService supplierService;
    private final CustomerService customerService;
    private final InventoryService inventoryService;
    private final InboundService inboundService;
    private final OutboundService outboundService;
    private final LogisticsOrderService logisticsOrderService;
    private final FileImportService fileImportService;
    private final LocationService locationService;
    private final StocktakeService stocktakeService;
    private final TaskService taskService;
    private final BatchService batchService;
    private final DashboardService dashboardService;
    private final ObjectMapper objectMapper;

    public WmsTools(LoginUser operator,
                    ProductService productService,
                    WarehouseService warehouseService,
                    SupplierService supplierService,
                    CustomerService customerService,
                    InventoryService inventoryService,
                    InboundService inboundService,
                    OutboundService outboundService,
                    LogisticsOrderService logisticsOrderService,
                    FileImportService fileImportService,
                    LocationService locationService,
                    StocktakeService stocktakeService,
                    TaskService taskService,
                    BatchService batchService,
                    DashboardService dashboardService,
                    ObjectMapper objectMapper) {
        this.operator = operator;
        this.productService = productService;
        this.warehouseService = warehouseService;
        this.supplierService = supplierService;
        this.customerService = customerService;
        this.inventoryService = inventoryService;
        this.inboundService = inboundService;
        this.outboundService = outboundService;
        this.logisticsOrderService = logisticsOrderService;
        this.fileImportService = fileImportService;
        this.locationService = locationService;
        this.stocktakeService = stocktakeService;
        this.taskService = taskService;
        this.batchService = batchService;
        this.dashboardService = dashboardService;
        this.objectMapper = objectMapper;
    }

    // ============================ 商品管理 ============================

    @Tool(description = "查询商品列表，支持按商品编码或名称模糊搜索；不传关键词则返回最新商品。返回商品ID、编码、名称、分类、单位、单价、预警值、状态。")
    public String searchProducts(
            @ToolParam(required = false, description = "关键词：商品编码或名称，可为空") String keyword) {
        try {
            List<Product> list = productService.lambdaQuery()
                    .and(StringUtils.hasText(keyword),
                            w -> w.like(Product::getCode, keyword).or().like(Product::getName, keyword))
                    .orderByDesc(Product::getId)
                    .last("LIMIT " + LIMIT)
                    .list();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Product p : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", p.getId());
                row.put("code", p.getCode());
                row.put("name", p.getName());
                row.put("category", p.getCategory());
                row.put("unit", p.getUnit());
                row.put("price", p.getPrice());
                row.put("warnStock", p.getWarnStock());
                row.put("status", p.getStatus() == 1 ? "启用" : "停用");
                rows.add(row);
            }
            return toJson(rows);
        } catch (Exception e) {
            return error("查询商品失败", e);
        }
    }

    @Tool(description = "新增商品。商品编码可留空由系统自动生成；名称必填。返回新建商品的ID和编码。")
    public String createProduct(
            @ToolParam(required = false, description = "商品编码，如 P101；留空则自动生成") String code,
            @ToolParam(description = "商品名称，必填") String name,
            @ToolParam(required = false, description = "商品分类，如 电子产品/日用品/服装") String category,
            @ToolParam(required = false, description = "计量单位，如 个/件/条/箱") String unit,
            @ToolParam(required = false, description = "单价（元），不传默认为0") Double price,
            @ToolParam(required = false, description = "库存预警值，不传默认10") Integer warnStock) {
        try {
            log.info("AI工具 createProduct 被调用，name={}，code={}", name, code);
            if (!hasPerm("base:product")) {
                return "权限不足：当前用户没有商品管理权限（base:product），无法新增商品。";
            }
            if (!StringUtils.hasText(name)) {
                return "参数错误：商品名称必填。";
            }
            Product p = new Product();
            p.setCode(StringUtils.hasText(code) ? code : genProductCode());
            p.setName(name.trim());
            p.setCategory(category);
            p.setUnit(StringUtils.hasText(unit) ? unit : "个");
            p.setPrice(price != null ? BigDecimal.valueOf(price) : BigDecimal.ZERO);
            p.setWarnStock(warnStock != null ? warnStock : 10);
            p.setStatus(1);
            productService.save(p);
            return "商品创建成功：ID=" + p.getId() + "，编码=" + p.getCode() + "，名称=" + p.getName()
                    + "，单价=" + p.getPrice() + " 元/" + p.getUnit() + "。";
        } catch (Exception e) {
            return error("新增商品失败", e);
        }
    }

    @Tool(description = "修改商品信息。仅更新传入的非空字段。可修改名称、分类、单位、单价、预警值、状态。")
    public String updateProduct(
            @ToolParam(description = "商品ID，必填") Long productId,
            @ToolParam(required = false, description = "新的商品名称") String name,
            @ToolParam(required = false, description = "新的分类") String category,
            @ToolParam(required = false, description = "新的单位") String unit,
            @ToolParam(required = false, description = "新的单价（元）") Double price,
            @ToolParam(required = false, description = "新的库存预警值") Integer warnStock,
            @ToolParam(required = false, description = "状态：1启用 0停用") Integer status) {
        try {
            log.info("AI工具 updateProduct 被调用，productId={}", productId);
            if (!hasPerm("base:product")) {
                return "权限不足：当前用户没有商品管理权限（base:product），无法修改商品。";
            }
            Product p = productService.getById(productId);
            if (p == null) {
                return "商品不存在：ID=" + productId;
            }
            if (StringUtils.hasText(name)) {
                p.setName(name.trim());
            }
            if (category != null) {
                p.setCategory(category);
            }
            if (StringUtils.hasText(unit)) {
                p.setUnit(unit);
            }
            if (price != null) {
                p.setPrice(BigDecimal.valueOf(price));
            }
            if (warnStock != null) {
                p.setWarnStock(warnStock);
            }
            if (status != null) {
                p.setStatus(status);
            }
            productService.updateById(p);
            return "商品修改成功：ID=" + p.getId() + "，编码=" + p.getCode() + "，名称=" + p.getName() + "。";
        } catch (Exception e) {
            return error("修改商品失败", e);
        }
    }

    @Tool(description = "删除商品（逻辑删除）。删除后该商品不可再用于出入库，请谨慎操作。")
    public String deleteProduct(
            @ToolParam(description = "要删除的商品ID，必填") Long productId) {
        try {
            log.info("AI工具 deleteProduct 被调用，productId={}", productId);
            if (!hasPerm("base:product")) {
                log.warn("AI工具 deleteProduct 权限不足");
                return "权限不足：当前用户没有商品管理权限（base:product），无法删除商品。";
            }
            Product p = productService.getById(productId);
            if (p == null) {
                log.warn("AI工具 deleteProduct 商品不存在，productId={}", productId);
                return "商品不存在：ID=" + productId;
            }
            boolean ok = productService.removeById(productId);
            log.info("AI工具 deleteProduct 执行结果：{}，商品={}，编码={}", ok, p.getName(), p.getCode());
            return "商品已删除：ID=" + productId + "，编码=" + p.getCode() + "，名称=" + p.getName() + "。";
        } catch (Exception e) {
            log.error("AI工具 deleteProduct 异常", e);
            return error("删除商品失败", e);
        }
    }

    // ============================ 基础档案 ============================

    @Tool(description = "查询仓库列表，返回仓库ID、编码、名称、地址、库容、负责人、状态。创建出入库单前可用此工具获取仓库ID。")
    public String listWarehouses() {
        try {
            List<Warehouse> list = warehouseService.lambdaQuery()
                    .orderByAsc(Warehouse::getId).list();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Warehouse w : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", w.getId());
                row.put("code", w.getCode());
                row.put("name", w.getName());
                row.put("address", w.getAddress());
                row.put("capacity", w.getCapacity());
                row.put("manager", w.getManagerName());
                row.put("status", w.getStatus() == 1 ? "启用" : "停用");
                rows.add(row);
            }
            return toJson(rows);
        } catch (Exception e) {
            return error("查询仓库失败", e);
        }
    }

    @Tool(description = "查询供应商列表，支持按名称模糊搜索。创建入库单前可用此工具获取供应商ID。")
    public String listSuppliers(
            @ToolParam(required = false, description = "供应商名称关键词，可为空") String keyword) {
        try {
            List<Supplier> list = supplierService.lambdaQuery()
                    .like(StringUtils.hasText(keyword), Supplier::getName, keyword)
                    .orderByAsc(Supplier::getId).last("LIMIT " + LIMIT).list();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Supplier s : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", s.getId());
                row.put("code", s.getCode());
                row.put("name", s.getName());
                row.put("contactName", s.getContactName());
                row.put("contactPhone", s.getContactPhone());
                rows.add(row);
            }
            return toJson(rows);
        } catch (Exception e) {
            return error("查询供应商失败", e);
        }
    }

    @Tool(description = "查询客户列表，支持按名称模糊搜索。创建出库单前可用此工具获取客户ID。")
    public String listCustomers(
            @ToolParam(required = false, description = "客户名称关键词，可为空") String keyword) {
        try {
            List<Customer> list = customerService.lambdaQuery()
                    .like(StringUtils.hasText(keyword), Customer::getName, keyword)
                    .orderByAsc(Customer::getId).last("LIMIT " + LIMIT).list();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Customer c : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", c.getId());
                row.put("code", c.getCode());
                row.put("name", c.getName());
                row.put("contactName", c.getContactName());
                row.put("contactPhone", c.getContactPhone());
                rows.add(row);
            }
            return toJson(rows);
        } catch (Exception e) {
            return error("查询客户失败", e);
        }
    }

    // ============================ 库存管理 ============================

    @Tool(description = "查询库存。可按仓库ID过滤，可按商品编码/名称关键词搜索。返回仓库、商品、库存数量、预警值、是否预警。")
    public String queryInventory(
            @ToolParam(required = false, description = "仓库ID，不传则查全部仓库") Long warehouseId,
            @ToolParam(required = false, description = "商品编码或名称关键词，可为空") String keyword) {
        try {
            Map<String, Object> data = inventoryService.pageData(1, LIMIT, warehouseId, keyword);
            Object records = data.get("records");
            Object total = data.get("total");
            return toJson(Map.of("total", total, "records", records));
        } catch (Exception e) {
            return error("查询库存失败", e);
        }
    }

    @Tool(description = "查询所有库存预警（库存数量低于或等于商品预警值）的商品，返回仓库、商品、当前库存、预警值、建议补货数量。")
    public String listStockAlerts() {
        try {
            List<Map<String, Object>> alerts = inventoryService.alertList();
            return toJson(Map.of("alertCount", alerts.size(), "alerts", alerts));
        } catch (Exception e) {
            return error("查询库存预警失败", e);
        }
    }

    // ============================ 入库管理 ============================

    @Tool(description = "查询入库单列表。status：0待审核 1已入库 2已驳回，不传则返回全部最新单据。")
    public String listInboundOrders(
            @ToolParam(required = false, description = "单据状态：0待审核 1已入库 2已驳回，可为空") Integer status) {
        try {
            List<InboundOrder> list = inboundService.lambdaQuery()
                    .eq(status != null, InboundOrder::getStatus, status)
                    .orderByDesc(InboundOrder::getId)
                    .last("LIMIT " + LIMIT)
                    .list();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (InboundOrder o : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", o.getId());
                row.put("orderNo", o.getOrderNo());
                row.put("warehouseId", o.getWarehouseId());
                row.put("supplierId", o.getSupplierId());
                row.put("totalAmount", o.getTotalAmount());
                row.put("status", statusText(o.getStatus(), new String[]{"待审核", "已入库", "已驳回"}));
                row.put("createBy", o.getCreateBy());
                row.put("createTime", o.getCreateTime());
                rows.add(row);
            }
            return toJson(rows);
        } catch (Exception e) {
            return error("查询入库单失败", e);
        }
    }

    @Tool(description = "创建入库单（状态为待审核）。warehouseId 和 items 必填；创建后需再调用 auditInboundOrder 审核通过才会增加库存。")
    public String createInboundOrder(
            @ToolParam(description = "入库仓库ID，必填（可先用 listWarehouses 查询）") Long warehouseId,
            @ToolParam(description = "入库明细JSON数组字符串，每项含 productId 或 productCode、quantity(数量)、price(单价,可选)。"
                    + "示例：[{\"productCode\":\"P001\",\"quantity\":100},{\"productId\":3,\"quantity\":50,\"price\":45.5}]") String itemsJson,
            @ToolParam(required = false, description = "供应商ID，可为空（可先用 listSuppliers 查询）") Long supplierId,
            @ToolParam(required = false, description = "备注，可为空") String remark) {
        try {
            if (!hasPerm("wms:inbound")) {
                return "权限不足：当前用户没有入库管理权限（wms:inbound），无法创建入库单。";
            }
            List<ItemForm> items = parseItems(itemsJson);
            if (items.isEmpty()) {
                return "参数错误：入库明细不能为空，itemsJson 需为非空JSON数组。";
            }
            InboundForm form = new InboundForm();
            form.setWarehouseId(warehouseId);
            form.setSupplierId(supplierId);
            form.setRemark(remark);
            form.setItems(items);
            inboundService.create(operator, form);
            InboundOrder latest = inboundService.lambdaQuery()
                    .eq(InboundOrder::getWarehouseId, warehouseId)
                    .orderByDesc(InboundOrder::getId).last("LIMIT 1").one();
            String idInfo = latest != null ? "入库单ID=" + latest.getId() + "，单号=" + latest.getOrderNo() : "";
            return idInfo + "，仓库ID=" + warehouseId
                    + "，共 " + items.size() + " 种商品，状态：待审核。"
                    + "审核时请使用该入库单ID调用 auditInboundOrder；审核通过库存将自动增加。";
        } catch (Exception e) {
            return error("创建入库单失败", e);
        }
    }

    @Tool(description = "审核入库单。approved=true 审核通过并自动增加库存；approved=false 驳回。仅待审核(status=0)单据可操作。注意参数 orderId 是入库单数字ID（创建成功时会返回），不是单号字符串。")
    public String auditInboundOrder(
            @ToolParam(description = "入库单数字ID，必填（创建入库单时返回的ID；也可用 listInboundOrders 查询）") Long orderId,
            @ToolParam(description = "true=审核通过(库存增加)，false=驳回") Boolean approved) {
        try {
            if (!hasPerm("wms:inbound")) {
                return "权限不足：当前用户没有入库管理权限（wms:inbound），无法审核入库单。";
            }
            InboundOrder order = inboundService.getById(orderId);
            if (order == null) {
                return "入库单不存在：ID=" + orderId + "。请用 listInboundOrders 查询正确的待审核单据ID。";
            }
            if (order.getStatus() != 0) {
                return "入库单 " + order.getOrderNo() + "（ID=" + orderId + "）当前状态为「"
                        + statusText(order.getStatus(), new String[]{"待审核", "已入库", "已驳回"})
                        + "」，无需重复审核。若要审核其他单据，请用 listInboundOrders(0) 查询待审核列表。";
            }
            inboundService.audit(orderId, Boolean.TRUE.equals(approved), operator);
            return Boolean.TRUE.equals(approved)
                    ? "入库单 " + order.getOrderNo() + "（ID=" + orderId + "）审核通过，对应仓库库存已自动增加，并已记录库存流水。"
                    : "入库单 " + order.getOrderNo() + "（ID=" + orderId + "）已驳回，库存未发生变化。";
        } catch (Exception e) {
            return error("审核入库单失败", e);
        }
    }

    // ============================ 出库管理 ============================

    @Tool(description = "查询出库单列表。status：0待审核 1已出库 2已驳回，不传则返回全部最新单据。")
    public String listOutboundOrders(
            @ToolParam(required = false, description = "单据状态：0待审核 1已出库 2已驳回，可为空") Integer status) {
        try {
            List<OutboundOrder> list = outboundService.lambdaQuery()
                    .eq(status != null, OutboundOrder::getStatus, status)
                    .orderByDesc(OutboundOrder::getId)
                    .last("LIMIT " + LIMIT)
                    .list();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (OutboundOrder o : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", o.getId());
                row.put("orderNo", o.getOrderNo());
                row.put("warehouseId", o.getWarehouseId());
                row.put("customerId", o.getCustomerId());
                row.put("totalAmount", o.getTotalAmount());
                row.put("status", statusText(o.getStatus(), new String[]{"待审核", "已出库", "已驳回"}));
                row.put("createBy", o.getCreateBy());
                row.put("createTime", o.getCreateTime());
                rows.add(row);
            }
            return toJson(rows);
        } catch (Exception e) {
            return error("查询出库单失败", e);
        }
    }

    @Tool(description = "创建出库单（状态为待审核）。warehouseId 和 items 必填；创建后需再调用 auditOutboundOrder 审核通过才会扣减库存（库存不足会失败）。")
    public String createOutboundOrder(
            @ToolParam(description = "出库仓库ID，必填（可先用 listWarehouses 查询）") Long warehouseId,
            @ToolParam(description = "出库明细JSON数组字符串，每项含 productId 或 productCode、quantity(数量)、price(单价,可选)。"
                    + "示例：[{\"productCode\":\"P001\",\"quantity\":20},{\"productId\":3,\"quantity\":10}]") String itemsJson,
            @ToolParam(required = false, description = "客户ID，可为空（可先用 listCustomers 查询）") Long customerId,
            @ToolParam(required = false, description = "备注，可为空") String remark) {
        try {
            if (!hasPerm("wms:outbound")) {
                return "权限不足：当前用户没有出库管理权限（wms:outbound），无法创建出库单。";
            }
            List<ItemForm> items = parseItems(itemsJson);
            if (items.isEmpty()) {
                return "参数错误：出库明细不能为空，itemsJson 需为非空JSON数组。";
            }
            OutboundForm form = new OutboundForm();
            form.setWarehouseId(warehouseId);
            form.setCustomerId(customerId);
            form.setRemark(remark);
            form.setItems(items);
            outboundService.create(operator, form);
            OutboundOrder latest = outboundService.lambdaQuery()
                    .eq(OutboundOrder::getWarehouseId, warehouseId)
                    .orderByDesc(OutboundOrder::getId).last("LIMIT 1").one();
            String idInfo = latest != null ? "出库单ID=" + latest.getId() + "，单号=" + latest.getOrderNo() : "";
            return idInfo + "，仓库ID=" + warehouseId
                    + "，共 " + items.size() + " 种商品，状态：待审核。"
                    + "审核时请使用该出库单ID调用 auditOutboundOrder；审核通过将扣减库存（库存不足会失败）。";
        } catch (Exception e) {
            return error("创建出库单失败", e);
        }
    }

    @Tool(description = "审核出库单。approved=true 审核通过并自动扣减库存（库存不足将失败）；approved=false 驳回。仅待审核(status=0)单据可操作。注意参数 orderId 是出库单数字ID（创建成功时会返回），不是单号字符串。")
    public String auditOutboundOrder(
            @ToolParam(description = "出库单数字ID，必填（创建出库单时返回的ID；也可用 listOutboundOrders 查询）") Long orderId,
            @ToolParam(description = "true=审核通过(扣减库存)，false=驳回") Boolean approved) {
        try {
            if (!hasPerm("wms:outbound")) {
                return "权限不足：当前用户没有出库管理权限（wms:outbound），无法审核出库单。";
            }
            OutboundOrder order = outboundService.getById(orderId);
            if (order == null) {
                return "出库单不存在：ID=" + orderId + "。请用 listOutboundOrders 查询正确的待审核单据ID。";
            }
            if (order.getStatus() != 0) {
                return "出库单 " + order.getOrderNo() + "（ID=" + orderId + "）当前状态为「"
                        + statusText(order.getStatus(), new String[]{"待审核", "已出库", "已驳回"})
                        + "」，无需重复审核。若要审核其他单据，请用 listOutboundOrders(0) 查询待审核列表。";
            }
            outboundService.audit(orderId, Boolean.TRUE.equals(approved), operator);
            return Boolean.TRUE.equals(approved)
                    ? "出库单 " + order.getOrderNo() + "（ID=" + orderId + "）审核通过，对应仓库库存已自动扣减，并已记录库存流水。"
                    : "出库单 " + order.getOrderNo() + "（ID=" + orderId + "）已驳回，库存未发生变化。";
        } catch (Exception e) {
            return error("审核出库单失败", e);
        }
    }

    // ============================ 文件导入 ============================

    @Tool(description = "查看用户上传的待导入文件（Excel/CSV）。用户消息中出现（fileToken=xxx）时，先用本工具查看列结构、行数、前几行样例和识别出的导入类型，再与用户确认导入方式。")
    public String inspectUploadedFile(
            @ToolParam(description = "文件令牌，来自用户消息中的 fileToken=xxx") String fileToken) {
        try {
            FileImportService.StagedFile f = fileImportService.get(fileToken);
            if (f == null) {
                return "文件不存在或已过期（有效期30分钟），请让用户重新上传。";
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("fileToken", f.getToken());
            data.put("fileName", f.getFileName());
            data.put("rowCount", f.getRows().size());
            data.put("columns", f.getHeaders());
            data.put("detectedType", f.getDetectedType());
            data.put("detectedTypeDesc", FileImportService.TYPE_STOCK.equals(f.getDetectedType())
                    ? "库存入库导入（包含数量列）" : "商品档案导入");
            data.put("preview", f.getRows().stream().limit(5).toList());
            return toJson(data);
        } catch (Exception e) {
            return error("查看上传文件失败", e);
        }
    }

    @Tool(description = "将用户上传的文件导入为商品档案（新增或更新商品）。要求文件包含「商品名称」列；有「商品编码」列且编码已存在时可覆盖更新。执行前需与用户确认。")
    public String commitProductImport(
            @ToolParam(description = "文件令牌，来自用户消息中的 fileToken=xxx") String fileToken,
            @ToolParam(required = false, description = "遇到已存在的商品编码时是否覆盖更新，默认 true") Boolean updateExisting) {
        try {
            if (!hasPerm("base:product")) {
                return "权限不足：当前用户没有商品管理权限（base:product），无法导入商品。";
            }
            FileImportService.StagedFile f = fileImportService.get(fileToken);
            if (f == null) {
                return "文件不存在或已过期（有效期30分钟），请让用户重新上传。";
            }
            Map<String, Object> result = fileImportService.commitProducts(f, !Boolean.FALSE.equals(updateExisting));
            return "商品档案导入完成：" + toJson(result);
        } catch (Exception e) {
            return error("商品导入失败", e);
        }
    }

    @Tool(description = "将用户上传的文件导入为入库单（批量库存导入）。要求文件包含「数量」列和「商品编码/商品名称」列；可全局指定仓库ID，或文件含「仓库」列按行指定。默认导入后自动审核（库存立即增加）。执行前需与用户确认。")
    public String commitStockImport(
            @ToolParam(description = "文件令牌，来自用户消息中的 fileToken=xxx") String fileToken,
            @ToolParam(required = false, description = "目标仓库ID（文件中没有仓库列时必填；可先用 listWarehouses 查询）") Long warehouseId,
            @ToolParam(required = false, description = "供应商ID，可为空") Long supplierId,
            @ToolParam(required = false, description = "导入后是否自动审核入库（库存立即增加），默认 true") Boolean autoAudit,
            @ToolParam(required = false, description = "商品不存在时是否自动创建新商品档案，默认 false") Boolean autoCreateProduct) {
        try {
            if (!hasPerm("wms:inbound")) {
                return "权限不足：当前用户没有入库管理权限（wms:inbound），无法导入库存。";
            }
            FileImportService.StagedFile f = fileImportService.get(fileToken);
            if (f == null) {
                return "文件不存在或已过期（有效期30分钟），请让用户重新上传。";
            }
            Map<String, Object> result = fileImportService.commitStock(f, operator, warehouseId, supplierId,
                    !Boolean.FALSE.equals(autoAudit), Boolean.TRUE.equals(autoCreateProduct));
            return "库存导入完成：" + toJson(result);
        } catch (Exception e) {
            return error("库存导入失败", e);
        }
    }

    // ============================ 物流运输 ============================

    @Tool(description = "查询物流订单列表。status：0待调度 1运输中 2已签收 3已取消，不传则返回全部最新订单。")
    public String listLogisticsOrders(
            @ToolParam(required = false, description = "订单状态：0待调度 1运输中 2已签收 3已取消，可为空") Integer status) {
        try {
            List<LogisticsOrder> list = logisticsOrderService.lambdaQuery()
                    .eq(status != null, LogisticsOrder::getStatus, status)
                    .orderByDesc(LogisticsOrder::getId)
                    .last("LIMIT " + LIMIT)
                    .list();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (LogisticsOrder o : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", o.getId());
                row.put("orderNo", o.getOrderNo());
                row.put("receiverName", o.getReceiverName());
                row.put("receiverAddress", o.getReceiverAddress());
                row.put("vehicleId", o.getVehicleId());
                row.put("driverName", o.getDriverName());
                row.put("status", statusText(o.getStatus(), new String[]{"待调度", "运输中", "已签收", "已取消"}));
                rows.add(row);
            }
            return toJson(rows);
        } catch (Exception e) {
            return error("查询物流订单失败", e);
        }
    }

    // ============================ 库位 / 盘点 / 任务 / 批次效期 / KPI ============================

    @Tool(description = "查询指定仓库的库位列表。zone 库区可选：RECEIVE收货区/STORE存储区/PICK拣选区/DEFECT残次区/SHIP发货区。返回库位ID、编码、库区、容量、状态。")
    public String listLocations(
            @ToolParam(description = "仓库ID，必填（可先用 listWarehouses 查询）") Long warehouseId,
            @ToolParam(required = false, description = "库区编码：RECEIVE/STORE/PICK/DEFECT/SHIP，可为空") String zone) {
        try {
            List<Location> list = locationService.lambdaQuery()
                    .eq(Location::getWarehouseId, warehouseId)
                    .eq(StringUtils.hasText(zone), Location::getZone, zone)
                    .eq(Location::getStatus, 1)
                    .orderByAsc(Location::getCode)
                    .last("LIMIT 50")
                    .list();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Location l : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", l.getId());
                row.put("code", l.getCode());
                row.put("zone", LocationService.zoneName(l.getZone()));
                row.put("capacity", l.getCapacity());
                row.put("used", l.getUsed());
                rows.add(row);
            }
            return toJson(rows);
        } catch (Exception e) {
            return error("查询库位失败", e);
        }
    }

    @Tool(description = "为某仓库智能推荐一个上架库位（优先拣选区空位，其次存储区）。返回推荐库位编码，用于安排上架。")
    public String recommendLocation(
            @ToolParam(description = "仓库ID，必填") Long warehouseId) {
        try {
            Location l = locationService.recommend(warehouseId);
            return "推荐上架库位：" + l.getCode() + "（" + LocationService.zoneName(l.getZone()) + "），库位ID=" + l.getId();
        } catch (Exception e) {
            return error("推荐库位失败", e);
        }
    }

    @Tool(description = "创建库存盘点单（会快照当前库存账面数量）。type：1全盘 2循环盘，默认1。创建后生成盘点任务。执行前需与用户确认。")
    public String createStocktake(
            @ToolParam(description = "盘点仓库ID，必填") Long warehouseId,
            @ToolParam(required = false, description = "盘点类型：1全盘（默认）2循环盘") Integer type) {
        try {
            if (!hasPerm("wms:stocktake")) {
                return "无权限：当前用户没有盘点管理（wms:stocktake）权限。";
            }
            Long id = stocktakeService.create(operator, warehouseId, type == null ? 1 : type, "AI助手创建盘点单");
            return "盘点单已创建，ID=" + id + "，已生成盘点任务。请在「盘点管理」中录入实盘数量后审核，盘盈盘亏将自动调整库存。";
        } catch (Exception e) {
            return error("创建盘点单失败", e);
        }
    }

    @Tool(description = "查询作业任务列表（上架/拣选/盘点/移库/补货/退货质检）。type：1上架 2拣选 3盘点 4移库 5补货 6退货质检；status：0待分配 1待执行 2进行中 3已完成 4已取消；均可不传。")
    public String listTasks(
            @ToolParam(required = false, description = "任务类型 1上架 2拣选 3盘点 4移库 5补货 6退货质检，可为空") Integer type,
            @ToolParam(required = false, description = "任务状态 0待分配 1待执行 2进行中 3已完成 4已取消，可为空") Integer status) {
        try {
            List<WmsTask> list = taskService.lambdaQuery()
                    .eq(type != null, WmsTask::getType, type)
                    .eq(status != null, WmsTask::getStatus, status)
                    .orderByDesc(WmsTask::getId)
                    .last("LIMIT " + LIMIT)
                    .list();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (WmsTask t : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", t.getId());
                row.put("taskNo", t.getTaskNo());
                row.put("type", TaskService.typeName(t.getType()));
                row.put("content", t.getContent());
                row.put("assignee", t.getAssigneeName() == null ? "未分配" : t.getAssigneeName());
                row.put("status", TaskService.statusName(t.getStatus()));
                rows.add(row);
            }
            return toJson(rows);
        } catch (Exception e) {
            return error("查询任务失败", e);
        }
    }

    @Tool(description = "呆滞库存分析：列出近180天无出库记录且仍有库存的商品（占用资金），按库存金额降序，辅助库存周转决策。")
    public String dormantStockAnalysis() {
        try {
            Map<String, Object> kpi = dashboardService.kpi();
            Object turnover = kpi.get("turnoverRate30");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dormant = (List<Map<String, Object>>) kpi.get("dormantTop10");
            StringBuilder sb = new StringBuilder();
            sb.append("近30天库存周转率：").append(turnover).append("。\n");
            sb.append("呆滞库存（近180天无出库）共 ").append(dormant == null ? 0 : dormant.size()).append(" 个商品（TOP10）：\n");
            if (dormant != null) {
                for (Map<String, Object> d : dormant) {
                    sb.append("- ").append(d.get("productCode")).append(" ").append(d.get("productName"))
                            .append("：库存 ").append(d.get("quantity")).append("，占用金额约 ").append(d.get("stockValue")).append(" 元\n");
                }
            }
            sb.append("建议：对呆滞商品开展促销、调拨或退货处理，释放仓储空间与资金。");
            return sb.toString();
        } catch (Exception e) {
            return error("呆滞库存分析失败", e);
        }
    }

    @Tool(description = "查询近效期批次预警：列出90天内到期或已过期的入库批次商品（FEFO先到期先出依据）。可按仓库过滤。")
    public String listNearExpiryBatches(
            @ToolParam(required = false, description = "仓库ID，不传则查全部仓库") Long warehouseId) {
        try {
            List<Map<String, Object>> rows = batchService.batchList(warehouseId, true);
            if (rows.isEmpty()) {
                return "未发现90天内到期或已过期的批次商品，效期状况良好。";
            }
            StringBuilder sb = new StringBuilder("近效期/过期批次共 ").append(rows.size()).append(" 条：\n");
            for (Map<String, Object> r : rows) {
                sb.append("- ").append(r.get("warehouseName")).append(" ").append(r.get("productCode"))
                        .append(" ").append(r.get("productName")).append("（批次 ").append(r.get("batchNo"))
                        .append("）：数量 ").append(r.get("quantity")).append("，到期日 ").append(r.get("expiryDate"));
                if (Boolean.TRUE.equals(r.get("expired"))) {
                    sb.append("【已过期】");
                } else {
                    sb.append("，剩余 ").append(r.get("daysLeft")).append(" 天");
                }
                sb.append("\n");
            }
            sb.append("建议：优先出库近效期批次（FEFO），过期品转入残次区/报损。");
            return sb.toString();
        } catch (Exception e) {
            return error("查询近效期批次失败", e);
        }
    }

    // ============================ 内部辅助 ============================

    private boolean hasPerm(String perm) {
        return operator != null && operator.getPerms() != null && operator.getPerms().contains(perm);
    }

    /** 解析明细 JSON：支持 productId 或 productCode；price 可选（为空时取商品默认价） */
    @SuppressWarnings("unchecked")
    private List<ItemForm> parseItems(String itemsJson) throws Exception {
        List<ItemForm> result = new ArrayList<>();
        if (!StringUtils.hasText(itemsJson)) {
            return result;
        }
        List<Map<String, Object>> arr = objectMapper.readValue(itemsJson, List.class);
        for (Map<String, Object> m : arr) {
            ItemForm item = new ItemForm();
            Object pid = m.get("productId");
            Object pcode = m.get("productCode");
            if (pid != null) {
                item.setProductId(Long.valueOf(pid.toString()));
            } else if (pcode != null) {
                Product p = productService.lambdaQuery().eq(Product::getCode, pcode.toString()).one();
                if (p == null) {
                    throw new IllegalArgumentException("商品编码不存在：" + pcode);
                }
                item.setProductId(p.getId());
            } else {
                throw new IllegalArgumentException("明细必须包含 productId 或 productCode");
            }
            Object qty = m.get("quantity");
            if (qty == null || Integer.parseInt(qty.toString()) <= 0) {
                throw new IllegalArgumentException("商品数量必须大于0：" + m);
            }
            item.setQuantity(Integer.parseInt(qty.toString()));
            Object price = m.get("price");
            if (price != null) {
                item.setPrice(new BigDecimal(price.toString()));
            }
            result.add(item);
        }
        return result;
    }

    /** 生成不重复的商品编码 P + 3位序号 */
    private String genProductCode() {
        long n = productService.count() + 1;
        String code;
        do {
            code = "P" + String.format("%03d", n++);
        } while (productService.lambdaQuery().eq(Product::getCode, code).exists());
        return code;
    }

    private String statusText(Integer status, String[] texts) {
        if (status == null || status < 0 || status >= texts.length) {
            return "未知";
        }
        return texts[status];
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String error(String action, Exception e) {
        log.warn("AI工具执行失败[{}]：{}", action, e.getMessage());
        return action + "：" + e.getMessage() + "。请向用户说明失败原因，必要时请用户修正参数后重试。";
    }
}
