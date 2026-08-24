import { generateKeyPairSync } from 'node:crypto'
import { test, expect, type Page } from '@playwright/test'

type CaptchaMode = 'enabled' | 'disabled' | 'error'
type PublicKeyMode = 'enabled' | 'disabled' | 'error'

interface MockOptions {
  captcha?: CaptchaMode
  publicKey?: PublicKeyMode
  insecureHttpAllowed?: boolean
  statusError?: boolean
  onRegister?: (body: Record<string, unknown>) => void
}

const applicationNo = 'VA-E2E-001'

function responseBody(data: unknown, code = 200, msg = 'ok') {
  return JSON.stringify({ code, msg, data, timestamp: Date.now() })
}

function publicKey() {
  const pair = generateKeyPairSync('rsa', { modulusLength: 2048 })
  return pair.publicKey.export({ type: 'spki', format: 'der' }).toString('base64')
}

async function mockAuthAndPublicApi(page: Page, options: MockOptions = {}) {
  const captcha = options.captcha || 'enabled'
  const keyMode = options.publicKey || 'enabled'
  const key = publicKey()

  await page.route('**/system/public/brand', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, data: { fullName: 'E2E', shortName: 'E2E', displayMode: 'FULL_NAME', loginSubtitle: '', logoUrl: '' } })
  }))
  await page.route('**/runtime/capabilities**', (route) => route.fulfill({
    contentType: 'application/json',
    body: responseBody({ tier: 'small', enabledModules: [], optionalServices: {}, featureFlags: {} })
  }))
  await page.route('**/auth/social/providers', (route) => route.fulfill({ contentType: 'application/json', body: responseBody({ github: false, wechat: false }) }))
  await page.route('**/auth/publicKey', (route) => route.fulfill({ contentType: 'application/json', body: responseBody({ enabled: true, publicKey: key }) }))
  await page.route('**/auth/captcha', async (route) => {
    if (captcha === 'error') {
      await route.fulfill({ status: 503, contentType: 'application/json', body: responseBody(null, 503, 'captcha unavailable') })
      return
    }
    const data = captcha === 'disabled'
      ? { enabled: 'false' }
      : { enabled: 'true', uuid: 'e2e-captcha', img: 'Y2FwdGNoYQ==' }
    await route.fulfill({ contentType: 'application/json', body: responseBody(data) })
  })
  await page.route('**/auth/vendor/publicKey', async (route) => {
    if (keyMode === 'error') {
      await route.fulfill({ status: 503, contentType: 'application/json', body: responseBody(null, 503, 'public key unavailable') })
      return
    }
    const data = keyMode === 'disabled'
      ? { enabled: false, allowInsecureHttp: Boolean(options.insecureHttpAllowed) }
      : { enabled: true, publicKey: key, allowInsecureHttp: Boolean(options.insecureHttpAllowed) }
    await route.fulfill({ contentType: 'application/json', body: responseBody(data) })
  })
  await page.route('**/auth/vendor/register', async (route) => {
    options.onRegister?.(route.request().postDataJSON() as Record<string, unknown>)
    await route.fulfill({ contentType: 'application/json', body: responseBody({ applicationNo }) })
  })
  await page.route('**/auth/vendor/application/status**', async (route) => {
    if (options.statusError) {
      await route.fulfill({ contentType: 'application/json', body: responseBody(null, 404, '申请不存在') })
      return
    }
    await route.fulfill({ contentType: 'application/json', body: responseBody({ applicationNo, status: 1, statusName: '待审核' }) })
  })
}

async function fillRequired(page: Page, includeCaptcha = true) {
  await page.getByRole('textbox', { name: '登录账号' }).fill('vendor-e2e')
  await page.getByRole('textbox', { name: '昵称' }).fill('E2E Vendor')
  await page.getByRole('textbox', { name: '登录密码' }).fill(['Vendor', 'Only', '2026', '!'].join(''))
  await page.getByRole('textbox', { name: '联系电话' }).first().fill('13800000001')
  if (includeCaptcha) await page.getByRole('textbox', { name: '验证码' }).fill('ABCD')
}

