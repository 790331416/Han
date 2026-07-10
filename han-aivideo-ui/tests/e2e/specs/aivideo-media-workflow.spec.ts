import { expect, test, type Page, type Route } from '@playwright/test'

const PNG_1X1 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p94AAAAASUVORK5CYII=',
  'base64'
)
const api = (data: unknown, code = 200, msg = 'success') => ({ code, msg, data })

const detail: any = {
  project: {
    projectId: 31,
    projectName: '媒体与剪辑回归项目',
    projectStatus: 'ACTIVE',
    currentStage: 'ASSET_CONFIRMED',
    defaultRatio: '9:16',
    defaultStyle: '写实电影感',
    generationStrategy: 'AUTO',
    audioMode: 'POST_TTS',
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
    paramsJson: JSON.stringify({ characterDesignType: 'REALISTIC_NATURAL' })
  },
  documents: [{ documentId: 1, projectId: 31, rawText: '女孩救猫。', parsedText: '女孩救猫。', confirmed: '1' }],
  contentVersions: [
    { versionId: 2, contentType: 'POLISH', versionNo: 1, title: '润色稿 v1', contentText: '女孩在雨夜救猫。', selected: '1', confirmStatus: 'APPROVED' },
    { versionId: 3, contentType: 'SCRIPT', versionNo: 1, title: '剧本 v1', contentText: '# 第1集', selected: '1', confirmStatus: 'APPROVED' },
    { versionId: 4, contentType: 'ASSET_EXTRACT', versionNo: 1, title: '资产 v1', contentText: '{}', selected: '1', confirmStatus: 'APPROVED' }
  ],
  characters: [{
    characterId: 11,
    characterName: '小雨',
    storyRole: '主角',
    appearance: '黄色雨衣，红色长靴',
    lockedMediaId: 101,
    confirmStatus: 'APPROVED'
  }],
  scenes: [{
    sceneId: 12,
    sceneName: '雨夜街角',
    atmosphere: '紧张又温暖',
    visualFeatures: '路灯暖光，雨水反光',
    lockedMediaId: 102,
    confirmStatus: 'APPROVED'
  }],
  props: [],
  shots: [{
    shotId: 13,
    episodeNo: 1,
    shotNo: 1,
    durationSec: 5,
    sceneId: 12,
    characterIds: '11',
    characterNames: '小雨',
    actionDesc: '小雨弯腰抱起小猫，停在怀里。',
    dialogue: '别怕，我来帮你。',
    cameraMovement: '缓慢推进',
    transitionType: 'OPENING',
    confirmStatus: 'APPROVED'
  }]
}

interface RuntimeState {
  videoAssets: any[]
  ttsAssets: any[]
  videoTasks: any[]
  editTasks: any[]
  editAssets: any[]
  videoSelected: boolean
  ttsAttempts: number
  editSubmitted: boolean
  videoStreamBodies: Record<string, unknown>[]
}

function fulfillJson(route: Route, data: unknown, code = 200, msg = 'success') {
  return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(api(data, code, msg)) })
}

function fulfillSse(route: Route, events: unknown[]) {
  return route.fulfill({
    status: 200,
    contentType: 'text/event-stream; charset=utf-8',
    body: events.map((event) => `data: ${typeof event === 'string' ? event : JSON.stringify(event)}\n\n`).join('')
  })
}

async function installSession(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('Admin-Token', 'aivideo-media-token')
    localStorage.setItem('Admin-Refresh-Token', 'aivideo-media-refresh')
    localStorage.setItem('HAN-user', JSON.stringify({ token: 'aivideo-media-token', tenantId: 1, _userId: 1 }))
  })
}

