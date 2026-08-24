import { get, post } from '@/utils/request'

export interface OpenApiResource {
  id?: string | number
  resourceCode: string
  resourceName: string
  category: string
  httpMethod: string
  path: string
  scopeCode: string
  description?: string
  sensitivity?: string
  status?: number
  sort?: number
  publishStatus?: number
  allowApply?: number
  allowTest?: number
  owner?: string
}

export interface OpenApiResourceVersion {
  id?: string | number
  resourceId?: string | number
  version: string
  openapiSchema?: Record<string, unknown>
  requestExample?: Record<string, unknown>
  responseExamples?: Record<string, unknown>
  errorExamples?: Record<string, unknown>
  authConfig?: Record<string, unknown>
  sandboxConfig?: Record<string, unknown>
  status?: number
  publishedAt?: string
  deprecatedAt?: string
}

export interface OpenApiResourceDetail extends OpenApiResource {
  versions: OpenApiResourceVersion[]
  currentVersion?: OpenApiResourceVersion
}

export function listOpenApiResource() {
  return get<OpenApiResource[]>('/open/api-resource/list', { includeDisabled: true })
}

export function getOpenApiResourceDetail(id: string | number) {
  return get<OpenApiResourceDetail>(`/open/api-resource/${id}`)
}

export function addOpenApiResource(data: OpenApiResource) {
  return post<void>('/open/api-resource', data)
}

export function updateOpenApiResource(data: OpenApiResource) {
  return post<void>('/open/api-resource/edit', data)
}

export function removeOpenApiResource(id: string | number) {
  return post<void>(`/open/api-resource/remove/${id}`)
}

export function changeOpenApiResourceStatus(id: string | number, status: number) {
  return post<void>('/open/api-resource/changeStatus', { id, status })
}

export function createOpenApiResourceDraftVersion(resourceId: string | number, data: OpenApiResourceVersion) {
  return post<OpenApiResourceVersion>(`/open/api-resource/${resourceId}/versions`, data)
}

export function updateOpenApiResourceDraftVersion(data: OpenApiResourceVersion) {
  return post<OpenApiResourceVersion>('/open/api-resource/versions/edit', data)
}

export function publishOpenApiResourceVersion(versionId: string | number) {
  return post<OpenApiResourceVersion>(`/open/api-resource/versions/${versionId}/publish`)
}

export function deprecateOpenApiResourceVersion(versionId: string | number) {
  return post<OpenApiResourceVersion>(`/open/api-resource/versions/${versionId}/deprecate`)
}
