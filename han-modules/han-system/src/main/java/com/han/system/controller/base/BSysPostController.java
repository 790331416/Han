package com.han.system.controller.base;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.system.domain.dto.SysPostDto;
import com.han.system.domain.po.SysPostPo;
import com.han.system.domain.query.SysPostQuery;
import com.han.system.service.ISysPostService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 岗位管理 - B层（基础控制器）
 */
public class BSysPostController {

    @Autowired
    protected ISysPostService postService;

    protected String getNodeName() {
        return "岗位管理";
    }

    public R<PageResult<SysPostPo>> list(SysPostQuery query) {
        return R.ok(postService.selectPostPage(query));
    }

    public R<List<SysPostPo>> listAll() {
        SysPostQuery query = new SysPostQuery();
        query.setStatus(0);
        return R.ok(postService.selectPostList(query));
    }

    public R<SysPostPo> getInfo(Long postId) {
        return R.ok(postService.selectPostById(postId));
    }

    public R<Void> add(SysPostDto dto) {
        postService.insertPost(dto);
        return R.ok();
    }

    public R<Void> edit(SysPostDto dto) {
        postService.updatePost(dto);
        return R.ok();
    }

    public R<Void> remove(Long postId) {
        postService.deletePostById(postId);
        return R.ok();
    }
}
