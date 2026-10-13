# 智能物流仓储管理系统（WMS）

基于 **Vue 3 + Spring Boot 3 + Spring AI + MyBatis-Plus + MySQL** 的前后端分离物流仓储管理系统，
覆盖基础数据、仓储作业、物流运输、系统管理与 AI 智能化五大板块。

## 技术栈（含版本号）

### 后端

| 技术 | 版本 | 用途 |
|---|---|---|
| Java (JDK) | 17+（本机运行 21.0.2） | 运行环境 |
| Spring Boot | 3.4.5 | 基础框架、自动装配 |
| Spring Web (Spring MVC) | 随 Boot 3.4.5 | RESTful API |
| Spring Security | 随 Boot 3.4.5 | 认证授权、RBAC |
| JJWT (jjwt-api/impl/jackson) | 0.12.6 | JWT Token 生成与校验 |
| MyBatis-Plus (spring-boot3-starter) | 3.5.12 | ORM、分页插件、逻辑删除、Lambda 查询 |
| mybatis-plus-jsqlparser | 3.5.12 | 分页 Count 优化 |
| MySQL Connector/J | 随 Boot 3.4.5 | MySQL 驱动 |
| Spring Data Redis | 随 Boot 3.4.5 | 缓存（可选，降级本地缓存） |
| Spring AI (spring-ai-starter-model-openai) | 1.0.0（BOM 管理） | AI 能力，OpenAI 兼容接口（默认 DeepSeek） |
| Spring AOP | 随 Boot 3.4.5 | 操作日志切面 |
| Hibernate Validator (starter-validation) | 随 Boot 3.4.5 | 参数校验 |
| Spring Boot Actuator | 随 Boot 3.4.5 | 健康检查与监控 |
| SpringDoc OpenAPI (Swagger UI) | 2.8.5 | 接口文档 |
| Lombok | 随 Boot 3.4.5 | 简化实体代码 |
| Maven | 3.9+（本机 3.9.10） | 构建与打包 |

### 前端

| 技术 | 版本 | 用途 |
|---|---|---|
| Vue 3（Composition API + `<script setup>`） | ^3.5.13 | 核心框架 |
| TypeScript | ~5.7.2 | 类型安全 |
| Vite（@vitejs/plugin-vue ^5.2.1） | ^6.0.7 | 构建与开发服务器 |
| Element Plus | ^2.9.3 | UI 组件库 |
| @element-plus/icons-vue | ^2.3.1 | 图标库 |
| Vue Router | ^4.5.0 | SPA 路由与导航守卫 |
| Pinia | ^3.0.2 | 全局状态管理 |
| Axios | ^1.7.9 | HTTP 请求封装与拦截器 |
| ECharts | ^5.6.0 | 驾驶舱统计图表 |
| Node.js | 18+（本机 22） | 前端运行环境 |

### 数据与部署

| 技术 | 版本 | 用途 |
|---|---|---|
| MySQL | 8.x | 数据持久化（17 张表，InnoDB + utf8mb4） |
| Redis | 可选 | 热点缓存（未部署时自动降级） |
| Nginx | 可选 | 生产环境托管前端静态文件并反代 `/api` |

## 功能模块

- **基础数据**：商品、仓库、供应商、客户管理
- **仓储作业**：入库单（创建/审核 → 自动增加库存）、出库单（创建/审核 → 校验并扣减库存）、库存查询/调整/流水/预警
- **物流运输**：物流订单、车辆调度（空闲车辆分配、状态流转）、轨迹记录与查询
- **系统管理**：用户管理（RBAC 角色 + 权限标识）、角色管理、操作日志（AOP 自动记录）
- **AI 智能管家**（Spring AI + DeepSeek Function Calling；未配置 API Key 时自动降级为离线规则模式）：
  - 自然语言对话，可直接操作系统数据（工具调用）：
    - 商品管理：查询 / 新增 / 修改 / 删除商品
    - 库存管理：按仓库/商品查询库存、库存预警列表
    - 入库：创建入库单、审核（通过后自动**增加库存**并记流水）
    - 出库：创建出库单、审核（通过后自动**扣减库存**，不足则失败）
    - 物流：查询物流订单状态
  - 安全机制：写操作继承当前登录用户权限（RBAC，越权返回权限不足）；审核单据/删除商品等敏感操作 AI 会先复述信息并等待用户确认
  - 专项接口：智能客服 `/api/ai/chat`、库存补货建议 `/api/ai/stock-advice`、物流轨迹解读 `/api/ai/track-explain/{orderId}`、调度建议 `/api/ai/schedule-advice`
- **其他**：驾驶舱统计与出入库趋势、SpringDoc 接口文档、Actuator 健康检查

## 项目结构

```
backend/   Spring Boot 后端（ai 工具层 / common / config / controller / dto / entity / mapper / security / service）
frontend/  Vue3 前端（api / layout / router / stores / views）
sql/       init.sql 数据库初始化脚本（17 张表 + 种子数据）
```

## 快速开始

### 1. 初始化数据库

修改 [sql/init.sql](sql/init.sql) 中的连接无关项后，用 MySQL root 执行（按本机实际密码调整）：

```bash
mysql -uroot -p < sql/init.sql
```

脚本自动创建 `wms_db` 库、17 张表及演示数据。

### 2. 启动后端（端口 8080）

```bash
cd backend
# Windows 示例（按本机 JDK/Maven 路径调整）
set JAVA_HOME=D:\Program Files\Java\jdk-21
mvn spring-boot:run
```

数据库连接默认 `localhost:3306`，账号 `root/root`，可用环境变量覆盖：
`DB_HOST`、`DB_PORT`、`DB_USER`、`DB_PASSWORD`。

### 3. 启动前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

Vite 已配置代理：前端 `/api` 请求自动转发至 `http://localhost:8080`。

### 4. 访问

- 前端界面：<http://localhost:5173>
- 接口文档：<http://localhost:8080/swagger-ui.html>

### 默认账号（密码均为 `123456`）

| 账号 | 角色 | 说明 |
|---|---|---|
| admin | ADMIN | 系统管理员，全部权限 |
| op | OPERATOR | 仓管操作员 |
| user | USER | 普通用户（只读，写操作返回 403） |

## AI（DeepSeek）配置

默认未配置 API Key，AI 接口自动降级为离线规则模式（功能可用，回复为规则模板）。
配置后自动切换真实大模型：

```bash
# 启动后端前设置环境变量
set AI_API_KEY=sk-你的DeepSeek密钥
set AI_BASE_URL=https://api.deepseek.com
set AI_MODEL=deepseek-chat
```

## 冒烟测试结论（已验证）

- 登录/鉴权：admin、user 登录正常；无 Token 返回 401；普通用户写操作返回 403
- 入库：创建 → 审核 → 库存 +50 正确入账并生成库存流水
- 出库：超库存单审核被拦截（“库存不足，无法完成出库”）；正常单审核后库存 -10
- 物流：建单 → 调度空闲车辆 → 追加轨迹 → 轨迹查询正常
- AI：四个接口均可用（离线模式），配置 Key 后切换大模型
- 系统管理：用户/角色分页、操作日志自动记录均正常

## 打包部署

```bash
# 后端
cd backend && mvn package -DskipTests
java -jar target/wms-backend-1.0.0.jar

# 前端
cd frontend && npm run build   # 产物在 dist/，可用 Nginx 托管并反代 /api
```
