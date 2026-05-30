SELECT
    carriage_number,
    seat_number,
    start_station,
    end_station,
    seat_status
FROM t_seat
WHERE train_id = 1
  AND seat_type = 1
ORDER BY seat_number, start_sequence;
