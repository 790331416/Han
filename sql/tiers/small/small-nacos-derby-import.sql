-- 95 small Nacos import
-- target: han-small-nacos
-- schema: nacos
-- group_id: DEFAULT_GROUP
-- tenant_id: ''

DELETE FROM nacos.his_config_info
WHERE data_id = 'application-shared.yml' AND group_id = 'DEFAULT_GROUP' AND tenant_id = '';

DELETE FROM nacos.config_info
WHERE data_id = 'application-shared.yml' AND group_id = 'DEFAULT_GROUP' AND tenant_id = '';

INSERT INTO nacos.config_info (
  data_id,
  group_id,
  tenant_id,
  app_name,
  content,
  md5,
  gmt_create,
  gmt_modified,
  src_user,
  src_ip,
  c_desc,
  c_use,
  effect,
  type,
  c_schema,
  encrypted_data_key
) VALUES (
  'application-shared.yml',
  'DEFAULT_GROUP',
  '',
  'shared',
  'spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:han@2026}
      database: 0
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms

logging:
  level:
    root: INFO
    com.han: INFO

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

han:
  deploy:
    tier: small
  security:
    inner-auth:
      enabled: ${HAN_INNER_AUTH_ENABLED:true}
      secret: ${HAN_INNER_AUTH_SECRET:han-cloud-inner-auth}
      clock-skew-seconds: ${HAN_INNER_AUTH_CLOCK_SKEW_SECONDS:300}
',
  NULL,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  'codex',
  '10.18.35.95',
  'shared config for 95 small tier',
  NULL,
  NULL,
  'yaml',
  NULL,
  NULL
);

DELETE FROM nacos.his_config_info
WHERE data_id = 'han-auth.yml' AND group_id = 'DEFAULT_GROUP' AND tenant_id = '';

DELETE FROM nacos.config_info
WHERE data_id = 'han-auth.yml' AND group_id = 'DEFAULT_GROUP' AND tenant_id = '';

INSERT INTO nacos.config_info (
  data_id, group_id, tenant_id, app_name, content, md5, gmt_create, gmt_modified, src_user, src_ip, c_desc, c_use, effect, type, c_schema, encrypted_data_key
) VALUES (
  'han-auth.yml',
  'DEFAULT_GROUP',
  '',
  'han-auth',
  'server:
  port: 9200
',
  NULL,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  'codex',
  '10.18.35.95',
  'auth runtime config',
  NULL,
  NULL,
  'yaml',
  NULL,
  NULL
);

DELETE FROM nacos.his_config_info
WHERE data_id = 'han-system.yml' AND group_id = 'DEFAULT_GROUP' AND tenant_id = '';

DELETE FROM nacos.config_info
WHERE data_id = 'han-system.yml' AND group_id = 'DEFAULT_GROUP' AND tenant_id = '';

INSERT INTO nacos.config_info (
  data_id, group_id, tenant_id, app_name, content, md5, gmt_create, gmt_modified, src_user, src_ip, c_desc, c_use, effect, type, c_schema, encrypted_data_key
) VALUES (
  'han-system.yml',
  'DEFAULT_GROUP',
  '',
  'han-system',
  'server:
  port: 9201

spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:han}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USER:han}
    password: ${DB_PASSWORD:han@2026}
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      idle-timeout: 300000
      connection-timeout: 30000
      max-lifetime: 1800000

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.han.system.domain.po
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
  global-config:
    db-config:
      id-type: assign_id
      logic-delete-field: delFlag
      logic-delete-value: 1
      logic-not-delete-value: 0
',
  NULL,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  'codex',
  '10.18.35.95',
  'system runtime config',
  NULL,
  NULL,
  'yaml',
  NULL,
  NULL
);

DELETE FROM nacos.his_config_info
WHERE data_id = 'han-job.yml' AND group_id = 'DEFAULT_GROUP' AND tenant_id = '';

DELETE FROM nacos.config_info
WHERE data_id = 'han-job.yml' AND group_id = 'DEFAULT_GROUP' AND tenant_id = '';

INSERT INTO nacos.config_info (
  data_id, group_id, tenant_id, app_name, content, md5, gmt_create, gmt_modified, src_user, src_ip, c_desc, c_use, effect, type, c_schema, encrypted_data_key
) VALUES (
  'han-job.yml',
  'DEFAULT_GROUP',
  '',
  'han-job',
  'server:
  port: 9204

spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:han}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USER:han}
    password: ${DB_PASSWORD:han@2026}
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      idle-timeout: 300000
      connection-timeout: 30000
      max-lifetime: 1800000

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.han.job.domain.po
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
  global-config:
    db-config:
      id-type: assign_id
      logic-delete-field: delFlag
      logic-delete-value: 1
      logic-not-delete-value: 0
',
  NULL,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  'codex',
  '10.18.35.95',
  'job runtime config',
  NULL,
  NULL,
  'yaml',
  NULL,
  NULL
);

DELETE FROM nacos.his_config_info
WHERE data_id = 'jobflow-scheduler.yml' AND group_id = 'DEFAULT_GROUP' AND tenant_id = '';

DELETE FROM nacos.config_info
WHERE data_id = 'jobflow-scheduler.yml' AND group_id = 'DEFAULT_GROUP' AND tenant_id = '';

INSERT INTO nacos.config_info (
  data_id, group_id, tenant_id, app_name, content, md5, gmt_create, gmt_modified, src_user, src_ip, c_desc, c_use, effect, type, c_schema, encrypted_data_key
) VALUES (
  'jobflow-scheduler.yml',
  'DEFAULT_GROUP',
  '',
  'han-job',
  'jobflow:
  scheduler:
    thread-pool-size: 20
    timeout: 300
    max-retry: 3
    connect-timeout: 5000
    read-timeout: 30000
    lock-timeout: 60
    compensation-enabled: true
    compensation-interval: 60000
    stuck-threshold: 600000
',
  NULL,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  'codex',
  '10.18.35.95',
  'jobflow scheduler config',
  NULL,
  NULL,
  'yaml',
  NULL,
  NULL
);
