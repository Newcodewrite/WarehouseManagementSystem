-- ============================================================
-- 智能仓储系统 V2 升级脚本
-- 新增：库位管理、盘点、退货逆向物流、作业任务中心；批次效期字段
-- 执行：mysql -uroot -proot wms_db --default-character-set=utf8mb4 < upgrade_wms_v2.sql
-- ============================================================

SET NAMES utf8mb4;

-- ---------- 1. 库位表 ----------
CREATE TABLE IF NOT EXISTS wms_location (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '库位ID',
  warehouse_id BIGINT      NOT NULL COMMENT '仓库ID',
  code         VARCHAR(50) NOT NULL COMMENT '库位编码，如 A-01-03',
  zone         VARCHAR(20) NOT NULL COMMENT '库区：RECEIVE收货/STORE存储/PICK拣选/DEFECT残次/SHIP发货',
  capacity     INT         NOT NULL DEFAULT 1000 COMMENT '容量（件）',
  used         INT         NOT NULL DEFAULT 0 COMMENT '已占用（件，简化维护）',
  status       TINYINT     NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
  remark       VARCHAR(255) DEFAULT NULL COMMENT '备注',
  create_time  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_wh_code (warehouse_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库位';

-- ---------- 2. 盘点单 ----------
CREATE TABLE IF NOT EXISTS wms_stocktake (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  order_no     VARCHAR(50) NOT NULL COMMENT '盘点单号 PD开头',
  warehouse_id BIGINT      NOT NULL COMMENT '仓库ID',
  type         TINYINT     NOT NULL DEFAULT 1 COMMENT '盘点类型 1全盘 2循环盘',
  status       TINYINT     NOT NULL DEFAULT 0 COMMENT '状态 0待盘点 1盘点中 2待审核 3已完成 4已取消',
  total_count  INT         NOT NULL DEFAULT 0 COMMENT '盘点项数',
  gain_qty     INT         NOT NULL DEFAULT 0 COMMENT '盘盈总数',
  loss_qty     INT         NOT NULL DEFAULT 0 COMMENT '盘亏总数',
  remark       VARCHAR(255) DEFAULT NULL,
  create_by    VARCHAR(50) DEFAULT NULL,
  audit_by     VARCHAR(50) DEFAULT NULL,
  audit_time   DATETIME    DEFAULT NULL,
  create_time  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点单';

-- ---------- 3. 盘点明细 ----------
CREATE TABLE IF NOT EXISTS wms_stocktake_item (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  stocktake_id  BIGINT NOT NULL COMMENT '盘点单ID',
  product_id    BIGINT NOT NULL COMMENT '商品ID',
  location_id   BIGINT DEFAULT NULL COMMENT '库位ID',
  book_qty      INT    NOT NULL DEFAULT 0 COMMENT '账面数量',
  actual_qty    INT    DEFAULT NULL COMMENT '实盘数量',
  diff_qty      INT    NOT NULL DEFAULT 0 COMMENT '差异 = 实盘 - 账面',
  PRIMARY KEY (id),
  KEY idx_stocktake (stocktake_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点明细';

-- ---------- 4. 退货单（逆向物流） ----------
CREATE TABLE IF NOT EXISTS wms_return_order (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  order_no     VARCHAR(50) NOT NULL COMMENT '退货单号 RT开头',
  customer_id  BIGINT      DEFAULT NULL COMMENT '客户ID',
  warehouse_id BIGINT      NOT NULL COMMENT '收货仓库ID',
  status       TINYINT     NOT NULL DEFAULT 0 COMMENT '状态 0待收货 1待质检 2已完成 3已取消',
  total_qty    INT         NOT NULL DEFAULT 0 COMMENT '退货总数量',
  resale_qty   INT         NOT NULL DEFAULT 0 COMMENT '可二次销售数量',
  scrap_qty    INT         NOT NULL DEFAULT 0 COMMENT '报废数量',
  remark       VARCHAR(255) DEFAULT NULL,
  create_by    VARCHAR(50) DEFAULT NULL,
  audit_by     VARCHAR(50) DEFAULT NULL COMMENT '质检处理人',
  audit_time   DATETIME    DEFAULT NULL,
  create_time  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货单';

-- ---------- 5. 退货明细 ----------
CREATE TABLE IF NOT EXISTS wms_return_item (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  return_id   BIGINT      NOT NULL COMMENT '退货单ID',
  product_id  BIGINT      NOT NULL COMMENT '商品ID',
  quantity    INT         NOT NULL COMMENT '退货数量',
  qc_grade    TINYINT     DEFAULT NULL COMMENT '质检分级 1可二次销售 2需翻新 3报废',
  qc_remark   VARCHAR(255) DEFAULT NULL COMMENT '质检备注',
  PRIMARY KEY (id),
  KEY idx_return (return_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货明细';

-- ---------- 6. 作业任务 ----------
CREATE TABLE IF NOT EXISTS wms_task (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  task_no       VARCHAR(50)  NOT NULL COMMENT '任务号 TK开头',
  type          TINYINT      NOT NULL COMMENT '任务类型 1上架 2拣选 3盘点 4移库 5补货 6退货质检',
  ref_type      VARCHAR(20)  DEFAULT NULL COMMENT '关联单据类型 INBOUND/OUTBOUND/STOCKTAKE/RETURN',
  ref_id        BIGINT       DEFAULT NULL COMMENT '关联单据ID',
  warehouse_id  BIGINT       NOT NULL COMMENT '仓库ID',
  location_id   BIGINT       DEFAULT NULL COMMENT '相关库位ID',
  content       VARCHAR(500) DEFAULT NULL COMMENT '任务内容描述',
  assignee_id   BIGINT       DEFAULT NULL COMMENT '指派人员ID',
  assignee_name VARCHAR(50)  DEFAULT NULL COMMENT '指派人姓名',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0待分配 1待执行 2进行中 3已完成 4已取消',
  finish_time   DATETIME     DEFAULT NULL COMMENT '完成时间',
  remark        VARCHAR(255) DEFAULT NULL,
  create_by     VARCHAR(50)  DEFAULT NULL,
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_no (task_no),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业任务';

-- ---------- 7. 增量加列（批次/效期/库位） ----------
ALTER TABLE wms_inventory       ADD COLUMN location_id BIGINT DEFAULT NULL COMMENT '默认库位ID' AFTER product_id;
ALTER TABLE wms_inbound_item    ADD COLUMN batch_no       VARCHAR(50) DEFAULT NULL COMMENT '批次号' AFTER price;
ALTER TABLE wms_inbound_item    ADD COLUMN production_date DATE        DEFAULT NULL COMMENT '生产日期' AFTER batch_no;
ALTER TABLE wms_inbound_item    ADD COLUMN expiry_date     DATE        DEFAULT NULL COMMENT '到期日期' AFTER production_date;
ALTER TABLE wms_inbound_item    ADD COLUMN location_id     BIGINT      DEFAULT NULL COMMENT '上架库位ID' AFTER expiry_date;
ALTER TABLE wms_inventory_record ADD COLUMN batch_no    VARCHAR(50) DEFAULT NULL COMMENT '批次号' AFTER remark;
ALTER TABLE wms_inventory_record ADD COLUMN location_id BIGINT      DEFAULT NULL COMMENT '库位ID' AFTER batch_no;

-- ---------- 8. 数据迁移：默认库位 + 库存回填 ----------
-- 每个仓库建一个默认存储库位
INSERT INTO wms_location (warehouse_id, code, zone, capacity, used, status, remark)
SELECT id, 'DEFAULT', 'STORE', 99999, 0, 1, '系统默认库位'
FROM base_warehouse w
WHERE NOT EXISTS (
  SELECT 1 FROM wms_location l WHERE l.warehouse_id = w.id AND l.code = 'DEFAULT' AND l.deleted = 0
);

-- 库存回填默认库位
UPDATE wms_inventory i
JOIN wms_location l ON l.warehouse_id = i.warehouse_id AND l.code = 'DEFAULT' AND l.deleted = 0
SET i.location_id = l.id
WHERE i.location_id IS NULL;

-- 库存唯一键扩展为 仓库+商品+库位
ALTER TABLE wms_inventory DROP KEY uk_warehouse_product;
ALTER TABLE wms_inventory ADD UNIQUE KEY uk_wh_prod_loc (warehouse_id, product_id, location_id);

-- ---------- 9. 库位种子数据（前10个仓库各12个库位） ----------
INSERT INTO wms_location (warehouse_id, code, zone, capacity, status, remark)
SELECT w.id, CONCAT(z.zone_code, '-', LPAD(z.row_no, 2, '0'), '-', LPAD(z.col_no, 2, '0')),
       z.zone, z.cap, 1, z.rm
FROM base_warehouse w
JOIN (
  -- 收货区 2
  SELECT 'RECEIVE' zone_code, 'RECEIVE' zone, n row_no, 1 col_no, 200 cap, '收货月台' rm FROM (SELECT 1 n UNION SELECT 2) t
  UNION ALL
  -- 存储区 6（高架）
  SELECT 'A', 'STORE', r.n, c.n, 500, '高位存储位'
  FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3) r
  CROSS JOIN (SELECT 1 n UNION SELECT 2) c
  UNION ALL
  -- 拣选区 3（黄金区）
  SELECT 'P', 'PICK', n, 1, 300, '拣选黄金位' FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3) t
  UNION ALL
  -- 残次区 1
  SELECT 'D', 'DEFECT', 1, 1, 200, '残品隔离位'
) z
WHERE w.id <= 10
  AND NOT EXISTS (SELECT 1 FROM wms_location l WHERE l.warehouse_id = w.id AND l.code = CONCAT(z.zone_code, '-', LPAD(z.row_no, 2, '0'), '-', LPAD(z.col_no, 2, '0')) AND l.deleted = 0);

-- ---------- 10. 权限补充 ----------
UPDATE sys_role
SET perms = CONCAT(perms, ',base:location,wms:stocktake,wms:return,wms:task')
WHERE code IN ('ADMIN', 'OP')
  AND perms NOT LIKE '%wms:task%';

-- ---------- 11. 盘点单种子（3张，不同状态） ----------
INSERT INTO wms_stocktake (id, order_no, warehouse_id, type, status, total_count, gain_qty, loss_qty, remark, create_by, audit_by, audit_time)
SELECT * FROM (
  SELECT 1 id, 'PD20260820001' order_no, 1 warehouse_id, 1 type, 3 status, 3 total_count, 5 gain_qty, 2 loss_qty, '月度全盘，差异已调整' remark, 'admin' create_by, 'admin' audit_by, '2026-08-20 10:00:00' audit_time
  UNION ALL SELECT 2, 'PD20260901001', 1, 2, 2, 2, 0, 0, '循环盘点待审核', 'op', NULL, NULL
  UNION ALL SELECT 3, 'PD20260905001', 2, 1, 0, 0, 0, 0, '第三季度全盘', 'op', NULL, NULL
) seed
WHERE NOT EXISTS (SELECT 1 FROM wms_stocktake WHERE id = seed.id);

-- 盘点明细（单据1：已完成，含盈亏；单据2：待审核）
INSERT INTO wms_stocktake_item (stocktake_id, product_id, location_id, book_qty, actual_qty, diff_qty)
SELECT * FROM (
  SELECT 1 stocktake_id, 1 product_id, (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1) location_id, 100 book_qty, 103 actual_qty, 3 diff_qty
  UNION ALL SELECT 1, 2, (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1), 96, 98, 2
  UNION ALL SELECT 1, 3, (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1), 527, 525, -2
  UNION ALL SELECT 2, 1, (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='P-01-01' LIMIT 1), 103, 103, 0
  UNION ALL SELECT 2, 2, (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='P-02-01' LIMIT 1), 98, NULL, 0
) seed
WHERE NOT EXISTS (SELECT 1 FROM wms_stocktake_item WHERE stocktake_id = seed.stocktake_id AND product_id = seed.product_id);

-- ---------- 12. 退货单种子（2张） ----------
INSERT INTO wms_return_order (id, order_no, customer_id, warehouse_id, status, total_qty, resale_qty, scrap_qty, remark, create_by, audit_by, audit_time)
SELECT * FROM (
  SELECT 1 id, 'RT20260828001' order_no, 1 customer_id, 1 warehouse_id, 2 status, 8 total_qty, 6 resale_qty, 2 scrap_qty, '客户退货，部分包装破损' remark, 'op' create_by, 'op' audit_by, '2026-08-29 15:00:00' audit_time
  UNION ALL SELECT 2, 'RT20260905001', 2, 1, 0, 5, 0, 0, '电商退货待收货', 'op', NULL, NULL
) seed
WHERE NOT EXISTS (SELECT 1 FROM wms_return_order WHERE id = seed.id);

INSERT INTO wms_return_item (return_id, product_id, quantity, qc_grade, qc_remark)
SELECT * FROM (
  SELECT 1 return_id, 2 product_id, 5 quantity, 1 qc_grade, '包装完好可二次销售' qc_remark
  UNION ALL SELECT 1, 5, 2, 3, '严重破损报废'
  UNION ALL SELECT 2, 3, 3, NULL, NULL
  UNION ALL SELECT 2, 6, 2, NULL, NULL
) seed
WHERE NOT EXISTS (SELECT 1 FROM wms_return_item WHERE return_id = seed.return_id AND product_id = seed.product_id);

-- ---------- 13. 作业任务种子 ----------
INSERT INTO wms_task (id, task_no, type, ref_type, ref_id, warehouse_id, location_id, content, assignee_id, assignee_name, status, finish_time, create_by)
SELECT * FROM (
  SELECT 1 id, 'TK20260905001' task_no, 1 type, 'INBOUND' ref_type, 100 ref_id, 1 warehouse_id,
         (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='P-01-01' LIMIT 1) location_id,
         '入库单商品上架至拣选区（智能推荐）' content, 2 assignee_id, '张仓管' assignee_name, 3 status, '2026-09-05 10:20:00' finish_time, 'admin' create_by
  UNION ALL SELECT 2, 'TK20260905002', 2, 'OUTBOUND', 100, 1,
         (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='P-02-01' LIMIT 1),
         '出库单拣选任务（按库位路径排序）', 2, '张仓管', 3, '2026-09-05 11:00:00', 'admin'
  UNION ALL SELECT 3, 'TK20260905003', 3, 'STOCKTAKE', 2, 1, NULL,
         '循环盘点：上海仓库1 拣选区', 2, '张仓管', 1, NULL, 'op'
  UNION ALL SELECT 4, 'TK20260905004', 6, 'RETURN', 2, 1,
         (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='D-01-01' LIMIT 1),
         '退货收货与质检分级', NULL, NULL, 0, NULL, 'op'
  UNION ALL SELECT 5, 'TK20260905005', 5, NULL, NULL, 1,
         (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='P-03-01' LIMIT 1),
         '拣选位补货：保温杯低于安全库存', NULL, NULL, 0, NULL, 'admin'
) seed
WHERE NOT EXISTS (SELECT 1 FROM wms_task WHERE id = seed.id);

-- ---------- 14. 完成盘点单据1的库存调整（账实一致，流水留痕） ----------
INSERT INTO wms_inventory_record (warehouse_id, product_id, type, quantity, after_quantity, remark, create_by, location_id, create_time)
SELECT 1, 1, 'CHECK', 3,
       (SELECT quantity FROM wms_inventory WHERE warehouse_id=1 AND product_id=1 LIMIT 1),
       '盘点盘盈：PD20260820001', 'admin',
       (SELECT id FROM wms_location WHERE warehouse_id=1 AND code='DEFAULT' LIMIT 1),
       '2026-08-20 10:05:00'
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory_record WHERE remark LIKE '%PD20260820001%' AND product_id=1);

-- ============================================================
-- 升级完成
-- ============================================================
