package com.han.system.sdfz.compat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.system.domain.po.SysDictDataPo;
import com.han.system.domain.po.SysUserPo;
import com.han.system.mapper.SysDictDataMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduDevicePo;
import com.han.system.sdfz.education.domain.EduPersonClassPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduRoomPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 只读 Han 教育主数据的兼容目录实现。
 *
 * <p>所有查询显式带租户条件：兼容请求不携带 Han 登录态，MyBatis 的租户拦截器在无上下文时会跳过注入，
 * 不能依赖它做隔离。敏感字段（密码、盐、身份证）一律不进入响应。
 *
 * <p><b>状态字段注意</b>：响应里的 {@code status}/{@code state} 是旧系统的软删除标志，
 * 由 Han 的 {@code del_flag} 翻译而来，与 Han 的 {@code status}（启用/停用）不是一回事，
 * 详见 {@link LegacyStatus}。查询里出现的 {@code .eq(...Status, 0)} 过滤的才是 Han 的启用状态，
 * 用途是让选择器只列出启用中的数据，两者不要合并。
 *
 * <p>本期只有教师能登录，但学生照常出现在目录里（名册、课程参与、历史统计），
 * 登录能力由 {@link LegacyCompatProperties#canIssueToken} 单独把关。
 */
@Service
@RequiredArgsConstructor
public class LegacyDirectoryService {

    public static final String TEACHER = "TEACHER";
    public static final String STUDENT = "STUDENT";

    /** 学校 path 递归拼装的深度上限，兼作父子成环时的熔断。 */
    private static final int MAX_ORG_DEPTH = 16;
    /** 按区划码反查学校时的取数上限，避免拼出超长 IN 列表。 */
    private static final int MAX_AREA_SCHOOLS = 500;

    private final LegacyCompatProperties properties;
    private final EduSchoolMapper schoolMapper;
    private final EduClassMapper classMapper;
    private final EduPersonMapper personMapper;
    private final EduPersonClassMapper personClassMapper;
    private final EduDeviceMapper deviceMapper;
    private final EduRoomMapper roomMapper;
    private final SysUserMapper userMapper;
    private final SysDictDataMapper dictDataMapper;

    // ---------------------------------------------------------------- 人员与身份

    /** B1 / B2：旧 {@code UserInfo}，只回填被读取的 pkId 与 realName。 */
    public LegacyPayload userInfo(LegacyRequest request) {
        String pkId = request.firstText("pkId", "userId", "id");
        String phone = request.text("phone");
        EduPersonPo person = pkId != null ? personByUserOrPersonId(pkId) : personByPhone(phone);
        SysUserPo user = person != null ? userById(person.getUserId()) : userByPhone(phone);
        if (person == null && user == null) {
            return LegacyPayload.same(Map.of());
        }

        Long userId = person != null && person.getUserId() != null
                ? person.getUserId() : (user != null ? user.getId() : null);
        String realName = person != null ? person.getPersonName()
                : (user != null ? user.getNickname() : null);
        String contact = person != null && person.getPhone() != null
                ? person.getPhone() : (user != null ? user.getPhone() : null);

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("pkId", userId);
        value.put("userId", string(userId));
        value.put("realName", blankToEmpty(realName));
        value.put("phone", blankToEmpty(contact));
        value.put("desensitizationPhone", desensitize(contact));
        value.put("status", legacyStatus(person, user));
        return LegacyPayload.same(value);
    }

    /** B3：{@code getLiveList} 无条件依赖的身份详情，拿不到旧侧直接 500。 */
    public LegacyPayload identity(LegacyRequest request) {
        String pkId = request.firstText("pkId", "identityId", "id");
        EduPersonPo person = personByPersonOrUserId(pkId);
        if (person == null) {
            return LegacyPayload.same(Map.of());
        }
        return LegacyPayload.same(identityOf(person));
    }

    public Map<String, Object> identityOf(EduPersonPo person) {
        EduSchoolPo school = schoolById(person.getSchoolId());
        String roleType = properties.roleTypeOf(person.getPersonType());
        List<EduClassPo> classes = classesOf(person.getId());

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("pkId", string(person.getId()));
        value.put("identityId", string(person.getId()));
        value.put("userId", externalUserId(person));
        value.put("userName", blankToEmpty(person.getPersonName()));
        value.put("realName", blankToEmpty(person.getPersonName()));
        value.put("identityType", roleType);
        value.put("roleType", roleType);
        value.put("identityName", properties.identityNameOf(person.getPersonType()));
        value.put("orgId", string(person.getSchoolId()));
        value.put("orgName", school != null ? blankToEmpty(school.getSchoolName()) : "");
        value.put("schoolId", string(person.getSchoolId()));
        value.put("schoolName", school != null ? blankToEmpty(school.getSchoolName()) : "");
        value.put("classId", classes.isEmpty() ? null : classes.getFirst().getId());
        value.put("branchId", classes.isEmpty() ? "" : string(classes.getFirst().getId()));
        value.put("branchName", classes.isEmpty() ? "" : blankToEmpty(classes.getFirst().getClassName()));
        value.put("areaCode", areaCodeOf(school));
        value.put("isSchool", properties.getIsSchool());
        value.put("status", LegacyStatus.ofDelFlag(person.getDelFlag()));
        return value;
    }

    /**
     * C3 的 {@code roles[]} 元素。
     *
     * <p><b>两个维度、两套编码，都叫 roleType，不要合并</b>：
     * 顶层 {@code roleType} 是身份类型（教师 2 / 学生 4），旧前端 {@code store/user.ts}
     * 用它做登录过滤；{@code dutyType[].roleType} 是岗位码，控制台菜单用
     * {@code isSchool + '-' + 岗位码} 做授权。岗位取自 {@code edu_person.duty_code}，
     * 映射见 {@link LegacyCompatProperties#getDutyType()}。
     *
     * <p>{@code dutyType} 与 {@code classes} 不能是 {@code undefined} 或空数组：
     * 旧 api 的操作日志有 {@code getDutyType().get(0)}，前端也有
     * {@code userInfo.dutyType.some(...)}，空数组即抛错。{@code dutyCodeOf} 对未配置的岗位
     * 会回落到默认岗位而不是空串，正是为了保证这个元素恒有意义。
     */
    public Map<String, Object> roleOf(EduPersonPo person) {
        EduSchoolPo school = schoolById(person.getSchoolId());
        String roleType = properties.roleTypeOf(person.getPersonType());
        String identityName = properties.identityNameOf(person.getPersonType());
        String dutyCode = properties.dutyCodeOf(person.getDutyCode());
        String dutyName = properties.dutyNameOf(person.getDutyCode());
        List<EduClassPo> classes = classesOf(person.getId());
        EduClassPo firstClass = classes.isEmpty() ? null : classes.getFirst();
        String schoolId = string(person.getSchoolId());
        String schoolName = school != null ? blankToEmpty(school.getSchoolName()) : "";
        String areaCode = areaCodeOf(school);

        Map<String, Object> duty = new LinkedHashMap<>();
        duty.put("pkId", string(person.getId()));
        duty.put("roleType", dutyCode);
        duty.put("positionName", dutyName);
        duty.put("itemText", dutyName);

        Map<String, Object> membership = new LinkedHashMap<>();
        membership.put("branchId", firstClass != null ? string(firstClass.getId()) : "");
        membership.put("branchName", firstClass != null ? blankToEmpty(firstClass.getClassName()) : "");
        membership.put("name", firstClass != null ? blankToEmpty(firstClass.getClassName()) : "");
        membership.put("schoolId", schoolId);
        membership.put("schoolName", schoolName);
        membership.put("areaCode", areaCode);
        for (String field : List.of("eduDepartId", "eduDepartName", "cityEduDepartId", "cityEduDepartName",
                "countyEduDepartId", "countyEduDepartName", "townEduDepartId", "townEduDepartName")) {
            membership.put(field, "");
        }

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("roleType", asNumber(roleType));
        value.put("userId", externalUserId(person));
        value.put("userName", blankToEmpty(person.getPersonName()));
        value.put("identityId", string(person.getId()));
        value.put("identityName", identityName);
        value.put("schoolId", schoolId);
        value.put("schoolName", schoolName);
        value.put("branchId", firstClass != null ? string(firstClass.getId()) : "");
        value.put("branchName", firstClass != null ? blankToEmpty(firstClass.getClassName()) : "");
        value.put("areaCode", areaCode);
        value.put("isSchool", properties.getIsSchool());
        value.put("dutyType", List.of(duty));
        value.put("classes", List.of(membership));
        return value;
    }

    /** B12 / C13：教师下拉框的数据源，元素字段名必须是 {@code teacherName}。 */
    public LegacyPayload teachers(LegacyRequest request) {
        // 旧侧的 state 是软删除标志而不是启用状态，筛「已删除」时只能给空集合，见 LegacyStatus。
        if (LegacyStatus.selectsDeleted(request.text("state"))) {
            return LegacyPayload.page(List.of(), 0, request.pageNo(), request.pageSize());
        }
        LambdaQueryWrapper<EduPersonPo> query = tenantScoped(new LambdaQueryWrapper<EduPersonPo>())
                .eq(EduPersonPo::getPersonType, TEACHER)
                // 这里过滤的是 Han 自己的启用状态，与旧侧的 state 不是同一个概念，不要合并。
                .eq(EduPersonPo::getStatus, 0)
                // 离校的人不能再出现在排课下拉里。leave_flag 与 status 是两件事：
                // status 管账号启停，leave_flag 管教育身份是否在校，可以独立变化
                // （见 EduPersonPo#leaveFlag）。只按 status 过滤的话，
                // 一个「账号还开着但人已经离校」的教师照样能被排进新课程。
                //
                // 只在**选择器**里排除；按 ID 查身份（B1/B3）不加这个条件，
                // 历史课程要能继续显示离校教师的姓名。
                .eq(EduPersonPo::getLeaveFlag, 0);
        Long orgId = request.number("orgId");
        if (orgId != null) {
            query.eq(EduPersonPo::getSchoolId, orgId);
        } else {
            restrictByAreaCode(query, request.text("areaCode"));
        }

        String keyword = request.firstText("loginName", "loginAlias", "account");
        if (keyword != null) {
            query.and(item -> item.like(EduPersonPo::getPersonName, keyword)
                    .or().like(EduPersonPo::getPersonNo, keyword));
        }
        query.orderByAsc(EduPersonPo::getPersonName);

        Page<EduPersonPo> page = personMapper.selectPage(
                new Page<>(request.pageNo(), request.pageSize()), query);
        Map<Long, EduSchoolPo> schools = schoolsByIds(page.getRecords().stream()
                .map(EduPersonPo::getSchoolId).toList());
        List<Map<String, Object>> records = page.getRecords().stream()
                .map(person -> teacherRecord(person, schools.get(person.getSchoolId())))
                .toList();
        return LegacyPayload.page(records, page.getTotal(), request.pageNo(), request.pageSize());
    }

    private Map<String, Object> teacherRecord(EduPersonPo person, EduSchoolPo school) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("userId", externalUserId(person));
        value.put("teacherName", blankToEmpty(person.getPersonName()));
        value.put("pkId", string(person.getId()));
        value.put("identityId", string(person.getId()));
        value.put("roleType", properties.roleTypeOf(person.getPersonType()));
        value.put("orgId", string(person.getSchoolId()));
        value.put("orgName", school != null ? blankToEmpty(school.getSchoolName()) : "");
        value.put("account", blankToEmpty(person.getPersonNo()));
        value.put("desensitizationPhone", desensitize(person.getPhone()));
        value.put("areaCode", areaCodeOf(school));
        value.put("state", LegacyStatus.ofDelFlag(person.getDelFlag()));
        value.put("status", LegacyStatus.ofDelFlag(person.getDelFlag()));
        return value;
    }

    // ---------------------------------------------------------------- 组织

    /** B5 / B8 / C10：按主键取单个组织。 */
    public LegacyPayload org(LegacyRequest request) {
        Long orgId = request.number("orgId");
        EduSchoolPo school = orgId != null ? schoolById(orgId) : null;
        return LegacyPayload.same(school != null ? orgNode(school) : Map.of());
    }

    /** B4：取下级组织。 */
    public LegacyPayload orgChildren(LegacyRequest request) {
        LambdaQueryWrapper<EduSchoolPo> query = tenantScoped(new LambdaQueryWrapper<EduSchoolPo>())
                .eq(EduSchoolPo::getStatus, 0)
                .eq(EduSchoolPo::getParentId, request.number("orgId"))
                .eq(request.text("areaCode") != null, EduSchoolPo::getAreaCode, request.text("areaCode"))
                .orderByAsc(EduSchoolPo::getSchoolName);
        return LegacyPayload.list(schoolMapper.selectList(query).stream().map(this::orgNode).toList());
    }

    /** B6：组织分页列表。{@code path}/{@code orgType}/{@code gridCode}/{@code schoolType} 无对应列，接收后忽略。 */
    public LegacyPayload orgPage(LegacyRequest request) {
        LambdaQueryWrapper<EduSchoolPo> query = tenantScoped(new LambdaQueryWrapper<EduSchoolPo>())
                .eq(EduSchoolPo::getStatus, 0)
                .eq(request.number("orgId") != null, EduSchoolPo::getId, request.number("orgId"))
                .eq(request.text("areaCode") != null, EduSchoolPo::getAreaCode, request.text("areaCode"))
                .like(request.text("orgCode") != null, EduSchoolPo::getSchoolCode, request.text("orgCode"))
                .like(request.text("orgName") != null, EduSchoolPo::getSchoolName, request.text("orgName"))
                .orderByAsc(EduSchoolPo::getSchoolName);
        return pageOfSchools(query, request);
    }

    /** B7：按区划码与校名分页查学校。 */
    public LegacyPayload schoolPage(LegacyRequest request) {
        LambdaQueryWrapper<EduSchoolPo> query = tenantScoped(new LambdaQueryWrapper<EduSchoolPo>())
                .eq(EduSchoolPo::getStatus, 0)
                .eq(request.text("areaCode") != null, EduSchoolPo::getAreaCode, request.text("areaCode"))
                .like(request.text("schoolName") != null, EduSchoolPo::getSchoolName, request.text("schoolName"))
                .orderByAsc(EduSchoolPo::getSchoolName);
        return pageOfSchools(query, request);
    }

    /** B11 / C9：按关键字与区划码检索组织。 */
    public LegacyPayload orgSearch(LegacyRequest request) {
        String keyword = request.firstText("conditions", "orgName");
        LambdaQueryWrapper<EduSchoolPo> query = tenantScoped(new LambdaQueryWrapper<EduSchoolPo>())
                .eq(EduSchoolPo::getStatus, 0)
                .eq(request.text("areaCode") != null, EduSchoolPo::getAreaCode, request.text("areaCode"))
                .orderByAsc(EduSchoolPo::getSchoolName);
        if (keyword != null) {
            query.and(item -> item.like(EduSchoolPo::getSchoolName, keyword)
                    .or().like(EduSchoolPo::getSchoolCode, keyword));
        }
        query.last("limit " + MAX_AREA_SCHOOLS);
        return LegacyPayload.list(schoolMapper.selectList(query).stream().map(this::orgNode).toList());
    }

    /**
     * B9 / C8：懒加载组织树。
     *
     * <p>调用方把区划码塞进了名为 {@code orgName} 的键，这是旧代码的既有缺陷，兼容层照着错的键名接收。
     */
    public LegacyPayload lazyOrgTree(LegacyRequest request) {
        Long parentId = request.number("orgId");
        String areaCode = request.text("orgName");
        LambdaQueryWrapper<EduSchoolPo> query = tenantScoped(new LambdaQueryWrapper<EduSchoolPo>())
                .eq(EduSchoolPo::getStatus, 0)
                .orderByAsc(EduSchoolPo::getSchoolName);
        if (parentId != null) {
            query.eq(EduSchoolPo::getParentId, parentId);
        } else {
            query.isNull(EduSchoolPo::getParentId)
                    .eq(areaCode != null, EduSchoolPo::getAreaCode, areaCode);
        }

        List<EduSchoolPo> schools = schoolMapper.selectList(query);
        Set<Long> withChildren = parentsOf(schools.stream().map(EduSchoolPo::getId).toList());
        List<Map<String, Object>> nodes = schools.stream().map(school -> {
            Map<String, Object> node = orgNode(school);
            boolean leaf = !withChildren.contains(school.getId());
            node.put("leaf", leaf);
            node.put("isLeaf", leaf);
            node.put("hasChildren", !leaf);
            node.put("children", List.of());
            return node;
        }).toList();
        return LegacyPayload.list(nodes);
    }

    /**
     * B10 / C11：年级班级树。
     *
     * <p>旧前端读元素的 {@code branchCode} 与 {@code standardName}，并过滤掉 {@code standardName === '毕业年级'}。
     */
    public LegacyPayload orgBranchTree(LegacyRequest request) {
        LambdaQueryWrapper<EduClassPo> query = tenantScoped(new LambdaQueryWrapper<EduClassPo>())
                .eq(EduClassPo::getStatus, 0)
                .eq(request.number("orgId") != null, EduClassPo::getSchoolId, request.number("orgId"))
                .orderByAsc(EduClassPo::getGradeCode)
                .orderByAsc(EduClassPo::getClassName);

        Map<String, List<EduClassPo>> byGrade = new LinkedHashMap<>();
        for (EduClassPo item : classMapper.selectList(query)) {
            byGrade.computeIfAbsent(blankToEmpty(item.getGradeCode()), key -> new ArrayList<>()).add(item);
        }

        List<Map<String, Object>> grades = new ArrayList<>();
        for (Map.Entry<String, List<EduClassPo>> entry : byGrade.entrySet()) {
            String gradeCode = entry.getKey();
            String gradeName = gradeName(gradeCode);
            List<Map<String, Object>> children = entry.getValue().stream()
                    .map(item -> branchNode(string(item.getId()), blankToEmpty(item.getClassCode()),
                            blankToEmpty(item.getClassName()), "CLASS", gradeCode, List.of()))
                    .toList();
            grades.add(branchNode(gradeCode, gradeCode, gradeName, "GRADE", "", children));
        }
        return LegacyPayload.list(grades);
    }

    // ---------------------------------------------------------------- 场所与设备

    /** B13 / C15：教室场所。{@code buildingId}/{@code floorId} 无对应列，接收后忽略。 */
    public LegacyPayload places(LegacyRequest request) {
        LambdaQueryWrapper<EduRoomPo> query = tenantScoped(new LambdaQueryWrapper<EduRoomPo>())
                .eq(EduRoomPo::getStatus, 0)
                .eq(request.number("orgId") != null, EduRoomPo::getSchoolId, request.number("orgId"))
                .eq(request.text("placeCode") != null, EduRoomPo::getRoomCode, request.text("placeCode"))
                .eq(request.text("placeType") != null, EduRoomPo::getRoomType, request.text("placeType"))
                .like(request.text("placeName") != null, EduRoomPo::getRoomName, request.text("placeName"))
                .orderByAsc(EduRoomPo::getRoomName);
        List<Map<String, Object>> places = roomMapper.selectList(query).stream().map(room -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("pkId", string(room.getId()));
            value.put("placeCode", blankToEmpty(room.getRoomCode()));
            value.put("placeName", blankToEmpty(room.getRoomName()));
            value.put("placeType", blankToEmpty(room.getRoomType()));
            value.put("orgId", string(room.getSchoolId()));
            value.put("status", LegacyStatus.ofDelFlag(room.getDelFlag()));
            return value;
        }).toList();
        return LegacyPayload.list(places);
    }

    /** B14 / C12：会场设备选择器，旧前端把 {@code result} 直接当数组用。 */
    public LegacyPayload devices(LegacyRequest request) {
        String applicationType = request.text("applicationType");
        List<String> deviceTypes = deviceTypesOf(applicationType);
        if (deviceTypes == null) {
            return LegacyPayload.list(List.of());
        }
        LambdaQueryWrapper<EduDevicePo> query = tenantScoped(new LambdaQueryWrapper<EduDevicePo>())
                .eq(EduDevicePo::getStatus, 0)
                .eq(request.number("orgId") != null, EduDevicePo::getSchoolId, request.number("orgId"))
                .like(request.text("deviceName") != null, EduDevicePo::getDeviceName, request.text("deviceName"))
                .in(!deviceTypes.isEmpty(), EduDevicePo::getDeviceType, deviceTypes)
                .orderByAsc(EduDevicePo::getDeviceName);
        List<Map<String, Object>> devices = deviceMapper.selectList(query).stream()
                .map(device -> deviceNode(device, applicationType))
                .toList();
        return LegacyPayload.list(devices);
    }

    /** B15：按设备编码取单台设备。 */
    public LegacyPayload device(LegacyRequest request) {
        String deviceCode = request.text("deviceCode");
        if (deviceCode == null) {
            return LegacyPayload.same(Map.of());
        }
        EduDevicePo device = deviceMapper.selectOne(tenantScoped(new LambdaQueryWrapper<EduDevicePo>())
                .eq(EduDevicePo::getDeviceCode, deviceCode).last("limit 1"));
        return LegacyPayload.same(device != null ? deviceNode(device, null) : Map.of());
    }

    private Map<String, Object> deviceNode(EduDevicePo device, String applicationType) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("pkId", string(device.getId()));
        value.put("deviceCode", blankToEmpty(device.getDeviceCode()));
        value.put("deviceName", blankToEmpty(device.getDeviceName()));
        value.put("deviceType", blankToEmpty(device.getDeviceType()));
        value.put("applicationType", applicationType != null
                ? applicationType : blankToEmpty(device.getDeviceType()));
        value.put("orgId", string(device.getSchoolId()));
        value.put("placeId", string(device.getRoomId()));
        value.put("model", blankToEmpty(device.getModel()));
        value.put("status", LegacyStatus.ofDelFlag(device.getDelFlag()));
        return value;
    }

    /**
     * 解析 applicationType 对应的 device_type 集合。
     *
     * @return 空列表表示不按类型过滤，{@code null} 表示严格模式下无映射、应返回空集合
     */
    private List<String> deviceTypesOf(String applicationType) {
        if (applicationType == null) {
            return List.of();
        }
        List<String> mapped = properties.getDeviceApplicationType().get(applicationType);
        if (mapped != null && !mapped.isEmpty()) {
            Set<String> types = new LinkedHashSet<>(mapped);
            types.add(applicationType);
            return List.copyOf(types);
        }
        return properties.isDeviceApplicationTypeStrict() ? null : List.of();
    }

    // ---------------------------------------------------------------- 字典

    /** C14：旧字典项接口，dictCode 经配置映射到 sys_dict_data.dict_type。 */
    public LegacyPayload dictItems(String dictCode) {
        if (dictCode == null || dictCode.isBlank()) {
            return LegacyPayload.list(List.of());
        }
        String dictType = properties.getDictType().getOrDefault(dictCode, dictCode);
        List<SysDictDataPo> items = dictDataMapper.selectList(
                tenantScoped(new LambdaQueryWrapper<SysDictDataPo>())
                        .eq(SysDictDataPo::getDictType, dictType)
                        .eq(SysDictDataPo::getStatus, 0)
                        .orderByAsc(SysDictDataPo::getDictSort));
        List<Map<String, Object>> values = items.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("value", blankToEmpty(item.getDictValue()));
            value.put("text", blankToEmpty(item.getDictLabel()));
            value.put("title", blankToEmpty(item.getDictLabel()));
            value.put("label", blankToEmpty(item.getDictLabel()));
            return value;
        }).toList();
        return LegacyPayload.list(values);
    }

    // ---------------------------------------------------------------- 查询原语

    public EduPersonPo personByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return personMapper.selectOne(tenantScoped(new LambdaQueryWrapper<EduPersonPo>())
                .eq(EduPersonPo::getUserId, userId)
                .orderByAsc(EduPersonPo::getId)
                .last("limit 1"));
    }

    public List<EduPersonPo> personsByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return personMapper.selectList(tenantScoped(new LambdaQueryWrapper<EduPersonPo>())
                .eq(EduPersonPo::getUserId, userId)
                .orderByAsc(EduPersonPo::getId));
    }

    public EduPersonPo personById(Long personId) {
        if (personId == null) {
            return null;
        }
        return personMapper.selectOne(tenantScoped(new LambdaQueryWrapper<EduPersonPo>())
                .eq(EduPersonPo::getId, personId).last("limit 1"));
    }

    public SysUserPo userByLoginName(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            return null;
        }
        String value = loginName.trim();
        SysUserPo user = userMapper.selectOne(tenantScoped(new LambdaQueryWrapper<SysUserPo>())
                .eq(SysUserPo::getUsername, value).last("limit 1"));
        return user != null ? user : userByPhone(value);
    }

    public EduSchoolPo schoolById(Long schoolId) {
        if (schoolId == null) {
            return null;
        }
        return schoolMapper.selectOne(tenantScoped(new LambdaQueryWrapper<EduSchoolPo>())
                .eq(EduSchoolPo::getId, schoolId).last("limit 1"));
    }

    /**
     * 人员的归班列表，按归班记录的创建顺序返回。
     *
     * <p>{@code edu_person_class} 的唯一索引不含 {@code del_flag}，同一人同一班可能留下多条历史行，
     * 因此这里按 class_id 去重后再取班级，避免同一个班在响应里出现两次。
     */
    public List<EduClassPo> classesOf(Long personId) {
        if (personId == null) {
            return List.of();
        }
        List<EduPersonClassPo> memberships = personClassMapper.selectList(
                tenantScoped(new LambdaQueryWrapper<EduPersonClassPo>())
                        .eq(EduPersonClassPo::getPersonId, personId)
                        .orderByAsc(EduPersonClassPo::getId));
        List<Long> classIds = memberships.stream()
                .map(EduPersonClassPo::getClassId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        List<EduClassPo> classes = classMapper.selectList(tenantScoped(new LambdaQueryWrapper<EduClassPo>())
                .in(EduClassPo::getId, classIds));
        Map<Long, EduClassPo> byId = classes.stream()
                .collect(Collectors.toMap(EduClassPo::getId, Function.identity(), (first, second) -> first));
        return classIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    public String areaCodeOf(EduSchoolPo school) {
        String areaCode = school != null ? school.getAreaCode() : null;
        return areaCode != null && !areaCode.isBlank() ? areaCode.trim() : properties.getDefaultAreaCode();
    }

    /** {@code edu_person.user_id} 为空的人员回落成人员主键，保证旧侧的 userId 永不为空。 */
    public String externalUserId(EduPersonPo person) {
        return person.getUserId() != null ? string(person.getUserId()) : string(person.getId());
    }

    // ---------------------------------------------------------------- 内部工具

    private LegacyPayload pageOfSchools(LambdaQueryWrapper<EduSchoolPo> query, LegacyRequest request) {
        Page<EduSchoolPo> page = schoolMapper.selectPage(
                new Page<>(request.pageNo(), request.pageSize()), query);
        List<Map<String, Object>> records = page.getRecords().stream().map(this::orgNode).toList();
        return LegacyPayload.page(records, page.getTotal(), request.pageNo(), request.pageSize());
    }

    /**
     * 组织节点。
     *
     * <p><b>{@code orgId} 用的是 Han 的雪花主键，不是 {@code external_id}</b>，这是 2026-08-12 拍的板：
     * 数字校园整条线已冻结，Han 新建的学校 {@code external_id} 本来就是空，U3/U4 不受影响。
     *
     * <p>前提是「旧库里这些 ID 列都是快照列而非外键，Han 是唯一的目录来源」，
     * 依据见 {@code doc/Han与三课堂实体ID映射结论-2026-08-12.md}。
     * 一旦数字校园解冻、需要兼容历史同步数据，这里必须改成
     * {@code external_id} 优先、雪花 ID 兜底的双向查找，<b>并且所有按 {@code orgId} 反查的地方要同步改</b>，
     * 否则前端传回来的历史 ID 会查不到。
     */
    private Map<String, Object> orgNode(EduSchoolPo school) {
        EduSchoolPo parent = schoolById(school.getParentId());
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("pkId", string(school.getId()));
        value.put("id", string(school.getId()));
        value.put("orgId", string(school.getId()));
        value.put("orgName", blankToEmpty(school.getSchoolName()));
        value.put("label", blankToEmpty(school.getSchoolName()));
        value.put("orgCode", blankToEmpty(school.getSchoolCode()));
        value.put("parentId", string(school.getParentId()));
        value.put("parentName", parent != null ? blankToEmpty(parent.getSchoolName()) : "");
        value.put("areaCode", areaCodeOf(school));
        value.put("orgType", properties.getOrgType());
        value.put("schoolType", properties.getSchoolType());
        value.put("path", orgPath(school));
        value.put("gridCode", "");
        value.put("gridName", "");
        value.put("status", LegacyStatus.ofDelFlag(school.getDelFlag()));
        return value;
    }

    /** {@code edu_school} 无 path 列，按 parent_id 自底向上拼出来，带深度熔断。 */
    private String orgPath(EduSchoolPo school) {
        List<String> segments = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        EduSchoolPo current = school;
        while (current != null && visited.add(current.getId()) && segments.size() < MAX_ORG_DEPTH) {
            segments.addFirst(string(current.getId()));
            current = schoolById(current.getParentId());
        }
        return String.join("/", segments);
    }

    private Map<String, Object> branchNode(String branchId, String branchCode, String standardName,
                                            String branchType, String parentId,
                                            List<Map<String, Object>> children) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("branchId", branchId);
        value.put("branchCode", branchCode);
        value.put("branchName", standardName);
        value.put("standardName", standardName);
        value.put("branchType", branchType);
        value.put("parentId", parentId);
        value.put("children", children);
        return value;
    }

    private String gradeName(String gradeCode) {
        if (gradeCode == null || gradeCode.isBlank()) {
            return properties.getUngradedName();
        }
        return properties.getGradeName().getOrDefault(gradeCode, gradeCode);
    }

    private Set<Long> parentsOf(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        List<EduSchoolPo> children = schoolMapper.selectList(tenantScoped(new LambdaQueryWrapper<EduSchoolPo>())
                .select(EduSchoolPo::getParentId)
                .in(EduSchoolPo::getParentId, ids));
        return children.stream().map(EduSchoolPo::getParentId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<Long, EduSchoolPo> schoolsByIds(Collection<Long> ids) {
        Set<Long> unique = ids == null ? Set.of()
                : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return Map.of();
        }
        return schoolMapper.selectList(tenantScoped(new LambdaQueryWrapper<EduSchoolPo>())
                        .in(EduSchoolPo::getId, unique)).stream()
                .collect(Collectors.toMap(EduSchoolPo::getId, Function.identity(), (first, second) -> first));
    }

    private void restrictByAreaCode(LambdaQueryWrapper<EduPersonPo> query, String areaCode) {
        if (areaCode == null) {
            return;
        }
        List<EduSchoolPo> schools = schoolMapper.selectList(tenantScoped(new LambdaQueryWrapper<EduSchoolPo>())
                .select(EduSchoolPo::getId)
                .eq(EduSchoolPo::getAreaCode, areaCode)
                .last("limit " + MAX_AREA_SCHOOLS));
        List<Long> ids = schools.stream().map(EduSchoolPo::getId).toList();
        if (ids.isEmpty()) {
            query.eq(EduPersonPo::getSchoolId, -1L);
        } else {
            query.in(EduPersonPo::getSchoolId, ids);
        }
    }

    private EduPersonPo personByUserOrPersonId(String rawId) {
        Long id = toLong(rawId);
        if (id == null) {
            return null;
        }
        EduPersonPo person = personByUserId(id);
        return person != null ? person : personById(id);
    }

    private EduPersonPo personByPersonOrUserId(String rawId) {
        Long id = toLong(rawId);
        if (id == null) {
            return null;
        }
        EduPersonPo person = personById(id);
        return person != null ? person : personByUserId(id);
    }

    private EduPersonPo personByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return personMapper.selectOne(tenantScoped(new LambdaQueryWrapper<EduPersonPo>())
                .eq(EduPersonPo::getPhone, phone.trim())
                .orderByAsc(EduPersonPo::getId)
                .last("limit 1"));
    }

    private SysUserPo userById(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectOne(tenantScoped(new LambdaQueryWrapper<SysUserPo>())
                .eq(SysUserPo::getId, userId).last("limit 1"));
    }

    private SysUserPo userByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return userMapper.selectOne(tenantScoped(new LambdaQueryWrapper<SysUserPo>())
                .eq(SysUserPo::getPhone, phone.trim()).last("limit 1"));
    }

    /**
     * 显式限定租户。
     *
     * <p>兼容请求没有 Han 登录态，{@code HanTenantLineHandler} 在拿不到租户上下文时会跳过条件注入，
     * 所以隔离必须由查询自己保证。用 {@code apply} 是因为 {@code tenant_id} 定义在父类实体上，
     * Lambda 列解析拿不到它。
     */
    private <T> LambdaQueryWrapper<T> tenantScoped(LambdaQueryWrapper<T> query) {
        return query.apply("tenant_id = {0}", properties.getTenantId());
    }

    /**
     * 人员在旧侧的 status，取的是 Han 的 {@code del_flag} 而不是 {@code status}。
     *
     * <p>Han 的 {@code status}（启用/停用）在旧侧没有对应字段，写进去会被当成删除标记，见 {@link LegacyStatus}。
     */
    private static String legacyStatus(EduPersonPo person, SysUserPo user) {
        if (person != null) {
            return LegacyStatus.ofDelFlag(person.getDelFlag());
        }
        return user != null ? LegacyStatus.ofDelFlag(user.getDelFlag()) : LegacyStatus.PRESENT;
    }

    private static Long toLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Object asNumber(String value) {
        try {
            return value == null || value.isBlank() ? value : Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static String string(Long value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static String blankToEmpty(String value) {
        return value != null ? value : "";
    }

    /** 手机号只保留前三后四，兼容响应不返回完整号码。 */
    private static String desensitize(String phone) {
        if (phone == null || phone.length() < 7) {
            return "";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
