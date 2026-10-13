-- ============================================================
-- AI 对话测试数据 — 覆盖 16 个 @Tool 全场景
-- 执行前确保 init.sql + upgrade_wms_v2.sql 已导入
-- 幂等设计：每条 INSERT 带 NOT EXISTS 防重
-- ============================================================

-- ============================================================
-- 一、新增商品（丰富商品库，含多分类/价格区间，供 searchProducts 测试）
-- ============================================================
INSERT INTO base_product (id, code, name, category, unit, price, warn_stock, status)
SELECT * FROM (
  SELECT 7 id, 'P007' code, '车载吸尘器' name, '汽车用品' category, '台' unit, 159.00 price, 20 warn_stock, 1 status
  UNION ALL SELECT 8, 'P008', '防静电手套', '劳保用品', '双', 12.50, 100, 1
  UNION ALL SELECT 9, 'P009', '钢制货架', '仓储设备', '组', 450.00, 5, 1
  UNION ALL SELECT 10, 'P010', '托盘搬运车', '仓储设备', '台', 1200.00, 3, 1
  UNION ALL SELECT 11, 'P011', '条码扫描枪', '电子产品', '把', 89.00, 15, 1
  UNION ALL SELECT 12, 'P012', '防潮干燥剂', '劳保用品', '包', 6.80, 200, 1
  UNION ALL SELECT 13, 'P013', '仓库警示带', '劳保用品', '卷', 3.50, 50, 1
  UNION ALL SELECT 14, 'P014', '电子温湿度计', '电子产品', '个', 35.00, 10, 1
  UNION ALL SELECT 15, 'P015', '无线理货灯', '仓储设备', '盏', 78.00, 8, 1
  UNION ALL SELECT 16, 'P016', '防滑地垫', '劳保用品', '卷', 45.00, 30, 1
  UNION ALL SELECT 17, 'P017', '安全帽', '劳保用品', '顶', 25.00, 50, 1
  UNION ALL SELECT 18, 'P018', '反光背心', '劳保用品', '件', 18.00, 60, 1
  UNION ALL SELECT 19, 'P019', '灭火器', '安防设备', '个', 85.00, 10, 1
  UNION ALL SELECT 20, 'P020', '物流周转箱', '仓储设备', '个', 32.00, 80, 1
) seed
WHERE NOT EXISTS (SELECT 1 FROM base_product WHERE id = seed.id);

-- ============================================================
-- 二、新增供应商和客户（供 listSuppliers / listCustomers 模糊搜索测试）
-- ============================================================
INSERT INTO base_supplier (id, code, name, contact_name, contact_phone, address)
SELECT * FROM (
  SELECT 3 id, 'SUP003' code, '宁波速达物流设备' name, '周经理' contact_name, '13900003333' contact_phone, '浙江省宁波市鄞州区物流产业园' address
  UNION ALL SELECT 4, 'SUP004', '杭州安安保器材', '吴经理', '13900004444', '浙江省杭州市萧山区安防大道'
  UNION ALL SELECT 5, 'SUP005', '上海容大包装材料' name, '郑经理', '13900005555', '上海市嘉定区包装路66号'
) seed
WHERE NOT EXISTS (SELECT 1 FROM base_supplier WHERE id = seed.id);

INSERT INTO base_customer (id, code, name, contact_name, contact_phone, address)
SELECT * FROM (
  SELECT 3 id, 'CUS003' code, '苏州工业园区零售' name, '钱总' contact_name, '13700003333' contact_phone, '江苏省苏州市工业园区星海街' address
  UNION ALL SELECT 4, 'CUS004', '合肥政务区商超', '卫总', '13700004444', '安徽省合肥市政务区潜山路'
  UNION ALL SELECT 5, 'CUS005', '宁波北仑区批发', '蒋总', '13700005555', '浙江省宁波市北仑区明州路'
) seed
WHERE NOT EXISTS (SELECT 1 FROM base_customer WHERE id = seed.id);

-- ============================================================
-- 三、库存与库位回填（含呆滞库存场景：P009/P010 有库存但无出库记录）
-- ============================================================

