-- ============================================================
-- 智能物流仓储管理系统 数据库初始化脚本
-- MySQL 8.x, utf8mb4
-- ============================================================
CREATE DATABASE IF NOT EXISTS wms_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE wms_db;

-- ----------------------------
-- 1. 系统用户表
-- ----------------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
  password    VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
  nickname    VARCHAR(50)  DEFAULT NULL COMMENT '姓名',
  phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  email       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0停用 1启用',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='系统用户';

-- ----------------------------
-- 2. 角色表
-- ----------------------------
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  code        VARCHAR(50) NOT NULL COMMENT '角色编码',
  name        VARCHAR(50) NOT NULL COMMENT '角色名称',
  perms       TEXT        DEFAULT NULL COMMENT '权限编码,逗号分隔',
  remark      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB COMMENT='角色';

-- ----------------------------
-- 3. 用户角色关联表
-- ----------------------------
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
  id      BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='用户角色关联';

-- ----------------------------
-- 4. 操作日志表
-- ----------------------------
DROP TABLE IF EXISTS sys_oper_log;
CREATE TABLE sys_oper_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  username    VARCHAR(50)  DEFAULT NULL COMMENT '操作人',
  module      VARCHAR(50)  DEFAULT NULL COMMENT '模块',
  description VARCHAR(255) DEFAULT NULL COMMENT '操作描述',
  method      VARCHAR(255) DEFAULT NULL COMMENT '请求方式/路径',
  params      TEXT         DEFAULT NULL COMMENT '请求参数',
  ip          VARCHAR(64)  DEFAULT NULL COMMENT 'IP',
  cost_ms     BIGINT       DEFAULT NULL COMMENT '耗时(ms)',
  status      TINYINT      DEFAULT 1 COMMENT '0失败 1成功',
  error_msg   TEXT         DEFAULT NULL COMMENT '异常信息',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='操作日志';

