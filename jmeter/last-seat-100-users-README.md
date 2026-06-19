# Last Seat 100 Users

文件：

- `last-seat-100-users.jmx`
- `last-seat-100-users.csv`

用途：

- 模拟 100 个并发请求同时竞争余票
- 端口在 `8082` / `18082` 间交替
- `passengerId` 使用 `1..100`

关键配置：

- `ThreadGroup.num_threads = 100`
- `Synchronizing Timer.groupSize = 100`
- `CSV` 共 100 行数据

注意：

- 这份 CSV 是模板
- 如果你数据库里没有 `1..100` 这些真实乘客，部分请求会因为乘客不存在或不属于当前用户而失败
- 真要测“库存/锁”，最好把 CSV 里的乘客 ID 替换成数据库里真实存在的 100 个乘客
