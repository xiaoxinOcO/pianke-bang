const { useMock } = require('../config/index')
const { mockApi } = require('../utils/mock')
const { get, post } = require('../utils/http')

function fetchWallet() {
  if (useMock) return mockApi.fetchWallet()
  return get('/wallet/me')
}

function withdraw(data) {
  if (useMock) return mockApi.withdraw(data)
  return post('/wallet/withdraw', data)
}

function alipayRecharge(amount) {
  if (useMock) return Promise.resolve({ message: '支付宝充值（本地模式暂未实现）' })
  return post('/wallet/alipay/recharge', { amount })
}

function syncAlipayRecharge() {
  if (useMock) return Promise.resolve({ status: 'PAID', message: '本地模式：同步成功' })
  return post('/wallet/alipay/sync-latest')
}

function alipayTaskPay(taskId) {
  if (useMock) return Promise.resolve({ message: '支付宝支付（本地模式暂未实现）', taskId })
  return post('/wallet/alipay/task-pay', { taskId })
}

module.exports = {
  fetchWallet,
  withdraw,
  alipayRecharge,
  syncAlipayRecharge,
  alipayTaskPay
}
