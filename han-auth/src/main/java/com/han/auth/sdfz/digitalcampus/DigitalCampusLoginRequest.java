package com.han.auth.sdfz.digitalcampus;

/**
 * 数字校园登录参数。多身份账号必须显式传入 identityId。
 */
public record DigitalCampusLoginRequest(String identityId) {
}
