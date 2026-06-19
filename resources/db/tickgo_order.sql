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
                                     task_type VARCHAR(64) NOT NULL COMMENT '任务类型：CANCEL_TICKET, CONFIRM_TICKET',
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