async function mockMediaWorkflow(page: Page, runtime: RuntimeState) {
  await page.route('https://cdn.test/**', (route) => route.fulfill({ status: 200, contentType: 'video/mp4', body: Buffer.from('mock-video') }))
  await page.route('**/dev-api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/dev-api', '')
    const body = () => JSON.parse(request.postData() || '{}')

    if (path === '/system/user/current') {
      await fulfillJson(route, { userId: 1, username: 'admin', nickname: 'Media Admin', tenantId: 1, roles: ['admin'], permissions: ['*:*:*'] })
      return
    }
    if (path.startsWith('/system/dict/data/type/')) {
      await fulfillJson(route, [])
      return
    }
    if (path === '/aivideo/studio/project/31') {
      await fulfillJson(route, detail)
      return
    }
    if (path === '/aivideo/studio/text/polish/prompt-preview' || path === '/aivideo/studio/text/script/prompt-preview' || path === '/aivideo/studio/assets/prompt-preview') {
      await fulfillJson(route, { effectivePrompt: '已确认提示词' })
      return
    }
    if (path === '/aivideo/studio/media/shot/video/prompt-preview') {
      await fulfillJson(route, { effectivePrompt: '主体：小雨；动作：弯腰抱起小猫；场景：雨夜街角；镜头：缓慢推进。' })
      return
    }
    if (path === '/aivideo/studio/media/shot/video/tasks') {
      await fulfillJson(route, runtime.videoTasks)
      return
    }
    if (path === '/aivideo/studio/media/list') {
      const assetType = url.searchParams.get('assetType')
      if (assetType === 'SHOT_VIDEO') {
        await fulfillJson(route, runtime.videoAssets)
      } else if (assetType === 'SHOT_TTS_AUDIO') {
        await fulfillJson(route, runtime.ttsAssets)
      } else if (assetType === 'PROJECT_EDIT_VIDEO') {
        await fulfillJson(route, runtime.editAssets)
      } else {
        await fulfillJson(route, [...runtime.videoAssets, ...runtime.ttsAssets])
      }
      return
    }
    if (/^\/aivideo\/studio\/media\/(101|102)\/preview$/.test(path)) {
      await route.fulfill({ status: 200, contentType: 'image/png', body: PNG_1X1 })
      return
    }
    if (/^\/aivideo\/studio\/media\/\d+\/preview$/.test(path)) {
      await route.fulfill({ status: 200, contentType: 'application/octet-stream', body: Buffer.from('preview') })
      return
    }
    if (path === '/aivideo/studio/media/shot/video/generate/stream') {
      const payload = body()
      runtime.videoStreamBodies.push(payload)
      const asset = {
        mediaId: 201,
        projectId: 31,
        assetType: 'SHOT_VIDEO',
        bizType: 'SHOT',
        bizId: 13,
        taskId: 211,
        fileUrl: 'https://cdn.test/shot-201.mp4',
        selected: '0',
        assetStatus: 'SUCCESS',
        createTime: '2026-07-10 12:00:00'
      }
      runtime.videoAssets = [asset]
      runtime.videoTasks = [{ taskId: 211, taskType: 'VIDEO', taskStatus: 'SUCCESS', progress: 100, providerTaskId: 'mock-video-211' }]
      await fulfillSse(route, [
        { type: 'meta', content: { event: 'candidate', taskId: 211, taskStatus: 'SUCCESS', progress: 100, asset } },
        '[DONE]'
      ])
      return
    }
    if (path === '/aivideo/studio/media/select') {
      const payload = body()
      if (Number(payload.mediaId) === 201) {
        runtime.videoSelected = true
        runtime.videoAssets[0].selected = '1'
      }
      if (Number(payload.mediaId) === 301 && runtime.ttsAssets[0]) {
        runtime.ttsAssets[0].selected = '1'
      }
      await fulfillJson(route, null)
      return
    }
    if (path === '/aivideo/studio/media/shot/tts/generate') {
      runtime.ttsAttempts += 1
      if (runtime.ttsAttempts === 1) {
        await fulfillJson(route, null, 500, 'TTS 凭据缺失，请配置语音模型')
        return
      }
      const payload = body()
      const asset = {
        mediaId: 301,
        projectId: 31,
        assetType: 'SHOT_TTS_AUDIO',
        bizType: 'SHOT',
        bizId: 13,
        fileUrl: 'https://cdn.test/tts-301.mp3',
        selected: '1',
        assetStatus: 'SUCCESS',
        paramsJson: JSON.stringify({ text: payload.text, speaker: payload.speaker, ttsStartMs: payload.ttsStartMs, ttsEndMs: payload.ttsEndMs }),
        createTime: '2026-07-10 12:05:00'
      }
      runtime.ttsAssets = [asset]
      await fulfillJson(route, asset)
      return
    }
    if (path === '/aivideo/studio/edit/preflight') {
      if (!runtime.videoSelected) {
        await fulfillJson(route, {
          ready: false,
          clipCount: 0,
          missingShotCount: 1,
          totalDurationSec: 0,
          audioTrackCount: 0,
          clips: [],
          warnings: [],
          errors: ['第 1 镜缺少已选视频']
        })
      } else {
        await fulfillJson(route, {
          ready: true,
          clipCount: 1,
          missingShotCount: 0,
          totalDurationSec: 5,
          audioTrackCount: runtime.ttsAssets.length,
          clips: [{
            shotId: 13,
            shotNo: 1,
            durationSec: 5,
            actionDesc: '小雨弯腰抱起小猫，停在怀里。',
            videoMediaId: 201,
            ttsAudioMediaId: runtime.ttsAssets[0]?.mediaId,
            transitionBeforeType: 'OPENING',
            transitionBeforeDesc: '开场',
            timelineStartMs: 0,
            timelineEndMs: 5000
          }],
          warnings: ['请人工复核声音与画面对齐'],
          errors: []
        })
      }
      return
    }
    if (path === '/aivideo/studio/edit/tasks') {
      await fulfillJson(route, runtime.editTasks)
      return
    }
    if (path === '/aivideo/studio/edit/generate') {
      runtime.editSubmitted = true
      runtime.editTasks = [{ taskId: 401, taskType: 'PROJECT_EDIT', taskStatus: 'SUCCESS', progress: 100, providerTaskId: 'mock-edit-401' }]
      runtime.editAssets = [{
        mediaId: 501,
        projectId: 31,
        assetType: 'PROJECT_EDIT_VIDEO',
        bizType: 'PROJECT',
        bizId: 31,
        fileUrl: 'vod://mock-fvid-501',
        selected: '1',
        assetStatus: 'SUCCESS'
      }]
      await fulfillJson(route, { taskId: 401, taskType: 'PROJECT_EDIT', taskStatus: 'RUNNING', progress: 5 })
      return
    }
    if (path === '/aivideo/studio/task/assets/latest') {
      await fulfillJson(route, null)
      return
    }

    await fulfillJson(route, null)
  })
}

