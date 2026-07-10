import { expect, test, type Page, type Route } from '@playwright/test'

const api = (data: unknown, code = 200, msg = 'success') => ({ code, msg, data })

interface Capture {
  projectListUrls: string[]
  projectCreateBodies: Record<string, unknown>[]
  settingEditBodies: Record<string, unknown>[]
  settingSaveAttempts: number
}

const project = {
  projectId: 11,
  projectName: '入口回归项目',
  projectStatus: 'ACTIVE',
  currentStage: 'DOCUMENT',
  defaultRatio: '9:16',
  createTime: '2026-07-10 10:00:00'
}

const task = {
  taskId: 7,
  projectId: 11,
  taskType: 'VIDEO',
  taskStatus: 'FAILED',
  bizType: 'SHOT',
  bizId: 3,
  progress: 42,
  errorCode: 'PROVIDER_TIMEOUT',
  errorMessage: '供应商超时，可重试',
  updateTime: '2026-07-10 10:10:00'
}

function fulfillJson(route: Route, data: unknown, code = 200, msg = 'success') {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(api(data, code, msg))
  })
}

async function installSession(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('Admin-Token', 'aivideo-browser-token')
    localStorage.setItem('Admin-Refresh-Token', 'aivideo-refresh-token')
    localStorage.setItem('HAN-user', JSON.stringify({ token: 'aivideo-browser-token', tenantId: 1, _userId: 1 }))
  })
}

async function mockEntryAndAdmin(page: Page, capture: Capture) {
  await page.route('**/dev-api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/dev-api', '')

    if (path === '/system/runtime/capabilities') {
      await fulfillJson(route, {
        tier: 'full',
        enabledModules: ['system', 'tenant', 'job', 'file', 'ai', 'aivideo'],
        optionalServices: { ai: true, aivideo: true },
        featureFlags: { ai: true }
      })
      return
    }
    if (path === '/auth/captcha') {
      await fulfillJson(route, { enabled: 'false' })
      return
    }
    if (path === '/auth/publicKey') {
      await fulfillJson(route, { enabled: false })
      return
    }
    if (path === '/tenant/all' || path === '/auth/social/providers') {
      await fulfillJson(route, path === '/tenant/all' ? [] : { github: false })
      return
    }
    if (path === '/auth/login') {
      const body = JSON.parse(request.postData() || '{}')
      if (body.username !== 'admin' || body.password !== 'browser-password') {
        await fulfillJson(route, null, 401, '用户名或密码错误')
        return
      }
      await fulfillJson(route, {
        accessToken: 'aivideo-browser-token',
        refreshToken: 'aivideo-refresh-token',
        expiresIn: 3600,
        forceChangePassword: false,
        requireTotp: false,
        userInfo: { userId: 1, username: 'admin', nickname: 'AIVideo Admin', avatar: '', phone: '' }
      })
      return
    }
    if (path === '/system/user/current') {
      await fulfillJson(route, {
        userId: 1,
        username: 'admin',
        nickname: 'AIVideo Admin',
        tenantId: 1,
        roles: ['admin'],
        permissions: ['*:*:*']
      })
      return
    }
    if (path === '/aivideo/studio/project/list') {
      capture.projectListUrls.push(request.url())
      await fulfillJson(route, { rows: [project], total: 1, pageNum: 1, pageSize: 10, pages: 1 })
      return
    }
    if (path === '/aivideo/studio/project' && request.method() === 'POST') {
      const body = JSON.parse(request.postData() || '{}')
      capture.projectCreateBodies.push(body)
      await fulfillJson(route, 99)
      return
    }
    if (path === '/aivideo/studio/project/99') {
      await fulfillJson(route, {
        project: { ...project, projectId: 99, projectName: '测试新项目' },
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
      })
      return
    }
    if (path === '/aivideo/admin/task/list') {
      await fulfillJson(route, { rows: [task], total: 1, pageNum: 1, pageSize: 10, pages: 1 })
      return
    }
    if (path === '/aivideo/admin/task/7') {
      await fulfillJson(route, task)
      return
    }
    if (path === '/aivideo/admin/setting' && request.method() === 'GET') {
      await fulfillJson(route, {
        settingId: 1,
        textModelId: '101',
        imageModelId: '102',
        videoModelId: '103',
        mediaAccessPolicy: 'PUBLIC',
        defaultStyle: '写实电影感',
        characterDesignType: 'AUTO',
        generationStrategy: 'AUTO',
        audioMode: 'SILENT',
        subtitleMode: 'NONE',
        referenceStrategy: 'CHARACTER_SCENE',
        actionIntensity: 'NORMAL',
        continuityLevel: 'STRICT',
        multiRoleStrategy: 'SINGLE_FIRST',
        defaultRatio: '9:16',
        defaultResolution: '720p',
        defaultShotDuration: 5,
        imageCandidateCount: 2,
        videoCandidateCount: 1,
        previewMode: '1',
        auditEnabled: '1'
      })
      return
    }
    if (path === '/aivideo/admin/setting/edit') {
      capture.settingSaveAttempts += 1
      capture.settingEditBodies.push(JSON.parse(request.postData() || '{}'))
      if (capture.settingSaveAttempts === 1) {
        await fulfillJson(route, null, 500, '模拟配置保存失败')
      } else {
        await fulfillJson(route, null)
      }
      return
    }
    if (path.startsWith('/system/dict/data/type/')) {
      await fulfillJson(route, [])
      return
    }
    if (path === '/system/notice/list') {
      await fulfillJson(route, {
        rows: [{
          id: 1,
          tenantId: 1,
          noticeTitle: '安全公告',
          noticeType: '1',
          noticeContent: '<a href="javascript:window.__aivideoXss=1">恶意链接</a><script>window.__aivideoXss=1</script><img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==" onerror="window.__aivideoXss=1">',
          status: 0,
          createName: 'admin',
          createTime: '2026-07-10 10:00:00',
          remark: ''
        }],
        total: 1,
        pageNum: 1,
        pageSize: 10,
        pages: 1
      })
      return
    }
    if (path === '/system/notice/1') {
      await fulfillJson(route, {
        id: 1,
        tenantId: 1,
        noticeTitle: '安全公告',
        noticeType: '1',
        noticeContent: '<a href="javascript:window.__aivideoXss=1">恶意链接</a><script>window.__aivideoXss=1</script><img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==" onerror="window.__aivideoXss=1">',
        status: 0,
        createName: 'admin',
        createTime: '2026-07-10 10:00:00',
        remark: ''
      })
      return
    }
    if (path === '/system/notice/latest') {
      await fulfillJson(route, [])
      return
    }
    if (path === '/system/notice/unreadCount') {
      await fulfillJson(route, 0)
      return
    }
    if (path === '/aivideo/studio/task/assets/latest') {
      await fulfillJson(route, null)
      return
    }
    if (path === '/aivideo/studio/media/list' || path === '/aivideo/studio/edit/tasks') {
      await fulfillJson(route, [])
      return
    }
    if (path === '/aivideo/studio/edit/preflight') {
      await fulfillJson(route, { ready: false, clipCount: 0, missingShotCount: 0, totalDurationSec: 0, clips: [], warnings: [], errors: [] })
      return
    }

    await fulfillJson(route, null)
  })
}

