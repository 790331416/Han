package com.han.aivideo.service.impl;

import com.google.protobuf.ByteString;
import com.han.aivideo.service.AivideoDirectEditProvider;
import com.han.common.core.exception.BusinessException;
import com.volcengine.service.BaseServiceImpl;
import com.volcengine.service.base.model.base.ResponseError;
import com.volcengine.service.base.model.base.ResponseMetadata;
import com.volcengine.service.vod.IVodService;
import com.volcengine.service.vod.impl.VodServiceImpl;
import com.volcengine.service.vod.model.business.GetDirectEditResult;
import com.volcengine.service.vod.model.business.VodPlayInfo;
import com.volcengine.service.vod.model.request.VodGetDirectEditProgressRequest;
import com.volcengine.service.vod.model.request.VodGetDirectEditResultRequest;
import com.volcengine.service.vod.model.request.VodGetPlayInfoRequest;
import com.volcengine.service.vod.model.request.VodSubmitDirectEditTaskAsyncRequest;
import com.volcengine.service.vod.model.response.VodGetDirectEditProgressResponse;
import com.volcengine.service.vod.model.response.VodGetDirectEditResultResponse;
import com.volcengine.service.vod.model.response.VodGetPlayInfoResponse;
import com.volcengine.service.vod.model.response.VodSubmitDirectEditTaskAsyncResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * Volcengine VOD direct edit provider.
 */
@Component
public class AivideoVolcDirectEditProvider implements AivideoDirectEditProvider {

    private static final String DEFAULT_REGION = "cn-north-1";
    private static final String DEFAULT_APPLICATION = "VideoTrackHighlight";

    private final String accessKey;
    private final String secretKey;
    private final String space;
    private final String application;
    private final String region;
    private final IVodService vodService;

    public AivideoVolcDirectEditProvider(
            @Value("${volcengine.vod.access-key:${VOLCENGINE_VOD_ACCESS_KEY_ID:}}") String accessKey,
            @Value("${volcengine.vod.secret-key:${VOLCENGINE_VOD_SECRET_ACCESS_KEY:}}") String secretKey,
            @Value("${volcengine.vod.space:${AIVIDEO_VOD_SPACE:}}") String space,
            @Value("${volcengine.vod.application:${AIVIDEO_VOD_APPLICATION:VideoTrackHighlight}}") String application,
            @Value("${volcengine.vod.region:${AIVIDEO_VOD_REGION:cn-north-1}}") String region) {
        this.accessKey = firstText(accessKey);
        this.secretKey = firstText(secretKey);
        this.space = firstText(space);
        this.application = firstText(application, DEFAULT_APPLICATION);
        this.region = firstText(region, DEFAULT_REGION);
        this.vodService = createVodService(this.region);
    }

    @Override
    public String uploader() {
        return space;
    }

    @Override
    public String application() {
        return application;
    }

    @Override
    public SubmitResult submit(String editParamJson, int priority, String callbackArgs) {
        validateConfigured();
        if (!StringUtils.hasText(editParamJson)) {
            throw new BusinessException("剪辑参数不能为空");
        }
        try {
            VodSubmitDirectEditTaskAsyncRequest request = VodSubmitDirectEditTaskAsyncRequest.newBuilder()
                    .setUploader(space)
                    .setApplication(application)
                    .setEditParam(ByteString.copyFrom(editParamJson, StandardCharsets.UTF_8))
                    .setPriority(priority)
                    .setCallbackArgs(firstText(callbackArgs))
                    .build();
            VodSubmitDirectEditTaskAsyncResponse response = vodService.submitDirectEditTaskAsync(request);
            checkResponseError(response.hasResponseMetadata() ? response.getResponseMetadata() : null);
            if (!response.hasResult() || !StringUtils.hasText(response.getResult().getReqId())) {
                throw new BusinessException("火山剪辑任务未返回 ReqId");
            }
            return new SubmitResult(response.getResult().getReqId());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("提交火山剪辑任务失败: " + exception.getMessage());
        }
    }

