export function isBenignAlipaySyncError(message: string) {
  const text = String(message || '').trim()
  return (
    text.includes('暂无待同步') ||
    text.includes('未找到该任务的支付宝订单') ||
    text.includes('充值订单不存在') ||
    text.includes('交易不存在') ||
    text.includes('账单不存在') ||
    text.includes('TRADE_NOT_EXIST')
  )
}