import { expect, test, type Page, type Route } from '@playwright/test'

const api = (data: unknown, code = 200, msg = 'success') => ({ code, msg, data })

const state: any = {
  project: {
    projectId: 21,
    projectName: '文本主链路回归项目',
    projectStatus: 'ACTIVE',
    currentStage: 'DOCUMENT',
    defaultRatio: '9:16',
    defaultStyle: '写实电影感',
    generationStrategy: 'AUTO',
    audioMode: 'SILENT',
    subtitleMode: 'NONE',
    referenceStrategy: 'CHARACTER_SCENE',
    actionIntensity: 'NORMAL',
    continuityLevel: 'STRICT',
    multiRoleStrategy: 'SINGLE_FIRST'
  },
  setting: {
    defaultRatio: '9:16',
    defaultResolution: '720p',
    imageCandidateCount: 2,
    videoCandidateCount: 1,
    defaultShotDuration: 5,
    paramsJson: '{}'
  },
  documents: [],
  contentVersions: [],
  characters: [],
  scenes: [],
  props: [],
  shots: []
}

interface Capture {
  polishAttempts: number
  scriptAttempts: number
  assetAttempts: number
  documentBodies: Record<string, unknown>[]
}

function fulfillJson(route: Route, data: unknown, code = 200, msg = 'success') {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(api(data, code, msg))
  })
}

function fulfillSse(route: Route, events: unknown[]) {
  const body = events
    .map((event) => `data: ${typeof event === 'string' ? event : JSON.stringify(event)}\n\n`)
    .join('')
  return route.fulfill({
    status: 200,
    contentType: 'text/event-stream; charset=utf-8',
    body
  })
}

async function installSession(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('Admin-Token', 'aivideo-text-token')
    localStorage.setItem('Admin-Refresh-Token', 'aivideo-text-refresh')
    localStorage.setItem('HAN-user', JSON.stringify({ token: 'aivideo-text-token', tenantId: 1, _userId: 1 }))
  })
}

function setSelectedVersion(type: string, versionId: number, selected: boolean) {
  for (const item of state.contentVersions) {
    if (item.contentType === type) {
      item.selected = item.versionId === versionId && selected ? '1' : '0'
      item.confirmStatus = item.versionId === versionId && selected ? 'APPROVED' : 'PENDING'
    }
  }
}