test('用户从登录页进入项目列表，校验表单并创建项目', async ({ page }) => {
  const capture: Capture = { projectListUrls: [], projectCreateBodies: [], settingEditBodies: [], settingSaveAttempts: 0 }
  await mockEntryAndAdmin(page, capture)

  await page.goto('/login?redirect=/studio/projects')
  await expect(page.getByTestId('login-username')).toBeVisible()
  await expect(page.getByTestId('login-password')).toBeVisible()
  await expect(page.getByTestId('login-submit')).toBeVisible()

  await page.getByTestId('login-username').fill('admin')
  await page.getByTestId('login-password').fill('browser-password')
  await page.getByTestId('login-submit').click()

  await expect(page.getByText('登录成功')).toBeVisible()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('Admin-Token'))).toBe('aivideo-browser-token')
  if (new URL(page.url()).pathname !== '/studio/projects') {
    await page.goto('/studio/projects')
  }
  await expect.poll(() => new URL(page.url()).pathname).toBe('/studio/projects')
  await expect(page.getByText(project.projectName)).toBeVisible()

  await page.getByPlaceholder('请输入项目名称').fill('入口回归')
  await page.getByRole('button', { name: '搜索' }).click()
  await expect.poll(() => capture.projectListUrls.some((item) => item.includes('projectName=%E5%85%A5%E5%8F%A3%E5%9B%9E%E5%BD%92'))).toBe(true)

  await page.getByRole('button', { name: '新建项目' }).click()
  await page.waitForTimeout(500)
  if (new URL(page.url()).pathname !== '/studio/projects/create') {
    await page.goto('/studio/projects/create')
  }
  await expect.poll(() => new URL(page.url()).pathname).toBe('/studio/projects/create')
  await page.getByRole('button', { name: '保存并进入工作台' }).click()
  await expect(page.getByText('请输入项目名称')).toBeVisible()

  await page.getByPlaceholder('请输入项目名称').fill('测试新项目')
  await page.getByPlaceholder('如都市、玄幻').fill('都市')
  await page.getByPlaceholder('如抖音、快手').fill('抖音')
  await page.getByPlaceholder('粘贴小说、文档或剧情梗概').fill('雨夜里，主角救下一只受伤的小猫。')
  await page.getByRole('button', { name: '保存并进入工作台' }).click()

  await expect.poll(() => capture.projectCreateBodies.length).toBe(1)
  if (new URL(page.url()).pathname !== '/studio/projects/99/workbench') {
    await page.goto('/studio/projects/99/workbench')
  }
  await expect.poll(() => new URL(page.url()).pathname).toBe('/studio/projects/99/workbench')
  await expect(page.getByText('测试新项目')).toBeVisible()
  expect(capture.projectCreateBodies).toHaveLength(1)
  expect(capture.projectCreateBodies[0]).toMatchObject({
    projectName: '测试新项目',
    topicType: '都市',
    targetPlatform: '抖音',
    rawText: '雨夜里，主角救下一只受伤的小猫。'
  })
})

