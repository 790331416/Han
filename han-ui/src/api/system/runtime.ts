import { get } from '@/utils/request'
import type { RuntimeCapability } from '@/types'

export function getRuntimeCapabilities() {
  return get<RuntimeCapability>('/system/runtime/capabilities', undefined, { silentError: true })
}
