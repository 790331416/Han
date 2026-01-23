package com.xuman.common.mybatis.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 基础实体 - 数据库持久化对象的根类
 * <p>
 * 适用场景：所有需要持久化到数据库的实体类
 * <p>
 * 如需纯 POJO（DTO/VO/API对象），请使用 xuman-common-core 中的 BaseModel
 * 
 * @see com.xuman.common.core.domain.model.BaseModel
 */
@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 删除标志（0存在 1删除） */
    @TableLogic
    private Integer delFlag;

    /** 搜索值（非数据库字段） */
    @TableField(exist = false)
    private String searchValue;

    /** 请求参数（非数据库字段） */
    @TableField(exist = false)
    private Map<String, Object> params;
}
