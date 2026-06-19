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