async function mockTextWorkflow(page: Page, capture: Capture) {
  await page.route('**/dev-api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/dev-api', '')
    const body = () => JSON.parse(request.postData() || '{}')

    if (path === '/system/user/current') {
      await fulfillJson(route, {
        userId: 1,
        username: 'admin',
        nickname: 'Text Flow Admin',
        tenantId: 1,
        roles: ['admin'],
        permissions: ['*:*:*']
      })
      return
    }
    if (path.startsWith('/system/dict/data/type/')) {
      await fulfillJson(route, [])
      return
    }
    if (path === '/aivideo/studio/project/21') {
      await fulfillJson(route, state)
      return
    }
    if (path === '/aivideo/studio/document/save') {
      const payload = body()
      capture.documentBodies.push(payload)
      const document = {
        documentId: 31,
        projectId: 21,
        sourceType: payload.sourceType || 'TEXT',
        fileName: payload.fileName || '',
        rawText: payload.rawText,
        parsedText: payload.rawText,
        charCount: String(payload.rawText || '').length,
        confirmed: '0',
        createTime: '2026-07-10 11:00:00'
      }
      state.documents = [document]
      await fulfillJson(route, 31)
      return
    }
    if (path === '/aivideo/studio/document/confirm') {
      if (state.documents[0]) state.documents[0].confirmed = '1'
      await fulfillJson(route, null)
      return
    }
    if (path === '/aivideo/studio/document/confirm/cancel') {
      if (state.documents[0]) state.documents[0].confirmed = '0'
      await fulfillJson(route, null)
      return
    }
    if (path === '/aivideo/studio/text/polish/prompt-preview') {
      await fulfillJson(route, { effectivePrompt: '润色提示词预览' })
      return
    }
    if (path === '/aivideo/studio/text/script/prompt-preview') {
      await fulfillJson(route, { effectivePrompt: '剧本提示词预览' })
      return
    }
    if (path === '/aivideo/studio/assets/prompt-preview') {
      await fulfillJson(route, { effectivePrompt: '资产提示词预览' })
      return
    }
    if (path === '/aivideo/studio/text/polish/generate/stream') {
      capture.polishAttempts += 1
      if (capture.polishAttempts === 1) {
        await fulfillSse(route, [
          { type: 'error', content: '文本模型配置缺失，请先配置默认模型' },
          '[DONE]'
        ])
        return
      }
      state.contentVersions = state.contentVersions.filter((item: any) => item.contentType !== 'POLISH')
      state.contentVersions.push({
        versionId: 41,
        projectId: 21,
        contentType: 'POLISH',
        versionNo: 1,
        title: '润色稿 v1',
        contentText: '雨夜里，女孩撑伞救下受伤的小猫。',
        selected: '0',
        confirmStatus: 'PENDING',
        createTime: '2026-07-10 11:05:00'
      })
      await fulfillSse(route, [
        { type: 'delta', content: '雨夜里，女孩撑伞救下受伤的小猫。' },
        { type: 'meta', content: { versionId: 41, modelCode: 'mock-text-model' } },
        '[DONE]'
      ])
      return
    }
    if (path === '/aivideo/studio/text/polish/confirm') {
      const payload = body()
      setSelectedVersion('POLISH', Number(payload.versionId), true)
      await fulfillJson(route, null)
      return
    }
    if (path === '/aivideo/studio/text/polish/confirm/cancel') {
      const payload = body()
      setSelectedVersion('POLISH', Number(payload.versionId), false)
      await fulfillJson(route, null)
      return
    }
    if (path === '/aivideo/studio/text/script/generate/stream') {
      capture.scriptAttempts += 1
      state.contentVersions = state.contentVersions.filter((item: any) => item.contentType !== 'SCRIPT')
      state.contentVersions.push({
        versionId: 51,
        projectId: 21,
        contentType: 'SCRIPT',
        versionNo: 1,
        title: '剧本 v1',
        contentText: '# 第1集\n\n女孩在暴雨中救下小猫。',
        selected: '0',
        confirmStatus: 'PENDING',
        createTime: '2026-07-10 11:10:00'
      })
      await fulfillSse(route, [
        { type: 'delta', content: '# 第1集\n\n女孩在暴雨中救下小猫。' },
        { type: 'meta', content: { versionId: 51, modelCode: 'mock-text-model' } },
        '[DONE]'
      ])
      return
    }
    if (path === '/aivideo/studio/text/script/confirm') {
      const payload = body()
      setSelectedVersion('SCRIPT', Number(payload.versionId), true)
      await fulfillJson(route, null)
      return
    }
    if (path === '/aivideo/studio/text/script/confirm/cancel') {
      const payload = body()
      setSelectedVersion('SCRIPT', Number(payload.versionId), false)
      await fulfillJson(route, null)
      return
    }
    if (path === '/aivideo/studio/assets/extract/stream') {
      capture.assetAttempts += 1
      state.contentVersions = state.contentVersions.filter((item: any) => item.contentType !== 'ASSET_EXTRACT')
      state.contentVersions.push({
        versionId: 61 + capture.assetAttempts,
        projectId: 21,
        contentType: 'ASSET_EXTRACT',
        versionNo: capture.assetAttempts,
        title: `资产提取 v${capture.assetAttempts}`,
        contentText: capture.assetAttempts === 1 ? '{"raw":"not structured"}' : '{"characters":1,"scenes":1,"props":1,"shots":1}',
        selected: '0',
        confirmStatus: 'PENDING'
      })
      if (capture.assetAttempts === 2) {
        state.characters = [{
          characterId: 71,
          characterName: '撑伞女孩',
          storyRole: '主角',
          appearance: '黄色雨衣，红伞',
          confirmStatus: 'PENDING'
        }]
        state.scenes = [{
          sceneId: 72,
          sceneName: '暴雨街道',
          atmosphere: '紧张但温暖',
          visualFeatures: '雨水反光，路灯暖色',
          confirmStatus: 'PENDING'
        }]
        state.props = [{
          propId: 73,
          propName: '红伞',
          propType: 'HANDHELD',
          visualDesc: '红色长柄伞',
          confirmStatus: 'PENDING'
        }]
        state.shots = [{
          shotId: 74,
          shotNo: 1,
          episodeNo: 1,
          durationSec: 5,
          sceneId: 72,
          characterIds: '71',
          actionDesc: '女孩弯腰抱起小猫',
          cameraMovement: '缓慢推进',
          confirmStatus: 'PENDING'
        }]
      }
      await fulfillSse(route, [
        { type: 'delta', content: capture.assetAttempts === 1 ? '{"raw":"not structured"}' : '{"characters":1}' },
        { type: 'meta', content: { taskId: 81 + capture.assetAttempts, taskStatus: 'SUCCESS', progress: 100 } },
        '[DONE]'
      ])
      return
    }
    if (path === '/aivideo/studio/assets/confirm') {
      for (const group of ['characters', 'scenes', 'props', 'shots']) {
        for (const item of state[group]) item.confirmStatus = 'APPROVED'
      }
      const assetVersion = state.contentVersions.find((item: any) => item.contentType === 'ASSET_EXTRACT')
      if (assetVersion) {
        assetVersion.selected = '1'
        assetVersion.confirmStatus = 'APPROVED'
      }
      await fulfillJson(route, null)
      return
    }
    if (path === '/aivideo/studio/assets/confirm/cancel') {
      for (const group of ['characters', 'scenes', 'props', 'shots']) {
        for (const item of state[group]) item.confirmStatus = 'PENDING'
      }
      const assetVersion = state.contentVersions.find((item: any) => item.contentType === 'ASSET_EXTRACT')
      if (assetVersion) {
        assetVersion.selected = '0'
        assetVersion.confirmStatus = 'PENDING'
      }
      await fulfillJson(route, null)
      return
    }
    if (path === '/aivideo/studio/task/assets/latest') {
      await fulfillJson(route, null)
      return
    }
    if (path === '/aivideo/studio/media/list' || path === '/aivideo/studio/media/shot/video/tasks' || path === '/aivideo/studio/edit/tasks') {
      await fulfillJson(route, [])
      return
    }
    if (path === '/aivideo/studio/edit/preflight') {
      await fulfillJson(route, { ready: false, clipCount: 0, missingShotCount: 1, totalDurationSec: 0, clips: [], warnings: [], errors: ['缺少已选分镜视频'] })
      return
    }

    await fulfillJson(route, null)
  })
}

