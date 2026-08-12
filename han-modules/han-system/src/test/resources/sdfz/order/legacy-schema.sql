-- 三课堂旧库的最小结构（H2），列名与列型对齐 three-classroom-service 的 POJO 与 Mapper。
--
-- 两个刻意保留的旧库特征，测试就是要打在这上面：
--   1. tb_course_attend 没有任何唯一索引，幂等只能由写入方保证；
--   2. status 是字符串且语义反转，'0' 正常 / '1' 删除。

CREATE TABLE tb_course_info (
    course_id VARCHAR(64) NOT NULL PRIMARY KEY,
    course_name VARCHAR(200),
    course_type VARCHAR(8),
    province_code VARCHAR(32), province_name VARCHAR(64),
    city_code VARCHAR(32), city_name VARCHAR(64),
    county_code VARCHAR(32), county_name VARCHAR(64),
    organ_id VARCHAR(64), organ_name VARCHAR(128),
    class_id VARCHAR(64), class_name VARCHAR(128),
    place_id VARCHAR(64), place_name VARCHAR(128),
    room_id VARCHAR(64),
    teacher_id VARCHAR(64),
    subject_code VARCHAR(64), subject_name VARCHAR(64),
    time_begin DATETIME,
    time_end DATETIME,
    status VARCHAR(8) NOT NULL DEFAULT '0'
);

CREATE TABLE tb_course_attend (
    attend_id VARCHAR(64) NOT NULL PRIMARY KEY,
    fk_course_id VARCHAR(64),
    course_type VARCHAR(8),
    province_code VARCHAR(32), province_name VARCHAR(64),
    city_code VARCHAR(32), city_name VARCHAR(64),
    county_code VARCHAR(32), county_name VARCHAR(64),
    organ_id VARCHAR(64), organ_name VARCHAR(128),
    class_id VARCHAR(64), class_name VARCHAR(128),
    place_id VARCHAR(64), place_name VARCHAR(128),
    room_id VARCHAR(64), room_name VARCHAR(128),
    member_id VARCHAR(64), member_name VARCHAR(128),
    status VARCHAR(8),
    create_id VARCHAR(64), create_name VARCHAR(128), create_time VARCHAR(32),
    create_unit_id VARCHAR(64), create_unit_name VARCHAR(128),
    update_id VARCHAR(64), update_name VARCHAR(128), update_time VARCHAR(32),
    update_unit_id VARCHAR(64), update_unit_name VARCHAR(128)
);
