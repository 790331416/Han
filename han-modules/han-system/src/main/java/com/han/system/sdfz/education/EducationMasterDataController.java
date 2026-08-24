package com.han.system.sdfz.education;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.common.web.excel.ExcelUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.system.domain.po.SysDictDataPo;
import com.han.system.domain.po.SysRolePo;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduDevicePo;
import com.han.system.sdfz.education.domain.EduPersonClassPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduPersonSubjectPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduSubjectPo;
import com.han.system.sdfz.education.domain.EducationForms;
import com.han.system.sdfz.education.domain.EducationPersonImportVo;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import com.han.system.mapper.SysDictDataMapper;
import com.han.system.mapper.SysRoleMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 教育主数据管理端接口。
 *
 * <p>人员写入接口关闭操作日志的参数与响应留痕：请求体可能携带初始口令，响应可能回传服务端生成的初始口令，
 * 两者都不允许落到 {@code sys_oper_log}。</p>
 */
@AdminAuth
@RestController
@RequestMapping("/system/education")
@RequiredArgsConstructor
public class EducationMasterDataController {

    private final EducationMasterDataService service;
    private final EducationPersonService personService;
    private final EduClassMapper classMapper;
    private final EduSubjectMapper subjectMapper;
    private final SysDictDataMapper dictDataMapper;
    private final SysRoleMapper roleMapper;

    // ---------------------------------------------------------------- 学校