function stageButton(page: Page, label: string) {
  return page.locator('.flow-item').filter({ hasText: label })
}

test('用户按原文、润色、剧本、资产顺序完成失败恢复和确认状态流转', async ({ page }) => {
  state.documents = []
  state.contentVersions = []
  state.characters = []
  state.scenes = []
  state.props = []
  state.shots = []
  const capture: Capture = { polishAttempts: 0, scriptAttempts: 0, assetAttempts: 0, documentBodies: [] }
  await installSession(page)
  await mockTextWorkflow(page, capture)

  await page.goto('/studio/projects/21/workbench', { waitUntil: 'networkidle' })
  await expect(page.getByText('文本主链路回归项目')).toBeVisible()

  const source = '暴雨夜，女孩撑着红伞，在路边发现一只受伤的小猫。'
  await page.getByPlaceholder('粘贴小说、文档、剧情梗概或 Markdown，保存后再确认原文。').fill(source)
  await page.getByRole('button', { name: '保存原文', exact: true }).click()
  await expect(page.getByText('原文已保存')).toBeVisible()
  await expect(page.getByText('待确认')).toBeVisible()
  expect(capture.documentBodies.at(-1)).toMatchObject({ projectId: '21', rawText: source })

  await page.getByRole('button', { name: '确认原文', exact: true }).click()
  await expect(page.getByRole('button', { name: '取消确认原文' })).toBeVisible()
  await page.getByRole('button', { name: '取消确认原文' }).click()
  await expect(page.getByText('原文已取消确认')).toBeVisible()
  await expect(page.getByRole('button', { name: '保存并确认原文' })).toBeVisible()
  await page.getByRole('button', { name: '保存并确认原文' }).click()
  await expect(page.getByRole('button', { name: '取消确认原文' })).toBeVisible()

  await stageButton(page, '润色').click()
  await page.getByRole('button', { name: '生成润色' }).click()
  await expect(page.getByText('文本模型配置缺失，请先配置默认模型').first()).toBeVisible()
  await page.getByRole('button', { name: '生成润色' }).click()
  await expect(page.getByText('雨夜里，女孩撑伞救下受伤的小猫。').last()).toBeVisible()
  await page.getByRole('button', { name: '确认这个润色稿' }).click()
  await expect(page.getByRole('button', { name: '取消确认', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '取消确认', exact: true }).click()
  await expect(page.getByRole('button', { name: '确认这个润色稿' })).toBeVisible()
  await page.getByRole('button', { name: '确认这个润色稿' }).click()

  await stageButton(page, '剧本').click()
  await page.getByRole('button', { name: '生成剧本' }).click()
  await expect(page.getByRole('article').filter({ hasText: '剧本 v1' }).locator('p').filter({ hasText: '女孩在暴雨中救下小猫。' })).toBeVisible()
  await page.getByRole('button', { name: '确认这个剧本' }).click()
  await expect(page.getByRole('button', { name: '取消确认', exact: true })).toBeVisible()

  await stageButton(page, '资产').click()
  await page.getByRole('button', { name: '提取资产' }).click()
  await expect(page.getByText('资产提取结果还没有结构化入库，暂时不能确认')).toBeVisible()
  await page.getByRole('button', { name: '提取资产' }).click()
  await expect(page.getByRole('tabpanel', { name: '角色' }).getByText('撑伞女孩')).toBeVisible()
  await page.getByRole('tab', { name: '场景' }).click()
  await expect(page.getByRole('tabpanel', { name: '场景' }).getByText('暴雨街道')).toBeVisible()
  await page.getByRole('tab', { name: '道具' }).click()
  await expect(page.getByRole('tabpanel', { name: '道具' }).getByText('红伞')).toBeVisible()
  await page.getByRole('tab', { name: '分镜' }).click()
  await expect(page.getByRole('tabpanel', { name: '分镜' }).getByText('女孩弯腰抱起小猫')).toBeVisible()

  await page.getByRole('button', { name: '确认全部' }).click()
  const assetActions = page.locator('.result-section .section-actions')
  await expect(assetActions.getByRole('button', { name: '取消全部确认' })).toBeVisible()
  await assetActions.getByRole('button', { name: '取消全部确认' }).click()
  await expect(page.getByText('资产已取消确认')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认全部' })).toBeEnabled()

  expect(capture.polishAttempts).toBe(2)
  expect(capture.scriptAttempts).toBe(1)
  expect(capture.assetAttempts).toBe(2)
})