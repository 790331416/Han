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

const manualSceneReferenceUrl = 'https://example.test/manual-scene-reference.png'
const uploadedReferenceUrl = 'https://cdn.test/uploaded-reference.png'
const sceneExtraPrompt = '固定路边皮球位置，不出现人物'

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
    defaultShotDuration: 5,
    paramsJson: JSON.stringify({ characterDesignType: 'CHIBI_FULL_BODY' })
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
  projectEditBodies: unknown[]
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
    if (path === '/aivideo/studio/project/edit') {
      const body = JSON.parse(request.postData() || '{}')
      capture.projectEditBodies.push(body)
      Object.assign(projectDetail.project, body)
      projectDetail.setting.defaultRatio = body.defaultRatio || projectDetail.setting.defaultRatio
      projectDetail.setting.defaultShotDuration = body.defaultShotDuration || projectDetail.setting.defaultShotDuration
      projectDetail.setting.imageCandidateCount = body.candidateImageCount || projectDetail.setting.imageCandidateCount
      projectDetail.setting.videoCandidateCount = body.videoCandidateCount || projectDetail.setting.videoCandidateCount
      projectDetail.setting.paramsJson = JSON.stringify({
        defaultStyle: body.defaultStyle,
        generationStrategy: body.generationStrategy,
        audioMode: body.audioMode,
        subtitleMode: body.subtitleMode,
        referenceStrategy: body.referenceStrategy,
        actionIntensity: body.actionIntensity,
        continuityLevel: body.continuityLevel,
        multiRoleStrategy: body.multiRoleStrategy,
        characterDesignType: body.characterDesignType,
        globalPrompt: body.globalPrompt,
        polishPrompt: body.polishPrompt,
        scriptPrompt: body.scriptPrompt,
        assetPrompt: body.assetPrompt,
        characterImagePrompt: body.characterImagePrompt,
        sceneImagePrompt: body.sceneImagePrompt,
        shotVideoPrompt: body.shotVideoPrompt
      })
      await fulfillJson(route, null)
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
    if (path === '/file/upload') {
      await fulfillJson(route, { url: uploadedReferenceUrl })
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
    characterPromptBodies: [] as any[],
    projectEditBodies: [] as any[]
  }
  await mockAivideoWorkbench(page, capture)

  await page.goto('/studio/projects/1/workbench', { waitUntil: 'networkidle' })
  await expect(page.getByText(labels.project)).toBeVisible()

  await page.getByTestId('edit-project-params').click()
  await page.getByTestId('project-image-candidate-count').getByRole('button', { name: '减少数值' }).click()
  await page.getByTestId('save-project-params').click()
  await expect.poll(() => capture.projectEditBodies.at(-1)?.candidateImageCount).toBe(1)
  await expect.poll(() => capture.projectEditBodies.at(-1)?.videoCandidateCount).toBe(1)

  await page.getByRole('button', { name: new RegExp(labels.assets) }).first().click()
  await page.getByRole('tab', { name: labels.sceneTab }).click()
  await expect(page.getByText(new RegExp(`建议参考.*${labels.eveningScene}.*#30`))).toBeVisible()

  await page.getByRole('row').filter({ hasText: labels.stormScene }).getByRole('button', { name: labels.sceneButton }).click()
  await expect(page.getByRole('button', { name: /生成 1 张候选图/ })).toBeVisible()
  const sceneReferencePicker = page.getByTestId('reference-image-picker-scene')
  await expect(sceneReferencePicker).toBeVisible()
  await page.locator('.image-extra-prompt-form').filter({ hasText: '本次场景图追加提示词' }).locator('textarea').fill(sceneExtraPrompt)
  await page.getByPlaceholder('粘贴场景参考图 URL，回车或点添加').fill(manualSceneReferenceUrl)
  await page.getByRole('button', { name: '添加 URL' }).click()
  await expect(page.getByText(/外部图1/)).toBeVisible()
  await page.locator('input[type="file"][accept="image/*"]').first().setInputFiles({
    name: 'scene-reference.png',
    mimeType: 'image/png',
    buffer: PNG_1X1
  })
  await expect(page.getByText(/外部图2/)).toBeVisible()
  const sceneReferenceCard = page.getByTestId('reference-selected-card-scene').filter({ hasText: `${labels.eveningScene} #30` })
  await expect(sceneReferenceCard).toBeVisible()
  await expect.poll(async () => (await sceneReferenceCard.boundingBox())?.width ?? 0).toBeGreaterThan(320)
  await sceneReferencePicker.locator('.el-select').click()
  const sceneReferencePopper = page.locator('.el-select__popper.reference-image-popper')
  await expect(sceneReferencePopper).toBeVisible()
  await expect.poll(async () => (await sceneReferencePopper.boundingBox())?.width ?? 0).toBeGreaterThan(520)
  await page.keyboard.press('Escape')
  await page.getByText('scene prompt refs=30').waitFor({ state: 'visible' })

  await page.getByTestId('reference-selected-card-scene').filter({ hasText: `${labels.eveningScene} #30` }).locator('img').click()
  await expect(page.locator('.el-image-viewer__wrapper')).toBeVisible()
  await page.locator('.el-image-viewer__close').click()
  await page.locator('.el-drawer__close-btn:visible').click()

  await page.getByRole('tab', { name: labels.characterTab }).click()
  await page.getByRole('row').filter({ hasText: labels.dog }).getByRole('button', { name: labels.characterButton }).click()
  await expect(page.getByRole('button', { name: /生成 1 张候选图/ })).toBeVisible()
  await expect(page.getByTestId('reference-image-picker-character')).toBeVisible()
  await expect(page.getByTestId('reference-selected-card-character').filter({ hasText: `${labels.dog} #41` })).toBeVisible()
  await page.getByText('character prompt refs=41').waitFor({ state: 'visible' })
  await page.locator('.el-drawer__close-btn:visible').click()

  await page.getByRole('tab', { name: labels.shotTab }).click()
  await page.getByRole('row').filter({ hasText: labels.shotAction }).getByRole('button', { name: labels.videoButton }).click()
  await expect(page.getByText(`场景：${labels.stormScene} #46`).first()).toBeVisible()
  await expect(page.getByText(`角色：${labels.dog} #41`).first()).toBeVisible()
  await expect(page.getByText(`角色：${labels.cat} #44`).first()).toBeVisible()
  await expect(page.getByText('视频生成前预检')).toBeVisible()
  await expect(page.getByText(/角色造型人工复核：Q版萌系全身/)).toBeVisible()
  const shotReferenceCards = page.locator('.shot-reference-panel .shot-reference-card')
  await expect(shotReferenceCards).toHaveCount(3)
  const shotReferenceImage = shotReferenceCards.first().locator('.el-image')
  await expect.poll(async () => (await shotReferenceImage.boundingBox())?.width ?? 0).toBeLessThanOrEqual(160)
  await expect.poll(async () => (await shotReferenceImage.boundingBox())?.height ?? 0).toBeLessThanOrEqual(120)
  await shotReferenceCards.first().locator('img').click()
  await expect(page.locator('.el-image-viewer__wrapper')).toBeVisible()
  await page.locator('.el-image-viewer__close').click()
  await page.getByRole('button', { name: /生成 1 条候选视频/ }).click()
  const preflightDialog = page.locator('.el-message-box').filter({ hasText: '参考图规则预检' })
  await expect(preflightDialog).toBeVisible()
  await expect(preflightDialog.getByText(/Q版单主体完整全身/)).toBeVisible()
  await preflightDialog.getByRole('button', { name: '先检查图片' }).click()

  expect(capture.scenePromptBodies.at(-1)?.referenceMediaIds).toEqual(['30'])
  await expect.poll(() => capture.scenePromptBodies.at(-1)?.referenceImageUrls || []).toEqual([
    manualSceneReferenceUrl,
    uploadedReferenceUrl
  ])
  await expect.poll(() => String(capture.scenePromptBodies.at(-1)?.customPrompt || '')).toContain(sceneExtraPrompt)
  expect(capture.characterPromptBodies.at(-1)?.referenceMediaIds).toEqual(['41'])
  expect(capture.characterPromptBodies.at(-1)?.characterDesignType).toBe('CHIBI_FULL_BODY')
})
