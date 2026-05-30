USE tick_go;

SELECT train_id, station_name, sequence_no
FROM t_train_station
WHERE train_id = 1
ORDER BY sequence_no;

SELECT sequence_no
FROM t_train_station
WHERE train_id = 1
  AND station_name = '北京南';

SELECT sequence_no
FROM t_train_station
WHERE train_id = 1
  AND station_name = '杭州东';