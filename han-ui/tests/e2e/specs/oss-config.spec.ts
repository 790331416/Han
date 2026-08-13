import fs from 'node:fs'
import path from 'node:path'
import type { APIRequestContext } from '@playwright/test'
import { test, expect, e2eRuntime } from '../fixtures/test'

const OSS_CONFIG_E2E_PREFIX = '中文OSS配置回归'
const OSS_UPLOAD_FILE_PATH = path.resolve(process.cwd(), 'tests/e2e/fixtures/files/oss-upload.txt')

interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

interface OssConfigRecord {
  ossConfigId: number
  configKey: string
  status: string
}

test.describe('OSS 配置页面', () => {
  test('medium 环境应展示 OSS 配置入口并加载页面', async ({ authenticatedPage, request, authSession }) => {
    const listResponse = await request.get(`${e2eRuntime.apiBaseUrl}/system/oss/config/list?pageNum=1&pageSize=10`, {
      headers: authHeaders(authSession.accessToken)
    })
    const listEnvelope = (await listResponse.json()) as ApiEnvelope<{ rows: unknown[]; total: number }>
    expect(listEnvelope.code).toBe(200)

    await authenticatedPage.goto('/system/oss-config')
    await authenticatedPage.waitForURL('**/system/oss-config')
    await authenticatedPage.waitForLoadState('networkidle')

    await expect(authenticatedPage.getByTestId('oss-config-page')).toBeVisible()
    await expect(authenticatedPage.getByTestId('oss-config-table')).toBeVisible()
    await expect(authenticatedPage.getByTestId('oss-config-add-button')).toBeVisible()
    await expect(authenticatedPage.getByTestId('sidebar-menu-ossconfig')).toBeVisible()
  })

  test('medium/full 环境应支持 OSS 配置启用并完成真实上传', async ({ authenticatedPage, request, authSession }) => {
    const uniqueSuffix = Date.now()
    const configKey = `${OSS_CONFIG_E2E_PREFIX}-${uniqueSuffix}`
    const fileName = `oss-upload-${uniqueSuffix}.txt`

    await cleanupOssConfigsByPrefix(request, authSession.accessToken, OSS_CONFIG_E2E_PREFIX)

    try {
      const createResponse = await request.post(`${e2eRuntime.apiBaseUrl}/system/oss/config`, {
        headers: authHeaders(authSession.accessToken),
        data: {
          configKey,
          accessKey: 'e2e-access-key',
          // 不要用真实口令做测试夹具：han@2026 已泄漏，仓库里出现一次就会被
          // check_deploy_secrets 判为明文凭据，也会误导读者以为这是有效凭据。
          secretKey: 'e2e-secret-key',
          bucketName: 'han',
          prefix: '',
          endpoint: 'http://rustfs:9000',
          region: 'us-east-1',
          isHttps: '1',
          status: '0',
          remark: 'OSS 上传回归验证'
        }
      })
      const createEnvelope = (await createResponse.json()) as ApiEnvelope<null>
      expect(createEnvelope.code).toBe(200)

      await expect
        .poll(async () => Boolean(await findOssConfigByKey(request, authSession.accessToken, configKey)))
        .toBeTruthy()
      const createdConfig = await findOssConfigByKey(request, authSession.accessToken, configKey)
      expect(createdConfig).not.toBeNull()

      await authenticatedPage.goto('/system/oss-config')
      await authenticatedPage.waitForURL('**/system/oss-config')
      await authenticatedPage.waitForLoadState('networkidle')
      await expect(authenticatedPage.getByTestId('oss-config-table')).toContainText(configKey)

      const uploadResponse = await request.post(`${e2eRuntime.apiBaseUrl}/file/upload`, {
        headers: authHeaders(authSession.accessToken),
        multipart: {
          file: {
            name: fileName,
            mimeType: 'text/plain',
            buffer: fs.readFileSync(OSS_UPLOAD_FILE_PATH)
          }
        }
      })
      const uploadEnvelope = (await uploadResponse.json()) as ApiEnvelope<{ name: string; url: string }>
      expect(uploadEnvelope.code).toBe(200)
      expect(uploadEnvelope.data.name).toMatch(/^[a-f0-9-]+\.txt$/)
      expect(uploadEnvelope.data.url).toContain('/file/public/')
      expect(uploadEnvelope.data.url).toContain(uploadEnvelope.data.name)

      const publicResponse = await request.get(uploadEnvelope.data.url)
      expect(publicResponse.ok()).toBeTruthy()
      const publicText = await publicResponse.text()
      expect(publicText).toContain('这是 OSS 上传回归夹具文件。')

      const deleteResponse = await request.post(
        `${e2eRuntime.apiBaseUrl}/system/oss/config/remove/${createdConfig!.ossConfigId}`,
        { headers: authHeaders(authSession.accessToken) }
      )
      const deleteEnvelope = (await deleteResponse.json()) as ApiEnvelope<null>
      expect(deleteEnvelope.code).toBe(200)

      await expect
        .poll(async () => findOssConfigByKey(request, authSession.accessToken, configKey))
        .toBeNull()
    } finally {
      await cleanupOssConfigsByPrefix(request, authSession.accessToken, OSS_CONFIG_E2E_PREFIX)
    }
  })
})

function authHeaders(accessToken: string) {
  return {
    Authorization: `Bearer ${accessToken}`
  }
}

async function listOssConfigs(request: APIRequestContext, accessToken: string, configKey?: string): Promise<OssConfigRecord[]> {
  const params = new URLSearchParams({
    pageNum: '1',
    pageSize: '100'
  })
  if (configKey) {
    params.set('configKey', configKey)
  }

  const response = await request.get(`${e2eRuntime.apiBaseUrl}/system/oss/config/list?${params.toString()}`, {
    headers: authHeaders(accessToken)
  })
  const envelope = (await response.json()) as ApiEnvelope<{ rows: OssConfigRecord[] }>
  if (envelope.code !== 200) {
    throw new Error(`OSS 配置列表查询失败: ${JSON.stringify(envelope)}`)
  }
  return envelope.data.rows || []
}

async function findOssConfigByKey(
  request: APIRequestContext,
  accessToken: string,
  configKey: string
): Promise<OssConfigRecord | null> {
  const rows = await listOssConfigs(request, accessToken, configKey)
  return rows.find((item) => item.configKey === configKey) || null
}

async function cleanupOssConfigsByPrefix(request: APIRequestContext, accessToken: string, prefix: string): Promise<void> {
  const rows = await listOssConfigs(request, accessToken)
  const targets = rows.filter((item) => item.configKey.startsWith(prefix))
  for (const item of targets) {
    const response = await request.post(`${e2eRuntime.apiBaseUrl}/system/oss/config/remove/${item.ossConfigId}`, {
      headers: authHeaders(accessToken)
    })
    const envelope = (await response.json()) as ApiEnvelope<null>
    if (envelope.code !== 200) {
      throw new Error(`OSS 配置清理失败: ${JSON.stringify(envelope)}`)
    }
  }
}