test.describe('厂商入驻公开入口', () => {
  test('登录页入口可进入申请页', async ({ page }) => {
    await mockAuthAndPublicApi(page)
    await page.goto('/login')
    await expect(page.getByTestId('login-page')).toBeVisible()
    await expect(page.getByTestId('vendor-apply-link')).toBeVisible()
  })

  test('验证码启用和明确关闭状态分别可见', async ({ page }) => {
    await mockAuthAndPublicApi(page, { captcha: 'enabled' })
    await page.goto('/open/vendor-apply')
    await expect(page.getByTestId('vendor-apply-captcha')).toBeVisible()

    const disabledPage = await page.context().newPage()
    await mockAuthAndPublicApi(disabledPage, { captcha: 'disabled' })
    await disabledPage.goto('/open/vendor-apply')
    await expect(disabledPage.getByTestId('vendor-apply-captcha')).toHaveCount(0)
    await disabledPage.close()
  })

  test('验证码请求失败时保留校验并禁止提交', async ({ page }) => {
    await mockAuthAndPublicApi(page, { captcha: 'error' })
    await page.goto('/open/vendor-apply')
    await expect(page.getByTestId('vendor-apply-captcha-error')).toBeVisible()
    await expect(page.getByTestId('vendor-apply-captcha')).toBeVisible()
    await expect(page.getByTestId('vendor-apply-submit')).toBeDisabled()
  })

  test('注册公钥不可用时禁止提交并显示提示', async ({ page }) => {
    await mockAuthAndPublicApi(page, { publicKey: 'disabled' })
    await page.goto('/open/vendor-apply')
    await expect(page.getByTestId('vendor-apply-public-key-error')).toBeVisible()
    await expect(page.getByTestId('vendor-apply-submit')).toBeDisabled()
    await expect(page).not.toHaveURL(/\/auth\/vendor\/register/)
  })

  test('HTTP 环境默认禁止提交密码', async ({ page }) => {
    await page.addInitScript(() => {
      Object.defineProperty(window, 'isSecureContext', { configurable: true, value: false })
    })
    await mockAuthAndPublicApi(page)
    await page.goto('/open/vendor-apply')

    await expect(page.getByTestId('vendor-apply-public-key-error')).toContainText('系统未开启测试兼容')
    await expect(page.getByTestId('vendor-apply-submit')).toBeDisabled()
  })

  test('HTTP 测试兼容开启时提交明文密码且显示警示', async ({ page }) => {
    let registerBody: Record<string, unknown> | undefined
    await page.addInitScript(() => {
      Object.defineProperty(window, 'isSecureContext', { configurable: true, value: false })
    })
    await mockAuthAndPublicApi(page, {
      insecureHttpAllowed: true,
      onRegister: (body) => { registerBody = body }
    })
    await page.goto('/open/vendor-apply')
    await fillRequired(page)

    await expect(page.getByTestId('vendor-apply-qualification')).toHaveCount(0)
    await expect(page.getByTestId('vendor-apply-insecure-http-warning')).toBeVisible()
    await page.getByTestId('vendor-apply-submit').click()
    await expect(page.locator('.el-message--success')).toContainText('可凭联系人电话查询审核进度')
    expect(typeof registerBody?.plainPassword).toBe('string')
    expect(registerBody?.encryptedPassword).toBeUndefined()
    expect(registerBody?.qualificationNo).toBeUndefined()
    expect(registerBody?.name).toBeUndefined()
    expect(registerBody?.contactName).toBeUndefined()
    expect(registerBody?.contactPhone).toBeUndefined()
  })

  test('成功提交后只按手机号查询进度并保留密码便于重试', async ({ page }) => {
    let registerBody: Record<string, unknown> | undefined
    let statusRequestUrl = ''
    const pageErrors: string[] = []
    page.on('pageerror', (error) => pageErrors.push(error.message))
    await mockAuthAndPublicApi(page, { onRegister: (body) => { registerBody = body } })
    await page.route('**/auth/vendor/application/status**', async (route) => {
      statusRequestUrl = route.request().url()
      await route.fulfill({ contentType: 'application/json', body: responseBody({ status: 1, statusName: '待审核' }) })
    })
    await page.goto('/open/vendor-apply')
    await fillRequired(page)

    await page.getByTestId('vendor-apply-submit').click()
    await expect(page.locator('.el-message--success')).toContainText('可凭联系人电话查询审核进度')
    await expect(page.getByRole('textbox', { name: '登录密码' })).toHaveValue('VendorOnly2026!')
    expect(typeof registerBody?.encryptedPassword).toBe('string')
    expect(registerBody?.password).toBeUndefined()
    expect(registerBody?.captchaCode).toBe('ABCD')
    expect(registerBody?.captchaUuid).toBe('e2e-captcha')

    await page.getByRole('textbox', { name: '联系人电话' }).last().fill('13800000001')
    await page.getByRole('button', { name: '查询' }).click()
    await expect(page.getByText('待审核')).toBeVisible()
    expect(statusRequestUrl).toContain('contactPhone=13800000001')
    expect(statusRequestUrl).not.toContain('applicationNo=')
    expect(pageErrors).toEqual([])
  })

  test('验证码错误后保留密码便于重新输入验证码', async ({ page }) => {
    await mockAuthAndPublicApi(page)
    await page.route('**/auth/vendor/register', (route) => route.fulfill({
      status: 400,
      contentType: 'application/json',
      body: responseBody(null, 400, '验证码错误')
    }))
    await page.goto('/open/vendor-apply')
    await fillRequired(page)

    await page.getByTestId('vendor-apply-submit').click()

    await expect(page.locator('.el-message--error').last()).toBeVisible()
    await expect(page.getByRole('textbox', { name: '登录密码' })).toHaveValue('VendorOnly2026!')
  })
})
