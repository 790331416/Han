package com.han.system.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.system.domain.po.SysDictDataPo;
import com.han.system.domain.po.SysDictTypePo;
import com.han.system.mapper.SysDictDataMapper;
import com.han.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AdminAuth
@RestController("adminSysDictController")
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class ASysDictController {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    // ==================== 字典类型 ====================

    @GetMapping("/type/list")
    @PreAuthorize("@ss.hasAuthority('system:dict:list')")
    public R<PageResult<SysDictTypePo>> listType(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "dictName", required = false) String dictName,
            @RequestParam(value = "dictType", required = false) String dictType) {
        LambdaQueryWrapper<SysDictTypePo> wrapper = new LambdaQueryWrapper<SysDictTypePo>()
                .like(dictName != null && !dictName.isEmpty(), SysDictTypePo::getDictName, dictName)
                .like(dictType != null && !dictType.isEmpty(), SysDictTypePo::getDictType, dictType);
        Page<SysDictTypePo> page = dictTypeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal()));
    }

    @GetMapping("/type/all")
    public R<List<SysDictTypePo>> listAllTypes() {
        return R.ok(dictTypeMapper.selectList(null));
    }

    @GetMapping("/type/{dictId}")
    @PreAuthorize("@ss.hasAuthority('system:dict:query')")
    public R<SysDictTypePo> getType(@PathVariable Long dictId) {
        return R.ok(dictTypeMapper.selectById(dictId));
    }

    @PostMapping("/type")
    @PreAuthorize("@ss.hasAuthority('system:dict:add')")
    public R<Void> addType(@RequestBody SysDictTypePo dictType) {
        dictTypeMapper.insert(dictType);
        return R.ok();
    }

    @PostMapping("/type/edit")
    @PreAuthorize("@ss.hasAuthority('system:dict:edit')")
    public R<Void> editType(@RequestBody SysDictTypePo dictType) {
        dictTypeMapper.updateById(dictType);
        return R.ok();
    }

    @PostMapping("/type/remove/{dictId}")
    @PreAuthorize("@ss.hasAuthority('system:dict:remove')")
    public R<Void> removeType(@PathVariable Long dictId) {
        dictTypeMapper.deleteById(dictId);
        return R.ok();
    }

    // ==================== 字典数据 ====================

    @GetMapping("/data/list")
    @PreAuthorize("@ss.hasAuthority('system:dict:list')")
    public R<PageResult<SysDictDataPo>> listData(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "dictType", required = false) String dictType) {
        LambdaQueryWrapper<SysDictDataPo> wrapper = new LambdaQueryWrapper<SysDictDataPo>()
                .eq(dictType != null && !dictType.isEmpty(), SysDictDataPo::getDictType, dictType)
                .orderByAsc(SysDictDataPo::getDictSort);
        Page<SysDictDataPo> page = dictDataMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal()));
    }

    @GetMapping("/data/type/{dictType}")
    public R<List<SysDictDataPo>> listDataByType(@PathVariable String dictType) {
        LambdaQueryWrapper<SysDictDataPo> wrapper = new LambdaQueryWrapper<SysDictDataPo>()
                .eq(SysDictDataPo::getDictType, dictType)
                .eq(SysDictDataPo::getStatus, 0)
                .orderByAsc(SysDictDataPo::getDictSort);
        return R.ok(dictDataMapper.selectList(wrapper));
    }

    @GetMapping("/data/{dictCode}")
    @PreAuthorize("@ss.hasAuthority('system:dict:query')")
    public R<SysDictDataPo> getData(@PathVariable Long dictCode) {
        return R.ok(dictDataMapper.selectById(dictCode));
    }

    @PostMapping("/data")
    @PreAuthorize("@ss.hasAuthority('system:dict:add')")
    public R<Void> addData(@RequestBody SysDictDataPo dictData) {
        dictDataMapper.insert(dictData);
        return R.ok();
    }

    @PostMapping("/data/edit")
    @PreAuthorize("@ss.hasAuthority('system:dict:edit')")
    public R<Void> editData(@RequestBody SysDictDataPo dictData) {
        dictDataMapper.updateById(dictData);
        return R.ok();
    }

    @PostMapping("/data/remove/{dictCode}")
    @PreAuthorize("@ss.hasAuthority('system:dict:remove')")
    public R<Void> removeData(@PathVariable Long dictCode) {
        dictDataMapper.deleteById(dictCode);
        return R.ok();
    }
}
