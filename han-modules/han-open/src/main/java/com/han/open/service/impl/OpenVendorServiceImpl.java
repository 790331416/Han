package com.han.open.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.han.common.core.domain.PageResult;
import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.api.open.domain.OpenVendorApplicationCreateDTO;
import com.han.api.open.domain.OpenVendorApplicationStatusVO;
import com.han.api.system.SystemServiceClient;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.converter.OpenVendorConverter;
import com.han.open.domain.po.OpenAppPo;
import com.han.open.domain.po.OpenVendorApplicationPo;
import com.han.open.domain.po.OpenVendorPo;
import com.han.open.domain.po.OpenVendorUserPo;
import com.han.open.domain.vo.VendorApplicationVO;
import com.han.open.domain.vo.VendorDetailVO;
import com.han.open.domain.vo.OpenVendorApplicationAdminVO;
import com.han.open.mapper.OpenAppMapper;
import com.han.open.mapper.OpenVendorApplicationMapper;
import com.han.open.mapper.OpenVendorMapper;
import com.han.open.mapper.OpenVendorUserMapper;
import com.han.open.service.OpenVendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * 厂商主体服务实现
 */
@Service
@RequiredArgsConstructor
public class OpenVendorServiceImpl extends ServiceImpl<OpenVendorMapper, OpenVendorPo> implements OpenVendorService {

    private static final long PLATFORM_TENANT_ID = 1L;
    private static final String PUBLIC_APPLICATION_MARKER = "PUBLIC_PORTAL";
    private static final Set<String> ALLOWED_ROLES = Set.of("OWNER", "DEVELOPER", "VIEWER");
    private static final int STATUS_PENDING = 2;
    private static final int STATUS_APPROVED = 4;
    private static final int STATUS_REJECTED = 5;
    private static final int STATUS_SUSPENDED = 6;
    private static final int STATUS_REVOKED = 7;

    private final OpenVendorUserMapper vendorUserMapper;
    private final OpenVendorApplicationMapper vendorApplicationMapper;
    private final OpenAppMapper appMapper;

    @Autowired
    private SystemServiceClient systemServiceClient;

