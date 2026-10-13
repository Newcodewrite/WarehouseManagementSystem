package com.lanxi.wms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanxi.wms.ai.WmsTools;
import com.lanxi.wms.common.BusinessException;
import com.lanxi.wms.dto.ChatForm;
import com.lanxi.wms.entity.LogTrack;
import com.lanxi.wms.entity.LogisticsOrder;
import com.lanxi.wms.entity.Vehicle;
import com.lanxi.wms.mapper.LogTrackMapper;
import com.lanxi.wms.mapper.VehicleMapper;
import com.lanxi.wms.security.LoginUser;
import com.lanxi.wms.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 智能服务：
 * 1) 智能管家对话——基于 DeepSeek（OpenAI 兼容协议）+ Function Calling，
 *    可通过工具实际操作系统：商品增删改查、库存查询、入库/出库单创建与审核、物流查询；
 * 2) 库存补货建议 / 物流轨迹解读 / 运输调度建议。
 * 未配置 API Key 或调用失败时，自动降级为本地规则回复，保证系统可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private static final String OFFLINE_NOTE = "\n（离线模式回复：未检测到有效的 DeepSeek API Key 或调用失败）";

    /** 智能管家系统提示词（工具调用模式） */
    private static final String AGENT_SYSTEM = """
            你是“智能物流仓储管理系统（WMS）”的AI管家，名字叫小仓。你可以通过系统提供的工具直接帮用户管理系统数据，能力包括：
            - 商品管理：查询、新增、修改、删除商品
            - 基础档案：查询仓库、供应商、客户
            - 库存管理：查询库存、查看库存预警
            - 入库管理：创建入库单、审核入库单（审核通过后系统会自动增加对应仓库库存）
            - 出库管理：创建出库单、审核出库单（审核通过后系统会自动扣减库存，库存不足会失败）
            - 物流管理：查询物流订单状态
            - 库位管理：查询仓库库位、为入库智能推荐上架库位
            - 作业任务：查询上架/拣选/盘点/移库/补货/退货质检任务（入库审核后自动生成上架任务、出库审核后自动生成拣选任务）
            - 盘点管理：创建库存盘点单（全盘/循环盘），盘盈盘亏审核后自动调整库存
            - 智能分析：呆滞库存分析（近180天无出库的积压商品与占用资金）、近效期批次预警（90天内到期/已过期，FEFO先到期先出）
            - 文件批量导入：用户消息中出现（fileToken=xxx）时代表上传了 Excel/CSV 文件。流程：
              ① 调用 inspectUploadedFile 查看列结构、行数与前几行样例，用中文向用户汇报识别出的导入类型（商品档案导入 / 库存入库导入）；
              ② 与用户确认导入方式（商品：编码重复时是否覆盖更新；库存：目标仓库、是否导入后自动审核、商品缺失时是否自动建档）；
              ③ 确认后调用 commitProductImport 或 commitStockImport 执行导入，并向用户汇报成功/失败统计与失败原因。

            工作规则：
            1. 一律使用中文回答，简洁专业，像一个熟练的仓储调度员。
            2. 【最高优先级】所有增删改查操作必须通过调用对应的工具函数来执行，严禁在未调用工具的情况下声称"已删除/已创建/已修改/已审核"等操作结果。如果你没有实际调用工具，就绝对不能说操作已完成。只有工具返回的结果才是真实结果。
            3. 缺少必要信息（如商品、仓库、数量、单号）时，先向用户询问，不要臆造ID；可先调用查询工具获取候选列表再与用户确认。
            4. 涉及【审核出入库单】【删除商品】这类重要或不可逆操作时，必须先向用户复述关键信息（单号/商品/数量/仓库）并征得明确同意，得到"确认/可以/同意"等肯定答复后再调用工具执行。
            5. 数据类问题必须以工具返回结果为准，严禁编造数据。
            6. 工具执行完成后，用自然语言明确告知用户结果（成功/失败、单号、库存变化、失败原因），回复中应引用工具返回的具体数据。
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final InventoryService inventoryService;
    private final LogisticsOrderService logisticsOrderService;
    private final LogTrackMapper trackMapper;
    private final VehicleMapper vehicleMapper;
    private final ProductService productService;
    private final WarehouseService warehouseService;
    private final SupplierService supplierService;
    private final CustomerService customerService;
    private final InboundService inboundService;
    private final OutboundService outboundService;
    private final FileImportService fileImportService;
    private final LocationService locationService;
    private final StocktakeService stocktakeService;
    private final TaskService taskService;
    private final BatchService batchService;
    private final DashboardService dashboardService;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    private ChatClient chatClient;

    /**
     * 智能管家对话（支持工具调用，可实际操作系统数据）
     */
    public Map<String, Object> chat(ChatForm form) {
        LoginUser user = SecurityUtil.getLoginUser();
        if (aiEnabled()) {
            String ai = callAgent(form, user);
            if (ai != null) {
                return Map.of("reply", ai, "mode", "ai");
            }
        }
        return Map.of("reply", fallbackChat(form.getMessage()), "mode", "rule");
    }

    /**
     * 库存预警与补货建议
     */
    public Map<String, Object> stockAdvice() {
        List<Map<String, Object>> alerts = inventoryService.alertList();
        String ai = null;
        if (!alerts.isEmpty()) {
            StringBuilder data = new StringBuilder("库存预警数据：");
            for (Map<String, Object> a : alerts) {
                data.append("\n- 仓库[").append(a.get("warehouseName"))
                        .append("] 商品[").append(a.get("productName"))
                        .append("] 当前库存").append(a.get("quantity"))
                        .append("，预警值").append(a.get("warnStock"));
            }
            ai = call("你是仓储管理专家，请根据库存预警数据给出简洁的中文补货建议，按优先级排序，给出建议补货数量和理由。", data.toString());
        }
        String reply;
        if (ai != null) {
            reply = ai;
        } else if (alerts.isEmpty()) {
            reply = "当前所有商品库存充足，暂无补货需求。";
        } else {
            StringBuilder sb = new StringBuilder("检测到 ").append(alerts.size()).append(" 条库存预警：");
            for (Map<String, Object> a : alerts) {
                sb.append("\n- ").append(a.get("warehouseName")).append(" / ").append(a.get("productName"))
                        .append("：当前 ").append(a.get("quantity"))
                        .append("，预警值 ").append(a.get("warnStock"))
                        .append("，建议补货 ").append(a.get("suggestQty"));
            }
            sb.append(OFFLINE_NOTE);
            reply = sb.toString();
        }
        return Map.of("reply", reply, "mode", ai != null ? "ai" : "rule");
    }

    /**
     * 物流轨迹智能解读
     */
    public Map<String, Object> trackExplain(Long orderId) {
        LogisticsOrder order = logisticsOrderService.getById(orderId);
        if (order == null) {
            throw new BusinessException("物流订单不存在");
        }
        List<LogTrack> tracks = trackMapper.selectList(new LambdaQueryWrapper<LogTrack>()
                .eq(LogTrack::getOrderId, orderId)
                .orderByAsc(LogTrack::getTrackTime));
        if (tracks.isEmpty()) {
            return Map.of("reply", "该订单暂无物流轨迹信息。", "mode", "rule");
        }
        StringBuilder data = new StringBuilder("物流单号：").append(order.getOrderNo())
                .append("，收货人：").append(order.getReceiverName())
                .append("，收货地址：").append(order.getReceiverAddress())
                .append("。轨迹记录：");
        for (LogTrack t : tracks) {
            data.append("\n- ").append(t.getTrackTime()).append(" [").append(t.getLocation()).append("] ").append(t.getContent());
        }
        String ai = call("你是物流客服，请根据物流轨迹信息，用自然生动的中文向用户解读当前物流状态和预计情况，不超过150字。", data.toString());
        LogTrack latest = tracks.get(tracks.size() - 1);
        String fallback = "订单 " + order.getOrderNo() + " 最新状态：[" + latest.getLocation() + "] "
                + latest.getContent() + "，已产生 " + tracks.size() + " 条轨迹记录。" + OFFLINE_NOTE;
        String reply = ai != null ? ai : fallback;
        return Map.of("reply", reply, "mode", ai != null ? "ai" : "rule");
    }

    /**
     * 运输调度优化建议
     */
    public Map<String, Object> scheduleAdvice() {
        List<LogisticsOrder> pending = logisticsOrderService.lambdaQuery()
                .eq(LogisticsOrder::getStatus, 0).list();
        List<Vehicle> idle = vehicleMapper.selectList(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getStatus, 0));

        StringBuilder data = new StringBuilder();
        data.append("待调度订单 ").append(pending.size()).append(" 个：");
        for (LogisticsOrder o : pending) {
            data.append("\n- ").append(o.getOrderNo()).append(" 收货地址：").append(o.getReceiverAddress());
        }
        data.append("。空闲车辆 ").append(idle.size()).append(" 辆：");
        for (Vehicle v : idle) {
            data.append("\n- ").append(v.getCode()).append(" ").append(v.getVehicleType())
                    .append(" 载重").append(v.getCapacityKg()).append("kg 司机").append(v.getDriverName());
        }
        String ai = call("你是物流调度专家，请根据待调度订单与空闲车辆信息，给出合理的车辆分配调度建议（中文，简洁）。", data.toString());

        StringBuilder fallback = new StringBuilder();
        if (pending.isEmpty()) {
            fallback.append("当前没有待调度订单，所有车辆安排就绪。");
        } else if (idle.isEmpty()) {
            fallback.append("当前没有空闲车辆，共有 ").append(pending.size()).append(" 个订单待调度，建议尽快安排车辆回仓。");
        } else {
            fallback.append("建议调度方案：");
            int n = Math.min(pending.size(), idle.size());
            for (int i = 0; i < n; i++) {
                fallback.append("\n- 订单 ").append(pending.get(i).getOrderNo())
                        .append(" → 车辆 ").append(idle.get(i).getCode())
                        .append("（司机：").append(idle.get(i).getDriverName()).append("）");
            }
            if (pending.size() > n) {
                fallback.append("。剩余 ").append(pending.size() - n).append(" 个订单等待空闲车辆。");
            }
        }
        fallback.append(OFFLINE_NOTE);
        String reply = ai != null ? ai : fallback.toString();
        return Map.of("reply", reply, "mode", ai != null ? "ai" : "rule");
    }

    // ============================ 内部方法 ============================

    /** DeepSeek 是否可用（API Key 已配置且不是占位符） */
    private boolean aiEnabled() {
        return StringUtils.hasText(apiKey) && !apiKey.startsWith("sk-please-config");
    }

    /**
     * 工具调用模式对话：注册 WMS 管理工具，由大模型自主决定调用
     */
    private String callAgent(ChatForm form, LoginUser user) {
        try {
            log.info("AI工具模式对话开始，用户={}，消息长度={}", user != null ? user.getUsername() : "null", form.getMessage() != null ? form.getMessage().length() : 0);
            ChatClient client = getChatClient();
            List<Message> history = new ArrayList<>();
            if (form.getHistory() != null) {
                for (Map<String, String> h : form.getHistory()) {
                    String content = h.get("content");
                    if (!StringUtils.hasText(content)) {
                        continue;
                    }
                    if ("assistant".equals(h.get("role"))) {
                        history.add(new AssistantMessage(content));
                    } else {
                        history.add(new UserMessage(content));
                    }
                }
            }
            String system = AGENT_SYSTEM + "\n当前登录用户："
                    + (user != null ? user.getNickname() + "（账号：" + user.getUsername() + "）" : "未知")
                    + "。当前时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "。";

            WmsTools tools = new WmsTools(user, productService, warehouseService, supplierService,
                    customerService, inventoryService, inboundService, outboundService,
                    logisticsOrderService, fileImportService,
                    locationService, stocktakeService, taskService, batchService, dashboardService,
                    objectMapper);

            String reply = client.prompt()
                    .system(system)
                    .messages(history)
                    .user(form.getMessage())
                    .tools(tools)
                    .call()
                    .content();
            log.info("AI工具模式对话完成，回复长度={}", reply != null ? reply.length() : 0);
            return reply;
        } catch (Exception e) {
            log.error("AI工具模式对话异常", e);
            log.warn("AI 管家对话失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 调用大模型（纯文本），失败返回 null（自动降级）
     */
    private String call(String system, String user) {
        if (!aiEnabled()) {
            return null;
        }
        try {
            return getChatClient().prompt()
                    .system(system)
                    .user(user)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("AI 调用失败：{}", e.getMessage());
            return null;
        }
    }

    private ChatClient getChatClient() {
        if (chatClient == null) {
            chatClient = chatClientBuilder.build();
        }
        return chatClient;
    }

    private String fallbackChat(String message) {
        String m = message == null ? "" : message;
        if (m.contains("库存") || m.contains("缺货") || m.contains("补货")) {
            return "库存查询结果：" + alertSummary() + OFFLINE_NOTE;
        }
        if (m.contains("订单") || m.contains("物流") || m.contains("运输") || m.contains("轨迹")) {
            long transit = logisticsOrderService.lambdaQuery().eq(LogisticsOrder::getStatus, 1).count();
            long dispatch = logisticsOrderService.lambdaQuery().eq(LogisticsOrder::getStatus, 0).count();
            return "当前共有 " + transit + " 个订单在途，" + dispatch
                    + " 个订单待调度。您可以在「物流订单」页面查看详情与轨迹。" + OFFLINE_NOTE;
        }
        if (m.contains("入库") || m.contains("出库") || m.contains("商品")) {
            return "您可以在「商品管理」「入库管理」「出库管理」页面直接操作。"
                    + "配置 DeepSeek API Key（环境变量 AI_API_KEY）后，我也可以直接帮您创建单据、审核出入库、管理商品。" + OFFLINE_NOTE;
        }
        return "你好，我是智能物流仓储管家小仓。配置 DeepSeek API Key 后，我可以直接帮您查询商品库存、"
                + "创建并审核出入库单、管理商品档案。" + OFFLINE_NOTE;
    }

    private String alertSummary() {
        List<Map<String, Object>> alerts = inventoryService.alertList();
        if (alerts.isEmpty()) {
            return "当前所有商品库存充足，暂无预警。";
        }
        StringBuilder sb = new StringBuilder("共有 ").append(alerts.size()).append(" 条库存预警：");
        for (Map<String, Object> a : alerts) {
            sb.append("\n- ").append(a.get("productName")).append("（").append(a.get("warehouseName"))
                    .append("）当前 ").append(a.get("quantity")).append("，预警值 ").append(a.get("warnStock"));
        }
        return sb.toString();
    }
}
