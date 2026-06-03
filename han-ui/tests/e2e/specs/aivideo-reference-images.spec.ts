import { test, expect, type Page, type Route } from '@playwright/test'

const PNG_1X1 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p94AAAAASUVORK5CYII=',
  'base64'
)

const api = (data: unknown) => ({ code: 200, msg: 'success', data })

const labels = {
  project: '浏览器验证项目',
  assets: '资产',
  sceneTab: '场景',
  characterTab: '角色',
  shotTab: '分镜',
  sceneButton: '场景图',
  characterButton: '角色图',
  videoButton: '视频',
  eveningScene: '傍晚静谧小区街道',
  stormScene: '暴雨夜小区街道',
  dog: '狗狗',
  cat: '橘猫',
  shotAction: '狗狗冲向摇晃的广告牌'
}

const projectDetail = {
  project: {
    projectId: 1,
    projectName: labels.project,
    currentStage: 'ASSET_CONFIRMED',
    defaultRatio: '9:16',
    defaultStyle: '写实电影感',
    generationStrategy: 'AUTO',
    audioMode: 'NATIVE_AUDIO',
    subtitleMode: 'BOTTOM',
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
    defaultShotDuration: 5
  },
  documents: [
    {
      documentId: 1,
      projectId: 1,
      rawText: '一只狗在小区街道救援小猫。',
      parsedText: '一只狗在小区街道救援小猫。',
      confirmed: '1'
    }
  ],
  contentVersions: [
    {
      versionId: 11,
      projectId: 1,
      contentType: 'POLISH',
      versionNo: 1,
      title: '润色稿 v1',
      contentText: '润色内容',
      selected: '1',
      confirmStatus: 'APPROVED'
    },
    {
      versionId: 12,
      projectId: 1,
      contentType: 'SCRIPT',
      versionNo: 1,
      title: '剧本 v1',
      contentText: '短剧剧本内容',
      selected: '1',
      confirmStatus: 'APPROVED'
    },
    {
      versionId: 13,
      projectId: 1,
      contentType: 'ASSET_EXTRACT',
      versionNo: 1,
      title: '资产 v1',
      contentText: '{"characters":[],"scenes":[],"shots":[]}',
      selected: '1',
      confirmStatus: 'APPROVED'
    }
  ],
  characters: [
    {
      characterId: 1,
      characterName: labels.dog,
      storyRole: '主角，救援者',
      appearance: '中型黄色短毛犬，眼神灵动',
      lockedMediaId: 41,
      confirmStatus: 'APPROVED'
    },
    {
      characterId: 2,
      characterName: labels.cat,
      storyRole: '被救援者',
      appearance: '小橘猫，湿毛，惊恐',
      lockedMediaId: 44,
      confirmStatus: 'APPROVED'
    }
  ],
  scenes: [
    {
      sceneId: 1,
      sceneName: labels.eveningScene,
      atmosphere: '宁静，日常',
      visualFeatures: '老旧小区街道，地面有零星落叶和小花，一个皮球停在路边。柔和漫射光。',
      lockedMediaId: 30,
      confirmStatus: 'APPROVED'
    },
    {
      sceneId: 2,
      sceneName: labels.stormScene,
      atmosphere: '紧张，危险，冷冽',
      visualFeatures: '同一条街道，暴雨如注，地面积水反光，闪电瞬间照亮对面商铺摇摇欲坠的广告牌铁架。',
      lockedMediaId: 46,
      confirmStatus: 'APPROVED'
    }
  ],
  shots: [
    {
      shotId: 17,
      episodeNo: 1,
      shotNo: 1,
      durationSec: 5,
      sceneId: 2,
      characterIds: '1,2',
      cameraMovement: '微推',
      actionDesc: `${labels.shotAction}，${labels.cat}蜷缩在雨中。`,
      confirmStatus: 'APPROVED'
    }
  ]
}

async function fulfillJson(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(api(data))
  })
}

