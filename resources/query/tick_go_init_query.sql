SELECT
    carriage_number,
    seat_number,
    start_station,
    end_station,
    start_sequence,
    end_sequence,
    seat_status
FROM t_seat
WHERE train_id = 1
  AND carriage_number = '01'
  AND seat_number = '01A'
ORDER BY start_sequence;


UPDATE t_seat
SET seat_status = 1
WHERE train_id = 1
  AND carriage_number = '01'
  AND seat_number = '01A'
  AND start_sequence >= 1
  AND end_sequence <= 4;
# 查询修改后状态
SELECT
    start_station,
    end_station,
    seat_status
FROM t_seat
WHERE train_id = 1
  AND carriage_number = '01'
  AND seat_number = '01A'
ORDER BY start_sequence;


SELECT COUNT(*) FROM t_train WHERE id = 1;

SELECT COUNT(*) FROM t_train_station WHERE train_id = 1;

SELECT COUNT(*) FROM t_seat WHERE train_id = 1;

# 恢复修改
UPDATE t_seat
SET seat_status = 0
WHERE train_id = 1
  AND carriage_number = '01'
  AND seat_number = '01A';
# 恢复全部测试数据
UPDATE t_seat
SET seat_status = 0
WHERE train_id = 1;

# 查询起始站和终点站顺序
SELECT sequence_no
FROM t_train_station
WHERE train_id = 1
  AND station_name = '北京南';

SELECT sequence_no
FROM t_train_station
WHERE train_id = 1
  AND station_name = '杭州东';

# 查询余票
SELECT
    seat_type,
    COUNT(*) AS remain_count
FROM (
         SELECT
             carriage_number,
             seat_number,
             seat_type,
             COUNT(*) AS available_segment_count
         FROM t_seat
         WHERE train_id = 1
           AND start_sequence >= 1
           AND end_sequence <= 4
           AND seat_status = 0
         GROUP BY carriage_number, seat_number, seat_type
         HAVING COUNT(*) = 3
     ) available_seat
GROUP BY seat_type;
