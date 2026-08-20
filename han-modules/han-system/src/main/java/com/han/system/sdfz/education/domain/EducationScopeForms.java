package com.han.system.sdfz.education.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 教育管理员范围授权的管理端写入模型。 */
public final class EducationScopeForms {
    private EducationScopeForms() {
    }

    public record Item(
            @NotBlank @Size(max = 16) String scopeType,
            @NotNull Long scopeId,
            boolean includeChildren,
            @Size(max = 500) String remark) {
    }

    public record Replace(
            @NotNull Long userId,
            @NotNull List<@Valid Item> items) {
    }
}
