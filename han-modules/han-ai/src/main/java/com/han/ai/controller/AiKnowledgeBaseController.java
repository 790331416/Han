package com.han.ai.controller;

import com.han.ai.domain.dto.HitTestRequest;
import com.han.ai.domain.po.AiDocumentPo;
import com.han.ai.domain.po.AiKnowledgeBasePo;
import com.han.ai.domain.query.AiDocumentQuery;
import com.han.ai.domain.query.AiKnowledgeBaseQuery;
import com.han.ai.service.IAiKnowledgeBaseService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识库控制器。
 */
@AdminAuth
@RestController
@RequestMapping("/ai/kb")
@RequiredArgsConstructor
public class AiKnowledgeBaseController {

    private final IAiKnowledgeBaseService aiKnowledgeBaseService;

    /**
     * 分页查询知识库列表。
     *
     * @param query query params
     * @return page result
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('ai:kb:list')")
    public R<PageResult<AiKnowledgeBasePo>> list(AiKnowledgeBaseQuery query) {
        return R.ok(aiKnowledgeBaseService.selectPage(query));
    }

    /**
     * 查询知识库详情。
     *
     * @param kbId knowledge base id
     * @return detail
     */
    @GetMapping("/{kbId}")
    @PreAuthorize("@ss.hasAuthority('ai:kb:query')")
    public R<AiKnowledgeBasePo> getInfo(@PathVariable Long kbId) {
        return R.ok(aiKnowledgeBaseService.selectById(kbId));
    }

    /**
     * 查询全部已启用的知识库。
     *
     * @return knowledge base list
     */
    @GetMapping("/all")
    @PreAuthorize("@ss.hasAuthority('ai:kb:list')")
    public R<List<AiKnowledgeBasePo>> listAll() {
        return R.ok(aiKnowledgeBaseService.selectAll());
    }

    /**
     * 新增知识库。
     *
     * @param knowledgeBase knowledge base data
     * @return result
     */
    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('ai:kb:add')")
    public R<Void> add(@Valid @RequestBody AiKnowledgeBasePo knowledgeBase) {
        aiKnowledgeBaseService.insert(knowledgeBase);
        return R.ok();
    }

    /**
     * 修改知识库。
     *
     * @param knowledgeBase knowledge base data
     * @return result
     */
    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('ai:kb:edit')")
    public R<Void> edit(@Valid @RequestBody AiKnowledgeBasePo knowledgeBase) {
        aiKnowledgeBaseService.update(knowledgeBase);
        return R.ok();
    }

    /**
     * 删除知识库。
     *
     * @param kbId knowledge base id
     * @return result
     */
    @RepeatSubmit
    @PostMapping("/remove/{kbId}")
    @PreAuthorize("@ss.hasAuthority('ai:kb:remove')")
    public R<Void> remove(@PathVariable Long kbId) {
        aiKnowledgeBaseService.deleteById(kbId);
        return R.ok();
    }

    /**
     * 查询指定知识库下的文档列表。
     *
     * @param kbId knowledge base id
     * @param query query params
     * @return page result
     */
    @GetMapping("/{kbId}/document/list")
    @PreAuthorize("@ss.hasAuthority('ai:kb:list')")
    public R<PageResult<AiDocumentPo>> listDocuments(@PathVariable Long kbId, AiDocumentQuery query) {
        return R.ok(aiKnowledgeBaseService.selectDocumentPage(kbId, query));
    }

    /**
     * 上传文档到知识库。
     *
     * @param kbId knowledge base id
     * @param file file
     * @return result
     */
    @RepeatSubmit
    @PostMapping("/{kbId}/document/upload")
    @PreAuthorize("@ss.hasAuthority('ai:kb:upload')")
    public R<Void> uploadDocument(@PathVariable Long kbId, @RequestParam("file") MultipartFile file) {
        aiKnowledgeBaseService.uploadDocument(kbId, file);
        return R.ok();
    }

    /**
     * 重建文档索引。
     *
     * @param docId document id
     * @return result
     */
    @RepeatSubmit
    @PostMapping("/document/reindex/{docId}")
    @PreAuthorize("@ss.hasAuthority('ai:kb:edit')")
    public R<Void> reindexDocument(@PathVariable Long docId) {
        aiKnowledgeBaseService.reindexDocument(docId);
        return R.ok();
    }

    /**
     * 删除文档。
     *
     * @param docId document id
     * @return result
     */
    @RepeatSubmit
    @PostMapping("/document/remove/{docId}")
    @PreAuthorize("@ss.hasAuthority('ai:kb:remove')")
    public R<Void> removeDocument(@PathVariable Long docId) {
        aiKnowledgeBaseService.deleteDocument(docId);
        return R.ok();
    }

    /**
     * 对知识库执行命中测试。
     *
     * @param kbId knowledge base id
     * @param request query payload
     * @return hit result list
     */
    @PostMapping("/hit-test/{kbId}")
    @PreAuthorize("@ss.hasAuthority('ai:kb:list')")
    public R<List<Map<String, Object>>> hitTest(@PathVariable Long kbId, @RequestBody HitTestRequest request) {
        return R.ok(aiKnowledgeBaseService.hitTest(kbId, request != null ? request.getQuery() : null));
    }

    /**
     * 查询段落详情（引用来源点击跳转用）。
     *
     * @param paragraphId paragraph id
     * @return paragraph detail
     */
    @GetMapping("/paragraph/{paragraphId}")
    @PreAuthorize("@ss.hasAuthority('ai:kb:list')")
    public R<Map<String, Object>> paragraphDetail(@PathVariable Long paragraphId) {
        return R.ok(aiKnowledgeBaseService.selectParagraphDetail(paragraphId));
    }
}
