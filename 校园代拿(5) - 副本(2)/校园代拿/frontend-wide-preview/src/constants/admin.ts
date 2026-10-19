/**
 * @deprecated 管理员权限已改为登录态返回的 platformAdmin / accountRole，勿再维护本地名单。
 * 保留空实现仅防止旧引用编译失败。
 */
export function isAdminUsername(_name: string): boolean {
  return false
}
