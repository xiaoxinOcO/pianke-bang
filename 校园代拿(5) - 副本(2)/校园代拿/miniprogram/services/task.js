const { useMock } = require('../config/index')
const { mockApi } = require('../utils/mock')
const { get, post, put, del } = require('../utils/http')

function fetchTasks(params) {
  if (useMock) return mockApi.fetchTasks(params)
  return get('/tasks', params)
}

function fetchMyTasks(params) {
  if (useMock) return mockApi.fetchMyTasks(params)
  return get('/tasks/mine', params)
}

function createTask(data) {
  if (useMock) return mockApi.createTask(data)
  return post('/tasks', data)
}

function claimTask(id) {
  if (useMock) return mockApi.claimTask(id)
  return post(`/tasks/${id}/claim`)
}

function completeTask(id) {
  if (useMock) return mockApi.completeTask(id)
  return post(`/tasks/${id}/complete`)
}

function confirmTask(id) {
  if (useMock) return mockApi.confirmTask(id)
  return post(`/tasks/${id}/confirm`)
}

function cancelTask(id) {
  if (useMock) return mockApi.cancelTask(id)
  return post(`/tasks/${id}/cancel`)
}

function rateTask(id, data) {
  return post(`/tasks/${id}/rate`, data)
}

function resubmitTask(id, data) {
  return put(`/tasks/${id}/resubmit`, data)
}

function deleteTask(id) {
  return del(`/tasks/${id}`)
}

/** 仅发布者：获取本单接单员手机号 */
function fetchClaimerPhone(id) {
  return get(`/tasks/${id}/claimer-phone`)
}

function fetchUrgentQuota() {
  return get('/tasks/urgent-quota')
}

module.exports = {
  fetchTasks,
  fetchMyTasks,
  createTask,
  claimTask,
  completeTask,
  confirmTask,
  cancelTask,
  fetchClaimerPhone,
  fetchUrgentQuota,
  rateTask,
  resubmitTask,
  deleteTask
}
