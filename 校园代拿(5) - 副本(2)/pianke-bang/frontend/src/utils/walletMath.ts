/** 钱包提现：常量与纯计算（不碰 Vue 状态） */

export const WITHDRAW_MIN = 1
export const WITHDRAW_STEP = 0.1
export const WITHDRAW_FEE_THRESHOLD = 100
export const WITHDRAW_FEE = 0.1
export const WITHDRAW_HARD_MAX = 10000

export function floorToWalletWithdrawStep(n: number) {
  if (!Number.isFinite(n) || n <= 0) return 0
  return Math.floor(n * 10 + 1e-8) / 10
}

export function calcWalletWithdrawFee(amount: number) {
  if (!Number.isFinite(amount) || amount < WITHDRAW_MIN) return 0
  return amount < WITHDRAW_FEE_THRESHOLD ? WITHDRAW_FEE : 0
}

export function calcWalletWithdrawPayout(amount: number) {
  const a = Number(amount)
  if (!Number.isFinite(a)) return 0
  return Math.max(0, Math.round((a - calcWalletWithdrawFee(a)) * 100) / 100)
}

export function isValidWalletWithdrawStep(amount: number) {
  if (!Number.isFinite(amount)) return false
  return Math.round(amount * 100) % 10 === 0
}

/** @param max 当前用户最多可提金额（由调用方传入 walletMaxWithdrawable） */
export function sanitizeWalletWithdrawAmountInput(raw: string, max: number, hardMax = WITHDRAW_HARD_MAX) {
  let s = String(raw || '').replace(/[^\d.]/g, '')
  const firstDot = s.indexOf('.')
  if (firstDot >= 0) {
    s = s.slice(0, firstDot + 1) + s.slice(firstDot + 1).replace(/\./g, '')
    const [intPart, decPart = ''] = s.split('.')
    s = `${intPart}.${decPart.slice(0, 1)}`
  }
  if (s.includes('.')) {
    const [i, d] = s.split('.')
    const intNorm = i.replace(/^0+(?=\d)/, '') || '0'
    s = `${intNorm}.${d}`
  } else if (s.length > 1) {
    s = s.replace(/^0+(?=\d)/, '')
  }

  const maxIntLen = String(Math.floor(hardMax)).length
  if (s.includes('.')) {
    const [i, d] = s.split('.')
    if (i.length > maxIntLen) s = `${i.slice(0, maxIntLen)}.${d}`
  } else if (s.length > maxIntLen) {
    s = s.slice(0, maxIntLen)
  }

  if (s !== '' && s !== '.') {
    const n = Number(s)
    if (Number.isFinite(n) && max >= 0 && n > max) {
      s = max > 0 ? max.toFixed(1) : '0'
    }
  }
  return s
}
