export type CsUserContextPayload = {
  profile?: {
    username?: string
    riderApproved?: boolean
    customerServiceStaff?: boolean
    creditScore?: number
    phoneMasked?: string
  }
  wallet?: {
    available?: boolean
    error?: string
    balance?: number | string
    totalIncome?: number | string
    totalWithdrawn?: number | string
    withdrawingAmount?: number | string
  }
  tasks?: Array<{
    id?: number | string
    title?: string
    status?: string
    statusLabel?: string
    reward?: number | string
    role?: string
    roleLabel?: string
  }>
}
