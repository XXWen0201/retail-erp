-- =====================================================================
--  中小零售门店进销存与库存预警管理系统  ——  数据库结构
--  MySQL 8.0 / utf8mb4
--
--  设计说明：
--   1. 所有业务主表均带 create_time / update_time / deleted（逻辑删除）
--   2. stock           库存汇总表，带 version 乐观锁字段，是扣减一致性的核心
--   3. stock_batch     批次表，记录生产日期与到期日期，支撑保质期管理与 FEFO 扣减
--   4. stock_record    库存流水表，记录每笔变动的前后值，是盘点差异追溯的唯一凭据
--   5. 金额统一 DECIMAL(12,2)，成本单价 DECIMAL(12,4) 防止移动加权平均的精度丢失
-- =====================================================================

DROP DATABASE IF EXISTS retail_erp;
CREATE DATABASE retail_erp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE retail_erp;

-- ---------------------------------------------------------------------
-- 1. 系统用户
-- ---------------------------------------------------------------------
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    real_name   VARCHAR(50)           DEFAULT NULL COMMENT '姓名',
    role        VARCHAR(20)  NOT NULL DEFAULT 'STAFF' COMMENT '角色 ADMIN/MANAGER/STAFF',
    phone       VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB COMMENT '系统用户表';

-- ---------------------------------------------------------------------
-- 2. 商品分类
-- ---------------------------------------------------------------------
CREATE TABLE category (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50) NOT NULL COMMENT '分类名称',
    parent_id   BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类，0 为顶级',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '排序号',
    status      TINYINT     NOT NULL DEFAULT 1,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_category_parent (parent_id)
) ENGINE = InnoDB COMMENT '商品分类表';

-- ---------------------------------------------------------------------
-- 3. 供应商
-- ---------------------------------------------------------------------
CREATE TABLE supplier (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(32)  NOT NULL COMMENT '供应商编号',
    name        VARCHAR(100) NOT NULL COMMENT '供应商名称',
    contact     VARCHAR(50)           DEFAULT NULL COMMENT '联系人',
    phone       VARCHAR(20)           DEFAULT NULL COMMENT '联系电话',
    address     VARCHAR(255)          DEFAULT NULL COMMENT '地址',
    remark      VARCHAR(255)          DEFAULT NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_code (code)
) ENGINE = InnoDB COMMENT '供应商表';

-- ---------------------------------------------------------------------
-- 4. 商品
-- ---------------------------------------------------------------------
CREATE TABLE product (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)   NOT NULL COMMENT '商品编码',
    barcode         VARCHAR(64)            DEFAULT NULL COMMENT '条形码',
    name            VARCHAR(100)  NOT NULL COMMENT '商品名称',
    category_id     BIGINT        NOT NULL COMMENT '分类 id',
    spec            VARCHAR(64)            DEFAULT NULL COMMENT '规格',
    unit            VARCHAR(16)   NOT NULL DEFAULT '个' COMMENT '单位',
    purchase_price  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '参考进价',
    sale_price      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '零售价',
    stock_upper     INT           NOT NULL DEFAULT 0 COMMENT '库存上限，0=不限',
    stock_lower     INT           NOT NULL DEFAULT 0 COMMENT '库存下限，触发低库存预警',
    shelf_life_days INT           NOT NULL DEFAULT 0 COMMENT '保质期天数，0=不管理',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1在售 0停售',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_code (code),
    KEY idx_product_category (category_id),
    KEY idx_product_name (name)
) ENGINE = InnoDB COMMENT '商品表';

-- ---------------------------------------------------------------------
-- 5. 库存汇总（扣减一致性核心表）
--    version 字段供 MyBatis-Plus @Version 乐观锁使用，
--    所有扣减都必须带上 version 条件，避免并发超卖。
-- ---------------------------------------------------------------------
CREATE TABLE stock (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    product_id      BIGINT        NOT NULL COMMENT '商品 id',
    quantity        INT           NOT NULL DEFAULT 0 COMMENT '当前可用库存',
    locked_quantity INT           NOT NULL DEFAULT 0 COMMENT '锁定库存（已下单未出库）',
    avg_cost        DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '移动加权平均成本',
    version         INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_product (product_id)
) ENGINE = InnoDB COMMENT '库存汇总表';