-- 新商品的初始库存（杭州中心仓）
INSERT INTO wms_inventory (warehouse_id, product_id, quantity, location_id)
SELECT 1, p.id, qty, (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1)
FROM (
  SELECT 7 id, 120 qty UNION ALL
  SELECT 8, 200 UNION ALL
  SELECT 9, 8 UNION ALL      -- 呆滞：钢制货架有8台但180天无出库，占用3600元
  SELECT 10, 5 UNION ALL     -- 呆滞：托盘搬运车有5台，占用6000元
  SELECT 11, 30 UNION ALL
  SELECT 12, 500 UNION ALL
  SELECT 13, 120 UNION ALL
  SELECT 14, 25 UNION ALL
  SELECT 15, 10 UNION ALL
  SELECT 16, 60 UNION ALL
  SELECT 17, 80 UNION ALL
  SELECT 18, 100 UNION ALL
  SELECT 19, 15 UNION ALL
  SELECT 20, 150
) p
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory WHERE warehouse_id=1 AND product_id=p.id);

-- 上海前置仓：部分商品库存低于预警值（供 listStockAlerts 测试）
-- P001 warn_stock=50, 库存40 → 预警
-- P003 warn_stock=100, 库存30 → 预警
-- 已在 init.sql 中存在，确保 location_id 已回填
UPDATE wms_inventory SET location_id = (SELECT id FROM wms_location WHERE warehouse_id=2 AND code='DEFAULT' LIMIT 1)
WHERE warehouse_id=2 AND location_id IS NULL;

-- 给上海仓也补一部分新商品低库存（触发预警）
INSERT INTO wms_inventory (warehouse_id, product_id, quantity, location_id)
SELECT 2, p.id, qty, (SELECT id FROM wms_location WHERE warehouse_id=2 AND code='DEFAULT' LIMIT 1)
FROM (
  SELECT 8 id, 5 qty UNION ALL     -- 防静电手套 warn=100, 库存5 → 预警
  SELECT 11 id, 3 qty UNION ALL    -- 条码扫描枪 warn=15, 库存3 → 预警
  SELECT 19 id, 2 qty              -- 灭火器 warn=10, 库存2 → 预警
) p
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory WHERE warehouse_id=2 AND product_id=p.id);

-- 初始流水（为新库存记录留痕）
INSERT INTO wms_inventory_record (warehouse_id, product_id, type, quantity, after_quantity, remark, create_by, location_id)
SELECT 1, p.id, 'IN', p.qty, p.qty, 'AI测试数据初始化', 'admin',
       (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1)
FROM (
  SELECT 7 id, 120 qty UNION ALL SELECT 8, 200 UNION ALL SELECT 9, 8 UNION ALL SELECT 10, 5
  UNION ALL SELECT 11, 30 UNION ALL SELECT 12, 500 UNION ALL SELECT 13, 120 UNION ALL SELECT 14, 25
  UNION ALL SELECT 15, 10 UNION ALL SELECT 16, 60 UNION ALL SELECT 17, 80 UNION ALL SELECT 18, 100
  UNION ALL SELECT 19, 15 UNION ALL SELECT 20, 150
) p
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory_record WHERE warehouse_id=1 AND product_id=p.id AND remark='AI测试数据初始化');

-- ============================================================
-- 四、入库单（含批次/效期数据，供 listNearExpiryBatches 测试）
-- 创建3张已审核入库单 + 1张待审核入库单
-- ============================================================

-- 入库单4：杭州仓，供应商1，已审核（含批次效期数据）
INSERT INTO wms_inbound_order (id, order_no, warehouse_id, supplier_id, total_amount, status, remark, create_by, audit_by, audit_time)
SELECT 4, 'BI20260815001', 1, 1, 2680.00, 1, '8月电子料批次入库（含效期管理）', 'op', 'admin', '2026-08-15 09:30:00'
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_order WHERE id=4);

INSERT INTO wms_inbound_item (order_id, product_id, quantity, price, batch_no, production_date, expiry_date, location_id)
SELECT 4, 1, 100, 129.00, 'BAT2026081501', '2026-08-15', '2027-02-15',
       (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_item WHERE order_id=4 AND product_id=1 AND batch_no='BAT2026081501');

