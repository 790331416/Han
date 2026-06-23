package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 资产提取请求参数。
 */
@Data
public class AivideoAssetExtractDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /**
     * 当前页面确认的剧本版本 ID，用于避免重新提取时误读旧剧本。
     */
    private Long scriptVersionId;

    /**
     * 是否按当前剧本重建结构资产；重新提取时会清理旧角色、场景、道具和分镜记录。
     */
    private Boolean forceRefresh;

    private String customPrompt;
}