-- ----------------------------
-- 5. 商品表
-- ----------------------------
DROP TABLE IF EXISTS base_product;
CREATE TABLE base_product (
  id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  code        VARCHAR(50)   NOT NULL COMMENT '商品编码',
  name        VARCHAR(100)  NOT NULL COMMENT '商品名称',
  category    VARCHAR(50)   DEFAULT NULL COMMENT '分类',
  unit        VARCHAR(20)   DEFAULT NULL COMMENT '单位',
  price       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '单价',
  warn_stock  INT           NOT NULL DEFAULT 10 COMMENT '库存预警值',
  status      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态 0停用 1启用',
  remark      VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB COMMENT='商品';

-- ----------------------------
-- 6. 仓库表
-- ----------------------------
DROP TABLE IF EXISTS base_warehouse;
CREATE TABLE base_warehouse (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '仓库ID',
  code         VARCHAR(50)  NOT NULL COMMENT '仓库编码',
  name         VARCHAR(100) NOT NULL COMMENT '仓库名称',
  address      VARCHAR(255) DEFAULT NULL COMMENT '地址',
  capacity     INT          DEFAULT 0 COMMENT '库容(件)',
  manager_name VARCHAR(50)  DEFAULT NULL COMMENT '负责人',
  manager_phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0停用 1启用',
  remark       VARCHAR(255) DEFAULT NULL COMMENT '备注',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB COMMENT='仓库';

-- ----------------------------
-- 7. 供应商表
-- ----------------------------
DROP TABLE IF EXISTS base_supplier;
CREATE TABLE base_supplier (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  code         VARCHAR(50)  NOT NULL COMMENT '编码',
  name         VARCHAR(100) NOT NULL COMMENT '名称',
  contact_name VARCHAR(50)  DEFAULT NULL COMMENT '联系人',
  contact_phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  address      VARCHAR(255) DEFAULT NULL COMMENT '地址',
  remark       VARCHAR(255) DEFAULT NULL COMMENT '备注',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB COMMENT='供应商';

-- ----------------------------
-- 8. 客户表
-- ----------------------------
DROP TABLE IF EXISTS base_customer;
CREATE TABLE base_customer (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  code         VARCHAR(50)  NOT NULL COMMENT '编码',
  name         VARCHAR(100) NOT NULL COMMENT '名称',
  contact_name VARCHAR(50)  DEFAULT NULL COMMENT '联系人',
  contact_phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  address      VARCHAR(255) DEFAULT NULL COMMENT '地址',
  remark       VARCHAR(255) DEFAULT NULL COMMENT '备注',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB COMMENT='客户';

-- ----------------------------
-- 9. 入库单
-- ----------------------------
DROP TABLE IF EXISTS wms_inbound_order;
CREATE TABLE wms_inbound_order (
  id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '入库单ID',
  order_no    VARCHAR(50)   NOT NULL COMMENT '入库单号',
  warehouse_id BIGINT       NOT NULL COMMENT '仓库ID',
  supplier_id BIGINT        DEFAULT NULL COMMENT '供应商ID',
  total_amount DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '总金额',
  status      TINYINT       NOT NULL DEFAULT 0 COMMENT '状态 0待审核 1已入库 2已驳回',
  remark      VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  create_by   VARCHAR(50)   DEFAULT NULL COMMENT '创建人',
  audit_by    VARCHAR(50)   DEFAULT NULL COMMENT '审核人',
  audit_time  DATETIME      DEFAULT NULL COMMENT '审核时间',
  create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='入库单';

-- ----------------------------
-- 10. 入库单明细
-- ----------------------------
DROP TABLE IF EXISTS wms_inbound_item;
CREATE TABLE wms_inbound_item (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  order_id   BIGINT        NOT NULL COMMENT '入库单ID',
  product_id BIGINT        NOT NULL COMMENT '商品ID',
  quantity   INT           NOT NULL COMMENT '数量',
  price      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '入库单价',
  PRIMARY KEY (id),
  KEY idx_order_id (order_id)
) ENGINE=InnoDB COMMENT='入库单明细';

-- ----------------------------
-- 11. 出库单
-- ----------------------------
DROP TABLE IF EXISTS wms_outbound_order;
CREATE TABLE wms_outbound_order (
  id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '出库单ID',
  order_no    VARCHAR(50)   NOT NULL COMMENT '出库单号',
  warehouse_id BIGINT       NOT NULL COMMENT '仓库ID',
  customer_id BIGINT        DEFAULT NULL COMMENT '客户ID',
  total_amount DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '总金额',
  status      TINYINT       NOT NULL DEFAULT 0 COMMENT '状态 0待审核 1已出库 2已驳回',
  remark      VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  create_by   VARCHAR(50)   DEFAULT NULL COMMENT '创建人',
  audit_by    VARCHAR(50)   DEFAULT NULL COMMENT '审核人',
  audit_time  DATETIME      DEFAULT NULL COMMENT '审核时间',
  create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='出库单';

-- ----------------------------
-- 12. 出库单明细
-- ----------------------------
DROP TABLE IF EXISTS wms_outbound_item;
CREATE TABLE wms_outbound_item (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  order_id   BIGINT        NOT NULL COMMENT '出库单ID',
  product_id BIGINT        NOT NULL COMMENT '商品ID',
  quantity   INT           NOT NULL COMMENT '数量',
  price      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '出库单价',
  PRIMARY KEY (id),
  KEY idx_order_id (order_id)
) ENGINE=InnoDB COMMENT='出库单明细';

-- ----------------------------
-- 13. 库存表
-- ----------------------------
DROP TABLE IF EXISTS wms_inventory;
CREATE TABLE wms_inventory (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  warehouse_id BIGINT      NOT NULL COMMENT '仓库ID',
  product_id   BIGINT      NOT NULL COMMENT '商品ID',
  quantity     INT         NOT NULL DEFAULT 0 COMMENT '库存数量',
  create_time  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_warehouse_product (warehouse_id, product_id)
) ENGINE=InnoDB COMMENT='库存';

-- ----------------------------
-- 14. 库存流水表
-- ----------------------------
DROP TABLE IF EXISTS wms_inventory_record;
CREATE TABLE wms_inventory_record (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  warehouse_id   BIGINT       NOT NULL COMMENT '仓库ID',
  product_id     BIGINT       NOT NULL COMMENT '商品ID',
  type           VARCHAR(10)  NOT NULL COMMENT '类型 IN入库 OUT出库 ADJUST调整',
  quantity       INT          NOT NULL COMMENT '变动数量(正负)',
  after_quantity INT          NOT NULL COMMENT '变动后数量',
  order_id       BIGINT       DEFAULT NULL COMMENT '关联单据ID',
  remark         VARCHAR(255) DEFAULT NULL COMMENT '备注',
  create_by      VARCHAR(50)  DEFAULT NULL COMMENT '操作人',
  create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_product_warehouse (product_id, warehouse_id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='库存流水';

-- ----------------------------
-- 15. 物流订单表
-- ----------------------------
DROP TABLE IF EXISTS log_order;
CREATE TABLE log_order (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '物流订单ID',
  order_no         VARCHAR(50)  NOT NULL COMMENT '物流单号',
  outbound_order_id BIGINT      DEFAULT NULL COMMENT '关联出库单ID',
  receiver_name    VARCHAR(50)  DEFAULT NULL COMMENT '收货人',
  receiver_phone   VARCHAR(20)  DEFAULT NULL COMMENT '收货电话',
  receiver_address VARCHAR(255) DEFAULT NULL COMMENT '收货地址',
  vehicle_id       BIGINT       DEFAULT NULL COMMENT '车辆ID',
  driver_name      VARCHAR(50)  DEFAULT NULL COMMENT '司机',
  status           TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0待调度 1运输中 2已签收 3已取消',
  remark           VARCHAR(255) DEFAULT NULL COMMENT '备注',
  create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='物流订单';

-- ----------------------------
-- 16. 物流轨迹表
-- ----------------------------
DROP TABLE IF EXISTS log_track;
CREATE TABLE log_track (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  order_id    BIGINT       NOT NULL COMMENT '物流订单ID',
  location    VARCHAR(100) DEFAULT NULL COMMENT '位置',
  content     VARCHAR(255) NOT NULL COMMENT '轨迹描述',
  track_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '轨迹时间',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_order_id (order_id)
) ENGINE=InnoDB COMMENT='物流轨迹';

-- ----------------------------
-- 17. 车辆表
-- ----------------------------
DROP TABLE IF EXISTS log_vehicle;
CREATE TABLE log_vehicle (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  code         VARCHAR(20)  NOT NULL COMMENT '车牌号',
  vehicle_type VARCHAR(30)  DEFAULT NULL COMMENT '车型',
  driver_name  VARCHAR(50)  DEFAULT NULL COMMENT '默认司机',
  driver_phone VARCHAR(20)  DEFAULT NULL COMMENT '司机电话',
  capacity_kg  INT          DEFAULT 0 COMMENT '载重(kg)',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0空闲 1运输中 2维修',
  remark       VARCHAR(255) DEFAULT NULL COMMENT '备注',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB COMMENT='车辆';

-- ============================================================
-- 种子数据
-- ============================================================

-- 角色 (admin 拥有全部权限编码)
INSERT INTO sys_role (id, code, name, perms, remark) VALUES
(1, 'ADMIN', '管理员', 'system:user,system:role,system:log,base:product,base:warehouse,base:supplier,base:customer,wms:inbound,wms:outbound,wms:inventory,logistics:order,logistics:vehicle', '系统管理员，拥有全部权限'),
(2, 'OPERATOR', '仓库操作员', 'base:product,base:warehouse,base:supplier,base:customer,wms:inbound,wms:outbound,wms:inventory,logistics:order,logistics:vehicle', '负责日常仓储与物流作业'),
(3, 'USER', '普通用户', '', '仅查询与AI助手');

-- 用户 密码均为 123456 (BCrypt)
INSERT INTO sys_user (id, username, password, nickname, phone, email, status) VALUES
(1, 'admin', '$2a$10$cCur0ePb11uifVLazESVJumOEs9hlNb7rCVeyp.S/iLmsMc4mJsgi', '系统管理员', '13800000001', 'admin@wms.com', 1),
(2, 'op',    '$2a$10$cCur0ePb11uifVLazESVJumOEs9hlNb7rCVeyp.S/iLmsMc4mJsgi', '张仓管', '13800000002', 'op@wms.com', 1),
(3, 'user',  '$2a$10$cCur0ePb11uifVLazESVJumOEs9hlNb7rCVeyp.S/iLmsMc4mJsgi', '李客户', '13800000003', 'user@wms.com', 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1,1),(2,2),(3,3);

-- 仓库
INSERT INTO base_warehouse (id, code, name, address, capacity, manager_name, manager_phone, status) VALUES
(1, 'WH001', '杭州中心仓', '浙江省杭州市余杭区物流园区1号', 50000, '王强', '13811110001', 1),
(2, 'WH002', '上海前置仓', '上海市青浦区仓储路88号', 20000, '李敏', '13811110002', 1);

-- 商品
INSERT INTO base_product (id, code, name, category, unit, price, warn_stock, status) VALUES
(1, 'P001', '无线蓝牙耳机', '电子产品', '个', 129.00, 50, 1),
(2, 'P002', '机械键盘', '电子产品', '个', 299.00, 30, 1),
(3, 'P003', '保温杯', '日用品', '个', 49.00, 100, 1),
(4, 'P004', '登山背包', '户外', '个', 199.00, 20, 1),
(5, 'P005', 'iPhone数据线', '电子产品', '条', 19.90, 200, 1),
(6, 'P006', '速干T恤', '服装', '件', 79.00, 80, 1);

-- 供应商
INSERT INTO base_supplier (id, code, name, contact_name, contact_phone, address) VALUES
(1, 'SUP001', '深圳华强电子有限公司', '陈经理', '13900001111', '广东省深圳市福田区华强北路'),
(2, 'SUP002', '义乌日用品集团', '刘经理', '13900002222', '浙江省义乌市国际商贸城');

-- 客户
INSERT INTO base_customer (id, code, name, contact_name, contact_phone, address) VALUES
(1, 'CUS001', '南京新街口商贸', '赵总', '13700001111', '江苏省南京市秦淮区新街口'),
(2, 'CUS002', '武汉光谷电商', '孙总', '13700002222', '湖北省武汉市洪山区光谷大道');

-- 车辆
INSERT INTO log_vehicle (id, code, vehicle_type, driver_name, driver_phone, capacity_kg, status) VALUES
(1, '浙A12345', '厢式货车 4.2m', '马师傅', '13600001111', 5000, 0),
(2, '浙B67890', '冷藏车 6.8m', '吴师傅', '13600002222', 10000, 0),
(3, '沪C24680', '厢式货车 9.6m', '郑师傅', '13600003333', 15000, 0);

-- 初始库存 (上海前置仓少量库存用于演示预警)
INSERT INTO wms_inventory (warehouse_id, product_id, quantity) VALUES
(1, 1, 500), (1, 2, 300), (1, 3, 800), (1, 4, 150), (1, 5, 2000), (1, 6, 400),
(2, 1, 40), (2, 5, 150), (2, 3, 30);

-- 初始流水示例
INSERT INTO wms_inventory_record (warehouse_id, product_id, type, quantity, after_quantity, remark, create_by) VALUES
(1, 1, 'IN', 500, 500, '系统初始化库存', 'admin'),
(1, 2, 'IN', 300, 300, '系统初始化库存', 'admin');

-- 示例物流订单 + 轨迹
INSERT INTO log_order (id, order_no, receiver_name, receiver_phone, receiver_address, vehicle_id, driver_name, status, remark) VALUES
(1, 'LO20260101000001', '赵总', '13700001111', '江苏省南京市秦淮区新街口', 1, '马师傅', 1, '示例订单');

INSERT INTO log_track (order_id, location, content, track_time) VALUES
(1, '杭州中心仓', '货物已从杭州中心仓发出', '2026-01-01 09:00:00'),
(1, '浙江湖州服务区', '运输途中，一切正常', '2026-01-01 11:30:00'),
(1, '江苏南京绕城高速', '即将到达南京市，预计14:00送达', '2026-01-01 13:10:00');
