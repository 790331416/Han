package com.han.system.sdfz.order.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 集成测试的建数据与查数据助手。直接走 JDBC，绕开被测服务，避免用被测代码验证被测代码。
 */
public class OrderTestFixtures {

    public static final long TENANT = 1L;
    public static final long LISTEN_SCHOOL = 1001L;
    public static final long LECTURE_SCHOOL = 1002L;
    public static final long LISTEN_CLASS = 2001L;
    public static final long LISTEN_CLASS_B = 2003L;
    public static final long LECTURE_CLASS = 2002L;
    public static final long ROOM = 3001L;
    public static final long DEVICE = 4001L;
    public static final long SUBJECT_CHINESE = 5001L;
    public static final long SUBJECT_MATH = 5002L;

    private static final DateTimeFormatter LEGACY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate han;
    private final JdbcTemplate legacy;

    public OrderTestFixtures(JdbcTemplate han, JdbcTemplate legacy) {
        this.han = han;
        this.legacy = legacy;
    }

    public void reset() {
        for (String table : List.of("edu_course_order_grant", "edu_course_order_subject", "edu_course_order",
                "edu_device", "edu_room", "edu_subject", "edu_semester", "edu_class", "edu_school")) {
            han.update("DELETE FROM " + table);
        }
        legacy.update("DELETE FROM tb_course_attend");
        legacy.update("DELETE FROM tb_course_info");
    }

    /**
     * 一所听讲校 + 一所主讲校，各一个班，外加教室、设备、两门科目。
     */
    public void seedMasterData() {
        han.update("INSERT INTO edu_school (id, tenant_id, school_code, school_name, school_role, area_code, status)"
                + " VALUES (?, ?, 'S-LISTEN', '听讲学校', 'ATTEND', '500103', 0)", LISTEN_SCHOOL, TENANT);
        han.update("INSERT INTO edu_school (id, tenant_id, school_code, school_name, school_role, area_code, status)"
                + " VALUES (?, ?, 'S-MAIN', '主讲学校', 'MAIN', '500103', 0)", LECTURE_SCHOOL, TENANT);
        han.update("INSERT INTO edu_class (id, tenant_id, school_id, class_code, class_name, class_role, status)"
                + " VALUES (?, ?, ?, 'C-LISTEN', '听讲一班', 'ATTEND', 0)", LISTEN_CLASS, TENANT, LISTEN_SCHOOL);
        han.update("INSERT INTO edu_class (id, tenant_id, school_id, class_code, class_name, class_role, status)"
                + " VALUES (?, ?, ?, 'C-LISTEN-B', '听讲二班', 'ATTEND', 0)", LISTEN_CLASS_B, TENANT, LISTEN_SCHOOL);
        han.update("INSERT INTO edu_class (id, tenant_id, school_id, class_code, class_name, class_role, status)"
                + " VALUES (?, ?, ?, 'C-MAIN', '主讲一班', 'MAIN', 0)", LECTURE_CLASS, TENANT, LECTURE_SCHOOL);
        han.update("INSERT INTO edu_room (id, tenant_id, school_id, room_code, room_name, room_type, status)"
                + " VALUES (?, ?, ?, 'R-01', '录播教室', 'RECORD', 0)", ROOM, TENANT, LISTEN_SCHOOL);
        han.update("INSERT INTO edu_device (id, tenant_id, school_id, room_id, device_code, device_name,"
                        + " device_type, asset_status, status)"
                        + " VALUES (?, ?, ?, ?, 'DEV-01', '听讲端一体机', 'TERMINAL', 'IN_SERVICE', 0)",
                DEVICE, TENANT, LISTEN_SCHOOL, ROOM);
        han.update("INSERT INTO edu_subject (id, tenant_id, subject_code, subject_name, sort, status)"
                + " VALUES (?, ?, 'YW', '语文', 1, 0)", SUBJECT_CHINESE, TENANT);
        han.update("INSERT INTO edu_subject (id, tenant_id, subject_code, subject_name, sort, status)"
                + " VALUES (?, ?, 'SX', '数学', 2, 0)", SUBJECT_MATH, TENANT);
    }

    public long seedSemester(long id, String code, LocalDate begin, LocalDate end, String lifecycle) {
        han.update("INSERT INTO edu_semester (id, tenant_id, semester_code, semester_name, begin_date, end_date,"
                        + " current_flag, lifecycle_status, status) VALUES (?, ?, ?, ?, ?, ?, 0, ?, 0)",
                id, TENANT, code, code, begin, end, lifecycle);
        return id;
    }

    /**
     * 往三课堂库里放一节课。
     */
    public void seedCourse(String courseId, String name, long lectureClassId,
                           String subjectCode, LocalDateTime begin) {
        legacy.update("INSERT INTO tb_course_info (course_id, course_name, course_type, organ_id, organ_name,"
                        + " class_id, class_name, subject_code, subject_name, time_begin, time_end, status)"
                        + " VALUES (?, ?, '1', ?, '主讲学校', ?, '主讲一班', ?, ?, ?, ?, '0')",
                courseId, name, String.valueOf(LECTURE_SCHOOL), String.valueOf(lectureClassId),
                subjectCode, subjectCode, begin.format(LEGACY_TIME), begin.plusMinutes(45).format(LEGACY_TIME));
    }

    public void deleteCourse(String courseId) {
        legacy.update("UPDATE tb_course_info SET status = '1' WHERE course_id = ?", courseId);
    }

    public int countAttend(String courseId, long classId) {
        Integer count = legacy.queryForObject(
                "SELECT COUNT(*) FROM tb_course_attend WHERE fk_course_id = ? AND class_id = ?",
                Integer.class, courseId, String.valueOf(classId));
        return count == null ? 0 : count;
    }

    public int countActiveAttend(String courseId, long classId) {
        Integer count = legacy.queryForObject(
                "SELECT COUNT(*) FROM tb_course_attend WHERE fk_course_id = ? AND class_id = ? AND status = '0'",
                Integer.class, courseId, String.valueOf(classId));
        return count == null ? 0 : count;
    }

    public Map<String, Object> attendRow(String courseId, long classId) {
        List<Map<String, Object>> rows = legacy.queryForList(
                "SELECT * FROM tb_course_attend WHERE fk_course_id = ? AND class_id = ?",
                courseId, String.valueOf(classId));
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    /** 模拟教师在旧前端改课：旧 api 的 updateCourseInfo 会先 deleteByCourseId 硬删整门课的听课行。 */
    public void simulateLegacyCourseEdit(String courseId) {
        legacy.update("DELETE FROM tb_course_attend WHERE fk_course_id = ?", courseId);
    }

    public int countOrders() {
        Integer count = han.queryForObject("SELECT COUNT(*) FROM edu_course_order", Integer.class);
        return count == null ? 0 : count;
    }

    public int countOrderSubjects() {
        Integer count = han.queryForObject("SELECT COUNT(*) FROM edu_course_order_subject", Integer.class);
        return count == null ? 0 : count;
    }

    public int countGrants(String grantStatus) {
        Integer count = han.queryForObject(
                "SELECT COUNT(*) FROM edu_course_order_grant WHERE grant_status = ?", Integer.class, grantStatus);
        return count == null ? 0 : count;
    }

    public int countAllGrants() {
        Integer count = han.queryForObject("SELECT COUNT(*) FROM edu_course_order_grant", Integer.class);
        return count == null ? 0 : count;
    }

    public JdbcTemplate han() {
        return han;
    }

    public JdbcTemplate legacy() {
        return legacy;
    }
}
