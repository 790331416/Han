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
  userId: number
  tenantId: number
  deptId: number
  username: string
  nickname: string
  avatar: string
  phone: string
  email: string
  roles: string[]
  permissions: string[]
}

// 登录请求
export interface LoginDTO {
  username: string
  password: string
  code?: string
  uuid?: string
  tenantId?: number
}

// 登录响应
export interface LoginVO {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userInfo: {
    userId: number
    username: string
    nickname: string
    avatar: string
    phone: string
  }
}

// 路由菜单
export interface RouteMenu {
  id: number
  parentId: number
  menuName: string
  menuType: string
  path: string
  component: string | null
  perms: string | null
  icon: string
  sort: number
  visible: number
  status: number
  children?: RouteMenu[]
}
