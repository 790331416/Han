/**
 * RSA 加密工具（使用浏览器原生 Web Crypto API，无需额外依赖）
 */

/**
 * 将 Base64 编码的 SPKI 公钥导入为 CryptoKey
 */
async function importPublicKey(base64Key: string): Promise<CryptoKey> {
  const binaryString = atob(base64Key)
  const bytes = new Uint8Array(binaryString.length)
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i)
  }
  return crypto.subtle.importKey(
    'spki',
    bytes.buffer,
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt']
  )
}

/**
 * 使用 RSA 公钥加密文本，返回 Base64 编码的密文
 */
export async function rsaEncrypt(plainText: string, publicKeyBase64: string): Promise<string> {
  const publicKey = await importPublicKey(publicKeyBase64)
  const encoded = new TextEncoder().encode(plainText)
  const encrypted = await crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    publicKey,
    encoded
  )
  const bytes = new Uint8Array(encrypted)
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary)
}
