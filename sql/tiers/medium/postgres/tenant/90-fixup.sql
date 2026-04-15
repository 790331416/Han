-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

-- 绉熸埛璁¤垂琛ㄧ粨鏋?
-- 绉熸埛璁㈤槄璁板綍
CREATE TABLE IF NOT EXISTS sys_tenant_subscription (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '绉熸埛ID',
    package_id      BIGINT NOT NULL COMMENT '濂楅ID',
    start_time      TIMESTAMP NOT NULL COMMENT '璁㈤槄寮€濮嬫椂闂?,
    end_time        TIMESTAMP NOT NULL COMMENT '璁㈤槄鍒版湡鏃堕棿',
    status          SMALLINT DEFAULT 0 COMMENT '鐘舵€侊紙0姝ｅ父 1宸茶繃鏈?2宸插彇娑堬級',
    amount          NUMERIC(10,2) DEFAULT 0 COMMENT '璁㈤槄閲戦',
    payment_method  VARCHAR(32) DEFAULT NULL COMMENT '鏀粯鏂瑰紡',
    payment_no      VARCHAR(128) DEFAULT NULL COMMENT '鏀粯鍗曞彿',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT NULL
);

COMMENT ON TABLE sys_tenant_subscription IS '绉熸埛璁㈤槄璁板綍';
CREATE INDEX IF NOT EXISTS idx_tenant_sub_tenant_id ON sys_tenant_subscription(tenant_id);

-- 绉熸埛璐﹀崟
CREATE TABLE IF NOT EXISTS sys_tenant_bill (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '绉熸埛ID',
    subscription_id BIGINT COMMENT '鍏宠仈璁㈤槄ID',
    bill_type       VARCHAR(32) NOT NULL COMMENT '璐﹀崟绫诲瀷锛坰ubscribe/renew/upgrade锛?,
    amount          NUMERIC(10,2) NOT NULL COMMENT '閲戦',
    status          SMALLINT DEFAULT 0 COMMENT '鐘舵€侊紙0寰呮敮浠?1宸叉敮浠?2宸插彇娑堬級',
    remark          VARCHAR(500) DEFAULT NULL COMMENT '澶囨敞',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    pay_time        TIMESTAMP DEFAULT NULL COMMENT '鏀粯鏃堕棿'
);

COMMENT ON TABLE sys_tenant_bill IS '绉熸埛璐﹀崟';
CREATE INDEX IF NOT EXISTS idx_tenant_bill_tenant_id ON sys_tenant_bill(tenant_id);
