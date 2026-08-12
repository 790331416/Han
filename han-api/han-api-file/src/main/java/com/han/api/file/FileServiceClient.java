package com.han.api.file;

import com.han.api.file.domain.FileBase64DTO;
import com.han.api.file.domain.FileDTO;
import com.han.common.core.domain.R;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 文件服务内部调用契约。
 *
 * <p><b>幂等性与重试</b>：{@link #loadBase64} 是只读 GET，幂等可重试；
 * {@link #upload} 非幂等（每次调用产生一条新的 {@code sys_file} 记录与一个新对象），
 * <b>禁止自动重试</b>，否则会留下孤儿文件并把租户存储配额算重。
 *
 * <p><b>租户归属</b>：{@link #upload} 落库时的 {@code tenant_id} / {@code create_by} 取自
 * 服务端上下文。服务间调用必须由底座透传经签名覆盖的 {@code X-Tenant-Id} / {@code X-User-Id}
 * 请求头，否则记录会退化成平台级（tenant_id = 0、create_by 为空），租户配额与审计追溯全部失真。
 */
@HttpExchange
public interface FileServiceClient {

    /**
     * 文件上传。
     *
     * <p>非幂等，不得重试。
     */
    @PostExchange(value = "/file/upload", contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
    R<FileDTO> upload(@RequestPart("file") Resource file);

    /**
     * 按文件ID读取 Base64 内容（内部接口，多模态图片注入等场景）。
     *
     * <p><b>越权拦截责任在提供方</b>：han-file 必须在查询条件里带上租户维度
     * （命中不到就返回「文件不存在」），不能把 {@code tenant_id} 查出来塞进
     * {@link FileBase64DTO} 再让调用方自己判 —— 那样任何新调用方漏判就是跨租户读文件。
     * 租户身份取自签名覆盖的 {@code X-Tenant-Id} 请求头，不接受调用方以参数自报。
     */
    @GetExchange("/inner/file/base64/{fileId}")
    R<FileBase64DTO> loadBase64(@PathVariable("fileId") Long fileId);
}
