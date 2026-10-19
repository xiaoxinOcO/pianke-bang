const { get, post } = require('../utils/http')

function fetchActivities() {
  return get('/coupons/activities')
}

function fetchMine(status) {
  const params = {}
  if (status) params.status = status
  return get('/coupons/mine', params)
}

function preview(userCouponId, reward) {
  return post('/coupons/preview', { userCouponId, reward })
}

function claimNewcomer() {
  return post('/coupons/claim-newcomer', {})
}

function claimTemplate(templateId) {
  return post(`/coupons/claim/${templateId}`, {})
}

function redeem(code) {
  return post('/coupons/redeem', { code })
}

module.exports = {
  fetchActivities,
  fetchMine,
  preview,
  claimNewcomer,
  claimTemplate,
  redeem
}
