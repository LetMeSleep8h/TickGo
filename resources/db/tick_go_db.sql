CREATE DATABASE IF NOT EXISTS tick_go
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE tick_go;

DROP TABLE IF EXISTS t_order_item;
DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_ticket;
DROP TABLE IF EXISTS t_seat;
DROP TABLE IF EXISTS t_train_station;
DROP TABLE IF EXISTS t_train;

CREATE TABLE t_train (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT,
                         train_number VARCHAR(32) NOT NULL COMMENT '车次',
                         start_station VARCHAR(64) NOT NULL,
                         end_station VARCHAR(64) NOT NULL,
                         departure_time DATETIME NOT NULL,
                         arrival_time DATETIME NOT NULL,
                         sale_status TINYINT NOT NULL DEFAULT 0 COMMENT '0可售 1不可售',
                         create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='车次表';

CREATE TABLE t_train_station (
                                 id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                 train_id BIGINT NOT NULL,
                                 station_name VARCHAR(64) NOT NULL,
                                 sequence_no INT NOT NULL COMMENT '站点顺序，从1开始',
                                 arrival_time DATETIME NULL,
                                 departure_time DATETIME NULL,
                                 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                 UNIQUE KEY uk_train_station_seq (train_id, sequence_no),
                                 UNIQUE KEY uk_train_station_name (train_id, station_name)
) COMMENT='车次经停站表';

CREATE TABLE t_seat (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        train_id BIGINT NOT NULL,
                        carriage_number VARCHAR(16) NOT NULL COMMENT '车厢号',
                        seat_number VARCHAR(16) NOT NULL COMMENT '座位号',
                        seat_type TINYINT NOT NULL COMMENT '1二等座',
                        start_station VARCHAR(64) NOT NULL COMMENT '区间起点',
                        end_station VARCHAR(64) NOT NULL COMMENT '区间终点',
                        start_sequence INT NOT NULL,
                        end_sequence INT NOT NULL,
                        price INT NOT NULL DEFAULT 0 COMMENT '单位：分',
                        seat_status TINYINT NOT NULL DEFAULT 0 COMMENT '0可售 1锁定 2已售',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_seat_segment (
                                                    train_id, carriage_number, seat_number, seat_type, start_sequence, end_sequence
                            ),
                        KEY idx_train_segment (train_id, start_sequence, end_sequence),
                        KEY idx_seat_lookup (train_id, carriage_number, seat_number)
) COMMENT='区间座位表';

CREATE TABLE t_ticket (
                          id BIGINT PRIMARY KEY AUTO_INCREMENT,
                          username VARCHAR(64) NOT NULL,
                          train_id BIGINT NOT NULL,
                          carriage_number VARCHAR(16) NOT NULL,
                          seat_number VARCHAR(16) NOT NULL,
                          passenger_id BIGINT NOT NULL COMMENT '乘车人ID',
                          seat_type TINYINT NOT NULL,
                          ticket_status TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 5已关闭',
                          create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='车票表';

CREATE TABLE t_order (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT,
                         order_sn VARCHAR(64) NOT NULL,
                         user_id BIGINT NOT NULL,
                         username VARCHAR(64) NOT NULL,
                         train_id BIGINT NOT NULL,
                         train_number VARCHAR(32) NOT NULL,
                         departure VARCHAR(64) NOT NULL,
                         arrival VARCHAR(64) NOT NULL,
                         status TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 10已支付 30已取消',
                         order_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         UNIQUE KEY uk_order_sn (order_sn),
                         KEY idx_user_id (user_id)
) COMMENT='订单表';

CREATE TABLE t_order_item (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT,
                              order_sn VARCHAR(64) NOT NULL,
                              user_id BIGINT NOT NULL,
                              username VARCHAR(64) NOT NULL,
                              train_id BIGINT NOT NULL,
                              carriage_number VARCHAR(16) NOT NULL,
                              seat_number VARCHAR(16) NOT NULL,
                              seat_type TINYINT NOT NULL,
                              real_name VARCHAR(64) NOT NULL,
                              id_card VARCHAR(64) NOT NULL,
                              status TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 10已支付 30已取消',
                              amount INT NOT NULL DEFAULT 0 COMMENT '单位：分',
                              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              KEY idx_order_sn (order_sn)
) COMMENT='订单明细表';
