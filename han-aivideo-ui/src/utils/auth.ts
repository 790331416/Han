const TokenKey = 'Admin-Token'
const RefreshTokenKey = 'Admin-Refresh-Token'

export function getToken(): string | null {
  return localStorage.getItem(TokenKey)
}

export function setToken(token: string): void {
  localStorage.setItem(TokenKey, token)
}

export function removeToken(): void {
  localStorage.removeItem(TokenKey)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(RefreshTokenKey)
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(RefreshTokenKey, token)
}

export function removeRefreshToken(): void {
  localStorage.removeItem(RefreshTokenKey)
}
