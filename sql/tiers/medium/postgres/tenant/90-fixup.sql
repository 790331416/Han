-- PostgreSQL tenant billing extension tables.

CREATE TABLE IF NOT EXISTS sys_tenant_subscription (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    status SMALLINT DEFAULT 0,
    amount NUMERIC(10,2) DEFAULT 0,
    payment_method VARCHAR(32) DEFAULT NULL,
    payment_no VARCHAR(128) DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT NULL
);

COMMENT ON TABLE sys_tenant_subscription IS 'Tenant subscription record';
COMMENT ON COLUMN sys_tenant_subscription.tenant_id IS 'Tenant ID';
COMMENT ON COLUMN sys_tenant_subscription.package_id IS 'Package ID';
COMMENT ON COLUMN sys_tenant_subscription.start_time IS 'Subscription start time';
COMMENT ON COLUMN sys_tenant_subscription.end_time IS 'Subscription end time';
COMMENT ON COLUMN sys_tenant_subscription.status IS '0 active, 1 expired, 2 canceled';
COMMENT ON COLUMN sys_tenant_subscription.amount IS 'Subscription amount';
COMMENT ON COLUMN sys_tenant_subscription.payment_method IS 'Payment method';
COMMENT ON COLUMN sys_tenant_subscription.payment_no IS 'Payment order number';
CREATE INDEX IF NOT EXISTS idx_tenant_sub_tenant_id ON sys_tenant_subscription (tenant_id);

CREATE TABLE IF NOT EXISTS sys_tenant_bill (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    subscription_id BIGINT DEFAULT NULL,
    bill_type VARCHAR(32) NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    status SMALLINT DEFAULT 0,
    remark VARCHAR(500) DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    pay_time TIMESTAMP DEFAULT NULL
);

COMMENT ON TABLE sys_tenant_bill IS 'Tenant billing record';
COMMENT ON COLUMN sys_tenant_bill.tenant_id IS 'Tenant ID';
COMMENT ON COLUMN sys_tenant_bill.subscription_id IS 'Related subscription ID';
COMMENT ON COLUMN sys_tenant_bill.bill_type IS 'subscribe, renew, upgrade';
COMMENT ON COLUMN sys_tenant_bill.amount IS 'Bill amount';
COMMENT ON COLUMN sys_tenant_bill.status IS '0 pending, 1 paid, 2 canceled';
COMMENT ON COLUMN sys_tenant_bill.remark IS 'Billing note';
COMMENT ON COLUMN sys_tenant_bill.pay_time IS 'Payment time';
CREATE INDEX IF NOT EXISTS idx_tenant_bill_tenant_id ON sys_tenant_bill (tenant_id);
