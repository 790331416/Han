-- 租户计费表结构

-- 租户订阅记录
CREATE TABLE IF NOT EXISTS sys_tenant_subscription (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    package_id      BIGINT NOT NULL COMMENT '套餐ID',
    start_time      TIMESTAMP NOT NULL COMMENT '订阅开始时间',
    end_time        TIMESTAMP NOT NULL COMMENT '订阅到期时间',
    status          SMALLINT DEFAULT 0 COMMENT '状态（0正常 1已过期 2已取消）',
    amount          NUMERIC(10,2) DEFAULT 0 COMMENT '订阅金额',
    payment_method  VARCHAR(32) DEFAULT NULL COMMENT '支付方式',
    payment_no      VARCHAR(128) DEFAULT NULL COMMENT '支付单号',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT NULL
);

COMMENT ON TABLE sys_tenant_subscription IS '租户订阅记录';
CREATE INDEX IF NOT EXISTS idx_tenant_sub_tenant_id ON sys_tenant_subscription(tenant_id);

-- 租户账单
CREATE TABLE IF NOT EXISTS sys_tenant_bill (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    subscription_id BIGINT COMMENT '关联订阅ID',
    bill_type       VARCHAR(32) NOT NULL COMMENT '账单类型（subscribe/renew/upgrade）',
    amount          NUMERIC(10,2) NOT NULL COMMENT '金额',
    status          SMALLINT DEFAULT 0 COMMENT '状态（0待支付 1已支付 2已取消）',
    remark          VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    pay_time        TIMESTAMP DEFAULT NULL COMMENT '支付时间'
);

COMMENT ON TABLE sys_tenant_bill IS '租户账单';
CREATE INDEX IF NOT EXISTS idx_tenant_bill_tenant_id ON sys_tenant_bill(tenant_id);
