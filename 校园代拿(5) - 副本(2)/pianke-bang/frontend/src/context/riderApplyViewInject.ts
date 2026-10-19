/**
 * RiderApplyView 由 stores/use*Store 提供的字段
 */
export interface RiderApplyViewInject {
  isRiderApproved: boolean
  riderApplicationPending: boolean
  riderApplyError: string
  riderApplyForm: { realName: string; studentId: string; phone: string; reason: string }
  applyRider: () => void | Promise<void>
}
