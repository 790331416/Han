package com.han.open.converter;

import com.han.open.domain.po.OpenAppPo;
import com.han.open.domain.po.OpenVendorApplicationPo;
import com.han.open.domain.po.OpenVendorPo;
import com.han.open.domain.po.OpenVendorUserPo;
import com.han.open.domain.vo.OpenVendorApplicationAdminVO;
import com.han.open.domain.vo.VendorApplicationVO;
import com.han.open.domain.vo.VendorDetailVO;

import java.util.List;

/**
 * 厂商领域对象转换器。
 *
 * <p>显式列出允许进入管理端 VO 的字段，避免 PO 新增字段后被无意透传；
 * 申请入库也不复制审计、租户或状态字段。</p>
 */
public final class OpenVendorConverter {

    private OpenVendorConverter() {
    }

    public static OpenVendorPo toVendorPo(VendorApplicationVO source) {
        if (source == null) {
            return null;
        }
        OpenVendorPo target = new OpenVendorPo();
        target.setName(source.getName());
        target.setQualificationNo(source.getQualificationNo());
        target.setIndustry(source.getIndustry());
        target.setContactName(source.getContactName());
        target.setContactPhone(source.getContactPhone());
        target.setContactEmail(source.getContactEmail());
        target.setWebsite(source.getWebsite());
        return target;
    }

    public static OpenVendorApplicationAdminVO toApplicationAdminVO(OpenVendorApplicationPo source) {
        if (source == null) {
            return null;
        }
        OpenVendorApplicationAdminVO target = new OpenVendorApplicationAdminVO();
        target.setId(source.getId());
        target.setApplicationId(source.getId());
        target.setVendorId(source.getVendorId());
        target.setApplicantUserId(source.getApplicantUserId());
        target.setApplicationNo(source.getApplicationNo());
        target.setStatus(source.getStatus());
        target.setApplyData(source.getApplyData());
        target.setReason(source.getReason());
        target.setReviewerId(source.getReviewerId());
        target.setReviewTime(source.getReviewTime());
        target.setCreateTime(source.getCreateTime());
        return target;
    }

    public static VendorDetailVO toDetailVO(OpenVendorPo source,
                                            List<VendorDetailVO.VendorUserVO> users,
                                            List<VendorDetailVO.VendorAppVO> apps) {
        if (source == null) {
            return null;
        }
        VendorDetailVO target = new VendorDetailVO();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setQualificationNo(source.getQualificationNo());
        target.setIndustry(source.getIndustry());
        target.setContactName(source.getContactName());
        target.setContactPhone(source.getContactPhone());
        target.setContactEmail(source.getContactEmail());
        target.setWebsite(source.getWebsite());
        target.setStatus(source.getStatus());
        target.setReviewInfo(source.getReviewInfo());
        target.setApplyTime(source.getApplyTime());
        target.setReviewTime(source.getReviewTime());
        target.setUsers(users);
        target.setApps(apps);
        return target;
    }

    public static VendorDetailVO.VendorUserVO toUserVO(OpenVendorUserPo source) {
        if (source == null) {
            return null;
        }
        VendorDetailVO.VendorUserVO target = new VendorDetailVO.VendorUserVO();
        target.setUserId(source.getUserId());
        target.setRole(source.getRole());
        target.setStatus(source.getStatus());
        return target;
    }

    public static VendorDetailVO.VendorAppVO toAppVO(OpenAppPo source) {
        if (source == null) {
            return null;
        }
        VendorDetailVO.VendorAppVO target = new VendorDetailVO.VendorAppVO();
        target.setAppId(source.getId());
        target.setAppName(source.getAppName());
        target.setAppType(source.getAppType());
        target.setLifecycleStatus(source.getLifecycleStatus());
        return target;
    }
}
