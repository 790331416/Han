package com.han.system.sdfz.order.legacy;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 直连三课堂库的物化实现。
 *
 * <h2>为什么不是走旧 api 内部接口</h2>
 *
 * 《Han 与三课堂实体 ID 映射结论》§6 D1 建议走接口，理由是避免两个写入方。核查旧 api 之后，
 * 结论是<b>该接口目前不具备条件</b>：
 *
 * <ol>
 *   <li>{@code TbCourseAttendController} 是空类，没有任何端点。听课记录没有单条增删接口。</li>
 *   <li>唯一能改动听课记录的是 {@code /tb-course-info/updateCourseInfo}，语义是整门课全量覆盖：
 *       先 {@code updateById(tbCourseInfo)} 覆写课程本身，再 {@code deleteByCourseId} <b>物理删除</b>
 *       该课全部听课行，最后按请求体重插。Han 要用它加一行，必须先 {@code getCourseInfo} 读回整门课，
 *       在内存里追加再整体写回——这是对不属于自己的实体做读-改-写，两次调用之间教师在旧前端改了同一门课，
 *       改动会被静默覆盖，而且旧库没有版本列可做乐观锁。</li>
 *   <li>{@code TbCourseInfoServiceImpl} 全类没有 {@code @Transactional}。上面那串
 *       「更新 + 硬删 + N 次插入」不在事务里，中途失败会留下一门听课记录被删光且无法回滚的课。</li>
 *   <li>{@code updateCourseInfo} 第 587 行 {@code userInfo.getRealName()} 没有判空
 *       （同文件的 {@code saveCourseInfo} 反而判了），身份接口返回空就 NPE。</li>
 *   <li>{@code saveCourseInfo} 上挂着 {@code @OperatorLog(module = "开课操作", operate = "创建课堂")}，
 *       Han 每同步一次都会在旧库操作日志里伪造一条「教师创建了课堂」。</li>
 * </ol>
 *
 * 综合下来，用现有接口做物化，风险高于直连库。而「零 JAR 改动」是既定约束，本期不能给旧 api 加端点。
 *
 * <h2>替代方案与收敛路径</h2>
 *
 * 本类只碰 {@code tb_course_attend} 一张表，且只在「该课程当前没有属于这个听讲班的行」时插入、
 * 只把自己插入的行置为删除，<b>不动 {@code tb_course_info}</b>，因此不会与教师手工建课互相覆盖：
 * 教师改课时旧 api 的 {@code deleteByCourseId} 会连同 Han 写的行一起清掉，下一轮对账会把它补回来
 * （对账口径见《课程订购关系管理说明》§6.2 第 3 类差异）。
 *
 * 等旧 api 侧愿意加一个幂等的内部端点（契约见 {@link LegacyClassroomHttpGateway}），
 * 把 {@code sdfz.order.legacy.channel} 从 {@code jdbc} 改成 {@code http} 即可切换，不用改业务代码。
 *
 * <h2>幂等</h2>
 *
 * {@code tb_course_attend} 全表零二级索引、无唯一约束，幂等只能靠调用方。这里的做法是
 * 「先按 {@code (fk_course_id, class_id)} 查重，命中复用 {@code attend_id} 并恢复状态，未命中才插入」。
 */
@Slf4j
public class LegacyClassroomJdbcGateway implements LegacyClassroomGateway {

    private static final DateTimeFormatter LEGACY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 旧库 status 的语义是「0 正常 / 1 删除」，与 Han 的 del_flag 相反，写入时必须是字符串 "0"。 */
    private static final String LEGACY_NORMAL = "0";
    private static final String LEGACY_DELETED = "1";

    private static final String COURSE_COLUMNS =
            "course_id, course_name, course_type, class_id, class_name, organ_id, organ_name, "
                    + "subject_code, subject_name, time_begin, time_end";

    private final JdbcTemplate jdbcTemplate;
    private final LegacyClassroomProperties properties;

