# TickGo Service Restructure And Pay Service Design

## Goal

重构现有微服务目录结构，将所有服务收敛到 `services/` 目录下；新增独立 `pay-service` 与独立库 `tickgo_pay`；保持 `user-service`、`ticket-service`、`order-service` 现有职责边界，并补一个可快速联调的前端测试页面，形成“查票 -> 下单 -> 创建支付单 -> 模拟支付 -> 支付回调订单 -> 票务确认”的完整闭环。

## Scope

本次改动包含：

- 调整 Maven 聚合模块路径，将现有服务迁移到 `services/`
- 新增 `services/pay-service`
- 为 `gateway-service` 增加 `pay-service` 路由
- 为 `order-service` 增加支付成功通知入口
- 为 `pay-service` 增加支付单、支付回调重试能力
- 汇总所有服务建表和初始化数据到一份总初始化 SQL
- 在现有 `frontend/` 中补一个快速测试闭环页面

本次改动不包含：

- 接入真实第三方支付平台
- 服务内分库分表
- 将支付结果改为 MQ 异步通知
- 对现有核心业务流程做额外架构升级

## Confirmed Decisions

- 所有微服务统一放到 `services/` 目录下
- 新增独立 `pay-service`
- `pay-service` 使用独立库 `tickgo_pay`
- 不做服务内分库分表
- 所有建表和初始化数据统一收敛到一份初始化 SQL
- 支付链路采用 `pay-service` 主动回调 `order-service`
- 支付成功后，`order-service` 继续负责推进订单状态并调用 `ticket-service` 完成票务确认
- 快速测试前端直接复用现有 `frontend/` 项目，不新建第二套前端

## Target Structure

重构后的顶层结构：

```text
TickGo/
  services/
    gateway-service/
    ticket-service/
    order-service/
    user-service/
    pay-service/
  frontend/
  resources/
```

根 `pom.xml` 作为聚合工程，模块路径改为：

- `services/gateway-service`
- `services/ticket-service`
- `services/order-service`
- `services/user-service`
- `services/pay-service`

## Service Responsibilities

### gateway-service

- 保持统一网关职责
- 新增 `/api/pay/**` 到 `pay-service` 的路由
- 现有 `/api/user/**`、`/api/ticket/**`、`/api/order/**` 路由保持不变

### user-service

- 维持当前用户信息和乘车人管理能力
- 继续为购票流程提供用户和乘车人校验

### ticket-service

- 维持当前查票、锁座、释放、确认职责
- 不承担支付逻辑

### order-service

- 维持订单创建、订单取消、订单状态流转职责
- 新增接收支付成功通知接口
- 在合法状态下执行 `WAIT_PAY -> PAID`
- 在事务提交后继续调用 `ticket-service.confirm`
- 保持当前补偿逻辑不变，用于票务确认失败兜底

### pay-service

- 负责支付单创建
- 负责支付状态机管理
- 负责支付幂等与重复支付拦截
- 负责支付成功后主动回调 `order-service`
- 负责回调失败后的补偿重试

## Payment Domain Model

`pay-service` 独立库：`tickgo_pay`

### t_payment_order

用于记录支付单主状态。

核心字段：

- `id`
- `payment_sn`：支付单号，唯一
- `order_sn`：关联订单号
- `user_id`
- `pay_amount`
- `pay_channel`：当前先用 `MOCK`
- `status`：`INIT / PAYING / SUCCESS / FAILED / CLOSED`
- `callback_status`：`PENDING / SUCCESS / FAILED`
- `callback_retry_count`
- `last_callback_time`
- `success_time`
- `create_time`
- `update_time`

约束：

- 一个 `order_sn` 只允许存在一笔有效支付单
- `payment_sn` 唯一

### t_payment_callback_task

用于记录支付成功后对订单服务回调失败的补偿任务。

核心字段：

- `id`
- `payment_sn`
- `order_sn`
- `status`
- `retry_count`
- `max_retry_count`
- `next_retry_time`
- `error_msg`
- `create_time`
- `update_time`

## Payment State Machine

支付单状态：

- `INIT`：支付单已创建，待支付
- `PAYING`：支付处理中
- `SUCCESS`：支付成功
- `FAILED`：支付失败
- `CLOSED`：支付关闭

回调状态：

- `PENDING`
- `SUCCESS`
- `FAILED`

