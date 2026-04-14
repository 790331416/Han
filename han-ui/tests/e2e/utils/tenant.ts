import type { APIRequestContext } from '@playwright/test'

interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

interface PageResult<T> {
  rows: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

export interface TenantRecord {
  tenantId: string | number
  tenantName: string
  packageName?: string
  userCount?: number
  userLimit?: number
}

export interface TenantPackageRecord {
  packageId: string | number
  packageName: string
  tenantCount: number
}

export interface TenantQuotaRecord {
  tenantId: string | number
  userLimit?: number
  userUsed?: number
  storageLimit?: number
  storageUsed?: number
  apiLimit?: number
  apiUsed?: number
  resetCycle?: string
}

function buildHeaders(accessToken: string): Record<string, string> {
  return {
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  }
}

export async function fetchTenantList(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string
): Promise<TenantRecord[]> {
  const response = await request.get(`${apiBaseUrl}/tenant/list?pageNum=1&pageSize=10`, {
    headers: buildHeaders(accessToken)
  })
  const result = (await response.json()) as ApiEnvelope<PageResult<TenantRecord>>
  if (!response.ok() || result.code !== 200) {
    throw new Error(`Failed to fetch tenant list: ${JSON.stringify(result)}`)
  }
  return result.data?.rows || []
}

export async function fetchTenantPackages(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string
): Promise<TenantPackageRecord[]> {
  const response = await request.get(`${apiBaseUrl}/tenant/package/list?pageNum=1&pageSize=10`, {
    headers: buildHeaders(accessToken)
  })
  const result = (await response.json()) as ApiEnvelope<PageResult<TenantPackageRecord>>
  if (!response.ok() || result.code !== 200) {
    throw new Error(`Failed to fetch tenant package list: ${JSON.stringify(result)}`)
  }
  return result.data?.rows || []
}

export async function fetchValidTenants(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string
): Promise<TenantRecord[]> {
  const response = await request.get(`${apiBaseUrl}/tenant/listAllValid`, {
    headers: buildHeaders(accessToken)
  })
  const result = (await response.json()) as ApiEnvelope<TenantRecord[]>
  if (!response.ok() || result.code !== 200) {
    throw new Error(`Failed to fetch valid tenants: ${JSON.stringify(result)}`)
  }
  return result.data || []
}

export async function fetchTenantQuota(
  request: APIRequestContext,
  apiBaseUrl: string,
  accessToken: string,
  tenantId: string | number
): Promise<TenantQuotaRecord> {
  const response = await request.get(`${apiBaseUrl}/tenant/quota/${tenantId}`, {
    headers: buildHeaders(accessToken)
  })
  const result = (await response.json()) as ApiEnvelope<TenantQuotaRecord>
  if (!response.ok() || result.code !== 200) {
    throw new Error(`Failed to fetch tenant quota: ${JSON.stringify(result)}`)
  }
  return result.data
}
