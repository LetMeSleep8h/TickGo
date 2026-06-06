# TickGo 微服务直接测试用例

## 前提条件
1. 启动 MySQL、Redis、RocketMQ
2. 执行数据库初始化脚本（见下面说明）
3. 启动所有服务：
   ```bash
   # 启动 user-service (端口 8084)
   # 启动 ticket-service (端口 8082)
   # 启动 order-service (端口 8083)
   ```

## 数据库初始化说明

**重要！** 由于微服务拆分后每个服务有自己的数据库：
- `tickgo_user` - 用户数据库
- `tickgo_ticket` - 车票数据库  
- `tickgo_order` - 订单数据库

请依次执行：
```sql
-- 1. 创建用户库和表
source resources/db/tickgo_user.sql;

-- 2. 创建车票库和表
source resources/db/tickgo_ticket.sql;

-- 3. 创建订单库和表
source resources/db/tickgo_order.sql;

-- 4. 插入测试数据
source resources/data/tick_go_init_data.sql;
```

## 测试数据
- 车次ID: 1, 车次号: G1001
- 站点：北京南 -> 济南西 -> 南京南 -> 杭州东 -> 宁波
- 用户ID: 1
- 乘车人ID: 1, 2, 3

---

## 一、用户服务测试 (端口 8084)

### 1.1 获取用户信息
```bash
curl -X GET 'http://localhost:8084/user/1'
```

**预期响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "admin",
    "realName": "管理员",
    "idCard": "110101199001011234",
    "phone": "13800138000"
  }
}
```

### 1.2 获取用户乘车人列表
```bash
curl -X GET 'http://localhost:8084/user/1/passengers'
```

**预期响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {"id": 1, "realName": "张三", "idCard": "110101199001011234"},
    {"id": 2, "realName": "李四", "idCard": "110101199002021234"},
    {"id": 3, "realName": "王五", "idCard": "110101199003031234"}
  ]
}
```

### 1.3 验证乘车人归属
```bash
curl -X POST 'http://localhost:8084/user/validate-passengers' \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": 1,
    "passengerIds": [1, 2]
  }'
```

---

## 二、车票服务测试 (端口 8082)

### 2.1 初始化 Token（重要！必须先执行）
```bash
curl -X POST 'http://localhost:8082/ticket/initToken?trainId=1&departure=北京南&arrival=宁波'
```

**预期响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 2.2 查询余票
```bash
curl -X GET 'http://localhost:8082/ticket/query?trainId=1&departure=南京南&arrival=杭州东'
```

**预期响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "trainId": 1,
    "departure": "南京南",
    "arrival": "杭州东",
    "seatTypeRemains": [
      {
        "seatType": 1,
        "remainCount": 3
      }
    ]
  }
}
```

### 2.3 预占座位
```bash
curl -X POST 'http://localhost:8082/ticket/preOccupy' \
  -H 'Content-Type: application/json' \
  -d '{
    "trainId": 1,
    "departure": "南京南",
    "arrival": "杭州东",
    "orderSn": "ORDER20260606001",
    "passengers": [
      {
        "passengerId": 1,
        "seatType": 1
      }
    ]
  }'
```

**预期响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "trainNumber": "G1001",
    "items": [
      {
        "passengerId": 1,
        "seatType": 1,
        "carriageNumber": "01",
        "seatNumber": "01A",
        "amount": 20000
      }
    ]
  }
}
```

---

## 三、订单服务测试 (端口 8083)

### 3.1 创建订单
```bash
curl -X POST 'http://localhost:8083/order/create' \
  -H 'Content-Type: application/json' \
  -d '{
    "orderSn": "ORDER20260606001",
    "userId": 1,
    "username": "admin",
    "trainId": 1,
    "trainNumber": "G1001",
    "departure": "南京南",
    "arrival": "杭州东",
    "items": [
      {
        "passengerId": 1,
        "seatType": 1,
        "carriageNumber": "01",
        "seatNumber": "01A",
        "amount": 20000
      }
    ]
  }'
```

### 3.2 支付订单
```bash
curl -X POST 'http://localhost:8083/order/pay?orderSn=ORDER20260606001'
```

