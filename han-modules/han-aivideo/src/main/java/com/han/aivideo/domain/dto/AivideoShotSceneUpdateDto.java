package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Manual scene binding update for a storyboard shot.
 */
@Data
public class AivideoShotSceneUpdateDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "分镜ID不能为空")
    private Long shotId;

    @NotNull(message = "场景ID不能为空")
    private Long sceneId;
}
