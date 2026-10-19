import type { WalletOverview, WalletTransactionItem, WalletWithdrawItem } from '../types/app'

/**
 * WalletView 由 stores/use*Store 提供的字段
 */
export interface WalletViewInject {
  myName: string
  walletSubModule: 'overview' | 'withdraws' | 'transactions'
  /** 未认证接单员仅可查看钱包，不可提现 */
  isRiderApproved: boolean
  walletOverview: WalletOverview
  walletWithdrawForm: {
    amount: number
    amountText: string
    accountName: string
    accountNo: string
    remark: string
  }
  walletWithdrawMin: number
  walletWithdrawStep: number
  walletWithdrawHardMax: number
  walletMaxWithdrawable: number
  walletWithdrawFeePreview: number
  walletWithdrawPayoutPreview: number
  onWalletWithdrawAmountInput: (e: Event) => void
  normalizeWalletWithdrawAmount: () => void
  fillWalletMaxWithdraw: () => void
  walletWithdrawHint: { type: 'error' | 'success'; text: string } | null
  withdrawSubmitting: boolean
  submitWithdrawApply: () => void | Promise<void>
  walletRechargeForm: { amount: number }
  alipayRechargeSubmitting: boolean
  alipaySyncSubmitting: boolean
  alipayRechargeHint: { type: 'error' | 'success'; text: string } | null
  pendingAlipayOutTradeNo: string
  submitAlipayRecharge: () => void | Promise<void>
  syncAlipayRecharge: () => void | Promise<void>
  fetchWalletData: () => void | Promise<void>
  rebuildWalletData: () => void | Promise<void>
  walletWithdraws: WalletWithdrawItem[]
  withdrawStatusClass: (status: string) => string
  withdrawStatusLabel: (status: string) => string
  walletTransactions: WalletTransactionItem[]
  walletTypeLabel: (type: string) => string
  openWalletTaskDetail: (tx: WalletTransactionItem) => void | Promise<void>
  walletLoading: boolean
  riderApplicationPending?: boolean
  goRiderApply?: () => void
}
