package com.han.open.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.han.common.core.domain.PageResult;
import com.han.open.domain.po.OpenVendorPo;
import com.han.open.domain.vo.OpenVendorApplicationAdminVO;
import com.han.open.domain.vo.VendorApplicationVO;
import com.han.open.domain.vo.VendorDetailVO;
import com.han.api.open.domain.OpenVendorApplicationCreateDTO;
import com.han.api.open.domain.OpenVendorApplicationStatusVO;
import java.util.List;

/**
 * 厂商主体服务接口
 */
public interface OpenVendorService extends IService<OpenVendorPo> {

    /** 管理端按当前租户分页查询厂商。 */
    PageResult<OpenVendorPo> listPage(String name, Integer status, Integer pageNum, Integer pageSize);

    /** 管理端按当前租户分页查询厂商入驻申请。 */
    PageResult<OpenVendorApplicationAdminVO> listApplicationPage(
            Long vendorId, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 提交厂商入驻申请
     * @param applicationVO 申请信息
     * @return 申请ID
     */
    Long submitApplication(VendorApplicationVO applicationVO);

    /**
     * 审核厂商入驻申请
     * @param applicationId 申请ID
     * @param status 审核状态
     * @param reason 审核原因
     * @return 审核结果
     */
    boolean reviewApplication(Long applicationId, Integer status, String reason);

    /**
     * 获取厂商详情
     * @param vendorId 厂商ID
     * @return 厂商详情
     */
    VendorDetailVO getDetail(Long vendorId);

    /**
     * 关联用户到厂商
     * @param vendorId 厂商ID
     * @param userId 用户ID
     * @param role 角色
     * @return 关联结果
     */
    boolean bindUser(Long vendorId, Long userId, String role);

    /**
     * 仅允许已配置的厂商生命周期状态转换。
     */
    boolean updateStatus(Long vendorId, Integer status, String reason);

    /**
     * 查询用户所属厂商
     * @param userId 用户ID
     * @return 厂商列表
     */
    List<OpenVendorPo> listByUserId(Long userId);

    /** 内部创建厂商入驻申请，账号创建与验证码校验由 han-auth 负责。 */
    String createPortalApplication(OpenVendorApplicationCreateDTO applicationDTO);

    /** 通过申请编号与联系人手机号查询公开申请状态。 */
    OpenVendorApplicationStatusVO queryPublicApplication(String applicationNo, String contactPhone);
}
