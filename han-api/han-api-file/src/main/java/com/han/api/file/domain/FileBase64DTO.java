package com.han.api.file.domain;

import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文件 Base64 内容传输对象（服务间读取文件字节用，如多模态图片注入）。
 */
@Data
public class FileBase64DTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 归属租户ID（0 表示平台级/无租户）。
     *
     * <p>这是提供方回传的归属信息，<b>不是</b>越权拦截手段。租户校验必须由 han-file 在
     * 查询时完成（见 {@code FileServiceClient#loadBase64} 的契约说明），调用方拿到本字段
     * 只用于审计与展示。
     */
    private Long tenantId;

    /**
     * 文件名
     */
    private String name;

    /**
     * MIME 类型（如 image/png）
     */
    private String mimeType;

    /**
     * 公开访问地址
     */
    private String url;

    /**
     * Base64 编码内容（不含 data: 前缀）。
     *
     * <p>体积敏感：Base64 让载荷膨胀约 33%，收发两侧还各要一份 JSON 缓冲，
     * 峰值堆占用约为原文件的 4 倍。提供方必须设置单文件上限并在超限时明确报错，
     * 不要一次性 readAllBytes 大文件。已排除出 {@code toString()}。
     */
    @ToString.Exclude
    private String base64;
}
