package com.han.open.converter;

import com.han.open.domain.po.OpenApiTestRunPo;
import com.han.open.domain.vo.OpenApiTestRunVO;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 调测审计的安全手工转换器。 */
public final class OpenApiTestRunConverter {

    private static final Pattern BYTES = Pattern.compile("(?:^|,)bytes=(\\d+)(?:$|,)");

    private OpenApiTestRunConverter() {
    }

    public static OpenApiTestRunVO toVO(OpenApiTestRunPo source) {
        OpenApiTestRunVO target = new OpenApiTestRunVO();
        target.setId(source.getId());
        target.setAppId(source.getAppId());
        target.setResourceId(source.getResourceId());
        target.setEnvironment(source.getEnvironment());
        target.setRequestMethod(source.getRequestMethod());
        target.setRequestPath(source.getRequestPath());
        target.setStatusCode(source.getStatusCode());
        target.setResult(source.getResult());
        target.setTraceId(source.getTraceId());
        target.setDurationMs(source.getDurationMs());
        target.setResponseSize(readBytes(source.getRedactedSummary()));
        target.setCreateTime(source.getCreateTime());
        return target;
    }

    private static Long readBytes(String summary) {
        if (summary == null) {
            return null;
        }
        Matcher matcher = BYTES.matcher(summary);
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }
}
