# Last Seat Four Users

文件：

- `last-seat-four-users.jmx`
- `last-seat-four-users.csv`

用途：

- 验证最后 1 张票场景下，4 个不同乘客同时请求时，系统是否仍然只能成功 1 次

当前 CSV 配置：

- `8082` -> `passengerId=1`
- `18082` -> `passengerId=2`
- `8082` -> `passengerId=3`
- `18082` -> `passengerId=4`

说明：

- 当前初始化 SQL 明确有 `passengerId=1,2,3`
- `passengerId=4` 是否存在取决于你当前数据库
- 如果不存在，直接编辑 `last-seat-four-users.csv` 把最后一行换成一个真实存在的乘客 ID

运行前准备：

1. 保证车次对应区间只剩 1 张可售票
2. 确保 `ticket-service` 两个实例已启动：`8082`、`18082`
3. 清理这 4 个乘客已有的待支付/已支付票记录，避免被幂等或业务校验提前拦截

预期结果：

- 4 个请求里只能成功 1 个
- 其余 3 个必须失败
- 数据库最终只能有 1 条新增有效票记录

建议验证 SQL：

```sql
select order_sn, passenger_id, ticket_status
from t_ticket
where train_id = 1
  and passenger_id in (1, 2, 3, 4)
order by id desc;
```
