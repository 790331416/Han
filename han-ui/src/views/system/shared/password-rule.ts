/**
 * 管理端密码强度规则，与后端 `PasswordUtil.validate` 逐条对齐：
 * 8~20 位，且大写字母 / 小写字母 / 数字 / 特殊字符四类中至少包含三类。
 *
 * <p>后端在「新增用户」「重置密码」「个人中心改密」「新建租户管理员」四条路径上都会调用
 * `PasswordUtil.validate`，前端各入口必须引用本文件，避免再次出现「前端放行、后端抛业务异常」
 * 或「前端更严、合法密码被拦下」的分叉。
 *
 * <p>落位说明：公共前端工具的正式位置是 `src/utils/`，该目录当前由 ui-framework 组维护，
 * 本文件暂放系统管理板块内，待 `src/utils/` 就绪后整体迁移。
 */

/** 密码最小长度，对应后端 PasswordUtil.MIN_LENGTH */
export const PASSWORD_MIN_LENGTH = 8

/** 密码最大长度，对应后端 PasswordUtil.MAX_LENGTH */
export const PASSWORD_MAX_LENGTH = 20

/** 统一的输入框提示文案 */
export const PASSWORD_RULE_TEXT =
  `${PASSWORD_MIN_LENGTH}-${PASSWORD_MAX_LENGTH}位，含大写字母、小写字母、数字、特殊字符中的至少3种`

/**
 * 校验密码强度，通过返回 null，不通过返回与后端一致的错误文案。
 */
export function checkPasswordStrength(password: string): string | null {
  if (!password) {
    return '请输入密码'
  }
  if (password.length < PASSWORD_MIN_LENGTH) {
    return `密码长度不能少于${PASSWORD_MIN_LENGTH}位`
  }
  if (password.length > PASSWORD_MAX_LENGTH) {
    return `密码长度不能超过${PASSWORD_MAX_LENGTH}位`
  }
  let categories = 0
  if (/[a-z]/.test(password)) categories++
  if (/[A-Z]/.test(password)) categories++
  if (/[0-9]/.test(password)) categories++
  if (/[^a-zA-Z0-9]/.test(password)) categories++
  if (categories < 3) {
    return '密码必须包含大写字母、小写字母、数字、特殊字符中的至少3种'
  }
  return null
}

/**
 * el-form 表单项的 validator 形态。
 *
 * <p>空值不在这里拦截，是否必填由同一条规则链上的 `required` 决定。
 */
export function validatePasswordRule(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void
): void {
  if (!value) {
    callback()
    return
  }
  const message = checkPasswordStrength(value)
  callback(message ? new Error(message) : undefined)
}

/**
 * ElMessageBox.prompt 的 inputValidator 形态：通过返回 true，不通过返回错误文案。
 */
export function passwordInputValidator(value: string): true | string {
  return checkPasswordStrength(value) ?? true
}
