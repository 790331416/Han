import service from '@/utils/request'

/**
 * 通用文件下载（Excel 导出）
 * @param url 后端导出接口地址
 * @param params 查询参数
 * @param fileName 下载文件名（不含后缀）
 */
export async function downloadExcel(url: string, params?: Record<string, any>, fileName?: string) {
  const response = await service({
    url,
    method: 'GET',
    params,
    responseType: 'blob'
  })
  const blob = new Blob([response.data], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  })
  const name = fileName || extractFileName(response) || '导出数据'
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `${name}.xlsx`
  link.click()
  URL.revokeObjectURL(link.href)
}

function extractFileName(response: any): string | null {
  const disposition = response.headers?.['content-disposition']
  if (!disposition) return null
  const match = disposition.match(/filename\*?=(?:UTF-8''|"?)([^";]+)/i)
  if (match) return decodeURIComponent(match[1].replace(/\.xlsx$/i, ''))
  return null
}