INSERT INTO wms_inbound_item (order_id, product_id, quantity, price, batch_no, production_date, expiry_date, location_id)
SELECT 4, 11, 30, 89.00, 'BAT2026081502', '2026-08-15', '2027-08-15',
       (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_item WHERE order_id=4 AND product_id=11 AND batch_no='BAT2026081502');

-- 入库单5：杭州仓，供应商3，已审核（近效期批次：30天内到期）
INSERT INTO wms_inbound_order (id, order_no, warehouse_id, supplier_id, total_amount, status, remark, create_by, audit_by, audit_time)
SELECT 5, 'BI20260610001', 1, 3, 900.00, 1, '6月仓储设备入库（部分近效期）', 'op', 'admin', '2026-06-10 14:00:00'
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_order WHERE id=5);

INSERT INTO wms_inbound_item (order_id, product_id, quantity, price, batch_no, production_date, expiry_date, location_id)
SELECT 5, 12, 200, 6.80, 'BAT2026061001', '2026-06-10', '2026-09-10',
       (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_item WHERE order_id=5 AND product_id=12 AND batch_no='BAT2026061001');

INSERT INTO wms_inbound_item (order_id, product_id, quantity, price, batch_no, production_date, expiry_date, location_id)
SELECT 5, 13, 50, 3.50, 'BAT2026061002', '2026-06-10', '2026-09-05',
       (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_item WHERE order_id=5 AND product_id=13 AND batch_no='BAT2026061002');

-- 入库单6：上海仓，供应商2，已审核（已过期批次）
INSERT INTO wms_inbound_order (id, order_no, warehouse_id, supplier_id, total_amount, status, remark, create_by, audit_by, audit_time)
SELECT 6, 'BI20260520001', 2, 2, 1470.00, 1, '5月日用品入库（含过期批次）', 'op', 'admin', '2026-05-20 10:00:00'
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_order WHERE id=6);

INSERT INTO wms_inbound_item (order_id, product_id, quantity, price, batch_no, production_date, expiry_date, location_id)
SELECT 6, 3, 200, 49.00, 'BAT2026052001', '2026-05-20', '2026-08-20',
       (SELECT id FROM wms_location WHERE warehouse_id=2 AND code='DEFAULT' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_item WHERE order_id=6 AND product_id=3 AND batch_no='BAT2026052001');

INSERT INTO wms_inbound_item (order_id, product_id, quantity, price, batch_no, production_date, expiry_date, location_id)
SELECT 6, 1, 10, 129.00, 'BAT2026052002', '2026-05-20', '2026-08-25',
       (SELECT id FROM wms_location WHERE warehouse_id=2 AND code='DEFAULT' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_item WHERE order_id=6 AND product_id=1 AND batch_no='BAT2026052002');

-- 入库单7：杭州仓，供应商4，待审核（供 AI createInboundOrder→auditInboundOrder 测试）
INSERT INTO wms_inbound_order (id, order_no, warehouse_id, supplier_id, total_amount, status, remark, create_by)
SELECT 7, 'BI20260907001', 1, 4, 1275.00, 0, '安防设备入库待审核（AI对话测试）', 'op'
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_order WHERE id=7);

INSERT INTO wms_inbound_item (order_id, product_id, quantity, price, batch_no, production_date, expiry_date, location_id)
SELECT 7, 19, 10, 85.00, 'BAT2026090701', '2026-09-01', '2028-09-01',
       (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_item WHERE order_id=7 AND product_id=19);

INSERT INTO wms_inbound_item (order_id, product_id, quantity, price, location_id)
SELECT 7, 20, 15, 32.00, (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_item WHERE order_id=7 AND product_id=20);

-- 入库单8：上海仓，供应商5，待审核（供 AI 测试多仓库入库审核）
INSERT INTO wms_inbound_order (id, order_no, warehouse_id, supplier_id, total_amount, status, remark, create_by)
SELECT 8, 'BI20260907002', 2, 5, 560.00, 0, '包装材料入库待审核（AI对话测试）', 'op'
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_order WHERE id=8);

