import { get, post } from '@/utils/request'
import type { PageResult, PageQuery } from '@/types'

// ===================== 文件管理（E-filemanage） =====================

export interface SysFile {
  id: string | number
  tenantId?: string | number
  fileName: string
  fileUrl?: string
  fileSize?: number
  fileType?: string
  mimeType?: string
  storageType?: string
  bucket?: string
  createBy?: string | number
  createTime?: string
}

export interface SysFileQuery extends PageQuery {
  fileName?: string
  fileType?: string
  beginTime?: string
  endTime?: string
}

// 文件分页列表
export function listFile(query: SysFileQuery) {
  return get<PageResult<SysFile>>('/file/list', query)
}

// 批量删除文件（软删记录 + 尽力物理删除对象）
export function removeFile(ids: (string | number)[]) {
  return post<number>(`/file/remove/${ids.join(',')}`)
}
