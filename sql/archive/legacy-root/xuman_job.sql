-- =============================================
-- Han Cloud 定时任务模块数据库脚本
-- 基于 Quartz 调度框架
-- 数据库：MySQL 8.0+
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 一、业务表（任务管理）
-- =============================================

-- ----------------------------
-- 1. 定时任务调度表
-- ----------------------------
DROP TABLE IF EXISTS sys_job;
CREATE TABLE sys_job (
    job_id          BIGINT          NOT NULL AUTO_INCREMENT     COMMENT '任务ID',
    tenant_id       BIGINT          DEFAULT NULL                COMMENT '租户ID(NULL表示系统级任务)',
    job_name        VARCHAR(100)    NOT NULL                    COMMENT '任务名称',
    job_group       VARCHAR(64)     DEFAULT 'DEFAULT'           COMMENT '任务组名',
    invoke_target   VARCHAR(500)    NOT NULL                    COMMENT '调用目标(beanName.methodName)',
    cron_expression VARCHAR(255)    NOT NULL                    COMMENT 'cron执行表达式',
    misfire_policy  CHAR(1)         DEFAULT '3'                 COMMENT '计划执行错误策略(1立即执行 2执行一次 3放弃执行)',
    concurrent      CHAR(1)         DEFAULT '1'                 COMMENT '是否并发执行(0允许 1禁止)',
    status          CHAR(1)         DEFAULT '0'                 COMMENT '状态(0正常 1暂停)',
    create_by       VARCHAR(64)     DEFAULT ''                  COMMENT '创建者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       VARCHAR(64)     DEFAULT ''                  COMMENT '更新者',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark          VARCHAR(500)    DEFAULT ''                  COMMENT '备注信息',
    PRIMARY KEY (job_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_job_group (job_group),
    INDEX idx_status (status)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务调度表';

-- ----------------------------
-- 2. 定时任务调度日志表
-- ----------------------------
DROP TABLE IF EXISTS sys_job_log;
CREATE TABLE sys_job_log (
    job_log_id      BIGINT          NOT NULL AUTO_INCREMENT     COMMENT '日志ID',
    job_name        VARCHAR(100)    NOT NULL                    COMMENT '任务名称',
    job_group       VARCHAR(64)     DEFAULT NULL                COMMENT '任务组名',
    invoke_target   VARCHAR(500)    NOT NULL                    COMMENT '调用目标',
    job_message     VARCHAR(500)    DEFAULT NULL                COMMENT '日志信息',
    status          CHAR(1)         DEFAULT '0'                 COMMENT '执行状态(0成功 1失败)',
    exception_info  VARCHAR(2000)   DEFAULT NULL                COMMENT '异常信息',
    start_time      DATETIME        DEFAULT NULL                COMMENT '开始时间',
    stop_time       DATETIME        DEFAULT NULL                COMMENT '结束时间',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    PRIMARY KEY (job_log_id),
    INDEX idx_job_name (job_name),
    INDEX idx_create_time (create_time),
    INDEX idx_status (status)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务调度日志表';

-- ----------------------------
-- 初始化示例任务
-- ----------------------------
INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, remark) VALUES
(1, '示例任务-无参', 'DEFAULT', 'sampleTask.execute', '0 0/5 * * * ?', '3', '1', '1', '每5分钟执行一次的示例任务'),
(2, '示例任务-带参', 'DEFAULT', 'sampleTask.executeWithParam(test)', '0 0/10 * * * ?', '3', '1', '1', '每10分钟执行一次的带参数示例任务'),
(3, '数据同步任务', 'SYSTEM', 'sampleTask.syncData', '0 0 2 * * ?', '3', '1', '1', '每天凌晨2点执行数据同步'),
(4, '数据清理任务', 'SYSTEM', 'sampleTask.cleanData', '0 0 3 * * ?', '3', '1', '1', '每天凌晨3点执行数据清理');

-- =============================================
-- 二、Quartz 官方表结构（集群模式必需）
-- =============================================

-- 存储每一个已配置的Job的详细信息
DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
CREATE TABLE QRTZ_JOB_DETAILS (
    SCHED_NAME        VARCHAR(120)    NOT NULL,
    JOB_NAME          VARCHAR(200)    NOT NULL,
    JOB_GROUP         VARCHAR(200)    NOT NULL,
    DESCRIPTION       VARCHAR(250)    NULL,
    JOB_CLASS_NAME    VARCHAR(250)    NOT NULL,
    IS_DURABLE        VARCHAR(1)      NOT NULL,
    IS_NONCONCURRENT  VARCHAR(1)      NOT NULL,
    IS_UPDATE_DATA    VARCHAR(1)      NOT NULL,
    REQUESTS_RECOVERY VARCHAR(1)      NOT NULL,
    JOB_DATA          BLOB            NULL,
    PRIMARY KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Quartz任务详细信息表';

-- 存储已配置的Trigger的信息
DROP TABLE IF EXISTS QRTZ_TRIGGERS;
CREATE TABLE QRTZ_TRIGGERS (
    SCHED_NAME     VARCHAR(120)    NOT NULL,
    TRIGGER_NAME   VARCHAR(200)    NOT NULL,
    TRIGGER_GROUP  VARCHAR(200)    NOT NULL,
    JOB_NAME       VARCHAR(200)    NOT NULL,
    JOB_GROUP      VARCHAR(200)    NOT NULL,
    DESCRIPTION    VARCHAR(250)    NULL,
    NEXT_FIRE_TIME BIGINT          NULL,
    PREV_FIRE_TIME BIGINT          NULL,
    PRIORITY       INT             NULL,
    TRIGGER_STATE  VARCHAR(16)     NOT NULL,
    TRIGGER_TYPE   VARCHAR(8)      NOT NULL,
    START_TIME     BIGINT          NOT NULL,
    END_TIME       BIGINT          NULL,
    CALENDAR_NAME  VARCHAR(200)    NULL,
    MISFIRE_INSTR  SMALLINT        NULL,
    JOB_DATA       BLOB            NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, JOB_NAME, JOB_GROUP) 
        REFERENCES QRTZ_JOB_DETAILS(SCHED_NAME, JOB_NAME, JOB_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Quartz触发器信息表';

-- 存储简单的Trigger，包括重复次数，间隔，以及已触发的次数
DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
CREATE TABLE QRTZ_SIMPLE_TRIGGERS (
    SCHED_NAME      VARCHAR(120)    NOT NULL,
    TRIGGER_NAME    VARCHAR(200)    NOT NULL,
    TRIGGER_GROUP   VARCHAR(200)    NOT NULL,
    REPEAT_COUNT    BIGINT          NOT NULL,
    REPEAT_INTERVAL BIGINT          NOT NULL,
    TIMES_TRIGGERED BIGINT          NOT NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) 
        REFERENCES QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Quartz简单触发器表';

-- 存储Cron Trigger，包括Cron表达式和时区信息
DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
CREATE TABLE QRTZ_CRON_TRIGGERS (
    SCHED_NAME      VARCHAR(120)    NOT NULL,
    TRIGGER_NAME    VARCHAR(200)    NOT NULL,
    TRIGGER_GROUP   VARCHAR(200)    NOT NULL,
    CRON_EXPRESSION VARCHAR(120)    NOT NULL,
    TIME_ZONE_ID    VARCHAR(80),
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) 
        REFERENCES QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='QuartzCron触发器表';

-- 存储Blob类型的Trigger
DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
CREATE TABLE QRTZ_BLOB_TRIGGERS (
    SCHED_NAME    VARCHAR(120)    NOT NULL,
    TRIGGER_NAME  VARCHAR(200)    NOT NULL,
    TRIGGER_GROUP VARCHAR(200)    NOT NULL,
    BLOB_DATA     BLOB            NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) 
        REFERENCES QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='QuartzBlob触发器表';

-- 以Blob类型存储Quartz的Calendar日历信息
DROP TABLE IF EXISTS QRTZ_CALENDARS;
CREATE TABLE QRTZ_CALENDARS (
    SCHED_NAME    VARCHAR(120)    NOT NULL,
    CALENDAR_NAME VARCHAR(200)    NOT NULL,
    CALENDAR      BLOB            NOT NULL,
    PRIMARY KEY (SCHED_NAME, CALENDAR_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Quartz日历信息表';

-- 存储已暂停的Trigger组的信息
DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
CREATE TABLE QRTZ_PAUSED_TRIGGER_GRPS (
    SCHED_NAME    VARCHAR(120)    NOT NULL,
    TRIGGER_GROUP VARCHAR(200)    NOT NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Quartz暂停触发器组表';

-- 存储与已触发的Trigger相关的状态信息，以及相联Job的执行信息
DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
CREATE TABLE QRTZ_FIRED_TRIGGERS (
    SCHED_NAME        VARCHAR(120)    NOT NULL,
    ENTRY_ID          VARCHAR(95)     NOT NULL,
    TRIGGER_NAME      VARCHAR(200)    NOT NULL,
    TRIGGER_GROUP     VARCHAR(200)    NOT NULL,
    INSTANCE_NAME     VARCHAR(200)    NOT NULL,
    FIRED_TIME        BIGINT          NOT NULL,
    SCHED_TIME        BIGINT          NOT NULL,
    PRIORITY          INT             NOT NULL,
    STATE             VARCHAR(16)     NOT NULL,
    JOB_NAME          VARCHAR(200)    NULL,
    JOB_GROUP         VARCHAR(200)    NULL,
    IS_NONCONCURRENT  VARCHAR(1)      NULL,
    REQUESTS_RECOVERY VARCHAR(1)      NULL,
    PRIMARY KEY (SCHED_NAME, ENTRY_ID),
    INDEX IDX_QRTZ_FT_TRIG_INST_NAME (SCHED_NAME, INSTANCE_NAME),
    INDEX IDX_QRTZ_FT_INST_JOB_REQ_RCVRY (SCHED_NAME, INSTANCE_NAME, REQUESTS_RECOVERY),
    INDEX IDX_QRTZ_FT_J_G (SCHED_NAME, JOB_NAME, JOB_GROUP),
    INDEX IDX_QRTZ_FT_JG (SCHED_NAME, JOB_GROUP),
    INDEX IDX_QRTZ_FT_T_G (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    INDEX IDX_QRTZ_FT_TG (SCHED_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Quartz已触发触发器表';

-- 存储少量的有关Scheduler的状态信息，以及别的Scheduler实例
DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
CREATE TABLE QRTZ_SCHEDULER_STATE (
    SCHED_NAME        VARCHAR(120)    NOT NULL,
    INSTANCE_NAME     VARCHAR(200)    NOT NULL,
    LAST_CHECKIN_TIME BIGINT          NOT NULL,
    CHECKIN_INTERVAL  BIGINT          NOT NULL,
    PRIMARY KEY (SCHED_NAME, INSTANCE_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Quartz调度器状态表';

-- 存储程序的悲观锁的信息
DROP TABLE IF EXISTS QRTZ_LOCKS;
CREATE TABLE QRTZ_LOCKS (
    SCHED_NAME VARCHAR(120)    NOT NULL,
    LOCK_NAME  VARCHAR(40)     NOT NULL,
    PRIMARY KEY (SCHED_NAME, LOCK_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Quartz锁表';

-- 存储SimplePropertyJob的SimpleTrigger信息
DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
CREATE TABLE QRTZ_SIMPROP_TRIGGERS (
    SCHED_NAME    VARCHAR(120)   NOT NULL,
    TRIGGER_NAME  VARCHAR(200)   NOT NULL,
    TRIGGER_GROUP VARCHAR(200)   NOT NULL,
    STR_PROP_1    VARCHAR(512)   NULL,
    STR_PROP_2    VARCHAR(512)   NULL,
    STR_PROP_3    VARCHAR(512)   NULL,
    INT_PROP_1    INT            NULL,
    INT_PROP_2    INT            NULL,
    LONG_PROP_1   BIGINT         NULL,
    LONG_PROP_2   BIGINT         NULL,
    DEC_PROP_1    NUMERIC(13,4)  NULL,
    DEC_PROP_2    NUMERIC(13,4)  NULL,
    BOOL_PROP_1   VARCHAR(1)     NULL,
    BOOL_PROP_2   VARCHAR(1)     NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) 
        REFERENCES QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='QuartzSimpleProp触发器表';

SET FOREIGN_KEY_CHECKS = 1;