INSERT INTO wms_inbound_item (order_id, product_id, quantity, price, location_id)
SELECT 8, 12, 100, 6.80, (SELECT id FROM wms_location WHERE warehouse_id=2 AND code='DEFAULT' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM wms_inbound_item WHERE order_id=8 AND product_id=12);

-- 为已审核入库单4/5/6增加库存（模拟审核通过后的库存变动）
-- 入库单4: P001+100, P011+30
UPDATE wms_inventory SET quantity = quantity + 100 WHERE warehouse_id=1 AND product_id=1;
UPDATE wms_inventory SET quantity = quantity + 30 WHERE warehouse_id=1 AND product_id=11;
-- 入库单5: P012+200, P013+50
UPDATE wms_inventory SET quantity = quantity + 200 WHERE warehouse_id=1 AND product_id=12;
UPDATE wms_inventory SET quantity = quantity + 50 WHERE warehouse_id=1 AND product_id=13;
-- 入库单6: P003+200(上海仓), P001+10(上海仓)
UPDATE wms_inventory SET quantity = quantity + 200 WHERE warehouse_id=2 AND product_id=3;
UPDATE wms_inventory SET quantity = quantity + 10 WHERE warehouse_id=2 AND product_id=1;

-- 入库流水留痕
INSERT INTO wms_inventory_record (warehouse_id, product_id, type, quantity, after_quantity, order_id, remark, create_by, location_id, batch_no)
SELECT 1, 1, 'IN', 100, (SELECT quantity FROM wms_inventory WHERE warehouse_id=1 AND product_id=1), 4, '入库审核：BI20260815001', 'admin',
       (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1), 'BAT2026081501'
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory_record WHERE order_id=4 AND product_id=1);

INSERT INTO wms_inventory_record (warehouse_id, product_id, type, quantity, after_quantity, order_id, remark, create_by, location_id, batch_no)
SELECT 1, 11, 'IN', 30, (SELECT quantity FROM wms_inventory WHERE warehouse_id=1 AND product_id=11), 4, '入库审核：BI20260815001', 'admin',
       (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1), 'BAT2026081502'
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory_record WHERE order_id=4 AND product_id=11);

INSERT INTO wms_inventory_record (warehouse_id, product_id, type, quantity, after_quantity, order_id, remark, create_by, location_id, batch_no)
SELECT 1, 12, 'IN', 200, (SELECT quantity FROM wms_inventory WHERE warehouse_id=1 AND product_id=12), 5, '入库审核：BI20260610001', 'admin',
       (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1), 'BAT2026061001'
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory_record WHERE order_id=5 AND product_id=12);

INSERT INTO wms_inventory_record (warehouse_id, product_id, type, quantity, after_quantity, order_id, remark, create_by, location_id, batch_no)
SELECT 1, 13, 'IN', 50, (SELECT quantity FROM wms_inventory WHERE warehouse_id=1 AND product_id=13), 5, '入库审核：BI20260610001', 'admin',
       (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1), 'BAT2026061002'
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory_record WHERE order_id=5 AND product_id=13);

INSERT INTO wms_inventory_record (warehouse_id, product_id, type, quantity, after_quantity, order_id, remark, create_by, location_id, batch_no)
SELECT 2, 3, 'IN', 200, (SELECT quantity FROM wms_inventory WHERE warehouse_id=2 AND product_id=3), 6, '入库审核：BI20260520001', 'admin',
       (SELECT id FROM wms_location WHERE warehouse_id=2 AND code='DEFAULT' LIMIT 1), 'BAT2026052001'
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory_record WHERE order_id=6 AND product_id=3);

INSERT INTO wms_inventory_record (warehouse_id, product_id, type, quantity, after_quantity, order_id, remark, create_by, location_id, batch_no)
SELECT 2, 1, 'IN', 10, (SELECT quantity FROM wms_inventory WHERE warehouse_id=2 AND product_id=1), 6, '入库审核：BI20260520001', 'admin',
       (SELECT id FROM wms_location WHERE warehouse_id=2 AND code='DEFAULT' LIMIT 1), 'BAT2026052002'
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory_record WHERE order_id=6 AND product_id=1);