状态规则：

- `INIT -> PAYING -> SUCCESS`
- `INIT -> PAYING -> FAILED`
- `INIT -> CLOSED`
- `SUCCESS` 之后不允许再次支付
- 对同一支付单重复提交支付请求时，必须幂等返回当前结果，不能重复触发订单回调

## API Design

### pay-service external APIs

- `POST /pay/create`
  - 输入：`orderSn`、`userId`、`payAmount`
  - 输出：`paymentSn`、支付状态
- `POST /pay/submit`
  - 输入：`paymentSn`
  - 行为：模拟支付成功
  - 输出：支付状态、回调状态
- `GET /pay/status`
  - 输入：`paymentSn` 或 `orderSn`
  - 输出：支付单详情、支付状态、回调状态
- `POST /pay/close`
  - 输入：`paymentSn`
  - 输出：关闭结果

### order-service internal callback API

- `POST /order/pay-notify`
  - 输入：`paymentSn`、`orderSn`
  - 行为：订单服务接收支付成功通知，幂等推进订单支付完成

## Main Flow

完整链路如下：

1. 前端调用现有购票流程创建订单，订单状态为 `WAIT_PAY`
2. 前端调用 `pay-service.create` 创建支付单
3. 前端调用 `pay-service.submit` 模拟支付成功
4. `pay-service` 本地事务将支付单更新为 `SUCCESS`
5. `pay-service` 在事务提交后主动回调 `order-service.pay-notify`
6. `order-service` 幂等校验订单状态，只允许 `WAIT_PAY -> PAID`
7. `order-service` 事务提交后继续调用 `ticket-service.confirm`
8. 如果支付回调失败，`pay-service` 写回调补偿任务并定时重试
9. 如果票务确认失败，沿用 `order-service` 现有补偿机制

## Failure Handling

### pay-service callback failure

如果 `pay-service` 支付成功但调用 `order-service.pay-notify` 失败：

- 记录 `t_payment_callback_task`
- 标记 `t_payment_order.callback_status = FAILED`
- 定时任务扫描失败或待重试任务
- 重试成功后将 `callback_status` 更新为 `SUCCESS`

### repeated callback

`order-service` 的支付通知入口必须幂等：

- 若订单已经是 `PAID`，直接返回成功
- 若订单已经是 `CANCELED`，返回明确失败，交由 `pay-service` 记录和重试策略处理

### repeated pay submit

`pay-service.submit` 对已成功支付单重复请求时：

- 直接返回已成功状态
- 不重复创建新的支付回调任务

## Frontend Fast Test Page

快速测试前端放在现有 `frontend/` 中，目标是简化闭环验证，而不是做正式产品页面。

页面能力：

- 查询默认车次余票
- 选择乘车人并创建订单
- 查看当前最近订单
- 创建支付单
- 模拟支付成功
- 查看支付状态
- 查看订单状态

页面重点：

- 把现有“查票 / 下单 / 支付 / 状态查看”串在一个页面里
- 输出关键单号：`orderSn`、`paymentSn`
- 输出关键状态：订单状态、支付状态、支付回调状态

## Initialization SQL

最终保留一份总初始化 SQL，统一包含：

- `tickgo_user`
- `tickgo_ticket`
- `tickgo_order`
- `tickgo_pay`

要求：

- 能一键完成本地联调环境初始化
- 包含最少可用测试数据
- 订单、车票、支付单、补偿任务初始化为空

## Testing Strategy

验证重点：

- 目录迁移后 Maven 聚合编译正常
- 网关路由正常转发到 `pay-service`
- 创建订单后可成功创建支付单
- 模拟支付成功后，`pay-service` 可回调 `order-service`
- `order-service` 可推进订单到 `PAID`
- `order-service` 可继续确认车票
- 支付重复提交不会重复推进订单
- 支付回调失败可写补偿任务并重试
- 快速测试前端可跑通完整闭环

## Risks

- 目录迁移会影响模块路径、IDE 配置和脚本路径
- `order-service` 原有 `payOrder` 语义要重新收敛，避免和 `pay-service` 冲突
- 初始化 SQL 需要和现有三个服务库保持兼容，再新增 `tickgo_pay`
- 前端若继续复用现有页面，需要控制改动范围，避免把演示页和业务页混在一起过深