-- ---------------------------------------------------------------------
-- 6. 库存批次（保质期管理）
-- ---------------------------------------------------------------------
CREATE TABLE stock_batch (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    product_id        BIGINT        NOT NULL,
    batch_no          VARCHAR(64)   NOT NULL COMMENT '批次号',
    purchase_order_id BIGINT                 DEFAULT NULL COMMENT '来源采购单 id',
    production_date   DATE                   DEFAULT NULL COMMENT '生产日期',
    expire_date       DATE                   DEFAULT NULL COMMENT '到期日期',
    init_quantity     INT           NOT NULL DEFAULT 0 COMMENT '入库数量',
    out_quantity      INT           NOT NULL DEFAULT 0 COMMENT '已出库数量',
    stock_quantity    INT           NOT NULL DEFAULT 0 COMMENT '批次剩余数量',
    cost_price        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '批次进价',
    status            TINYINT       NOT NULL DEFAULT 1 COMMENT '1有效 0已耗尽',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_batch_product (product_id, status),
    KEY idx_batch_expire (expire_date),
    KEY idx_batch_no (batch_no)
) ENGINE = InnoDB COMMENT '库存批次表';

-- ---------------------------------------------------------------------
-- 7. 库存流水（差异追溯的唯一凭据）
--    change_quantity 带符号：入库为正、出库为负
--    before_quantity / after_quantity 记录变动前后值，便于对账
-- ---------------------------------------------------------------------
CREATE TABLE stock_record (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    product_id      BIGINT        NOT NULL,
    batch_id        BIGINT                 DEFAULT NULL,
    biz_type        VARCHAR(32)   NOT NULL COMMENT '业务类型，见 BizTypeEnum',
    change_quantity INT           NOT NULL COMMENT '变动数量（带符号）',
    before_quantity INT           NOT NULL COMMENT '变动前库存',
    after_quantity  INT           NOT NULL COMMENT '变动后库存',
    unit_cost       DECIMAL(12,4) NOT NULL DEFAULT 0.0000,
    biz_no          VARCHAR(40)            DEFAULT NULL COMMENT '来源单号',
    biz_id          BIGINT                 DEFAULT NULL COMMENT '来源单据 id',
    operator_id     BIGINT                 DEFAULT NULL,
    operator_name   VARCHAR(50)            DEFAULT NULL,
    remark          VARCHAR(255)           DEFAULT NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_record_product (product_id, create_time),
    KEY idx_record_biz (biz_type, biz_no),
    KEY idx_record_time (create_time)
) ENGINE = InnoDB COMMENT '库存流水表';

-- ---------------------------------------------------------------------
-- 8. 采购单
-- ---------------------------------------------------------------------
CREATE TABLE purchase_order (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    order_no       VARCHAR(40)   NOT NULL COMMENT '采购单号',
    supplier_id    BIGINT        NOT NULL,
    supplier_name  VARCHAR(100)           DEFAULT NULL,
    total_quantity INT           NOT NULL DEFAULT 0,
    total_amount   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/FINISHED/CANCELED',
    order_date     DATE          NOT NULL COMMENT '采购日期',
    receive_time   DATETIME               DEFAULT NULL COMMENT '实际入库时间',
    operator_id    BIGINT                 DEFAULT NULL,
    operator_name  VARCHAR(50)            DEFAULT NULL,
    remark         VARCHAR(255)           DEFAULT NULL,
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_no (order_no),
    KEY idx_purchase_supplier (supplier_id),
    KEY idx_purchase_date (order_date),
    KEY idx_purchase_status (status)
) ENGINE = InnoDB COMMENT '采购单表';

-- ---------------------------------------------------------------------
-- 9. 采购明细
-- ---------------------------------------------------------------------
CREATE TABLE purchase_order_item (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_id        BIGINT        NOT NULL,
    product_id      BIGINT        NOT NULL,
    product_name    VARCHAR(100)  NOT NULL,
    quantity        INT           NOT NULL COMMENT '采购数量',
    price           DECIMAL(12,2) NOT NULL COMMENT '采购单价',
    amount          DECIMAL(12,2) NOT NULL COMMENT '金额',
    batch_no        VARCHAR(64)            DEFAULT NULL COMMENT '入库批次号',
    production_date DATE                   DEFAULT NULL,
    expire_date     DATE                   DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_poi_order (order_id),
    KEY idx_poi_product (product_id)
) ENGINE = InnoDB COMMENT '采购单明细表';

