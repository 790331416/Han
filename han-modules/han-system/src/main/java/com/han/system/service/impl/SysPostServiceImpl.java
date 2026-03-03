package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.system.converter.SysPostConverter;
import com.han.system.domain.dto.SysPostDto;
import com.han.system.domain.po.SysPostPo;
import com.han.system.domain.po.SysUserPostPo;
import com.han.system.domain.query.SysPostQuery;
import com.han.system.mapper.SysPostMapper;
import com.han.system.mapper.SysUserPostMapper;
import com.han.system.service.ISysPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 岗位服务实现
 */
@Service
@RequiredArgsConstructor
public class SysPostServiceImpl implements ISysPostService {

    private final SysPostMapper postMapper;
    private final SysUserPostMapper userPostMapper;
    private final SysPostConverter postConverter;

    @Override
    public PageResult<SysPostPo> selectPostPage(SysPostQuery query) {
        Page<SysPostPo> page = postMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                buildQueryWrapper(query)
        );
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public List<SysPostPo> selectPostList(SysPostQuery query) {
        return postMapper.selectList(buildQueryWrapper(query));
    }

    @Override
    public SysPostPo selectPostById(Long postId) {
        return postMapper.selectById(postId);
    }

    @Override
    public void insertPost(SysPostDto dto) {
        validatePost(dto);
        SysPostPo post = postConverter.toPo(dto);
        if (post.getStatus() == null) {
            post.setStatus(0);
        }
        postMapper.insert(post);
    }

    @Override
    public void updatePost(SysPostDto dto) {
        if (dto.getPostId() == null) {
            throw new BusinessException("岗位ID不能为空");
        }
        validatePost(dto);

        SysPostPo post = postMapper.selectById(dto.getPostId());
        if (post == null) {
            throw new BusinessException("岗位不存在");
        }

        postConverter.updatePo(dto, post);
        postMapper.updateById(post);
    }

    @Override
    public void deletePostById(Long postId) {
        long count = countUserByPostId(postId);
        if (count > 0) {
            throw new BusinessException("该岗位已分配" + count + "名用户，不能删除");
        }
        postMapper.deleteById(postId);
    }

    @Override
    public void deletePostByIds(List<Long> postIds) {
        for (Long postId : postIds) {
            long count = countUserByPostId(postId);
            if (count > 0) {
                SysPostPo post = postMapper.selectById(postId);
                String name = post != null ? post.getPostName() : String.valueOf(postId);
                throw new BusinessException("岗位[" + name + "]已分配用户，不能删除");
            }
        }
        postMapper.deleteByIds(postIds);
    }

    @Override
    public boolean checkPostNameUnique(String postName, Long postId) {
        LambdaQueryWrapper<SysPostPo> wrapper = new LambdaQueryWrapper<SysPostPo>()
                .eq(SysPostPo::getPostName, postName);
        if (postId != null) {
            wrapper.ne(SysPostPo::getId, postId);
        }
        return postMapper.selectCount(wrapper) == 0;
    }

    @Override
    public boolean checkPostCodeUnique(String postCode, Long postId) {
        LambdaQueryWrapper<SysPostPo> wrapper = new LambdaQueryWrapper<SysPostPo>()
                .eq(SysPostPo::getPostCode, postCode);
        if (postId != null) {
            wrapper.ne(SysPostPo::getId, postId);
        }
        return postMapper.selectCount(wrapper) == 0;
    }

    @Override
    public long countUserByPostId(Long postId) {
        return userPostMapper.selectCount(
                new LambdaQueryWrapper<SysUserPostPo>().eq(SysUserPostPo::getPostId, postId)
        );
    }

    // ==================== 私有方法 ====================

    private LambdaQueryWrapper<SysPostPo> buildQueryWrapper(SysPostQuery query) {
        return new LambdaQueryWrapper<SysPostPo>()
                .like(query.getPostCode() != null && !query.getPostCode().isEmpty(),
                        SysPostPo::getPostCode, query.getPostCode())
                .like(query.getPostName() != null && !query.getPostName().isEmpty(),
                        SysPostPo::getPostName, query.getPostName())
                .eq(query.getStatus() != null, SysPostPo::getStatus, query.getStatus())
                .orderByAsc(SysPostPo::getPostSort);
    }

    private void validatePost(SysPostDto dto) {
        if (!checkPostNameUnique(dto.getPostName(), dto.getPostId())) {
            throw new BusinessException("岗位名称[" + dto.getPostName() + "]已存在");
        }
        if (!checkPostCodeUnique(dto.getPostCode(), dto.getPostId())) {
            throw new BusinessException("岗位编码[" + dto.getPostCode() + "]已存在");
        }
    }
}
