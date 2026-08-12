import service from '@/utils/request'

/**
 * 长耗时下载的默认超时。
 *
 * 请求层全局超时是 30 秒，对大数据量的 Excel 导出、代码生成打包偏紧，
 * 用户看到的会是「请求超时」而不是真实原因。
 */
const DOWNLOAD_TIMEOUT_MS = 120000

export interface DownloadOptions {
  /** 查询参数。 */
  params?: Record<string, any>
  /** 文件名（不含后缀），缺省时从 Content-Disposition 解析。 */
  fileName?: string
  /** 文件后缀，不含点。 */
  extension?: string
  /** Blob MIME 类型。 */
  mimeType?: string
  /** 覆盖默认超时。 */
  timeout?: number
}

const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

/**
 * 通用二进制下载。
 *
 * 错误体识别在请求拦截层已经统一做掉了：后端返回 R 包装的 JSON 错误时
 * 这里根本不会走到，调用方会直接拿到 reject。
 */
export async function downloadBlob(url: string, options: DownloadOptions = {}) {
  const {
    params,
    fileName,
    extension = 'xlsx',
    mimeType = XLSX_MIME,
    timeout = DOWNLOAD_TIMEOUT_MS
  } = options

  const response = await service({
    url,
    method: 'GET',
    params,
    responseType: 'blob',
    timeout
  })

  const blob = new Blob([response.data], { type: mimeType })
  const name = fileName || extractFileName(response, extension) || '导出数据'
  const objectUrl = URL.createObjectURL(blob)

  /**
   * anchor 必须挂到 DOM 再点击，`revokeObjectURL` 也不能紧跟 `click()` 同步执行。
   * 游离节点点击 + 同步 revoke 在 Firefox / Safari 上是已知会导致下载失败或空文件的写法。
   */
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = `${name}.${extension}`
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
}

/**
 * Excel 导出，`downloadBlob` 的 xlsx 预设薄封装。
 *
 * @param url 后端导出接口地址
 * @param params 查询参数
 * @param fileName 下载文件名（不含后缀）
 */
export async function downloadExcel(url: string, params?: Record<string, any>, fileName?: string) {
  return downloadBlob(url, { params, fileName, extension: 'xlsx', mimeType: XLSX_MIME })
}

function extractFileName(response: any, extension: string): string | null {
  const disposition = response.headers?.['content-disposition']
  if (!disposition) return null
  const match = disposition.match(/filename\*?=(?:UTF-8''|"?)([^";]+)/i)
  if (match) {
    const decoded = decodeURIComponent(match[1])
    return decoded.replace(new RegExp(`\\.${extension}$`, 'i'), '')
  }
  return null
}