    @Override
    public PageResult<OpenVendorPo> listPage(String name, Integer status, Integer pageNum, Integer pageSize) {
        Long tenantId = requireTenantId();
        int safePageNum = normalizePageNum(pageNum);
        int safePageSize = normalizePageSize(pageSize);
        LambdaQueryWrapper<OpenVendorPo> wrapper = new LambdaQueryWrapper<OpenVendorPo>()
                .eq(OpenVendorPo::getTenantId, tenantId)
                .eq(OpenVendorPo::getDelFlag, 0)
                .orderByDesc(OpenVendorPo::getCreateTime);
        if (StringUtils.hasText(name)) {
            wrapper.like(OpenVendorPo::getName, name.trim());
        }
        if (status != null) {
            wrapper.eq(OpenVendorPo::getStatus, status);
        }
        Page<OpenVendorPo> page = baseMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), safePageNum, safePageSize);
    }

    @Override
    public PageResult<OpenVendorApplicationAdminVO> listApplicationPage(
            Long vendorId, Integer status, Integer pageNum, Integer pageSize) {
        Long tenantId = requireTenantId();
        int safePageNum = normalizePageNum(pageNum);
        int safePageSize = normalizePageSize(pageSize);
        LambdaQueryWrapper<OpenVendorApplicationPo> wrapper = new LambdaQueryWrapper<OpenVendorApplicationPo>()
                .eq(OpenVendorApplicationPo::getTenantId, tenantId)
                .eq(OpenVendorApplicationPo::getDelFlag, 0)
                .orderByDesc(OpenVendorApplicationPo::getCreateTime);
        if (vendorId != null) {
            wrapper.eq(OpenVendorApplicationPo::getVendorId, vendorId);
        }
        if (status != null) {
            wrapper.eq(OpenVendorApplicationPo::getStatus, status);
        }
        Page<OpenVendorApplicationPo> page = vendorApplicationMapper.selectPage(
                new Page<>(safePageNum, safePageSize), wrapper);
        List<OpenVendorApplicationAdminVO> rows = page.getRecords().stream()
                .map(OpenVendorConverter::toApplicationAdminVO).toList();
        return PageResult.of(rows, page.getTotal(), safePageNum, safePageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitApplication(VendorApplicationVO applicationVO) {
        if (applicationVO == null || applicationVO.getName() == null
                || applicationVO.getQualificationNo() == null
                || applicationVO.getName().isBlank() || applicationVO.getQualificationNo().isBlank()) {
            throw new BusinessException("厂商名称和统一社会信用代码不能为空");
        }
        Long currentUserId = SecurityContextHolder.getUserId();
        if (currentUserId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }

        String name = applicationVO.getName().trim();
        String qualificationNo = applicationVO.getQualificationNo().trim();

        // 名称、资格号均按租户隔离去重；数据库唯一键负责最终并发兜底。
        long count = count(new LambdaQueryWrapper<OpenVendorPo>()
                .eq(OpenVendorPo::getName, name)
                .eq(OpenVendorPo::getTenantId, tenantId));
        if (count > 0) {
            throw new BusinessException("厂商名称已存在");
        }
        count = count(new LambdaQueryWrapper<OpenVendorPo>()
                .eq(OpenVendorPo::getQualificationNo, qualificationNo)
                .eq(OpenVendorPo::getTenantId, tenantId));
        if (count > 0) {
            throw new BusinessException("统一社会信用代码已存在");
        }

        // 创建厂商主体
        OpenVendorPo vendor = OpenVendorConverter.toVendorPo(applicationVO);
        vendor.setName(name);
        vendor.setQualificationNo(qualificationNo);
        vendor.setTenantId(tenantId);
        vendor.setStatus(2); // 待审核
        vendor.setApplyTime(LocalDateTime.now());
        vendor.setCreateBy(currentUserId);
        save(vendor);

        // 申请人自动成为首个所有者；普通绑定入口仍需已有 OWNER/管理员授权。
        bindUserInternal(vendor, currentUserId, "OWNER", false);

        // 创建申请记录
        OpenVendorApplicationPo application = new OpenVendorApplicationPo();
        application.setVendorId(vendor.getId());
        application.setApplicantUserId(currentUserId);
        application.setApplicationNo("VA" + System.currentTimeMillis());
        application.setStatus(1); // 待审核
        application.setReason(applicationVO.getApplyReason());
        application.setCreateBy(currentUserId);
        vendorApplicationMapper.insert(application);

        return application.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createPortalApplication(OpenVendorApplicationCreateDTO applicationDTO) {
        if (applicationDTO == null || applicationDTO.getAccountUserId() == null) {
            throw new BusinessException("厂商账号信息不能为空");
        }
        String name = requiredText(applicationDTO.getName(), "厂商名称不能为空");
        String qualificationNo = requiredText(applicationDTO.getQualificationNo(), "统一社会信用代码不能为空");
        String contactName = requiredText(applicationDTO.getContactName(), "联系人姓名不能为空");
        String contactPhone = requiredText(applicationDTO.getContactPhone(), "联系电话不能为空");
        boolean duplicateName = TenantHelper.ignore(() -> baseMapper.selectCount(new LambdaQueryWrapper<OpenVendorPo>()
                .eq(OpenVendorPo::getTenantId, PLATFORM_TENANT_ID)
                .eq(OpenVendorPo::getName, name)
                .eq(OpenVendorPo::getDelFlag, 0))) > 0;
        boolean duplicateQualification = TenantHelper.ignore(() -> baseMapper.selectCount(new LambdaQueryWrapper<OpenVendorPo>()
                .eq(OpenVendorPo::getTenantId, PLATFORM_TENANT_ID)
                .eq(OpenVendorPo::getQualificationNo, qualificationNo)
                .eq(OpenVendorPo::getDelFlag, 0))) > 0;
        if (duplicateName || duplicateQualification) {
            String existingApplicationNo = findIdempotentPortalApplication(applicationDTO, name, qualificationNo);
            if (existingApplicationNo != null) {
                return existingApplicationNo;
            }
            throw new BusinessException(duplicateName ? "厂商名称已存在" : "统一社会信用代码已存在");
        }

        String applicationNo = UUID.randomUUID().toString().replace("-", "");
        try {
            TenantHelper.ignore(() -> {
                OpenVendorPo vendor = new OpenVendorPo();
                vendor.setTenantId(PLATFORM_TENANT_ID);
                vendor.setName(name);
                vendor.setQualificationNo(qualificationNo);
                vendor.setIndustry(applicationDTO.getIndustry());
                vendor.setContactName(contactName);
                vendor.setContactPhone(contactPhone);
                vendor.setContactEmail(applicationDTO.getContactEmail());
                vendor.setWebsite(applicationDTO.getWebsite());
                vendor.setStatus(STATUS_PENDING);
                vendor.setApplyTime(LocalDateTime.now());
                vendor.setCreateBy(applicationDTO.getAccountUserId());
                baseMapper.insert(vendor);
                bindUserInternal(vendor, applicationDTO.getAccountUserId(), "OWNER", false);

                OpenVendorApplicationPo application = new OpenVendorApplicationPo();
                application.setTenantId(PLATFORM_TENANT_ID);
                application.setVendorId(vendor.getId());
                application.setApplicantUserId(applicationDTO.getAccountUserId());
                application.setApplicationNo(applicationNo);
                application.setStatus(1);
                application.setApplyData(PUBLIC_APPLICATION_MARKER);
                application.setReason(applicationDTO.getApplyReason());
                application.setCreateBy(applicationDTO.getAccountUserId());
                vendorApplicationMapper.insert(application);
            });
        } catch (DataIntegrityViolationException e) {
            String existingApplicationNo = findIdempotentPortalApplication(applicationDTO, name, qualificationNo);
            if (existingApplicationNo != null) {
                return existingApplicationNo;
            }
            throw new BusinessException("厂商名称或统一社会信用代码已存在");
        }
        return applicationNo;
    }

    /** 只有同账号、同 OWNER 绑定、同公开申请才允许在响应丢失后复用申请号。 */
    private String findIdempotentPortalApplication(OpenVendorApplicationCreateDTO dto,
                                                   String name, String qualificationNo) {
        List<OpenVendorPo> candidates = TenantHelper.ignore(() -> baseMapper.selectList(
                new LambdaQueryWrapper<OpenVendorPo>()
                        .eq(OpenVendorPo::getTenantId, PLATFORM_TENANT_ID)
                        .and(wrapper -> wrapper.eq(OpenVendorPo::getName, name)
                                .or().eq(OpenVendorPo::getQualificationNo, qualificationNo))
                        .eq(OpenVendorPo::getDelFlag, 0)
                        .last("LIMIT 5")));
        if (candidates == null) {
            return null;
        }
        for (OpenVendorPo vendor : candidates) {
            Long vendorId = vendor.getId();
            if (vendorId == null || vendorUserMapper.selectCount(new LambdaQueryWrapper<OpenVendorUserPo>()
                    .eq(OpenVendorUserPo::getTenantId, PLATFORM_TENANT_ID)
                    .eq(OpenVendorUserPo::getVendorId, vendorId)
                    .eq(OpenVendorUserPo::getUserId, dto.getAccountUserId())
                    .eq(OpenVendorUserPo::getRole, "OWNER")
                    .eq(OpenVendorUserPo::getStatus, 0)
                    .eq(OpenVendorUserPo::getDelFlag, 0)) == 0) {
                continue;
            }
            OpenVendorApplicationPo application = vendorApplicationMapper.selectOne(
                    new LambdaQueryWrapper<OpenVendorApplicationPo>()
                            .eq(OpenVendorApplicationPo::getTenantId, PLATFORM_TENANT_ID)
                            .eq(OpenVendorApplicationPo::getVendorId, vendorId)
                            .eq(OpenVendorApplicationPo::getApplicantUserId, dto.getAccountUserId())
                            .eq(OpenVendorApplicationPo::getApplyData, PUBLIC_APPLICATION_MARKER)
                            .eq(OpenVendorApplicationPo::getDelFlag, 0)
                            .last("LIMIT 1"));
            if (application != null && StringUtils.hasText(application.getApplicationNo())) {
                return application.getApplicationNo();
            }
        }
        return null;
    }

    @Override
    public OpenVendorApplicationStatusVO queryPublicApplication(String contactPhone) {
        String phone = requiredText(contactPhone, "联系电话不能为空");
        OpenVendorPo vendor = TenantHelper.ignore(() -> baseMapper.selectOne(
                new LambdaQueryWrapper<OpenVendorPo>()
                        .eq(OpenVendorPo::getContactPhone, phone)
                        .eq(OpenVendorPo::getTenantId, PLATFORM_TENANT_ID)
                        .eq(OpenVendorPo::getDelFlag, 0)
                        .orderByDesc(OpenVendorPo::getCreateTime)
                        .last("LIMIT 1")));
        if (vendor == null) {
            throw new BusinessException("申请不存在或校验信息不匹配");
        }
        OpenVendorApplicationPo application = TenantHelper.ignore(() -> vendorApplicationMapper.selectOne(
                new LambdaQueryWrapper<OpenVendorApplicationPo>()
                        .eq(OpenVendorApplicationPo::getVendorId, vendor.getId())
                        .eq(OpenVendorApplicationPo::getTenantId, PLATFORM_TENANT_ID)
                        .eq(OpenVendorApplicationPo::getApplyData, PUBLIC_APPLICATION_MARKER)
                        .eq(OpenVendorApplicationPo::getDelFlag, 0)
                        .orderByDesc(OpenVendorApplicationPo::getCreateTime)
                        .last("LIMIT 1")));
        if (application == null || !PUBLIC_APPLICATION_MARKER.equals(application.getApplyData())) {
            throw new BusinessException("申请不存在或校验信息不匹配");
        }
        OpenVendorApplicationStatusVO result = new OpenVendorApplicationStatusVO();
        result.setApplicationNo(application.getApplicationNo());
        result.setStatus(application.getStatus());
        result.setStatusName(statusName(application.getStatus()));
        result.setReason(application.getReason());
        result.setVendorName(vendor.getName());
        result.setCreateTime(application.getCreateTime());
        result.setReviewTime(application.getReviewTime());
        return result;
    }

    private String statusName(Integer status) {
        return switch (status == null ? 0 : status) {
            case 1 -> "待审核";
            case 2 -> "审核通过";
            case 3 -> "审核驳回";
            default -> "处理中";
        };
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reviewApplication(Long applicationId, Integer status, String reason) {
        requireAdministrator();
        if (status == null || (status != 2 && status != 3)) {
            throw new BusinessException("厂商审核结果不合法");
        }
        OpenVendorApplicationPo application = vendorApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException("申请记录不存在");
        }
        if (!Objects.equals(application.getStatus(), 1)) { // 只能审核待审核的申请
            throw new BusinessException("申请状态不正确，无法审核");
        }

        Long currentUserId = requireCurrentUser();

        // 更新申请状态
        application.setStatus(status);
        application.setReason(reason);
        application.setReviewerId(currentUserId);
        application.setReviewTime(LocalDateTime.now());
        application.setUpdateBy(currentUserId);
        vendorApplicationMapper.updateById(application);

        // 更新厂商状态
        OpenVendorPo vendor = getById(application.getVendorId());
        if (vendor == null) {
            throw new BusinessException("厂商不存在");
        }
        checkTenant(vendor.getTenantId());
        if (!Objects.equals(vendor.getStatus(), STATUS_PENDING)) {
            throw new BusinessException("厂商状态不允许审核");
        }
        vendor.setStatus(status == 2 ? STATUS_APPROVED : STATUS_REJECTED);
        vendor.setReviewInfo(status == 3 ? reason : null);
        vendor.setReviewTime(LocalDateTime.now());
        vendor.setReviewerId(currentUserId);
        vendor.setUpdateBy(currentUserId);
        updateById(vendor);

        if (status == 2 && PUBLIC_APPLICATION_MARKER.equals(application.getApplyData())) {
            R<Void> activation = systemServiceClient.activateOpenVendorAccount(application.getApplicantUserId());
            if (activation == null || activation.getCode() != Constants.SUCCESS) {
                throw new BusinessException("厂商门户账号激活失败");
            }
        }

        return true;
    }

    @Override
    public VendorDetailVO getDetail(Long vendorId) {
        OpenVendorPo accessVendor = requireVendorAccess(vendorId, false);
        OpenVendorPo vendor = accessVendor;

        // 查询关联用户
        List<OpenVendorUserPo> userPos = vendorUserMapper.selectList(new LambdaQueryWrapper<OpenVendorUserPo>()
                .eq(OpenVendorUserPo::getVendorId, vendorId)
                .eq(OpenVendorUserPo::getTenantId, vendor.getTenantId())
                .eq(OpenVendorUserPo::getStatus, 0));
        List<VendorDetailVO.VendorUserVO> users = userPos.stream()
                .map(OpenVendorConverter::toUserVO).collect(Collectors.toList());

        // 查询关联应用
        List<OpenAppPo> appPos = appMapper.selectList(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getVendorId, vendorId)
                .eq(OpenAppPo::getTenantId, vendor.getTenantId())
                .eq(OpenAppPo::getDelFlag, 0));
        List<VendorDetailVO.VendorAppVO> apps = appPos.stream()
                .map(OpenVendorConverter::toAppVO).collect(Collectors.toList());
        return OpenVendorConverter.toDetailVO(vendor, users, apps);
    }

    @Override
    public boolean bindUser(Long vendorId, Long userId, String role) {
        if (vendorId == null || userId == null) {
            throw new BusinessException("厂商ID和用户ID不能为空");
        }
        normalizeRole(role);
        OpenVendorPo vendor = requireVendorAccess(vendorId, true);
        requireCurrentUser();
        return bindUserInternal(vendor, userId, role, true);
    }

    private boolean bindUserInternal(OpenVendorPo vendor, Long userId, String role, boolean checkOperator) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        String normalizedRole = normalizeRole(role);
        if (checkOperator && !isAdministrator() && !isOwner(vendor.getId(), SecurityContextHolder.getUserId())) {
            throw new BusinessException("仅厂商所有者或管理员可绑定用户");
        }
        // 检查是否已关联
        OpenVendorUserPo exist = vendorUserMapper.selectOne(new LambdaQueryWrapper<OpenVendorUserPo>()
                .eq(OpenVendorUserPo::getVendorId, vendor.getId())
                .eq(OpenVendorUserPo::getUserId, userId)
                .eq(OpenVendorUserPo::getTenantId, vendor.getTenantId()));
        if (exist != null) {
            exist.setRole(normalizedRole);
            exist.setStatus(0);
            exist.setUpdateTime(LocalDateTime.now());
            return vendorUserMapper.updateById(exist) > 0;
        } else {
            OpenVendorUserPo user = new OpenVendorUserPo();
            user.setTenantId(vendor.getTenantId());
            user.setVendorId(vendor.getId());
            user.setUserId(userId);
            user.setRole(normalizedRole);
            user.setStatus(0);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            return vendorUserMapper.insert(user) > 0;
        }
    }

    @Override
    public List<OpenVendorPo> listByUserId(Long userId) {
        Long currentUserId = requireCurrentUser();
        if (!isAdministrator() && !Objects.equals(currentUserId, userId)) {
            throw new BusinessException("无权查询其他用户所属厂商");
        }
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        List<Long> vendorIds = vendorUserMapper.selectList(new LambdaQueryWrapper<OpenVendorUserPo>()
                        .eq(OpenVendorUserPo::getUserId, userId)
                        .eq(OpenVendorUserPo::getTenantId, tenantId)
                        .eq(OpenVendorUserPo::getStatus, 0))
                .stream()
                .map(OpenVendorUserPo::getVendorId)
                .collect(Collectors.toList());
        if (vendorIds.isEmpty()) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<OpenVendorPo>()
                .in(OpenVendorPo::getId, vendorIds)
                .eq(OpenVendorPo::getTenantId, tenantId)
                .eq(OpenVendorPo::getDelFlag, 0));
    }

    @Override
    public boolean updateStatus(Long vendorId, Integer status, String reason) {
        requireAdministrator();
        OpenVendorPo vendor = requireVendorAccess(vendorId, true);
        if (!isLegalStatusTransition(vendor.getStatus(), status)) {
            throw new BusinessException("厂商状态转换不合法");
        }
        vendor.setStatus(status);
        vendor.setReviewInfo(reason);
        vendor.setReviewerId(requireCurrentUser());
        vendor.setReviewTime(LocalDateTime.now());
        vendor.setUpdateBy(SecurityContextHolder.getUserId());
        return updateById(vendor);
    }

    private OpenVendorPo requireVendorAccess(Long vendorId, boolean manage) {
        OpenVendorPo vendor = getById(vendorId);
        if (vendor == null) {
            throw new BusinessException("厂商不存在");
        }
        checkTenant(vendor.getTenantId());
        if (!isAdministrator() && (!isVendorMember(vendorId, SecurityContextHolder.getUserId())
                || (manage && !isOwner(vendorId, SecurityContextHolder.getUserId())))) {
            throw new BusinessException(manage ? "仅厂商所有者或管理员可操作" : "无权查看该厂商");
        }
        return vendor;
    }

    private boolean isOwner(Long vendorId, Long userId) {
        return vendorUserMapper.selectCount(new LambdaQueryWrapper<OpenVendorUserPo>()
                .eq(OpenVendorUserPo::getVendorId, vendorId)
                .eq(OpenVendorUserPo::getUserId, userId)
                .eq(OpenVendorUserPo::getTenantId, SecurityContextHolder.getTenantId())
                .eq(OpenVendorUserPo::getRole, "OWNER")
                .eq(OpenVendorUserPo::getStatus, 0)) > 0;
    }

    private boolean isVendorMember(Long vendorId, Long userId) {
        return userId != null && vendorUserMapper.selectCount(new LambdaQueryWrapper<OpenVendorUserPo>()
                .eq(OpenVendorUserPo::getVendorId, vendorId)
                .eq(OpenVendorUserPo::getUserId, userId)
                .eq(OpenVendorUserPo::getTenantId, SecurityContextHolder.getTenantId())
                .eq(OpenVendorUserPo::getStatus, 0)) > 0;
    }

    private String normalizeRole(String role) {
        if (role == null || !ALLOWED_ROLES.contains(role.trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessException("厂商用户角色不合法");
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isLegalStatusTransition(Integer current, Integer target) {
        if (current == null || target == null || Objects.equals(current, target)) {
            return false;
        }
        return switch (current) {
            case STATUS_PENDING -> target == 3 || target == STATUS_APPROVED || target == STATUS_REJECTED;
            case STATUS_APPROVED -> target == STATUS_SUSPENDED || target == STATUS_REVOKED;
            case STATUS_SUSPENDED -> target == STATUS_APPROVED || target == STATUS_REVOKED;
            default -> false;
        };
    }

    private Long requireCurrentUser() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        return userId;
    }

    private void requireAdministrator() {
        requireCurrentUser();
        if (!isAdministrator()) {
            throw new BusinessException("仅管理员可审核厂商");
        }
    }

    private boolean isAdministrator() {
        LoginUser user = SecurityContextHolder.getLoginUser();
        return user != null && (user.isAdmin() || user.hasRole("admin") || user.hasRole("tenantAdmin"));
    }

    private void checkTenant(Long vendorTenantId) {
        Long currentTenantId = SecurityContextHolder.getTenantId();
        if (currentTenantId != null && !Objects.equals(vendorTenantId, currentTenantId)) {
            throw new BusinessException("无权操作其他租户数据");
        }
        if (currentTenantId == null && !isAdministrator()) {
            throw new BusinessException("获取当前租户信息失败");
        }
    }

    private Long requireTenantId() {
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        return tenantId;
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }
}
