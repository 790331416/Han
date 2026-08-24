// 通用响应类型
export interface R<T = any> {
  code: number
  msg: string
  data: T
  timestamp: number
}

// 分页响应
export interface PageResult<T = any> {
  total: number
  rows: T[]
  pageNum: number
  pageSize: number
  pages: number
}

// 分页请求
export interface PageQuery {
  pageNum?: number
  pageSize?: number
}

// 登录用户信息
export interface UserInfo {
  userId: string | number
  tenantId: string | number
  deptId: string | number
  username: string
  nickname: string
  avatar: string
  phone: string
  email: string
  roles: string[]
  permissions: string[]
  /** 当前学校身份（后端 userinfo 暂未返回，字段预留，实际以 /auth/identities 为准）。 */
  identityId?: string | number
  schoolId?: string | number
  schoolName?: string
  personType?: string
  dutyCode?: string
  dutyName?: string
  identityDisplayName?: string
}

/**
 * 学校身份摘要（登录身份选择 / 顶部身份展示 / 身份切换）。
 *
 * 后端 IdentityVO 实际返回：identityId/schoolId/schoolName/personType/dutyCode/
 * dutyName/identityDisplayName/current，以及后端计算的管理端可用性
 * managementAvailable / managementUnavailableReason。前端**不再**按 dutyCode
 * 推导管理端可用性，一律直接使用后端字段。
 */
export interface IdentityVO {
  identityId: string | number
  schoolId: string | number
  schoolName: string
  personType: string
  dutyCode: string
  dutyName: string
  identityDisplayName: string
  current: boolean
  /** 后端计算的管理端可用性（如 SCHOOL_ADMIN 无管理角色时为 false）。 */
  managementAvailable: boolean
  /** 管理端不可用原因（后端返回，如“无管理端角色”）。 */
  managementUnavailableReason?: string
}

// 登录请求
export interface LoginDTO {
  username: string
  password: string
  code?: string
  uuid?: string
  tenantId?: string | number
}

// 登录响应
export interface LoginVO {
  accessToken: string
  refreshToken: string
  expiresIn: number
  forceChangePassword: boolean
  requireTotp: boolean
  /** 多学校身份：为 true 时未签发正式 Token，需凭 identityTicket 选择身份。 */
  requireIdentity?: boolean
  identityTicket?: string
  identities?: IdentityVO[]
  userInfo: {
    userId: string | number
    username: string
    nickname: string
    avatar: string
    phone: string
  }
}

// 路由菜单
export interface RouteMenu {
  id?: string | number
  parentId?: string | number
  menuName?: string
  menuType?: string
  name?: string
  path: string
  component: string | null
  perms?: string | null
  icon?: string
  sort?: number
  visible?: number
  hidden?: boolean
  redirect?: string | null
  meta?: { title?: string; icon?: string; noCache?: boolean; link?: string | null }
  status?: number
  children?: RouteMenu[]
}

export interface RuntimeCapability {
  tier: 'small' | 'medium' | 'full'
  enabledModules: string[]
  optionalServices: Record<string, boolean>
  featureFlags: Record<string, boolean>
}