    @GetMapping("/schools/list")
    @PreAuthorize("@ss.hasAuthority('education:school:list')")
    public R<PageResult<EduSchoolPo>> schools(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.listSchools(keyword, status, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/schools")
    @PreAuthorize("@ss.hasAuthority('education:school:add')")
    @OperLog(module = "学校管理", type = OperLog.OperType.INSERT)
    public R<Long> addSchool(@Valid @RequestBody EducationForms.School form) {
        requireCreate(form.id());
        return R.ok(service.saveSchool(form));
    }

    @RepeatSubmit
    @PostMapping("/schools/edit")
    @PreAuthorize("@ss.hasAuthority('education:school:edit')")
    @OperLog(module = "学校管理", type = OperLog.OperType.UPDATE)
    public R<Long> editSchool(@Valid @RequestBody EducationForms.School form) {
        requireEdit(form.id());
        return R.ok(service.saveSchool(form));
    }

    @RepeatSubmit
    @PostMapping("/schools/remove")
    @PreAuthorize("@ss.hasAuthority('education:school:remove')")
    @OperLog(module = "学校管理", type = OperLog.OperType.DELETE)
    public R<Integer> removeSchools(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(service.deleteSchools(request.ids()));
    }

    // ---------------------------------------------------------------- 班级

    @GetMapping("/classes/list")
    @PreAuthorize("@ss.hasAuthority('education:class:list')")
    public R<PageResult<EduClassPo>> classes(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.listClasses(schoolId, keyword, status, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/classes")
    @PreAuthorize("@ss.hasAuthority('education:class:add')")
    @OperLog(module = "班级管理", type = OperLog.OperType.INSERT)
    public R<Long> addClass(@Valid @RequestBody EducationForms.ClassInfo form) {
        requireCreate(form.id());
        return R.ok(service.saveClass(form));
    }

    @RepeatSubmit
    @PostMapping("/classes/edit")
    @PreAuthorize("@ss.hasAuthority('education:class:edit')")
    @OperLog(module = "班级管理", type = OperLog.OperType.UPDATE)
    public R<Long> editClass(@Valid @RequestBody EducationForms.ClassInfo form) {
        requireEdit(form.id());
        return R.ok(service.saveClass(form));
    }

    @RepeatSubmit
    @PostMapping("/classes/remove")
    @PreAuthorize("@ss.hasAuthority('education:class:remove')")
    @OperLog(module = "班级管理", type = OperLog.OperType.DELETE)
    public R<Integer> removeClasses(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(service.deleteClasses(request.ids()));
    }

    // ---------------------------------------------------------------- 人员

    @GetMapping("/people/list")
    @PreAuthorize("@ss.hasAuthority('education:person:list')")
    public R<PageResult<EduPersonPo>> people(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String personType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(personService.list(schoolId, personType, keyword, status, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/people")
    @PreAuthorize("@ss.hasAuthority('education:person:add')")
    @OperLog(module = "人员管理", type = OperLog.OperType.INSERT, saveParams = false, saveResult = false)
    public R<EducationForms.PersonResult> addPerson(@Valid @RequestBody EducationForms.Person form) {
        requireCreate(form.id());
        return R.ok(personService.save(form));
    }

    @RepeatSubmit
    @PostMapping("/people/edit")
    @PreAuthorize("@ss.hasAuthority('education:person:edit')")
    @OperLog(module = "人员管理", type = OperLog.OperType.UPDATE, saveParams = false, saveResult = false)
    public R<EducationForms.PersonResult> editPerson(@Valid @RequestBody EducationForms.Person form) {
        requireEdit(form.id());
        return R.ok(personService.save(form));
    }

    @RepeatSubmit
    @PostMapping("/people/reset-password")
    @PreAuthorize("@ss.hasAuthority('education:person:resetPwd')")
    @OperLog(module = "人员管理", type = OperLog.OperType.UPDATE, saveParams = false)
    public R<Void> resetPersonPassword(@RequestParam Long personId, @RequestParam String password) {
        personService.resetAccountPassword(personId, password);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/people/unbind")
    @PreAuthorize("@ss.hasAuthority('system:client-user:unbind')")
    @OperLog(module = "客户端用户", type = OperLog.OperType.UPDATE)
    public R<Void> unbindClientUser(@RequestParam Long userId) {
        personService.unbindClientUser(userId);
        return R.ok();
    }

    @GetMapping("/people/import-template")
    @PreAuthorize("@ss.hasAuthority('education:person:import')")
    public void personImportTemplate(@RequestParam Long schoolId, HttpServletResponse response) throws IOException {
        var school = personService.requireImportSchool(schoolId);
        exportPersonTemplate(response, schoolId, school.getSchoolName());
    }

    @RepeatSubmit(interval = 10)
    @PostMapping("/people/import")
    @PreAuthorize("@ss.hasAuthority('education:person:import')")
    @OperLog(module = "人员管理", type = OperLog.OperType.IMPORT, saveParams = false, saveResult = false)
    public R<List<EducationForms.PersonImportResult>> importPeople(@RequestParam("file") MultipartFile file,
                                                                    @RequestParam Long schoolId)
            throws IOException {
        String importSchoolName = personService.requireImportSchool(schoolId).getSchoolName();
        List<EducationPersonImportVo> rows = ExcelUtil.importExcel(file.getInputStream(), EducationPersonImportVo.class);
        if (rows == null || rows.isEmpty()) {
            return R.fail("导入数据为空");
        }

        List<EducationForms.PersonImportResult> results = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            EducationPersonImportVo row = rows.get(index);
            int rowNumber = index + 2;
            if ("示例数据（请删除本行）".equals(text(row.getSchoolName()))) {
                continue;
            }
            try {
                if (text(row.getSchoolName()) != null && !text(row.getSchoolName()).equals(importSchoolName)) {
                    throw new BusinessException("学校必须是当前选择的导入学校");
                }
                EducationForms.PersonResult saved = personService.save(toPersonForm(row, schoolId));
                results.add(new EducationForms.PersonImportResult(rowNumber, text(row.getPersonName()),
                        text(row.getPhone()), true, "导入成功", saved.personId(), saved.userId()));
            } catch (Exception ex) {
                results.add(new EducationForms.PersonImportResult(rowNumber, text(row.getPersonName()),
                        text(row.getPhone()), false, errorMessage(ex), null, null));
            }
        }
        return R.ok(results);
    }

    @RepeatSubmit
    @PostMapping("/people/remove")
    @PreAuthorize("@ss.hasAuthority('education:person:remove')")
    @OperLog(module = "人员管理", type = OperLog.OperType.DELETE)
    public R<Integer> removePeople(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(personService.deletePeople(request.ids()));
    }

    @GetMapping("/people/memberships")
    @PreAuthorize("@ss.hasAuthority('education:person:list')")
    public R<List<EduPersonClassPo>> memberships(@RequestParam Long personId) {
        return R.ok(personService.listMemberships(personId));
    }

    @RepeatSubmit
    @PostMapping("/people/memberships")
    @PreAuthorize("@ss.hasAuthority('education:person:edit')")
    @OperLog(module = "人员管理", type = OperLog.OperType.GRANT)
    public R<Integer> replaceMemberships(@Valid @RequestBody EducationForms.Membership form) {
        return R.ok(personService.replaceMemberships(form));
    }

    @GetMapping("/people/subjects")
    @PreAuthorize("@ss.hasAuthority('education:person:list')")
    public R<List<EduPersonSubjectPo>> assignments(@RequestParam Long personId) {
        return R.ok(personService.listAssignments(personId));
    }

    /** 供编辑页回填登录角色，避免前端提交空数组把角色清空。 */
    @GetMapping("/people/roles")
    @PreAuthorize("@ss.hasAuthority('education:person:list')")
    public R<List<Long>> personRoles(@RequestParam Long personId) {
        return R.ok(personService.listRoleIds(personId));
    }

    /**
     * 关联账号精确匹配（任务书 21-23）：按当前租户 + 精确手机号查询，只返回一条脱敏信息，
     * 不允许遍历全租户账号；保存时服务端会按 {@code linkUserId} 重新复核。
     */
    @GetMapping("/people/linkable-account")
    @PreAuthorize("@ss.hasAnyAuthority('education:person:add','education:person:edit')")
    public R<EducationForms.LinkableAccount> linkableAccount(@RequestParam String phone) {
        return R.ok(personService.linkableAccount(phone));
    }

    @RepeatSubmit
    @PostMapping("/people/subjects")
    @PreAuthorize("@ss.hasAuthority('education:person:edit')")
    @OperLog(module = "人员管理", type = OperLog.OperType.GRANT)
    public R<Integer> replaceAssignments(@Valid @RequestBody EducationForms.TeachingAssignment form) {
        return R.ok(personService.replaceAssignments(form));
    }

    // ---------------------------------------------------------------- 科目

    @GetMapping("/subjects/list")
    @PreAuthorize("@ss.hasAuthority('education:subject:list')")
    public R<PageResult<EduSubjectPo>> subjects(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.listSubjects(schoolId, keyword, status, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/subjects")
    @PreAuthorize("@ss.hasAuthority('education:subject:add')")
    @OperLog(module = "科目管理", type = OperLog.OperType.INSERT)
    public R<Long> addSubject(@Valid @RequestBody EducationForms.Subject form) {
        requireCreate(form.id());
        return R.ok(service.saveSubject(form));
    }

    @RepeatSubmit
    @PostMapping("/subjects/edit")
    @PreAuthorize("@ss.hasAuthority('education:subject:edit')")
    @OperLog(module = "科目管理", type = OperLog.OperType.UPDATE)
    public R<Long> editSubject(@Valid @RequestBody EducationForms.Subject form) {
        requireEdit(form.id());
        return R.ok(service.saveSubject(form));
    }

    @RepeatSubmit
    @PostMapping("/subjects/remove")
    @PreAuthorize("@ss.hasAuthority('education:subject:remove')")
    @OperLog(module = "科目管理", type = OperLog.OperType.DELETE)
    public R<Integer> removeSubjects(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(service.deleteSubjects(request.ids()));
    }

    // ---------------------------------------------------------------- 设备

    @GetMapping("/devices/list")
    @PreAuthorize("@ss.hasAuthority('education:device:list')")
    public R<PageResult<EduDevicePo>> devices(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.listDevices(schoolId, roomId, keyword, status, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/devices")
    @PreAuthorize("@ss.hasAuthority('education:device:add')")
    @OperLog(module = "设备管理", type = OperLog.OperType.INSERT)
    public R<Long> addDevice(@Valid @RequestBody EducationForms.Device form) {
        requireCreate(form.id());
        return R.ok(service.saveDevice(form));
    }

    @RepeatSubmit
    @PostMapping("/devices/edit")
    @PreAuthorize("@ss.hasAuthority('education:device:edit')")
    @OperLog(module = "设备管理", type = OperLog.OperType.UPDATE)
    public R<Long> editDevice(@Valid @RequestBody EducationForms.Device form) {
        requireEdit(form.id());
        return R.ok(service.saveDevice(form));
    }

    @RepeatSubmit
    @PostMapping("/devices/remove")
    @PreAuthorize("@ss.hasAuthority('education:device:remove')")
    @OperLog(module = "设备管理", type = OperLog.OperType.DELETE)
    public R<Integer> removeDevices(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(service.deleteDevices(request.ids()));
    }

    // 学期与教室不在这里：它们由 EducationCalendarController 独占 /semesters/* 与 /rooms/*。
    // 两边都挂在 @RequestMapping("/system/education") 上，同名端点各写一份会让 Spring 在启动期
    // 抛 Ambiguous mapping，han-system 直接起不来，而 mvn install 阶段看不出来。

    // ---------------------------------------------------------------- 内部

    private EducationForms.Person toPersonForm(EducationPersonImportVo row, Long schoolId) {
        String name = required(row.getPersonName(), "姓名");
        String personType = required(row.getPersonType(), "人员类型").toUpperCase(Locale.ROOT);
        if ("教师".equals(personType)) personType = "TEACHER";
        if ("学生".equals(personType)) personType = "STUDENT";
        String duty = resolveDutyName(row.getDutyName(), personType);
        return new EducationForms.Person(
                null, schoolId, null, name, personType, duty,
                required(row.getPhone(), "手机号"), statusValue(row.getStatus()),
                text(row.getRemark()), flagValue(row.getLeaveFlag(), "离校状态", 0),
                booleanValue(row.getLoginEnabled(), "启用校端登录", null), text(row.getUsername()),
                text(row.getPassword()), resolveRoles(row.getRoleNames(), personType),
                booleanValue(row.getClearRoles(), "清除管理端角色", false), resolveClasses(row.getClassNames(), schoolId),
                membershipRole(row.getMembershipRole()), resolveSubjects(row.getSubjectNames(), schoolId));
    }

    private String resolveDutyName(String value, String personType) {
        if ("STUDENT".equals(personType)) {
            if (text(value) != null) throw new BusinessException("学生不能配置校内职务");
            return null;
        }
        String name = text(value);
        if (name == null) return "TEACHER";
        SysDictDataPo item = dictDataMapper.selectOne(new LambdaQueryWrapper<SysDictDataPo>()
                .eq(SysDictDataPo::getDictType, "edu_school_duty").eq(SysDictDataPo::getDictLabel, name).eq(SysDictDataPo::getStatus, 0));
        if (item == null) throw new BusinessException("校内职务不存在: " + name);
        return item.getDictValue();
    }

    private List<Long> resolveClasses(String value, Long schoolId) {
        return resolveNames(value, "班级", classMapper.selectList(new LambdaQueryWrapper<com.han.system.sdfz.education.domain.EduClassPo>()
                .eq(com.han.system.sdfz.education.domain.EduClassPo::getSchoolId, schoolId)
                .eq(com.han.system.sdfz.education.domain.EduClassPo::getNodeType, "CLASS")
                .eq(com.han.system.sdfz.education.domain.EduClassPo::getStatus, 0)),
                com.han.system.sdfz.education.domain.EduClassPo::getClassName,
                com.han.system.sdfz.education.domain.EduClassPo::getId);
    }

    private List<Long> resolveSubjects(String value, Long schoolId) {
        return resolveNames(value, "科目", subjectMapper.selectList(new LambdaQueryWrapper<com.han.system.sdfz.education.domain.EduSubjectPo>()
                .eq(com.han.system.sdfz.education.domain.EduSubjectPo::getSchoolId, schoolId)
                .eq(com.han.system.sdfz.education.domain.EduSubjectPo::getStatus, 0)),
                com.han.system.sdfz.education.domain.EduSubjectPo::getSubjectName,
                com.han.system.sdfz.education.domain.EduSubjectPo::getId);
    }

    private List<Long> resolveRoles(String value, String personType) {
        if ("STUDENT".equals(personType) && text(value) != null) throw new BusinessException("学生不能分配管理端角色");
        if (text(value) == null) return List.of();
        Map<String, Long> names = roleMapper.selectList(new LambdaQueryWrapper<SysRolePo>().eq(SysRolePo::getStatus, 0))
                .stream().filter(item -> allowedImportRole(item.getRoleKey()))
                .collect(Collectors.toMap(SysRolePo::getRoleName, SysRolePo::getId, (a, b) -> a, LinkedHashMap::new));
        return textList(value).stream().map(name -> requiredMapValue(names, name, "管理端角色不存在或不可分配")).toList();
    }

    private static <T> List<Long> resolveNames(String value, String label, List<T> items,
                                                java.util.function.Function<T, String> name,
                                                java.util.function.Function<T, Long> id) {
        if (text(value) == null) return List.of();
        Map<String, Long> values = items.stream().collect(Collectors.toMap(name, id, (a, b) -> a, LinkedHashMap::new));
        return textList(value).stream().map(item -> requiredMapValue(values, item, label + "不属于所选学校")).toList();
    }

    private static Long requiredMapValue(Map<String, Long> values, String name, String message) {
        Long id = values.get(name);
        if (id == null) throw new BusinessException(message + ": " + name);
        return id;
    }
    private static String membershipRole(String value) {
        String text = text(value);
        if ("教师".equals(text)) return "TEACHER";
        if ("学生".equals(text)) return "STUDENT";
        return text;
    }
    private static List<String> textList(String value) { return java.util.Arrays.stream(value.split(",")).map(EducationMasterDataController::text).filter(java.util.Objects::nonNull).toList(); }

    private void exportPersonTemplate(HttpServletResponse response, Long schoolId, String schoolName) throws IOException {
        List<String> classes = classMapper.selectList(new LambdaQueryWrapper<com.han.system.sdfz.education.domain.EduClassPo>()
                        .eq(com.han.system.sdfz.education.domain.EduClassPo::getSchoolId, schoolId)
                        .eq(com.han.system.sdfz.education.domain.EduClassPo::getNodeType, "CLASS")
                        .eq(com.han.system.sdfz.education.domain.EduClassPo::getStatus, 0))
                .stream().map(com.han.system.sdfz.education.domain.EduClassPo::getClassName).toList();
        List<String> subjects = subjectMapper.selectList(new LambdaQueryWrapper<com.han.system.sdfz.education.domain.EduSubjectPo>()
                        .eq(com.han.system.sdfz.education.domain.EduSubjectPo::getSchoolId, schoolId)
                        .eq(com.han.system.sdfz.education.domain.EduSubjectPo::getStatus, 0))
                .stream().map(com.han.system.sdfz.education.domain.EduSubjectPo::getSubjectName).toList();
        List<String> duties = dictDataMapper.selectList(new LambdaQueryWrapper<SysDictDataPo>()
                        .eq(SysDictDataPo::getDictType, "edu_school_duty").eq(SysDictDataPo::getStatus, 0))
                .stream().map(SysDictDataPo::getDictLabel).toList();
        List<String> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRolePo>().eq(SysRolePo::getStatus, 0))
                .stream().filter(item -> allowedImportRole(item.getRoleKey()))
                .map(SysRolePo::getRoleName).toList();
        String[] headers = {"学校", "姓名", "人员类型", "校内岗位", "手机号", "状态", "备注", "离校状态", "启用校端登录", "用户名", "校端初始密码", "系统管理权限", "清除管理端角色", "所属班级", "归班角色", "任教科目"};
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("人员导入");
            Sheet dict = workbook.createSheet("填写说明");
            Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setWrapText(true);
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i); cell.setCellValue(headers[i]); cell.setCellStyle(headerStyle);
                Comment comment = cellComment(workbook, drawing, i, "填写说明：" + (i == 0 ? "固定为当前选择的学校。" : "按模板示例填写；多个值使用中文名称并用逗号分隔。"));
                cell.setCellComment(comment);
                sheet.setColumnWidth(i, 18 * 256);
            }
            Row sample = sheet.createRow(1);
            String[] values = {"示例数据（请删除本行）", "张三", "教师", duties.contains("普通教师") ? "普通教师" : "", "13800000000", "正常", "", "否", "是", "", "请填写初始密码", "", "否", classes.isEmpty() ? "" : classes.get(0), "教师", subjects.isEmpty() ? "" : subjects.get(0)};
            for (int i = 0; i < values.length; i++) sample.createCell(i).setCellValue(values[i]);
            addValidation(sheet, 2, 1000, 2, List.of("教师", "学生"), workbook);
            addValidation(sheet, 2, 1000, 0, List.of(schoolName), workbook);
            addValidation(sheet, 2, 1000, 3, duties, workbook);
            addValidation(sheet, 2, 1000, 5, List.of("正常", "停用"), workbook);
            addValidation(sheet, 2, 1000, 7, List.of("是", "否"), workbook);
            addValidation(sheet, 2, 1000, 8, List.of("是", "否"), workbook);
            addValidation(sheet, 2, 1000, 11, roles, workbook);
            addValidation(sheet, 2, 1000, 12, List.of("是", "否"), workbook);
            addValidation(sheet, 2, 1000, 13, classes, workbook);
            addValidation(sheet, 2, 1000, 14, List.of("教师", "学生"), workbook);
            addValidation(sheet, 2, 1000, 15, subjects, workbook);
            Row note = dict.createRow(0); note.createCell(0).setCellValue("当前导入学校"); note.createCell(1).setCellValue(schoolName);
            Row note2 = dict.createRow(1); note2.createCell(0).setCellValue("规则"); note2.createCell(1).setCellValue("第二行是示例数据，请删除；学校、班级、科目、职务和管理端角色均按中文名称填写，多个值用逗号分隔。学生不填写校内岗位和管理端权限。管理端角色留空表示无管理端权限。");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=person-import-template.xlsx");
            workbook.write(response.getOutputStream());
        }
    }

    private static Comment cellComment(Workbook workbook, Drawing<?> drawing, int column, String text) {
        CreationHelper helper = workbook.getCreationHelper();
        ClientAnchor anchor = helper.createClientAnchor();
        anchor.setCol1(column);
        anchor.setCol2(column + 3);
        anchor.setRow1(0);
        anchor.setRow2(3);
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(helper.createRichTextString(text));
        return comment;
    }

    private static void addValidation(Sheet sheet, int firstRow, int lastRow, int column, List<String> values, Workbook workbook) {
        if (values == null || values.isEmpty()) return;
        Sheet dict = workbook.getSheet("填写说明");
        int start = dict.getLastRowNum() + 2;
        for (int i = 0; i < values.size(); i++) dict.createRow(start + i).createCell(0).setCellValue(values.get(i));
        String rangeName = "person_import_options_" + column;
        org.apache.poi.ss.usermodel.Name named = workbook.getName(rangeName);
        if (named == null) named = workbook.createName();
        named.setNameName(rangeName);
        named.setRefersToFormula("'填写说明'!$A$" + (start + 1) + ":$A$" + (start + values.size()));
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint(rangeName);
        DataValidation validation = helper.createValidation(constraint, new org.apache.poi.ss.util.CellRangeAddressList(firstRow, lastRow, column, column));
        validation.setSuppressDropDownArrow(true); validation.setShowErrorBox(true); sheet.addValidationData(validation);
    }

    private static boolean allowedImportRole(String roleKey) {
        String key = String.valueOf(roleKey).toLowerCase(Locale.ROOT);
        return !List.of("teacher", "student").contains(key) && (SecurityContextHolder.isAdmin() || !"admin".equals(key));
    }

    private static Integer integerValue(String value, String label, int defaultValue) {
        String text = text(value);
        if (text == null) return defaultValue;
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            throw new BusinessException(label + "必须是 0 或 1");
        }
    }

    private static Integer statusValue(String value) {
        String text = text(value);
        if (text == null || "正常".equals(text)) return 0;
        if ("停用".equals(text)) return 1;
        return integerValue(text, "状态", 0);
    }

    private static Integer flagValue(String value, String label, int defaultValue) {
        String text = text(value);
        if (text == null) return defaultValue;
        if ("是".equals(text) || "启用".equalsIgnoreCase(text)) return 1;
        if ("否".equals(text) || "停用".equalsIgnoreCase(text)) return 0;
        return integerValue(text, label, defaultValue);
    }

    private static Boolean booleanValue(String value, String label, Boolean defaultValue) {
        String text = text(value);
        if (text == null) return defaultValue;
        String normalized = text.toLowerCase(Locale.ROOT);
        if (List.of("1", "true", "是", "启用", "正常").contains(normalized)) return true;
        if (List.of("0", "false", "否", "停用").contains(normalized)) return false;
        throw new BusinessException(label + "只能填写 0/1、是/否 或 true/false");
    }

    private static String required(String value, String label) {
        String text = text(value);
        if (text == null) throw new BusinessException(label + "不能为空");
        return text;
    }

    private static String text(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private static String errorMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && (current.getMessage() == null || current.getMessage().isBlank())) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank() ? "导入失败" : current.getMessage();
    }

    private static void requireCreate(Long id) {
        if (id != null) {
            throw new BusinessException("新增请求不能携带 ID");
        }
    }

    private static void requireEdit(Long id) {
        if (id == null) {
            throw new BusinessException("修改请求必须携带 ID");
        }
    }
}
