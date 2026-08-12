package com.han.gen.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 代码生成 — 业务表配置。
 *
 * <p>包名、模块名、业务名、功能名会一路流进 Java 包声明、Vue import 路径和 ZIP 条目路径，
 * 因此在入口就按 Java 标识符规则收口，不给非法字符进入生成链路的机会。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("gen_table")
public class GenTable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 表名称 */
    private String tableName;

    /** 表描述 */
    private String tableComment;

    /** 生成包路径 */
    @NotBlank(message = "生成包路径不能为空")
    @Size(max = 200, message = "生成包路径长度不能超过 200")
    @Pattern(regexp = "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$",
            message = "生成包路径只能是小写字母开头的点分 Java 包名")
    private String packageName;

    /** 生成模块名 */
    @NotBlank(message = "生成模块名不能为空")
    @Size(max = 50, message = "生成模块名长度不能超过 50")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$",
            message = "生成模块名只能由字母开头的字母、数字、下划线组成")
    private String moduleName;

    /** 生成业务名 */
    @NotBlank(message = "生成业务名不能为空")
    @Size(max = 50, message = "生成业务名长度不能超过 50")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$",
            message = "生成业务名只能由字母开头的字母、数字、下划线组成")
    private String businessName;

    /** 生成功能名 */
    @NotBlank(message = "生成功能名不能为空")
    @Size(max = 50, message = "生成功能名长度不能超过 50")
    @Pattern(regexp = "^[A-Z][A-Za-z0-9]*$",
            message = "生成功能名必须是大写字母开头的类名，如 NoticeRead")
    private String functionName;

    /** 作者 */
    @Size(max = 50, message = "作者长度不能超过 50")
    private String author;

    /** 父菜单ID */
    private Long parentMenuId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 列信息（非DB字段） */
    @TableField(exist = false)
    private List<GenTableColumn> columns;
}
