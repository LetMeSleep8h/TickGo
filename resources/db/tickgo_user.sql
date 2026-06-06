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

-- 插入测试数据
INSERT INTO t_user (username, password, real_name, id_card, phone)
VALUES ('admin', '123456', '管理员', '110101199001011234', '13800138000');

INSERT INTO t_passenger (user_id, real_name, id_card, phone, type)
VALUES (1, '张三', '110101199001011234', '13800138000', 1),
       (1, '李四', '110101199002021234', '13800138001', 1),
       (1, '王五', '110101199003031234', '13800138002', 1);