-- ---------------------------------------------------------------------
-- 10. 销售单
-- ---------------------------------------------------------------------
CREATE TABLE sale_order (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_no        VARCHAR(40)   NOT NULL COMMENT '销售单号',
    customer_name   VARCHAR(50)   NOT NULL DEFAULT '散客',
    total_quantity  INT           NOT NULL DEFAULT 0,
    total_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '应收金额',
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    pay_amount      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '实收金额',
    total_cost      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '出库成本合计',
    gross_profit    DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '毛利 = 实收 - 成本',
    status          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/FINISHED/CANCELED',
    order_date      DATE          NOT NULL,
    operator_id     BIGINT                 DEFAULT NULL,
    operator_name   VARCHAR(50)            DEFAULT NULL,
    remark          VARCHAR(255)           DEFAULT NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sale_no (order_no),
    KEY idx_sale_date (order_date),
    KEY idx_sale_status (status)
) ENGINE = InnoDB COMMENT '销售单表';

-- ---------------------------------------------------------------------
-- 11. 销售明细
-- ---------------------------------------------------------------------
CREATE TABLE sale_order_item (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_id     BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    product_name VARCHAR(100)  NOT NULL,
    batch_id     BIGINT                 DEFAULT NULL COMMENT '实际出库批次',
    batch_no     VARCHAR(64)            DEFAULT NULL,
    quantity     INT           NOT NULL,
    price        DECIMAL(12,2) NOT NULL COMMENT '销售单价',
    amount       DECIMAL(12,2) NOT NULL,
    cost_price   DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '该批次成本单价',
    cost_amount  DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    PRIMARY KEY (id),
    KEY idx_soi_order (order_id),
    KEY idx_soi_product (product_id)
) ENGINE = InnoDB COMMENT '销售单明细表';

-- ---------------------------------------------------------------------
-- 12. 盘点单
-- ---------------------------------------------------------------------
CREATE TABLE stock_check (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    check_no            VARCHAR(40) NOT NULL COMMENT '盘点单号',
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/FINISHED/CANCELED',
    check_date          DATE        NOT NULL,
    total_diff_quantity INT         NOT NULL DEFAULT 0 COMMENT '差异总量（绝对值之和）',
    diff_item_count     INT         NOT NULL DEFAULT 0 COMMENT '有差异的商品数',
    operator_id         BIGINT               DEFAULT NULL,
    operator_name       VARCHAR(50)          DEFAULT NULL,
    finish_time         DATETIME             DEFAULT NULL,
    remark              VARCHAR(255)         DEFAULT NULL,
    create_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_check_no (check_no),
    KEY idx_check_date (check_date)
) ENGINE = InnoDB COMMENT '盘点单表';

-- ---------------------------------------------------------------------
-- 13. 盘点明细（差异追溯）
-- ---------------------------------------------------------------------
CREATE TABLE stock_check_item (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    check_id        BIGINT      NOT NULL,
    product_id      BIGINT      NOT NULL,
    product_name    VARCHAR(100) NOT NULL,
    batch_id        BIGINT               DEFAULT NULL,
    batch_no        VARCHAR(64)          DEFAULT NULL,
    book_quantity   INT         NOT NULL DEFAULT 0 COMMENT '账面数量',
    actual_quantity INT         NOT NULL DEFAULT 0 COMMENT '实盘数量',
    diff_quantity   INT         NOT NULL DEFAULT 0 COMMENT '差异 = 实盘 - 账面',
    reason          VARCHAR(255)         DEFAULT NULL COMMENT '差异原因说明',
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sci_check (check_id),
    KEY idx_sci_product (product_id)
) ENGINE = InnoDB COMMENT '盘点明细表';

-- ---------------------------------------------------------------------
-- 14. 退货单（采购退货 / 销售退货）
-- ---------------------------------------------------------------------
CREATE TABLE return_order (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_no        VARCHAR(40)   NOT NULL,
    return_type     VARCHAR(20)   NOT NULL COMMENT 'PURCHASE_RETURN 采购退货 / SALE_RETURN 销售退货',
    source_order_id BIGINT                 DEFAULT NULL COMMENT '原单 id',
    source_order_no VARCHAR(40)            DEFAULT NULL COMMENT '原单号',
    partner_id      BIGINT                 DEFAULT NULL,
    partner_name    VARCHAR(100)           DEFAULT NULL,
    total_quantity  INT           NOT NULL DEFAULT 0,
    total_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    status          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/FINISHED/CANCELED',
    return_date     DATE          NOT NULL,
    operator_id     BIGINT                 DEFAULT NULL,
    operator_name   VARCHAR(50)            DEFAULT NULL,
    reason          VARCHAR(255)           DEFAULT NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_return_no (order_no),
    KEY idx_return_date (return_date),
    KEY idx_return_type (return_type)
) ENGINE = InnoDB COMMENT '退货单表';

