package com.han.api.file;

import com.han.api.file.domain.FileBase64DTO;
import com.han.api.file.domain.FileDTO;
import com.han.api.file.domain.FileInfoDTO;
import com.han.common.core.domain.R;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 文件服务内部调用契约。
 *
 * <p><b>租户归属</b>：服务间调用当前不透传登录上下文，涉及归属的接口都带 {@code tenantId}
 * 参数由调用方显式声明，文件服务按声明值强制校验（{@code FileStorageAccessService#requireTenantAccess}）。
 * 注意校验只在调用方声明了租户时才生效：<b>传 {@code null} 的重载等于不做跨租户校验</b>，
 * 上传时还会把文件落成平台级（{@code tenant_id = 0}，任何租户可读）。
 * 凡是租户私有的内容（知识库原文、业务附件、会话图片）必须走带租户ID的重载。
 * 待上下文透传（S-08）落地后，这里会改成以内部签名覆盖的 {@code X-Tenant-Id} 请求头为准，
 * 不再接受调用方以参数自报。
 *
 * <p><b>幂等性与重试</b>：{@link #loadInfo}、{@link #loadBase64}、{@link #download} 是只读 GET，
 * 幂等可重试；{@link #upload} 非幂等（每次调用产生一条新的 {@code sys_file} 记录与一个新对象），
 * <b>禁止自动重试</b>，否则会留下孤儿文件并把租户存储配额算重。
 */
@HttpExchange
public interface FileServiceClient {

    /**
     * 文件上传（不声明归属租户，落为平台级文件）。
     *
     * <p>非幂等，不得重试。
     */
    default R<FileDTO> upload(Resource file) {
        return upload(file, null);
    }

    /**
     * 文件上传并声明归属租户
     */
    @PostExchange(value = "/inner/file/upload", contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
    R<FileDTO> upload(@RequestPart("file") Resource file, @RequestParam("tenantId") Long tenantId);

    /**
     * 按文件ID读取元信息（内部接口）
     */
    @GetExchange("/inner/file/info/{fileId}")
    R<FileInfoDTO> loadInfo(@PathVariable("fileId") Long fileId, @RequestParam("tenantId") Long tenantId);

    /**
     * 按文件ID读取 Base64 内容（不声明租户，<b>跳过跨租户校验</b>，仅限平台级文件）。
     *
     * <p>越权拦截责任在提供方：{@link FileBase64DTO} 里回传的 {@code tenantId} 只作参考，
     * 调用方不该拿它自己判归属 —— 任何新调用方漏判就是跨租户读文件。
     * 租户私有内容请改用 {@link #loadBase64(Long, Long)}。
     *
     * <p>受服务端 {@code han.file.base64-max-size} 上限约束，超限会直接拒绝；
     * 大文件请改用 {@link #download(Long, Long)}。
     */
    default R<FileBase64DTO> loadBase64(Long fileId) {
        return loadBase64(fileId, null);
    }

    /**
     * 按文件ID读取 Base64 内容并声明归属租户（内部接口，多模态图片注入等场景）。
     */
    @GetExchange("/inner/file/base64/{fileId}")
    R<FileBase64DTO> loadBase64(@PathVariable("fileId") Long fileId, @RequestParam("tenantId") Long tenantId);

    /**
     * 按文件ID取回原文（内部接口，知识库原文取回等大文件场景，不受 Base64 上限约束）
     */
    @GetExchange("/inner/file/download/{fileId}")
    Resource download(@PathVariable("fileId") Long fileId, @RequestParam("tenantId") Long tenantId);
}
