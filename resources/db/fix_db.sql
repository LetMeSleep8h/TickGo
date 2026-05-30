USE tick_go;

ALTER DATABASE tick_go
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE t_train
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE t_train_station
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE t_seat
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE t_ticket
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE t_order
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE t_order_item
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;