import { getCampusApp } from '../app/campusAppState'
import type { WalletViewInject } from '../context/walletViewInject'

export function useWalletStore(): WalletViewInject {
  return getCampusApp().domains.wallet as unknown as WalletViewInject
}
