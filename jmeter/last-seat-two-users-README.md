# Last Seat Two Users

文件：

- `last-seat-two-users.jmx`
- `last-seat-two-users.csv`

用途：

- 验证最后 1 张票场景下，两个不同 `ticket-service` 实例是否会把票卖给两个不同乘客

当前数据：

- `8082` -> `passengerId=1`
- `18082` -> `passengerId=2`

运行前准备：

1. 保证车次对应区间只剩 1 张可售票
2. 确保 `ticket-service` 两个实例已启动：`8082`、`18082`
3. 确保 `passengerId=1` 和 `passengerId=2` 都可用
4. 清理这两个乘客已有的待支付/已支付票记录，避免被幂等或业务校验提前拦截

预期结果：

- 2 个请求里只能成功 1 个
- 另 1 个必须失败
- 数据库最终只能有 1 条新增有效票记录

重点验证：

```sql
select order_sn, passenger_id, ticket_status
from t_ticket
where train_id = 1
  and passenger_id in (1, 2)
order by id desc;
```

如果两个乘客都成功生成有效票，则说明存在超卖或锁粒度问题。