test('普通用户直接访问短剧管理页面会被权限守卫拦截', async ({ page }) => {
  const capture: Capture = { projectListUrls: [], projectCreateBodies: [], settingEditBodies: [], settingSaveAttempts: 0 }
  await installSession(page)
  await mockEntryAndAdmin(page, capture)
  await page.route('**/dev-api/system/user/current', async (route) => {
    await fulfillJson(route, {
      userId: 2,
      username: 'creator',
      nickname: 'AIVideo Creator',
      tenantId: 1,
      roles: ['user'],
      permissions: []
    })
  })

  await page.goto('/ai/aivideo/settings', { waitUntil: 'networkidle' })

  await expect.poll(() => new URL(page.url()).pathname).toBe('/404')
  await expect(page.locator('.app-container .card-header').getByText('短剧基础配置')).toHaveCount(0)
})
test('公告富文本会移除脚本、事件处理器和危险链接', async ({ page }) => {
  const capture: Capture = { projectListUrls: [], projectCreateBodies: [], settingEditBodies: [], settingSaveAttempts: 0 }
  await installSession(page)
  await mockEntryAndAdmin(page, capture)

  await page.goto('/system/notice', { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: '查看' }).click()

  const content = page.locator('.notice-dialog .notice-content')
  await expect(content).toContainText('恶意链接')
  await expect(content.locator('script')).toHaveCount(0)
  await expect(content.locator('[onerror]')).toHaveCount(0)
  await expect(content.locator('a', { hasText: '恶意链接' })).not.toHaveAttribute('href')
  expect(await page.evaluate(() => (window as Window & { __aivideoXss?: number }).__aivideoXss)).toBeUndefined()
})
test('管理员查看失败任务，并在配置保存失败后恢复重试', async ({ page }) => {
  const capture: Capture = { projectListUrls: [], projectCreateBodies: [], settingEditBodies: [], settingSaveAttempts: 0 }
  await installSession(page)
  await mockEntryAndAdmin(page, capture)

  await page.goto('/ai/aivideo/tasks', { waitUntil: 'networkidle' })
  await expect(page.getByText('短剧生成任务')).toBeVisible()
  const taskRow = page.getByRole('row').filter({ hasText: 'VIDEO' })
  await expect(taskRow).toContainText('失败')
  await expect(taskRow).toContainText('42%')
  await taskRow.getByRole('button', { name: '详情' }).click()

  await expect.poll(() => new URL(page.url()).pathname).toBe('/ai/aivideo/tasks/7')
  await expect(page.getByText('供应商超时，可重试')).toBeVisible()
  await expect(page.getByText('PROVIDER_TIMEOUT')).toBeVisible()

  await page.goto('/ai/aivideo/settings', { waitUntil: 'networkidle' })
  await expect(page.locator('.app-container .card-header').getByText('短剧基础配置')).toBeVisible()
  const textModelInput = page.locator('.setting-form .el-form-item').filter({ hasText: '默认文本模型' }).locator('input')
  await expect(textModelInput).toHaveValue('101')
  await textModelInput.fill('201')

  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('模拟配置保存失败')).toBeVisible()
  await expect(textModelInput).toHaveValue('201')

  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('保存成功')).toBeVisible()
  expect(capture.settingSaveAttempts).toBe(2)
  expect(capture.settingEditBodies.at(-1)).toMatchObject({ textModelId: '201' })
})