-- ---------------------------------------------------------------------
-- 15. 退货明细
-- ---------------------------------------------------------------------
CREATE TABLE return_order_item (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    return_id    BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    product_name VARCHAR(100)  NOT NULL,
    batch_id     BIGINT                 DEFAULT NULL,
    batch_no     VARCHAR(64)            DEFAULT NULL,
    quantity     INT           NOT NULL,
    price        DECIMAL(12,2) NOT NULL,
    amount       DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_roi_return (return_id)
) ENGINE = InnoDB COMMENT '退货单明细表';

-- ---------------------------------------------------------------------
-- 16. 库存预警记录
--    唯一键保证同一商品+批次+类型只产出一条未处理预警，避免刷屏
-- ---------------------------------------------------------------------
CREATE TABLE stock_alert (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    product_id      BIGINT      NOT NULL,
    product_name    VARCHAR(100) NOT NULL,
    batch_id        BIGINT               DEFAULT NULL,
    batch_no        VARCHAR(64)          DEFAULT NULL,
    alert_type      VARCHAR(24) NOT NULL COMMENT 'LOW_STOCK/OVER_STOCK/NEAR_EXPIRY/EXPIRED/OUT_OF_STOCK',
    alert_level     VARCHAR(10) NOT NULL DEFAULT 'WARN' COMMENT 'WARN/DANGER',
    current_value   INT         NOT NULL DEFAULT 0 COMMENT '当前值（库存量或剩余天数）',
    threshold_value INT         NOT NULL DEFAULT 0 COMMENT '阈值',
    expire_date     DATE                 DEFAULT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'UNHANDLED' COMMENT 'UNHANDLED/HANDLED/IGNORED',
    handle_remark   VARCHAR(255)         DEFAULT NULL,
    handle_time     DATETIME             DEFAULT NULL,
    handle_user     VARCHAR(50)          DEFAULT NULL,
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_alert_once (product_id, batch_id, alert_type, status),
    KEY idx_alert_status (status),
    KEY idx_alert_type (alert_type)
) ENGINE = InnoDB COMMENT '库存预警记录表';

-- ---------------------------------------------------------------------
-- 17. AI 建议与问答记录
-- ---------------------------------------------------------------------
CREATE TABLE ai_log (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    log_type          VARCHAR(20) NOT NULL COMMENT 'REPLENISH 补货建议 / CHAT 智能问答',
    product_id        BIGINT               DEFAULT NULL,
    title             VARCHAR(200)         DEFAULT NULL,
    question          TEXT COMMENT '用户提问 / 提示词',
    answer            TEXT COMMENT '模型回答',
    model             VARCHAR(64)          DEFAULT NULL,
    prompt_tokens     INT                  DEFAULT NULL,
    completion_tokens INT                  DEFAULT NULL,
    cost_ms           INT                  DEFAULT NULL COMMENT '耗时(毫秒)',
    operator_name     VARCHAR(50)          DEFAULT NULL,
    create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_type (log_type),
    KEY idx_ai_time (create_time)
) ENGINE = InnoDB COMMENT 'AI 记录表';

-- ---------------------------------------------------------------------
-- 18. 刷新令牌（服务端存储，支撑访问令牌短时效 + 无感刷新）
-- ---------------------------------------------------------------------
CREATE TABLE refresh_token (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    token_id    VARCHAR(64) NOT NULL COMMENT '令牌唯一标识',
    user_id     BIGINT      NOT NULL,
    username    VARCHAR(50) NOT NULL,
    expire_time DATETIME    NOT NULL,
    revoked     TINYINT     NOT NULL DEFAULT 0,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_token_id (token_id),
    KEY idx_rt_user (user_id)
) ENGINE = InnoDB COMMENT '刷新令牌表';