    public LegacyClassroomJdbcGateway(JdbcTemplate jdbcTemplate, LegacyClassroomProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public List<LegacyCourse> listCourses(String lectureClassId, LocalDateTime from, LocalDateTime to) {
        if (lectureClassId == null || lectureClassId.isBlank()) {
            return List.of();
        }
        String sql = "SELECT " + COURSE_COLUMNS + " FROM tb_course_info"
                + " WHERE status = ? AND class_id = ? AND time_begin >= ? AND time_begin <= ?"
                + " ORDER BY time_begin";
        return query(() -> jdbcTemplate.query(sql, courseMapper(),
                LEGACY_NORMAL, lectureClassId, format(from), format(to)));
    }

    @Override
    public LegacyCourse findCourse(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            return null;
        }
        String sql = "SELECT " + COURSE_COLUMNS + " FROM tb_course_info WHERE course_id = ? AND status = ?";
        List<LegacyCourse> rows = query(() -> jdbcTemplate.query(sql, courseMapper(), courseId, LEGACY_NORMAL));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public String materializeAttend(LegacyAttendRequest request) {
        String existing = findAttendId(request.courseId(), request.classId());
        if (existing != null) {
            // 已经有行了：可能是 Han 之前写的、教师建课时就把这个听讲班选上了、
            // 也可能是被冻结/撤销置成 status='1' 的旧行。一律复活并刷新快照字段，绝不新建。
            //
            // 这里查重<b>不带状态过滤</b>是有意的：旧系统「加入课堂」事件用
            // selectOne(fk_course_id, member_id) 反查听课行，那个查询不看 status，
            // 只要同一对 (课程, 听讲班) 留下两行——哪怕其中一行是已删除状态——就会抛
            // TooManyResultsException，听讲端直接进不去。所以必须保证每对最多一行。
            query(() -> jdbcTemplate.update(
                    "UPDATE tb_course_attend SET status = ?, course_type = ?,"
                            + " organ_id = ?, organ_name = ?, class_name = ?,"
                            + " place_id = ?, place_name = ?, member_id = ?, member_name = ?,"
                            + " update_id = ?, update_name = ?, update_time = ?"
                            + " WHERE attend_id = ?",
                    LEGACY_NORMAL, request.courseType(),
                    request.organId(), request.organName(), request.className(),
                    request.placeId(), request.placeName(), request.memberId(), request.memberName(),
                    properties.getOperatorId(), properties.getOperatorName(),
                    format(LocalDateTime.now()), existing));
            return existing;
        }

        String attendId = IdWorker.getIdStr();
        String now = format(LocalDateTime.now());
        query(() -> jdbcTemplate.update(
                "INSERT INTO tb_course_attend (attend_id, fk_course_id, course_type,"
                        + " province_code, province_name, city_code, city_name, county_code, county_name,"
                        + " organ_id, organ_name, class_id, class_name, place_id, place_name,"
                        + " room_id, room_name, member_id, member_name, status,"
                        + " create_id, create_name, create_time, create_unit_id, create_unit_name)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                attendId, request.courseId(), request.courseType(),
                request.provinceCode(), request.provinceName(),
                request.cityCode(), request.cityName(),
                request.countyCode(), request.countyName(),
                request.organId(), request.organName(),
                request.classId(), request.className(),
                request.placeId(), request.placeName(),
                request.roomId(), request.roomName(),
                request.memberId(), request.memberName(),
                LEGACY_NORMAL,
                properties.getOperatorId(), properties.getOperatorName(), now,
                request.organId(), request.organName()));
        return attendId;
    }

    @Override
    public void revokeAttend(String courseId, String classId) {
        if (courseId == null || classId == null) {
            return;
        }
        query(() -> jdbcTemplate.update(
                "UPDATE tb_course_attend SET status = ?, update_id = ?, update_name = ?, update_time = ?"
                        + " WHERE fk_course_id = ? AND class_id = ? AND status = ?",
                LEGACY_DELETED, properties.getOperatorId(), properties.getOperatorName(),
                format(LocalDateTime.now()), courseId, classId, LEGACY_NORMAL));
    }

    @Override
    public boolean isAttendActive(String courseId, String classId) {
        if (courseId == null || classId == null) {
            return false;
        }
        List<String> ids = query(() -> jdbcTemplate.queryForList(
                "SELECT attend_id FROM tb_course_attend"
                        + " WHERE fk_course_id = ? AND class_id = ? AND status = ?",
                String.class, courseId, classId, LEGACY_NORMAL));
        return !ids.isEmpty();
    }

    /**
     * 找这对 (课程, 听讲班) 的听课行，<b>不看状态</b>，理由见 {@link #materializeAttend}。
     * 存量数据里万一已经有多行，取状态正常的那条，保证行为可预期。
     */
    private String findAttendId(String courseId, String classId) {
        if (courseId == null || classId == null) {
            return null;
        }
        List<String> ids = query(() -> jdbcTemplate.queryForList(
                "SELECT attend_id FROM tb_course_attend"
                        + " WHERE fk_course_id = ? AND class_id = ? ORDER BY status, attend_id",
                String.class, courseId, classId));
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private static RowMapper<LegacyCourse> courseMapper() {
        return (ResultSet rs, int index) -> new LegacyCourse(
                rs.getString("course_id"),
                rs.getString("course_name"),
                rs.getString("course_type"),
                rs.getString("class_id"),
                rs.getString("class_name"),
                rs.getString("organ_id"),
                rs.getString("organ_name"),
                rs.getString("subject_code"),
                rs.getString("subject_name"),
                readTime(rs, "time_begin"),
                readTime(rs, "time_end"));
    }

    /**
     * 旧库里 time_begin/time_end 的列型在不同环境不一致（POJO 映射成 String，SQL 里又用
     * {@code DATE()} / {@code DATE_FORMAT()} 当日期用），所以按字符串读再自己解析，两种列型都能吃下。
     */
    private static LocalDateTime readTime(ResultSet rs, String column) throws SQLException {
        String raw = rs.getString(column);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim();
        int dot = normalized.indexOf('.');
        if (dot > 0) {
            normalized = normalized.substring(0, dot);
        }
        try {
            return LocalDateTime.parse(normalized, LEGACY_TIME);
        } catch (DateTimeParseException ignored) {
            log.warn("三课堂课程时间无法解析，按缺失处理: column={}", column);
            return null;
        }
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : value.format(LEGACY_TIME);
    }

    /**
     * 把 Spring 的数据访问异常翻译成带重试语义的网关异常。
     *
     * <p>连接失败、超时、瞬时错误（含死锁与锁等待）算可重试；其余算不可重试，直接转人工，
     * 免得一条永远修不好的记录把重试队列占满。</p>
     */
    private static <T> T query(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessResourceFailureException | TransientDataAccessException ex) {
            throw LegacyClassroomException.retryable("三课堂库暂时不可用: " + ex.getClass().getSimpleName(), ex);
        } catch (EmptyResultDataAccessException ex) {
            throw LegacyClassroomException.permanent("三课堂库查无对应记录");
        } catch (RuntimeException ex) {
            throw new LegacyClassroomException(
                    "三课堂库写入失败: " + ex.getClass().getSimpleName(), false, ex);
        }
    }
}
