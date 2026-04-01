import type { Page } from '@playwright/test'
import { test, expect, e2eRuntime } from '../fixtures/test'
import { cleanupAiModelsByPrefix, createAiModel, findAiModelByName } from '../utils/ai-model'

const AI_MODEL_E2E_PREFIX = 'E2E AI Model'
const AI_MODEL_PRESET = resolveAiModelPreset()

interface ApiEnvelope {
  code: number
  msg: string
  data: string | null
}

test.describe('AI 模型管理', () => {
  test('应支持环境变量凭证模型的真实连通测试与编辑保留原值', async ({ authenticatedPage, request, authSession }) => {
    const uniqueSuffix = Date.now()
    const modelName = `${AI_MODEL_E2E_PREFIX} ${uniqueSuffix}`
    const updatedRemark = `E2E updated ${uniqueSuffix}`

    await cleanupAiModelsByPrefix(request, e2eRuntime.apiBaseUrl, authSession.accessToken, AI_MODEL_E2E_PREFIX)

    try {
      const createdModel = await createAiModel(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
        modelName,
        provider: AI_MODEL_PRESET.provider,
        modelType: 'LLM',
        modelCode: AI_MODEL_PRESET.modelCode,
        baseUrl: AI_MODEL_PRESET.baseUrl,
        apiKey: '',
        maxTokens: 1024,
        temperature: 0.7,
        status: '0',
        remark: 'E2E create'
      })

      await openAiModelPage(authenticatedPage)

      await expect(authenticatedPage.getByTestId('ai-model-page')).toBeVisible()
      await expect(authenticatedPage.getByTestId('ai-model-table')).toBeVisible()
      await expect(authenticatedPage.getByTestId('ai-model-add-button')).toBeVisible()
      await expect(authenticatedPage.getByTestId('sidebar-menu-aimodel')).toBeVisible()

      const row = authenticatedPage.locator('.el-table__row').filter({ hasText: modelName }).first()
      await expect(row).toBeVisible()
      await expect(authenticatedPage.getByTestId(`ai-model-credential-status-${createdModel.modelId}`)).toContainText('已配置')
      await expect(authenticatedPage.getByTestId(`ai-model-credential-source-${createdModel.modelId}`)).toContainText('环境变量')

      const firstTestResponse = waitForApiEnvelope(authenticatedPage, '/ai/model/test/')
      await authenticatedPage.getByTestId(`ai-model-test-button-${createdModel.modelId}`).click()
      await expect(await firstTestResponse).toMatchObject({ code: 200 })
      await expect(authenticatedPage.locator('.el-message').last()).toContainText('模型真实连通成功')

      await authenticatedPage.getByTestId(`ai-model-edit-button-${createdModel.modelId}`).click()
      const editDialog = authenticatedPage.getByRole('dialog', { name: '编辑模型' })
      await expect(editDialog).toBeVisible()
      await expect(editDialog.getByTestId('ai-model-api-key-input')).toHaveValue('')
      await editDialog.getByTestId('ai-model-remark-input').fill(updatedRemark)

      const editResponse = waitForApiEnvelope(authenticatedPage, '/ai/model/edit')
      await editDialog.getByTestId('ai-model-submit-button').click()
      await expect(await editResponse).toMatchObject({ code: 200 })

      await expect
        .poll(async () => {
          const updated = await findAiModelByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, modelName)
          return {
            remark: updated?.remark,
            credentialSource: updated?.credentialSource,
            credentialConfigured: updated?.credentialConfigured
          }
        })
        .toMatchObject({
          remark: updatedRemark,
          credentialSource: 'env',
          credentialConfigured: true
        })

      const secondTestResponse = waitForApiEnvelope(authenticatedPage, '/ai/model/test/')
      await authenticatedPage.getByTestId(`ai-model-test-button-${createdModel.modelId}`).click()
      await expect(await secondTestResponse).toMatchObject({ code: 200 })

      await authenticatedPage.getByTestId(`ai-model-delete-button-${createdModel.modelId}`).click()
      const deleteDialog = authenticatedPage.locator('.el-message-box').last()
      await expect(deleteDialog).toContainText(`确定删除模型“${modelName}”吗`)
      await deleteDialog.locator('.el-button--primary').click()

      await expect.poll(async () => {
        return findAiModelByName(request, e2eRuntime.apiBaseUrl, authSession.accessToken, modelName)
      }).toBeNull()
    } finally {
      await cleanupAiModelsByPrefix(request, e2eRuntime.apiBaseUrl, authSession.accessToken, AI_MODEL_E2E_PREFIX)
    }
  })
})

async function waitForApiEnvelope(page: Page, urlFragment: string): Promise<ApiEnvelope> {
  const response = await page.waitForResponse((currentResponse) => {
    return currentResponse.request().method() === 'POST' && currentResponse.url().includes(urlFragment)
  })
  return response.json() as Promise<ApiEnvelope>
}

async function openAiModelPage(page: Page): Promise<void> {
  await page.goto('/')
  await page.waitForLoadState('networkidle')

  const aiMenu = page.getByTestId('sidebar-menu-ai')
  await expect(aiMenu).toBeVisible()

  const aiModelMenu = page.getByTestId('sidebar-menu-aimodel')
  if (!(await aiModelMenu.isVisible())) {
    await aiMenu.click()
  }

  await expect(aiModelMenu).toBeVisible()
  await aiModelMenu.click()
  await page.waitForURL('**/ai/model')
  await page.waitForLoadState('networkidle')
}

interface AiModelPreset {
  provider: string
  modelCode: string
  baseUrl: string
}

function resolveAiModelPreset(): AiModelPreset {
  const provider = (process.env.PW_AI_MODEL_PROVIDER || 'qwen').trim().toLowerCase()
  const presetMap: Record<string, AiModelPreset> = {
    qwen: {
      provider: 'qwen',
      modelCode: 'qwen-plus',
      baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1'
    },
    deepseek: {
      provider: 'deepseek',
      modelCode: 'deepseek-chat',
      baseUrl: 'https://api.deepseek.com/v1'
    },
    openai: {
      provider: 'openai',
      modelCode: 'gpt-4o',
      baseUrl: 'https://api.openai.com/v1'
    },
    zhipu: {
      provider: 'zhipu',
      modelCode: 'glm-4',
      baseUrl: 'https://open.bigmodel.cn/api/paas/v4'
    }
  }
  const preset = presetMap[provider] || presetMap.qwen
  return {
    provider: process.env.PW_AI_MODEL_PROVIDER?.trim() || preset.provider,
    modelCode: process.env.PW_AI_MODEL_CODE?.trim() || preset.modelCode,
    baseUrl: process.env.PW_AI_MODEL_BASE_URL?.trim() || preset.baseUrl
  }
}
