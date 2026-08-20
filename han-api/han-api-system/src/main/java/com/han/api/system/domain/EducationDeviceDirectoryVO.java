package com.han.api.system.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 面向已授权第三方应用的设备目录行，不携带设备密钥或视频平台凭据。 */
public record EducationDeviceDirectoryVO(
        Long deviceId,
        String deviceCode,
        String deviceName,
        String deviceType,
        List<String> applicationTypes,
        Long schoolId,
        String schoolName,
        Long roomId,
        String roomName,
        Integer status,
        LocalDateTime updatedAt
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