    @Override
    public int progress(String providerTaskId) {
        validateConfigured();
        if (!StringUtils.hasText(providerTaskId)) {
            return 0;
        }
        try {
            VodGetDirectEditProgressRequest request = VodGetDirectEditProgressRequest.newBuilder()
                    .setReqId(providerTaskId)
                    .build();
            VodGetDirectEditProgressResponse response = vodService.getDirectEditProgress(request);
            checkResponseError(response.hasResponseMetadata() ? response.getResponseMetadata() : null);
            if (!response.hasResult()) {
                return 1;
            }
            return response.getResult().getResult();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("查询火山剪辑进度失败: " + exception.getMessage());
        }
    }

    @Override
    public EditResult result(String providerTaskId) {
        validateConfigured();
        if (!StringUtils.hasText(providerTaskId)) {
            return null;
        }
        try {
            VodGetDirectEditResultRequest request = VodGetDirectEditResultRequest.newBuilder()
                    .addReqIds(providerTaskId)
                    .build();
            VodGetDirectEditResultResponse response = vodService.getDirectEditResult(request);
            checkResponseError(response.hasResponseMetadata() ? response.getResponseMetadata() : null);
            if (response.getResultCount() <= 0) {
                return null;
            }
            GetDirectEditResult result = response.getResult(0);
            return new EditResult(
                    firstText(result.getReqId(), providerTaskId),
                    firstText(result.getStatus()),
                    firstText(result.getMessage()),
                    firstText(result.getOutputVid())
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("查询火山剪辑结果失败: " + exception.getMessage());
        }
    }

    @Override
    public String playUrl(String outputVid) {
        validateConfigured();
        if (!StringUtils.hasText(outputVid)) {
            return "";
        }
        try {
            VodGetPlayInfoRequest request = VodGetPlayInfoRequest.newBuilder()
                    .setVid(outputVid)
                    .setSsl("1")
                    .setNeedOriginal("1")
                    .setGetAll(true)
                    .build();
            VodGetPlayInfoResponse response = vodService.getPlayInfo(request);
            checkResponseError(response.hasResponseMetadata() ? response.getResponseMetadata() : null);
            if (!response.hasResult()) {
                return "";
            }
            for (VodPlayInfo playInfo : response.getResult().getPlayInfoListList()) {
                String mainUrl = firstText(playInfo.getMainPlayUrl());
                if (StringUtils.hasText(mainUrl)) {
                    return mainUrl;
                }
                String backupUrl = firstText(playInfo.getBackupPlayUrl());
                if (StringUtils.hasText(backupUrl)) {
                    return backupUrl;
                }
            }
            return "";
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("获取火山剪辑成片播放地址失败: " + exception.getMessage());
        }
    }

    private IVodService createVodService(String serviceRegion) {
        try {
            IVodService service = VodServiceImpl.getInstance(serviceRegion);
            if (service instanceof BaseServiceImpl baseService) {
                baseService.setRegion(serviceRegion);
                if (StringUtils.hasText(accessKey)) {
                    baseService.setAccessKey(accessKey);
                }
                if (StringUtils.hasText(secretKey)) {
                    baseService.setSecretKey(secretKey);
                }
            }
            return service;
        } catch (Exception exception) {
            throw new BusinessException("初始化火山 VOD SDK 失败: " + exception.getMessage());
        }
    }

    private void validateConfigured() {
        if (!StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey) || !StringUtils.hasText(space)) {
            throw new BusinessException("火山剪辑 API 未配置完整，请配置 VOLCENGINE_VOD_ACCESS_KEY_ID、VOLCENGINE_VOD_SECRET_ACCESS_KEY、AIVIDEO_VOD_SPACE");
        }
    }

    private void checkResponseError(ResponseMetadata responseMetadata) {
        if (responseMetadata == null || !responseMetadata.hasError()) {
            return;
        }
        ResponseError error = responseMetadata.getError();
        if (error == null || !StringUtils.hasText(error.getCode())) {
            return;
        }
        throw new BusinessException("火山剪辑 API 返回错误(" + error.getCode() + "): " + error.getMessage());
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
