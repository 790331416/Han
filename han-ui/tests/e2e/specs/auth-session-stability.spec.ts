import { test, expect } from '@playwright/test'

const TOKEN_KEY = 'Admin-Token'
const REFRESH_TOKEN_KEY = 'Admin-Refresh-Token'
const USER_STORE_KEY = 'HAN-user'

async function bootstrapSession(page: import('@playwright/test').Page): Promise<void> {
  await page.context().addInitScript(
    ({ tokenKey, refreshTokenKey, userStoreKey }) => {
      localStorage.setItem(tokenKey, 'mock-access-token')
      localStorage.setItem(refreshTokenKey, 'mock-refresh-token')
      localStorage.setItem(
        userStoreKey,
        JSON.stringify({
          token: 'mock-access-token',
          tenantId: 1,
          _userId: 9527
        })
      )
    },
    {
      tokenKey: TOKEN_KEY,
      refreshTokenKey: REFRESH_TOKEN_KEY,
      userStoreKey: USER_STORE_KEY
    }
  )
}

async function mockRuntimeCapabilities(page: import('@playwright/test').Page): Promise<void> {
  await page.route('**/system/runtime/capabilities', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        msg: 'success',
        data: {
          tier: 'full',
          enabledModules: ['ai', 'system', 'workflow', 'job'],
          optionalServices: {},
          featureFlags: {}
        },
        timestamp: Date.now()
      })
    })
  })
}

interface MockAiChatState {
  conversationId: number
  refreshRequestCount: number
  regenerateRequestCount: number
  editRegenerateRequestCount: number
  latestUserContent: string
  latestAssistantContent: string
}

/**
 * 构造 AI 对话最小运行环境，用于验证重新生成和编辑重生成链路在 401 后是否先走会话续期。
 */
async function mockAiChatBootstrap(page: import('@playwright/test').Page): Promise<MockAiChatState> {
  const state: MockAiChatState = {
    conversationId: 1001,
    refreshRequestCount: 0,
    regenerateRequestCount: 0,
    editRegenerateRequestCount: 0,
    latestUserContent: '原始用户提问',
    latestAssistantContent: '原始助手回复'
  }

  await page.route('**/system/user/current', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        msg: 'success',
        data: {
          userId: 9527,
          tenantId: 1,
          deptId: 1,
          username: 'admin',
          nickname: '管理员',
          avatar: '',
          phone: '',
          email: '',
          roles: ['admin'],
          permissions: ['*:*:*']
        },
        timestamp: Date.now()
      })
    })
  })

  await page.route('**/ai/model/all**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        msg: 'success',
        data: [
          {
            modelId: 2001,
            modelName: '测试对话模型',
            modelType: 'LLM',
            provider: 'mock'
          }
        ],
        timestamp: Date.now()
      })
    })
  })

  await page.route('**/ai/kb/all**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        msg: 'success',
        data: [],
        timestamp: Date.now()
      })
    })
  })

  await page.route('**/ai/mcp/all**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        msg: 'success',
        data: [],
        timestamp: Date.now()
      })
    })
  })

  await page.route('**/ai/chat/conversations**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        msg: 'success',
        data: {
          rows: [
            {
              conversationId: state.conversationId,
              title: '续期稳定性对话',
              workflowId: null,
              modelId: 2001
            }
          ],
          total: 1
        },
        timestamp: Date.now()
      })
    })
  })

  await page.route(`**/ai/chat/messages/${state.conversationId}`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        msg: 'success',
        data: [
          {
            messageId: 5001,
            conversationId: state.conversationId,
            role: 'user',
            content: state.latestUserContent,
            sortOrder: 1
          },
          {
            messageId: 5002,
            conversationId: state.conversationId,
            role: 'assistant',
            content: state.latestAssistantContent,
            sortOrder: 2
          }
        ],
        timestamp: Date.now()
      })
    })
  })

  await page.route('**/auth/refresh', async (route) => {
    state.refreshRequestCount += 1
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        msg: 'success',
        data: {
          accessToken: 'chat-refreshed-access-token',
          refreshToken: 'chat-refreshed-refresh-token',
          expireIn: 7200,
          forceChangePwd: false,
          userInfo: {
            userId: 9527,
            username: 'admin',
            nickname: '管理员',
            avatar: '',
            phone: ''
          }
        },
        timestamp: Date.now()
      })
    })
  })

  await page.route(`**/ai/chat/regenerate/${state.conversationId}`, async (route) => {
    state.regenerateRequestCount += 1

    if (state.regenerateRequestCount === 1) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 401,
          msg: 'Token 已过期',
          data: null,
          timestamp: Date.now()
        })
      })
      return
    }

    state.latestAssistantContent = '续期后重新生成成功'
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream'
      },
      body: 'data: {"type":"delta","content":"续期后重新生成成功"}\n\ndata: [DONE]\n\n'
    })
  })

  await page.route('**/ai/chat/edit-regenerate', async (route) => {
    state.editRegenerateRequestCount += 1

    if (state.editRegenerateRequestCount === 1) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 401,
          msg: 'Token 已过期',
          data: null,
          timestamp: Date.now()
        })
      })
      return
    }

    const payload = route.request().postDataJSON() as { content?: string } | null
    state.latestUserContent = payload?.content || '编辑后的用户提问'
    state.latestAssistantContent = '续期后编辑重生成成功'
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream'
      },
      body: 'data: {"type":"delta","content":"续期后编辑重生成成功"}\n\ndata: [DONE]\n\n'
    })
  })

  return state
}

