SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------
-- 1. user-service database and seed data
-- ------------------------------------
CREATE DATABASE IF NOT EXISTS tickgo_user
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE tickgo_user;

DROP TABLE IF EXISTS t_passenger;
DROP TABLE IF EXISTS t_user;

CREATE TABLE t_user (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        username VARCHAR(64) NOT NULL UNIQUE,
                        password VARCHAR(128) NOT NULL,
                        real_name VARCHAR(64) NOT NULL,
                        id_card VARCHAR(64) NOT NULL,
                        phone VARCHAR(32) NOT NULL,
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='用户表';

CREATE TABLE t_passenger (
                             id BIGINT PRIMARY KEY AUTO_INCREMENT,
                             user_id BIGINT NOT NULL,
                             real_name VARCHAR(64) NOT NULL,
                             id_card VARCHAR(64) NOT NULL,
                             phone VARCHAR(32) NOT NULL,
                             type TINYINT NOT NULL DEFAULT 1 COMMENT '1成人 2儿童 3学生',
                             create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             KEY idx_user_id (user_id)
) COMMENT='乘车人表';

INSERT INTO t_user (id, username, password, real_name, id_card, phone)
VALUES (1, 'admin', '123456', '管理员', '110101199001011234', '13800138000');

INSERT INTO t_passenger (id, user_id, real_name, id_card, phone, type)
VALUES (1, 1, '张三', '110101199001011234', '13800138000', 1),
       (2, 1, '李四', '110101199002021234', '13800138001', 1),
       (3, 1, '王五', '110101199003031234', '13800138002', 1);

-- ------------------------------------
-- 2. ticket-service database and seed data
-- ------------------------------------
CREATE DATABASE IF NOT EXISTS tickgo_ticket
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE tickgo_ticket;

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
                        seat_type TINYINT NOT NULL COMMENT '1一等座 2二等座',
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
                          ticket_status TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 10已支付 30已取消',
                          order_sn VARCHAR(64) NOT NULL COMMENT '订单号',
                          departure VARCHAR(64) NOT NULL COMMENT '出发站',
                          arrival VARCHAR(64) NOT NULL COMMENT '到达站',
                          create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='车票表';

INSERT INTO t_train (
    id, train_number, start_station, end_station, departure_time, arrival_time, sale_status
) VALUES
    (1, 'G1001', '北京南', '宁波', '2026-06-01 08:00:00', '2026-06-01 14:00:00', 0);

INSERT INTO t_train_station (
    train_id, station_name, sequence_no, arrival_time, departure_time
) VALUES
    (1, '北京南', 1, NULL, '2026-06-01 08:00:00'),
    (1, '济南西', 2, '2026-06-01 09:30:00', '2026-06-01 09:35:00'),
    (1, '南京南', 3, '2026-06-01 11:10:00', '2026-06-01 11:15:00'),
    (1, '杭州东', 4, '2026-06-01 12:40:00', '2026-06-01 12:45:00'),
    (1, '宁波', 5, '2026-06-01 14:00:00', NULL);

INSERT INTO t_seat (
    train_id, carriage_number, seat_number, seat_type,
    start_station, end_station, start_sequence, end_sequence,
    price, seat_status
) VALUES
    (1, '01', '01A', 1, '北京南', '济南西', 1, 2, 18000, 0),
    (1, '01', '01A', 1, '济南西', '南京南', 2, 3, 22000, 0),
    (1, '01', '01A', 1, '南京南', '杭州东', 3, 4, 16000, 0),
    (1, '01', '01A', 1, '杭州东', '宁波', 4, 5, 10000, 0),
    (1, '01', '01B', 1, '北京南', '济南西', 1, 2, 18000, 0),
    (1, '01', '01B', 1, '济南西', '南京南', 2, 3, 22000, 0),
    (1, '01', '01B', 1, '南京南', '杭州东', 3, 4, 16000, 0),
    (1, '01', '01B', 1, '杭州东', '宁波', 4, 5, 10000, 0),
    (1, '01', '01C', 1, '北京南', '济南西', 1, 2, 18000, 0),
    (1, '01', '01C', 1, '济南西', '南京南', 2, 3, 22000, 0),
    (1, '01', '01C', 1, '南京南', '杭州东', 3, 4, 16000, 0),
    (1, '01', '01C', 1, '杭州东', '宁波', 4, 5, 10000, 0),
    (1, '02', '01A', 2, '北京南', '济南西', 1, 2, 10000, 0),
    (1, '02', '01A', 2, '济南西', '南京南', 2, 3, 15000, 0),
    (1, '02', '01A', 2, '南京南', '杭州东', 3, 4, 12000, 0),
    (1, '02', '01A', 2, '杭州东', '宁波', 4, 5, 8000, 0),
    (1, '02', '01B', 2, '北京南', '济南西', 1, 2, 10000, 0),
    (1, '02', '01B', 2, '济南西', '南京南', 2, 3, 15000, 0),
    (1, '02', '01B', 2, '南京南', '杭州东', 3, 4, 12000, 0),
    (1, '02', '01B', 2, '杭州东', '宁波', 4, 5, 8000, 0),
    (1, '02', '01C', 2, '北京南', '济南西', 1, 2, 10000, 0),
    (1, '02', '01C', 2, '济南西', '南京南', 2, 3, 15000, 0),
    (1, '02', '01C', 2, '南京南', '杭州东', 3, 4, 12000, 0),
    (1, '02', '01C', 2, '杭州东', '宁波', 4, 5, 8000, 0),
    (1, '02', '01D', 2, '北京南', '济南西', 1, 2, 10000, 0),
    (1, '02', '01D', 2, '济南西', '南京南', 2, 3, 15000, 0),
    (1, '02', '01D', 2, '南京南', '杭州东', 3, 4, 12000, 0),
    (1, '02', '01D', 2, '杭州东', '宁波', 4, 5, 8000, 0);

-- ------------------------------------
-- 3. order-service database and operational tables
-- ------------------------------------
CREATE DATABASE IF NOT EXISTS tickgo_order
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE tickgo_order;

DROP TABLE IF EXISTS t_compensation_task;
DROP TABLE IF EXISTS t_order_item;
DROP TABLE IF EXISTS t_order;

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

CREATE TABLE t_compensation_task (
                                     id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                     task_type VARCHAR(64) NOT NULL COMMENT '任务类型：CANCEL_TICKET, CONFIRM_TICKET, SEND_CANCEL_DELAY_MESSAGE',
                                     biz_id VARCHAR(128) NOT NULL COMMENT '业务ID，通常是订单号',
                                     status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2成功 3失败',
                                     retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
                                     max_retry_count INT NOT NULL DEFAULT 5 COMMENT '最大重试次数',
                                     next_retry_time DATETIME NOT NULL COMMENT '下次重试时间',
                                     error_msg TEXT COMMENT '错误信息',
                                     create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     UNIQUE KEY uk_task_type_biz_id (task_type, biz_id),
                                     KEY idx_status_next_retry_time (status, next_retry_time)
) COMMENT='补偿任务表';

-- 订单、车票和补偿表在初始化时保持为空，便于直接联调下单/支付/取消流程。

-- ------------------------------------
-- 4. pay-service database and operational tables
-- ------------------------------------
CREATE DATABASE IF NOT EXISTS tickgo_pay
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE tickgo_pay;

DROP TABLE IF EXISTS t_payment_callback_task;
DROP TABLE IF EXISTS t_payment_order;

CREATE TABLE t_payment_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_sn VARCHAR(64) NOT NULL,
    order_sn VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    pay_amount INT NOT NULL,
    pay_channel VARCHAR(32) NOT NULL DEFAULT 'MOCK',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0INIT 10PAYING 20SUCCESS 30FAILED 40CLOSED',
    callback_status TINYINT NOT NULL DEFAULT 0 COMMENT '0PENDING 10SUCCESS 20FAILED',
    callback_retry_count INT NOT NULL DEFAULT 0,
    last_callback_time DATETIME NULL,
    success_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_sn (payment_sn),
    UNIQUE KEY uk_order_sn (order_sn)
) COMMENT='支付单表';

CREATE TABLE t_payment_callback_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_sn VARCHAR(64) NOT NULL,
    order_sn VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2成功 3失败',
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 5,
    next_retry_time DATETIME NOT NULL,
    error_msg TEXT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_callback (payment_sn),
    KEY idx_status_next_retry_time (status, next_retry_time)
) COMMENT='支付回调补偿任务表';

SET FOREIGN_KEY_CHECKS = 1;