async function mockAivideoWorkbench(page: Page, capture: {
  scenePromptBodies: unknown[]
  characterPromptBodies: unknown[]
}) {
  await page.addInitScript(() => {
    localStorage.setItem('Admin-Token', 'browser-test-token')
    localStorage.setItem('HAN-user', JSON.stringify({ token: 'browser-test-token', tenantId: 1, _userId: 1 }))
  })

  await page.route('**/dev-api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/dev-api', '')

    if (path === '/system/user/current') {
      await fulfillJson(route, {
        userId: 1,
        username: 'admin',
        nickname: 'admin',
        tenantId: 1,
        roles: ['admin'],
        permissions: ['*:*:*']
      })
      return
    }
    if (path === '/aivideo/studio/project/1') {
      await fulfillJson(route, projectDetail)
      return
    }
    if (path === '/aivideo/studio/text/polish/prompt-preview') {
      await fulfillJson(route, { effectivePrompt: 'polish prompt' })
      return
    }
    if (path === '/aivideo/studio/text/script/prompt-preview') {
      await fulfillJson(route, { effectivePrompt: 'script prompt' })
      return
    }
    if (path === '/aivideo/studio/assets/prompt-preview') {
      await fulfillJson(route, { effectivePrompt: 'asset prompt' })
      return
    }
    if (path === '/aivideo/studio/media/scene/prompt-preview') {
      const body = JSON.parse(request.postData() || '{}')
      capture.scenePromptBodies.push(body)
      await fulfillJson(route, { effectivePrompt: `scene prompt refs=${(body.referenceMediaIds || []).join(',')}` })
      return
    }
    if (path === '/aivideo/studio/media/character/prompt-preview') {
      const body = JSON.parse(request.postData() || '{}')
      capture.characterPromptBodies.push(body)
      await fulfillJson(route, { effectivePrompt: `character prompt refs=${(body.referenceMediaIds || []).join(',')}` })
      return
    }
    if (path === '/aivideo/studio/media/shot/video/prompt-preview') {
      await fulfillJson(route, { effectivePrompt: 'shot video prompt' })
      return
    }
    if (path === '/aivideo/studio/media/list' || path === '/aivideo/studio/media/shot/video/tasks') {
      await fulfillJson(route, [])
      return
    }
    if (/^\/aivideo\/studio\/media\/\d+\/preview$/.test(path) || /^\/aivideo\/public\/media\/\d+\/preview$/.test(path)) {
      await route.fulfill({ status: 200, contentType: 'image/png', body: PNG_1X1 })
      return
    }

    await fulfillJson(route, null)
  })
}

test('aivideo workbench should use confirmed images through reusable reference pickers', async ({ page }) => {
  const capture = {
    scenePromptBodies: [] as any[],
    characterPromptBodies: [] as any[]
  }
  await mockAivideoWorkbench(page, capture)

  await page.goto('/studio/projects/1/workbench', { waitUntil: 'networkidle' })
  await expect(page.getByText(labels.project)).toBeVisible()

  await page.getByRole('button', { name: new RegExp(labels.assets) }).first().click()
  await page.getByRole('tab', { name: labels.sceneTab }).click()
  await expect(page.getByText(new RegExp(`建议参考.*${labels.eveningScene}.*#30`))).toBeVisible()

  await page.getByRole('row').filter({ hasText: labels.stormScene }).getByRole('button', { name: labels.sceneButton }).click()
  await expect(page.getByTestId('reference-image-picker-scene')).toBeVisible()
  await expect(page.getByTestId('reference-selected-card-scene').filter({ hasText: `${labels.eveningScene} #30` })).toBeVisible()
  await page.getByText('scene prompt refs=30').waitFor({ state: 'visible' })

  await page.getByTestId('reference-selected-card-scene').filter({ hasText: `${labels.eveningScene} #30` }).locator('img').click()
  await expect(page.locator('.el-image-viewer__wrapper')).toBeVisible()
  await page.locator('.el-image-viewer__close').click()
  await page.locator('.el-drawer__close-btn:visible').click()

  await page.getByRole('tab', { name: labels.characterTab }).click()
  await page.getByRole('row').filter({ hasText: labels.dog }).getByRole('button', { name: labels.characterButton }).click()
  await expect(page.getByTestId('reference-image-picker-character')).toBeVisible()
  await expect(page.getByTestId('reference-selected-card-character').filter({ hasText: `${labels.dog} #41` })).toBeVisible()
  await page.getByText('character prompt refs=41').waitFor({ state: 'visible' })
  await page.locator('.el-drawer__close-btn:visible').click()

  await page.getByRole('tab', { name: labels.shotTab }).click()
  await page.getByRole('row').filter({ hasText: labels.shotAction }).getByRole('button', { name: labels.videoButton }).click()
  await expect(page.getByText(`场景：${labels.stormScene} #46`).first()).toBeVisible()
  await expect(page.getByText(`角色：${labels.dog} #41`).first()).toBeVisible()
  await expect(page.getByText(`角色：${labels.cat} #44`).first()).toBeVisible()

  expect(capture.scenePromptBodies.at(-1)?.referenceMediaIds).toEqual(['30'])
  expect(capture.characterPromptBodies.at(-1)?.referenceMediaIds).toEqual(['41'])
})