### 3.3 取消订单
```bash
curl -X POST 'http://localhost:8083/order/cancel?orderSn=ORDER20260606001'
```

---

## 四、完整购票流程测试

### 4.1 场景：用户张三购买南京南到杭州东的车票

**Step 1: 查询余票**
```bash
curl -X GET 'http://localhost:8082/ticket/query?trainId=1&departure=南京南&arrival=杭州东'
```

**Step 2: 预占座位**
```bash
# 生成唯一订单号
ORDER_SN="ORDER$(date +%Y%m%d%H%M%S)"

curl -X POST 'http://localhost:8082/ticket/preOccupy' \
  -H 'Content-Type: application/json' \
  -d "{
    \"trainId\": 1,
    \"departure\": \"南京南\",
    \"arrival\": \"杭州东\",
    \"orderSn\": \"${ORDER_SN}\",
    \"passengers\": [
      {
        \"passengerId\": 1,
        \"seatType\": 1
      }
    ]
  }"
```

**Step 3: 创建订单**
```bash
curl -X POST 'http://localhost:8083/order/create' \
  -H 'Content-Type: application/json' \
  -d "{
    \"orderSn\": \"${ORDER_SN}\",
    \"userId\": 1,
    \"username\": \"admin\",
    \"trainId\": 1,
    \"trainNumber\": \"G1001\",
    \"departure\": \"南京南\",
    \"arrival\": \"杭州东\",
    \"items\": [
      {
        \"passengerId\": 1,
        \"seatType\": 1,
        \"carriageNumber\": \"01\",
        \"seatNumber\": \"01A\",
        \"amount\": 20000
      }
    ]
  }"
```

**Step 4: 支付订单**
```bash
curl -X POST "http://localhost:8083/order/pay?orderSn=${ORDER_SN}"
```

**Step 5: 确认出票**
```bash
curl -X POST "http://localhost:8082/ticket/confirm?orderSn=${ORDER_SN}"
```

---

## 五、Postman Collection

如果您使用 Postman，可以创建以下 Collection：

### Collection: TickGo API

#### Folder: User Service (Base URL: http://localhost:8084)
- **GET** `/user/{userId}`
- **GET** `/user/{userId}/passengers`
- **POST** `/user/validate-passengers`

#### Folder: Ticket Service (Base URL: http://localhost:8082)
- **POST** `/ticket/initToken`
  - Params: `trainId=1`, `departure=北京南`, `arrival=宁波`
- **GET** `/ticket/query`
  - Params: `trainId=1`, `departure=南京南`, `arrival=杭州东`
- **POST** `/ticket/preOccupy`
- **POST** `/ticket/confirm`
- **POST** `/ticket/release`

#### Folder: Order Service (Base URL: http://localhost:8083)
- **POST** `/order/create`
- **POST** `/order/pay`
  - Params: `orderSn=xxx`
- **POST** `/order/cancel`
  - Params: `orderSn=xxx`

---

## 六、故障排查

### 如果访问不到接口：

1. **检查服务是否正常启动**
   ```bash
   # 检查端口是否监听
   lsof -i :8084  # user-service
   lsof -i :8082  # ticket-service
   lsof -i :8083  # order-service
   ```

2. **检查数据库连接**
   - 确认 MySQL 是否启动
   - 确认数据库 `tickgo_user`、`tickgo_ticket`、`tickgo_order` 是否存在
   - 确认用户名密码正确（默认 root/12345678）

3. **查看服务日志**
   - 查看启动日志，确认无报错
   - 访问接口时查看是否有请求日志

4. **数据库初始化问题**
   - 如果数据库没有数据，请先执行初始化脚本

---

## 七、注意事项

1. **必须先初始化 Token**：每次重启服务或更换区间前，需要调用 `/ticket/initToken`
2. **订单号唯一性**：`orderSn` 必须是唯一的，否则会冲突
3. **时间窗口**：预占座位后需要在规定时间内完成支付，否则座位会被释放
4. **并发测试**：可以使用 JMeter 或 wrk 进行并发压测
5. **数据库**：每个微服务连接各自的数据库，确保都已初始化
