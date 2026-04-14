import type { APIRequestContext } from '@playwright/test'
import { test, e2eRuntime } from '../fixtures/test'
import { assertPageHealthy, pageHealthTargets, shouldRunForTier, type RuntimeTier } from '../utils/page-health'

async function resolveRuntimeTier(apiRequest: APIRequestContext): Promise<RuntimeTier> {
  const response = await apiRequest.get(`${e2eRuntime.apiBaseUrl}/system/runtime/capabilities`)
  const payload = await response.json()
  return (payload?.data?.tier || 'small') as RuntimeTier
}

for (const target of pageHealthTargets) {
  test(`page health ${target.path}`, async ({ authenticatedPage }) => {
    const tier = await resolveRuntimeTier(authenticatedPage.request)
    test.skip(!shouldRunForTier(target, tier), `${target.path} is not enabled for ${tier}`)
    await assertPageHealthy(authenticatedPage, e2eRuntime.apiBaseUrl, target)
  })
}