-- ============================================================
-- 五、出库单（含已出库记录用于呆滞分析参照 + 待审核出库单供 AI 测试）
-- ============================================================

-- 出库单3：杭州仓，客户1，已出库（正常出库记录，排除呆滞）
INSERT INTO wms_outbound_order (id, order_no, warehouse_id, customer_id, total_amount, status, remark, create_by, audit_by, audit_time)
SELECT 3, 'BO20260901001', 1, 1, 2580.00, 1, '9月常规出库', 'op', 'admin', '2026-09-01 11:00:00'
WHERE NOT EXISTS (SELECT 1 FROM wms_outbound_order WHERE id=3);

INSERT INTO wms_outbound_item (order_id, product_id, quantity, price)
SELECT 3, 1, 20, 129.00
WHERE NOT EXISTS (SELECT 1 FROM wms_outbound_item WHERE order_id=3 AND product_id=1);

INSERT INTO wms_outbound_item (order_id, product_id, quantity, price)
SELECT 3, 5, 100, 19.90
WHERE NOT EXISTS (SELECT 1 FROM wms_outbound_item WHERE order_id=3 AND product_id=5);

-- 出库流水（OUT 记录，证明这些商品有出库 → 不算呆滞）
INSERT INTO wms_inventory_record (warehouse_id, product_id, type, quantity, after_quantity, order_id, remark, create_by, location_id)
SELECT 1, 1, 'OUT', -20, (SELECT quantity FROM wms_inventory WHERE warehouse_id=1 AND product_id=1), 3, '出库审核：BO20260901001', 'admin',
       (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory_record WHERE order_id=3 AND product_id=1);

INSERT INTO wms_inventory_record (warehouse_id, product_id, type, quantity, after_quantity, order_id, remark, create_by, location_id)
SELECT 1, 5, 'OUT', -100, (SELECT quantity FROM wms_inventory WHERE warehouse_id=1 AND product_id=5), 3, '出库审核：BO20260901001', 'admin',
       (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory_record WHERE order_id=3 AND product_id=5);

-- 扣减出库库存
UPDATE wms_inventory SET quantity = quantity - 20 WHERE warehouse_id=1 AND product_id=1;
UPDATE wms_inventory SET quantity = quantity - 100 WHERE warehouse_id=1 AND product_id=5;

-- 出库单4：杭州仓，客户3，待审核（供 AI createOutboundOrder→auditOutboundOrder 测试）
INSERT INTO wms_outbound_order (id, order_no, warehouse_id, customer_id, total_amount, status, remark, create_by)
SELECT 4, 'BO20260907001', 1, 3, 1290.00, 0, '苏州客户出库待审核（AI对话测试）', 'op'
WHERE NOT EXISTS (SELECT 1 FROM wms_outbound_order WHERE id=4);

INSERT INTO wms_outbound_item (order_id, product_id, quantity, price)
SELECT 4, 1, 10, 129.00
WHERE NOT EXISTS (SELECT 1 FROM wms_outbound_item WHERE order_id=4 AND product_id=1);

-- 出库单5：上海仓，客户2，待审核（库存不足场景，供 AI 审核出库失败测试）
-- P002 在上海仓无库存，审核时会报"库存不足"
INSERT INTO wms_outbound_order (id, order_no, warehouse_id, customer_id, total_amount, status, remark, create_by)
SELECT 5, 'BO20260907002', 2, 2, 5980.00, 0, '武汉客户出库待审核-库存不足场景（AI对话测试）', 'op'
WHERE NOT EXISTS (SELECT 1 FROM wms_outbound_order WHERE id=5);

INSERT INTO wms_outbound_item (order_id, product_id, quantity, price)
SELECT 5, 2, 20, 299.00
WHERE NOT EXISTS (SELECT 1 FROM wms_outbound_item WHERE order_id=5 AND product_id=2);

