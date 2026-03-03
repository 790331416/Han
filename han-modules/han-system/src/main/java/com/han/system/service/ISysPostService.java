package com.han.system.service;

import com.han.common.core.domain.PageResult;
import com.han.system.domain.dto.SysPostDto;
import com.han.system.domain.po.SysPostPo;
import com.han.system.domain.query.SysPostQuery;

import java.util.List;

/**
 * 岗位服务接口
 */
public interface ISysPostService {

    /**
     * 分页查询岗位列表
     */
    PageResult<SysPostPo> selectPostPage(SysPostQuery query);

    /**
     * 查询岗位列表（不分页）
     */
    List<SysPostPo> selectPostList(SysPostQuery query);

    /**
     * 根据ID查询岗位
     */
    SysPostPo selectPostById(Long postId);

    /**
     * 新增岗位
     */
    void insertPost(SysPostDto dto);

    /**
     * 修改岗位
     */
    void updatePost(SysPostDto dto);

    /**
     * 删除岗位
     */
    void deletePostById(Long postId);

    /**
     * 批量删除岗位
     */
    void deletePostByIds(List<Long> postIds);

    /**
     * 校验岗位名称唯一
     */
    boolean checkPostNameUnique(String postName, Long postId);

    /**
     * 校验岗位编码唯一
     */
    boolean checkPostCodeUnique(String postCode, Long postId);

    /**
     * 查询岗位下用户数
     */
    long countUserByPostId(Long postId);
}