/**
 * 认证稳定性回归：
 * 1. 用户信息接口发生非 401 业务失败时，不应该直接踢回登录页。
 * 2. 线上默认不发送仅用于本地调试绕过的 X-User-Id 请求头。
 */
test.describe('auth session stability', () => {
  test('temporary refresh failure should not clear session or redirect to login', async ({ page }) => {
    await bootstrapSession(page)
    await mockRuntimeCapabilities(page)

    let currentUserRequestCount = 0
    let refreshRequestCount = 0

    await page.route('**/system/user/current', async (route) => {
      currentUserRequestCount += 1
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 401,
          msg: 'Token 已过期',
          data: null,
          timestamp: Date.now()
        })
      })
    })

    await page.route('**/auth/refresh', async (route) => {
      refreshRequestCount += 1
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 503,
          msg: '认证服务暂时不可用',
          data: null,
          timestamp: Date.now()
        })
      })
    })

    await page.goto('http://127.0.0.1:3000/')
    await page.waitForTimeout(1800)

    expect(currentUserRequestCount).toBe(1)
    expect(refreshRequestCount).toBe(1)
    await expect(page).not.toHaveURL(/\/login/)

    const storageState = await page.evaluate(({ tokenKey, refreshTokenKey, userStoreKey }) => ({
      accessToken: window.localStorage.getItem(tokenKey),
      refreshToken: window.localStorage.getItem(refreshTokenKey),
      persistedStore: window.localStorage.getItem(userStoreKey)
    }), {
      tokenKey: TOKEN_KEY,
      refreshTokenKey: REFRESH_TOKEN_KEY,
      userStoreKey: USER_STORE_KEY
    })

    expect(storageState.accessToken).toBe('mock-access-token')
    expect(storageState.refreshToken).toBe('mock-refresh-token')
    expect(storageState.persistedStore).toContain('mock-access-token')
  })

  test('expired access token should refresh session instead of redirecting to login', async ({ page }) => {
    await bootstrapSession(page)
    await mockRuntimeCapabilities(page)

    let currentUserRequestCount = 0
    let refreshRequestCount = 0

    await page.route('**/system/user/current', async (route) => {
      currentUserRequestCount += 1

      if (currentUserRequestCount === 1) {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 401,
            msg: 'Token 已过期',
            data: null,
            timestamp: Date.now()
          })
        })
        return
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          msg: 'success',
          data: {
            userId: 1,
            tenantId: 1,
            deptId: 1,
            username: 'admin',
            nickname: '管理员',
            avatar: '',
            phone: '',
            email: '',
            roles: ['admin'],
            permissions: ['*:*:*']
          },
          timestamp: Date.now()
        })
      })
    })

    await page.route('**/auth/refresh', async (route) => {
      refreshRequestCount += 1
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          msg: 'success',
          data: {
            accessToken: 'refreshed-access-token',
            refreshToken: 'refreshed-refresh-token',
            expireIn: 7200,
            forceChangePwd: false,
            userInfo: {
              userId: 1,
              username: 'admin',
              nickname: '管理员',
              avatar: '',
              phone: ''
            }
          },
          timestamp: Date.now()
        })
      })
    })

    await page.goto('http://127.0.0.1:3000/')
    await page.waitForTimeout(1800)

    expect(currentUserRequestCount).toBeGreaterThanOrEqual(2)
    expect(refreshRequestCount).toBe(1)
    await expect(page).not.toHaveURL(/\/login/)

    const storageState = await page.evaluate(({ tokenKey, refreshTokenKey, userStoreKey }) => ({
      accessToken: window.localStorage.getItem(tokenKey),
      refreshToken: window.localStorage.getItem(refreshTokenKey),
      persistedStore: window.localStorage.getItem(userStoreKey)
    }), {
      tokenKey: TOKEN_KEY,
      refreshTokenKey: REFRESH_TOKEN_KEY,
      userStoreKey: USER_STORE_KEY
    })

    expect(storageState.accessToken).toBe('refreshed-access-token')
    expect(storageState.refreshToken).toBe('refreshed-refresh-token')
    expect(storageState.persistedStore).toContain('refreshed-access-token')
  })

  test('non-401 getInfo failure should not force redirect to login', async ({ page }) => {
    await bootstrapSession(page)
    await mockRuntimeCapabilities(page)
    let currentUserRequestSeen = false

    await page.route('**/system/user/current', async (route) => {
      currentUserRequestSeen = true
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 500,
          msg: '模拟用户信息加载失败',
          data: null,
          timestamp: Date.now()
        })
      })
    })

    await page.goto('http://127.0.0.1:3000/')

    await page.waitForTimeout(1500)

    expect(currentUserRequestSeen).toBeTruthy()
    await expect(page).not.toHaveURL(/\/login/)
    await expect(page.getByTestId('login-page')).toHaveCount(0)
  })

  test('protected requests should not send X-User-Id header by default', async ({ page }) => {
    await bootstrapSession(page)
    await mockRuntimeCapabilities(page)

    let capturedUserIdHeader: string | undefined
    let currentUserRequestSeen = false

    await page.route('**/system/user/current', async (route, request) => {
      currentUserRequestSeen = true
      capturedUserIdHeader = request.headers()['x-user-id']
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          msg: 'success',
          data: {
            userId: 1,
            tenantId: 1,
            deptId: 1,
            username: 'admin',
            nickname: '管理员',
            avatar: '',
            phone: '',
            email: '',
            roles: ['admin'],
            permissions: ['*:*:*']
          },
          timestamp: Date.now()
        })
      })
    })

    await page.goto('http://127.0.0.1:3000/')
    await page.waitForTimeout(1500)

    expect(currentUserRequestSeen).toBeTruthy()
    expect(capturedUserIdHeader).toBeUndefined()
  })

  test('ai stream should refresh token before clearing session on unauthorized', async ({ page }) => {
    await bootstrapSession(page)
    await page.goto('http://127.0.0.1:3000/login')

    let refreshRequestCount = 0
    await page.route('**/auth/refresh', async (route) => {
      refreshRequestCount += 1
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          msg: 'success',
          data: {
            accessToken: 'stream-refreshed-access-token',
            refreshToken: 'stream-refreshed-refresh-token',
            expireIn: 7200,
            forceChangePwd: false,
            userInfo: {
              userId: 9527,
              username: 'admin',
              nickname: '管理员',
              avatar: '',
              phone: ''
            }
          },
          timestamp: Date.now()
        })
      })
    })

    const result = await page.evaluate(async ({ tokenKey, refreshTokenKey, userStoreKey }) => {
      window.localStorage.setItem(tokenKey, 'mock-access-token')
      window.localStorage.setItem(refreshTokenKey, 'mock-refresh-token')
      window.localStorage.setItem(
        userStoreKey,
        JSON.stringify({
          token: 'mock-access-token',
          tenantId: 1,
          _userId: 9527
        })
      )

      const originalFetch = window.fetch.bind(window)
      let streamRequestCount = 0

      window.fetch = async (input, init) => {
        const url = typeof input === 'string' ? input : input.url

        if (url.endsWith('/mock-ai-stream')) {
          streamRequestCount += 1

          if (streamRequestCount === 1) {
            return new Response('', { status: 401, statusText: 'Unauthorized' })
          }

          return new Response(
            'data: {"type":"delta","content":"续期成功"}\n\ndata: [DONE]\n\n',
            {
              status: 200,
              headers: {
                'Content-Type': 'text/event-stream'
              }
            }
          )
        }

        return originalFetch(input, init)
      }

      try {
        const streamModule = await import('/src/utils/ai-stream.ts')
        const content = await streamModule.requestAiStream({
          baseUrl: '',
          path: '/mock-ai-stream',
          token: 'mock-access-token'
        })

        return {
          content,
          streamRequestCount,
          token: window.localStorage.getItem(tokenKey),
          refreshToken: window.localStorage.getItem(refreshTokenKey),
          persistedUserStore: window.localStorage.getItem(userStoreKey)
        }
      } finally {
        window.fetch = originalFetch
      }
    }, {
      tokenKey: TOKEN_KEY,
      refreshTokenKey: REFRESH_TOKEN_KEY,
      userStoreKey: USER_STORE_KEY
    })

    expect(result.content).toBe('续期成功')
    expect(result.streamRequestCount).toBe(2)
    expect(refreshRequestCount).toBe(1)
    expect(result.token).toBe('stream-refreshed-access-token')
    expect(result.refreshToken).toBe('stream-refreshed-refresh-token')
    expect(result.persistedUserStore).toContain('stream-refreshed-access-token')
  })

  test('ai stream expired refresh should clear persisted user session state', async ({ page }) => {
    await bootstrapSession(page)
    await page.goto('http://127.0.0.1:3000/login')

    let refreshRequestCount = 0
    await page.route('**/auth/refresh', async (route) => {
      refreshRequestCount += 1
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 401,
          msg: '刷新 Token 已过期，请重新登录',
          data: null,
          timestamp: Date.now()
        })
      })
    })

    const result = await page.evaluate(async ({ tokenKey, refreshTokenKey, userStoreKey }) => {
      window.localStorage.setItem(tokenKey, 'mock-access-token')
      window.localStorage.setItem(refreshTokenKey, 'mock-refresh-token')
      window.localStorage.setItem(
        userStoreKey,
        JSON.stringify({
          token: 'mock-access-token',
          tenantId: 1,
          _userId: 9527
        })
      )

      const originalFetch = window.fetch.bind(window)
      window.fetch = async () => new Response('', { status: 401, statusText: 'Unauthorized' })

      try {
        const streamModule = await import('/src/utils/ai-stream.ts')
        await streamModule.requestAiStream({
          baseUrl: '',
          path: '/mock-ai-stream',
          token: 'mock-access-token'
        })
      } catch {
        // 这里只关心 401 后的本地会话清理结果。
      } finally {
        window.fetch = originalFetch
      }

      return {
        token: window.localStorage.getItem(tokenKey),
        refreshToken: window.localStorage.getItem(refreshTokenKey),
        persistedUserStore: window.localStorage.getItem(userStoreKey)
      }
    }, {
      tokenKey: TOKEN_KEY,
      refreshTokenKey: REFRESH_TOKEN_KEY,
      userStoreKey: USER_STORE_KEY
    })

    expect(refreshRequestCount).toBe(1)
    expect(result.token).toBeNull()
    expect(result.refreshToken).toBeNull()
    expect(
      result.persistedUserStore === null || !result.persistedUserStore.includes('mock-access-token')
    ).toBeTruthy()
  })

  test('ai stream temporary refresh failure should keep local session and stay on current page', async ({ page }) => {
    await bootstrapSession(page)
    await page.goto('http://127.0.0.1:3000/login')

    let refreshRequestCount = 0
    await page.route('**/auth/refresh', async (route) => {
      refreshRequestCount += 1
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 503,
          msg: '认证服务暂时不可用',
          data: null,
          timestamp: Date.now()
        })
      })
    })

    const result = await page.evaluate(async ({ tokenKey, refreshTokenKey, userStoreKey }) => {
      window.localStorage.setItem(tokenKey, 'mock-access-token')
      window.localStorage.setItem(refreshTokenKey, 'mock-refresh-token')
      window.localStorage.setItem(
        userStoreKey,
        JSON.stringify({
          token: 'mock-access-token',
          tenantId: 1,
          _userId: 9527
        })
      )

      const originalFetch = window.fetch.bind(window)
      window.fetch = async () => new Response('', { status: 401, statusText: 'Unauthorized' })

      try {
        const streamModule = await import('/src/utils/ai-stream.ts')
        await streamModule.requestAiStream({
          baseUrl: '',
          path: '/mock-ai-stream',
          token: 'mock-access-token'
        })
        return { errorMessage: '' }
      } catch (error) {
        return {
          errorMessage: error instanceof Error ? error.message : String(error),
          token: window.localStorage.getItem(tokenKey),
          refreshToken: window.localStorage.getItem(refreshTokenKey),
          persistedUserStore: window.localStorage.getItem(userStoreKey),
          pathname: window.location.pathname
        }
      } finally {
        window.fetch = originalFetch
      }
    }, {
      tokenKey: TOKEN_KEY,
      refreshTokenKey: REFRESH_TOKEN_KEY,
      userStoreKey: USER_STORE_KEY
    })

    expect(refreshRequestCount).toBe(1)
    expect(result.errorMessage).toContain('认证服务暂时不可用')
    expect(result.token).toBe('mock-access-token')
    expect(result.refreshToken).toBe('mock-refresh-token')
    expect(result.persistedUserStore).toContain('mock-access-token')
    expect(result.pathname).toBe('/login')
  })

  test('ai chat regenerate should refresh session before redirecting to login', async ({ page }) => {
    await bootstrapSession(page)
    await mockRuntimeCapabilities(page)
    const state = await mockAiChatBootstrap(page)

    await page.goto('http://127.0.0.1:3000/ai/chat')
    await expect(page.getByTestId('ai-chat-page')).toBeVisible()
    await expect(page.getByTestId('ai-chat-regenerate-button')).toBeVisible()

    await page.getByTestId('ai-chat-regenerate-button').click()

    await expect(page).not.toHaveURL(/\/login/)
    await expect(
      page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').last().locator('.message-text')
    ).toContainText('续期后重新生成成功', { timeout: 10_000 })

    const storageState = await page.evaluate(({ tokenKey, refreshTokenKey, userStoreKey }) => ({
      accessToken: window.localStorage.getItem(tokenKey),
      refreshToken: window.localStorage.getItem(refreshTokenKey),
      persistedStore: window.localStorage.getItem(userStoreKey)
    }), {
      tokenKey: TOKEN_KEY,
      refreshTokenKey: REFRESH_TOKEN_KEY,
      userStoreKey: USER_STORE_KEY
    })

    expect(state.regenerateRequestCount).toBe(2)
    expect(state.refreshRequestCount).toBe(1)
    expect(storageState.accessToken).toBe('chat-refreshed-access-token')
    expect(storageState.refreshToken).toBe('chat-refreshed-refresh-token')
    expect(storageState.persistedStore).toContain('chat-refreshed-access-token')
  })

  test('ai chat edit regenerate should refresh session before redirecting to login', async ({ page }) => {
    await bootstrapSession(page)
    await mockRuntimeCapabilities(page)
    const state = await mockAiChatBootstrap(page)

    await page.goto('http://127.0.0.1:3000/ai/chat')
    await expect(page.getByTestId('ai-chat-page')).toBeVisible()

    await page.getByTestId('ai-chat-edit-button').last().click()
    await page.getByTestId('ai-chat-edit-input').fill('编辑后的用户提问')
    await page.getByTestId('ai-chat-edit-submit-button').click()

    await expect(page).not.toHaveURL(/\/login/)
    await expect(
      page.locator('[data-testid="ai-chat-message"][data-role="user"]').last().locator('.message-text')
    ).toContainText('编辑后的用户提问', { timeout: 10_000 })
    await expect(
      page.locator('[data-testid="ai-chat-message"][data-role="assistant"]').last().locator('.message-text')
    ).toContainText('续期后编辑重生成成功', { timeout: 10_000 })

    const storageState = await page.evaluate(({ tokenKey, refreshTokenKey, userStoreKey }) => ({
      accessToken: window.localStorage.getItem(tokenKey),
      refreshToken: window.localStorage.getItem(refreshTokenKey),
      persistedStore: window.localStorage.getItem(userStoreKey)
    }), {
      tokenKey: TOKEN_KEY,
      refreshTokenKey: REFRESH_TOKEN_KEY,
      userStoreKey: USER_STORE_KEY
    })

    expect(state.editRegenerateRequestCount).toBe(2)
    expect(state.refreshRequestCount).toBe(1)
    expect(storageState.accessToken).toBe('chat-refreshed-access-token')
    expect(storageState.refreshToken).toBe('chat-refreshed-refresh-token')
    expect(storageState.persistedStore).toContain('chat-refreshed-access-token')
  })
})
