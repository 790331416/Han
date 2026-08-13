package com.han.system.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.builtin.SysBuiltinDictRegistry;
import com.han.system.domain.po.SysDictDataPo;
import com.han.system.domain.po.SysDictTypePo;
import com.han.system.mapper.SysDictDataMapper;
import com.han.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端字典接口。
 *
 * <p>负责字典类型、字典值的 CRUD，以及运行期的内置字典补齐。
 */
@AdminAuth
@RestController("adminSysDictController")
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class ASysDictController {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;
    private final SysBuiltinDictRegistry builtinDictRegistry;

    /**
     * 分页查询字典类型。
     */
    @GetMapping("/type/list")
    @PreAuthorize("@ss.hasAuthority('system:dict:list')")
    public R<PageResult<SysDictTypePo>> listType(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "dictName", required = false) String dictName,
            @RequestParam(value = "dictType", required = false) String dictType,
            @RequestParam(value = "status", required = false) Integer status) {
        builtinDictRegistry.ensureBuiltInDictionaries();
        LambdaQueryWrapper<SysDictTypePo> wrapper = new LambdaQueryWrapper<SysDictTypePo>()
                .like(dictName != null && !dictName.isEmpty(), SysDictTypePo::getDictName, dictName)
                .like(dictType != null && !dictType.isEmpty(), SysDictTypePo::getDictType, dictType)
                .eq(status != null, SysDictTypePo::getStatus, status);
        Page<SysDictTypePo> page = dictTypeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal()));
    }

    /**
     * 查询全部字典类型。
     */
    @GetMapping("/type/all")
    @PreAuthorize("@ss.hasAuthority('system:dict:list')")
    public R<List<SysDictTypePo>> listAllTypes() {
        builtinDictRegistry.ensureBuiltInDictionaries();
        return R.ok(dictTypeMapper.selectList(null));
    }

    /**
     * 查询字典类型详情。
     */
    @GetMapping("/type/{dictId}")
    @PreAuthorize("@ss.hasAuthority('system:dict:query')")
    public R<SysDictTypePo> getType(@PathVariable Long dictId) {
        return R.ok(dictTypeMapper.selectById(dictId));
    }

    /**
     * 新增字典类型。
     */
    @RepeatSubmit
    @PostMapping("/type")
    @PreAuthorize("@ss.hasAuthority('system:dict:add')")
    public R<Void> addType(@RequestBody SysDictTypePo dictType) {
        dictTypeMapper.insert(dictType);
        return R.ok();
    }

    /**
     * 修改字典类型。
     */
    @RepeatSubmit
    @PostMapping("/type/edit")
    @PreAuthorize("@ss.hasAuthority('system:dict:edit')")
    public R<Void> editType(@RequestBody SysDictTypePo dictType) {
        dictTypeMapper.updateById(dictType);
        return R.ok();
    }

    /**
     * 删除字典类型。
     */
    @RepeatSubmit
    @PostMapping("/type/remove/{dictId}")
    @PreAuthorize("@ss.hasAuthority('system:dict:remove')")
    public R<Void> removeType(@PathVariable Long dictId) {
        dictTypeMapper.deleteById(dictId);
        return R.ok();
    }

    /**
     * 分页查询字典值。
     *
     * <p>支持按字典类型、字典标签和状态过滤，供字典数据维护页直接使用。
     */
    @GetMapping("/data/list")
    @PreAuthorize("@ss.hasAuthority('system:dict:list')")
    public R<PageResult<SysDictDataPo>> listData(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "dictType", required = false) String dictType,
            @RequestParam(value = "dictLabel", required = false) String dictLabel,
            @RequestParam(value = "status", required = false) Integer status) {
        builtinDictRegistry.ensureBuiltInDictionaries();
        LambdaQueryWrapper<SysDictDataPo> wrapper = new LambdaQueryWrapper<SysDictDataPo>()
                .eq(dictType != null && !dictType.isEmpty(), SysDictDataPo::getDictType, dictType)
                .like(dictLabel != null && !dictLabel.isEmpty(), SysDictDataPo::getDictLabel, dictLabel)
                .eq(status != null, SysDictDataPo::getStatus, status)
                .orderByAsc(SysDictDataPo::getDictSort);
        Page<SysDictDataPo> page = dictDataMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal()));
    }

    /**
     * 按字典类型查询启用中的字典值。
     *
     * <p>全站下拉、标签渲染都走这个接口（前端 {@code utils/dict-options.ts}），
     * 挂权限点会让所有非字典管理员的页面标签失效，因此显式标注豁免而不是收权限。
     */
    @GetMapping("/data/type/{dictType}")
    @PermissionExempt("字典值供全站下拉与标签渲染，已登录即可访问")
    public R<List<SysDictDataPo>> listDataByType(@PathVariable String dictType) {
        builtinDictRegistry.ensureBuiltInDictionaries();
        LambdaQueryWrapper<SysDictDataPo> wrapper = new LambdaQueryWrapper<SysDictDataPo>()
                .eq(SysDictDataPo::getDictType, dictType)
                .eq(SysDictDataPo::getStatus, 0)
                .orderByAsc(SysDictDataPo::getDictSort);
        return R.ok(dictDataMapper.selectList(wrapper));
    }

    /**
     * 查询字典值详情。
     */
    @GetMapping("/data/{dictCode}")
    @PreAuthorize("@ss.hasAuthority('system:dict:query')")
    public R<SysDictDataPo> getData(@PathVariable Long dictCode) {
        return R.ok(dictDataMapper.selectById(dictCode));
    }

    /**
     * 新增字典值。
     */
    @RepeatSubmit
    @PostMapping("/data")
    @PreAuthorize("@ss.hasAuthority('system:dict:add')")
    public R<Void> addData(@RequestBody SysDictDataPo dictData) {
        dictDataMapper.insert(dictData);
        return R.ok();
    }

    /**
     * 修改字典值。
     */
    @RepeatSubmit
    @PostMapping("/data/edit")
    @PreAuthorize("@ss.hasAuthority('system:dict:edit')")
    public R<Void> editData(@RequestBody SysDictDataPo dictData) {
        dictDataMapper.updateById(dictData);
        return R.ok();
    }

    /**
     * 删除字典值。
     */
    @RepeatSubmit
    @PostMapping("/data/remove/{dictCode}")
    @PreAuthorize("@ss.hasAuthority('system:dict:remove')")
    public R<Void> removeData(@PathVariable Long dictCode) {
        dictDataMapper.deleteById(dictCode);
        return R.ok();
    }
}