function flowButton(page: Page, label: string) {
  return page.locator('.flow-item').filter({ hasText: label })
}

test('用户完成视频、TTS 和项目剪辑的预检、失败恢复与结果选择', async ({ page }) => {
  const runtime: RuntimeState = {
    videoAssets: [],
    ttsAssets: [],
    videoTasks: [],
    editTasks: [],
    editAssets: [],
    videoSelected: false,
    ttsAttempts: 0,
    editSubmitted: false,
    videoStreamBodies: []
  }
  await installSession(page)
  await mockMediaWorkflow(page, runtime)

  await page.goto('/studio/projects/31/workbench', { waitUntil: 'networkidle' })
  await flowButton(page, '资产').click()
  await page.getByRole('tab', { name: '剪辑' }).click()
  await expect(page.getByText('整片剪辑预检未通过')).toBeVisible()
  await expect(page.getByText('第 1 镜缺少已选视频')).toBeVisible()
  await expect(page.getByRole('button', { name: '生成整片成片' })).toBeDisabled()

  await page.getByRole('tab', { name: '分镜' }).click()
  const shotRow = page.getByRole('row').filter({ hasText: '小雨弯腰抱起小猫' })
  await shotRow.getByRole('button', { name: '视频' }).click()
  await expect(page.getByText('视频生成前预检')).toBeVisible()
  await expect(page.getByText('需人工复核')).toBeVisible()

  await page.getByRole('button', { name: '生成 1 条候选视频' }).click()
  const preflight = page.locator('.el-message-box').filter({ hasText: '参考图规则预检' })
  await expect(preflight).toBeVisible()
  await preflight.getByRole('button', { name: '继续生成' }).click()
  await expect(page.getByText('视频 #201')).toBeVisible()
  await page.getByRole('button', { name: '选择这条' }).click()
  await expect(page.getByText('分镜视频已选定')).toBeVisible()
  expect(runtime.videoSelected).toBe(true)
  expect(runtime.videoStreamBodies.at(-1)).toMatchObject({ projectId: '31', shotId: 13, candidateCount: 1 })

  const ttsText = page.getByPlaceholder('填写本分镜要真正说出口的旁白/对白；如本镜无台词可留空')
  await expect(ttsText).toHaveValue('别怕，我来帮你。')
  await page.getByPlaceholder('如：喵小萌 / 旁白').fill('小雨')
  await page.getByRole('button', { name: '生成配音素材' }).click()
  await expect(page.getByText('TTS 凭据缺失，请配置语音模型').first()).toBeVisible()
  await expect(ttsText).toHaveValue('别怕，我来帮你。')
  await page.getByRole('button', { name: '生成配音素材' }).click()
  await expect(page.locator('.shot-tts-list').getByText('配音 #301')).toBeVisible()
  await expect(page.getByText('已选配音 #301').first()).toBeVisible()
  expect(runtime.ttsAttempts).toBe(2)

  await page.locator('.el-drawer__close-btn:visible').click()
  await page.getByRole('tab', { name: '剪辑' }).click()
  await page.getByRole('button', { name: '刷新预检' }).click()
  await expect(page.getByText('整片剪辑预检通过')).toBeVisible()
  await expect(page.getByText('请人工复核声音与画面对齐')).toBeVisible()
  await page.getByRole('button', { name: '生成整片成片' }).click()
  const editConfirm = page.locator('.el-message-box').filter({ hasText: '生成整片成片' })
  await expect(editConfirm).toBeVisible()
  await editConfirm.getByRole('button', { name: '生成整片成片' }).click()
  await expect(page.getByText('整片剪辑合成任务已提交')).toBeVisible()
  await expect(page.getByText('成片 #501')).toBeVisible()
  await expect(page.getByText('mock-fvid-501')).toBeVisible()
  expect(runtime.editSubmitted).toBe(true)
})