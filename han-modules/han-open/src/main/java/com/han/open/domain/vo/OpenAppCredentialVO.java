package com.han.open.domain.vo;

/** 新建开放应用时仅返回一次的客户端凭据，禁止持久化或写入操作日志。 */
public record OpenAppCredentialVO(Long appId, String appKey, String appSecret) {
}