-- ============================================================
-- 六、物流订单（多状态，供 listLogisticsOrders 测试）
-- ============================================================
INSERT INTO log_order (id, order_no, receiver_name, receiver_phone, receiver_address, vehicle_id, driver_name, status, remark)
SELECT * FROM (
  SELECT 2 id, 'LO20260901000002' order_no, '孙总' receiver_name, '13700002222' receiver_phone, '湖北省武汉市洪山区光谷大道' receiver_address, 2 vehicle_id, '吴师傅' driver_name, 1 status, '武汉光谷电商运输中' remark
  UNION ALL SELECT 3, 'LO20260905000003', '钱总', '13700003333', '江苏省苏州市工业园区星海街', 3, '郑师傅', 0, '苏州零售待调度'
  UNION ALL SELECT 4, 'LO20260828000004', '赵总', '13700001111', '江苏省南京市秦淮区新街口', 1, '马师傅', 2, '南京商贸已签收'
) seed
WHERE NOT EXISTS (SELECT 1 FROM log_order WHERE id = seed.id);

-- 物流轨迹（订单2 运输中）
INSERT INTO log_track (order_id, location, content, track_time)
SELECT 2, '上海前置仓', '货物已从上海前置仓发出', '2026-09-01 08:00:00'
WHERE NOT EXISTS (SELECT 1 FROM log_track WHERE order_id=2 AND location='上海前置仓');

INSERT INTO log_track (order_id, location, content, track_time)
SELECT 2, '沪宁高速常州段', '运输途中，预计明日送达', '2026-09-01 14:30:00'
WHERE NOT EXISTS (SELECT 1 FROM log_track WHERE order_id=2 AND location='沪宁高速常州段');

-- 订单4已签收轨迹
INSERT INTO log_track (order_id, location, content, track_time)
SELECT 4, '南京新街口', '货物已送达并签收', '2026-08-28 15:00:00'
WHERE NOT EXISTS (SELECT 1 FROM log_track WHERE order_id=4 AND location='南京新街口');

-- ============================================================
-- 七、补充作业任务（覆盖全部6种类型 × 多状态，供 listTasks 测试）
-- ============================================================
INSERT INTO wms_task (id, task_no, type, ref_type, ref_id, warehouse_id, location_id, content, assignee_id, assignee_name, status, finish_time, create_by)
SELECT * FROM (
  SELECT 6 id, 'TK20260906001' task_no, 4 type, NULL ref_type, NULL ref_id, 1 warehouse_id,
         NULL location_id, '移库：P012从DEFAULT移至STORE区A-01-02' content, 2 assignee_id, '张仓管' assignee_name, 3 status, '2026-09-06 16:00:00' finish_time, 'op' create_by
  UNION ALL SELECT 7, 'TK20260906002', 1, 'INBOUND', 4, 1, NULL,
         '批次入库上架：BAT2026081501（近效期优先拣选区）', 2, '张仓管', 3, '2026-08-15 10:00:00', 'admin'
  UNION ALL SELECT 8, 'TK20260907001', 2, 'OUTBOUND', 4, 1, NULL,
         '出库拣选：BO20260907001（10个无线蓝牙耳机）', NULL, NULL, 0, NULL, 'op'
  UNION ALL SELECT 9, 'TK20260907002', 6, 'RETURN', 2, 1, NULL,
         '退货质检：RT20260905001（5件保温杯+2件速干T恤）', NULL, NULL, 0, NULL, 'op'
  UNION ALL SELECT 10, 'TK20260907003', 4, NULL, NULL, 2, NULL,
         '移库：P003过期批次从DEFAULT移至残次区', NULL, NULL, 1, NULL, 'admin'
) seed
WHERE NOT EXISTS (SELECT 1 FROM wms_task WHERE id = seed.id);

-- ============================================================
-- 完成提示
-- ============================================================
SELECT 'AI_TEST_DATA loaded' AS status,
       (SELECT COUNT(*) FROM base_product) AS products,
       (SELECT COUNT(*) FROM wms_inventory) AS inventory_rows,
       (SELECT COUNT(*) FROM wms_inbound_order) AS inbound_orders,
       (SELECT COUNT(*) FROM wms_outbound_order) AS outbound_orders,
       (SELECT COUNT(*) FROM wms_inbound_item WHERE batch_no IS NOT NULL) AS batch_items,
       (SELECT COUNT(*) FROM wms_task) AS tasks,
       (SELECT COUNT(*) FROM log_order) AS logistics_orders;
