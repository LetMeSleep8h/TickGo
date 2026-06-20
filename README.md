# TickGo

> 基于 Spring Boot、Spring Cloud、MySQL、Redis、Redisson、RocketMQ 实现的分布式购票系统，聚焦区间余票查询、并发锁座、防超卖、延迟取消与订单补偿。

## 项目概览

- 参考 12306 业务场景实现区间售票，而不是整趟列车一次性售卖
- 核心链路覆盖查票、锁座、下单、支付确认、超时取消、库存回补
- 重点验证高并发下的前置削峰、幂等控制、分布式锁和库存一致性

## 技术栈

- 后端：Java 17、Spring Boot、Spring Cloud Gateway、OpenFeign、MyBatis-Plus
- 数据与中间件：MySQL、Redis、Redisson、RocketMQ
- 前端：Vue 3、TypeScript

## 服务划分

- `gateway-service`：统一入口与路由转发
- `user-service`：用户与乘车人信息管理
- `ticket-service`：查票、token 管理、锁座、确认车票、释放座位、购票幂等
- `order-service`：创建订单、支付确认、取消订单、延迟消息、补偿任务
- `frontend`：购票页面与下单交互

## 核心设计

### 1. 区间库存模型

将一个物理座位拆成多个相邻站点间的 `segment`，按 `出发站 -> 到达站` 计算是否可售。  
这样可以支持区间复用，避免把整趟车库存简单粗暴地一次锁死。

### 2. 并发购票控制

购票主链路采用四层控制：

1. `Redis Token` 前置拦截无效请求
2. `Redis` 幂等控制拦截重复提交
3. `Redisson` 分布式锁降低同车次同席别并发冲突
4. `MySQL` 条件更新作为最终库存兜底

其中 Redis 和锁负责削峰与互斥，MySQL 负责最终判断是否锁座成功。

### 3. 超时取消与补偿

- 创建订单后发送 RocketMQ 延迟消息
- 到期未支付时触发取消订单与释放座位
- MQ 发送失败或资源释放失败时，写入补偿任务异步重试
- 通过订单状态机幂等控制，避免重复消费把已支付订单再次取消

## 核心流程

### 查票

1. 根据 `trainId + departure + arrival` 定位目标区间
2. 计算区间覆盖的全部座位段
3. 统计各席别剩余可售座位数

### 下单

1. 校验乘车人与购票请求
2. 执行 token 前置校验与幂等校验
3. 获取分布式锁并尝试锁座
4. 写入车票与订单数据
5. 发送延迟取消消息

### 支付确认

1. `order-service` 更新订单为已支付
2. 事务提交后调用 `ticket-service` 确认车票
3. 确认失败则写入补偿任务

### 超时取消

1. 延迟消息到期后消费取消逻辑
2. 仅允许 `WAIT_PAY -> CANCELED`
3. 事务提交后释放座位并回补资源

## 压测与结果

### 重复请求幂等拦截

- 利用 `Redis` 实现购票请求幂等前置拦截
- 将高并发重复购票请求平均响应时间由约 `2069ms` 降低至约 `31ms`
- 减少无效请求进入分布式锁与下游订单链路

### 并发购票防超卖

- 基于 `Redisson` 分布式锁与 `MySQL` 条件更新控制库存竞争
- 在 `100` 并发用户同时购票、余票仅剩 `3` 张的场景下
- 最终成功 `3` 单、失败 `97` 单，验证未发生超卖

### MQ 路由问题修复

- 通过显式绑定 `RocketMQ Broker` 地址
- 解决多网卡环境下 Broker 自动注册错误 IP 导致的消息发送失败与延迟消费异常

## 快速启动

### 环境依赖

- JDK 17
- MySQL 8.x
- Redis
- RocketMQ
- Node.js 18+

### 启动顺序

1. 执行 `resources/db` 下的建表 SQL
2. 启动 `user-service`
3. 启动 `ticket-service`
4. 启动 `order-service`
5. 启动 `gateway-service`
6. 启动 `frontend`

### 默认入口

- 网关：`http://localhost:8080`
- 前端：`http://localhost:5173`

## 后续优化

- 用 `Redis Hash + Lua` 优化区间 token 的精细化扣减
- 增加更完整的监控、告警与压测链路
- 优化多乘客邻座分配策略
