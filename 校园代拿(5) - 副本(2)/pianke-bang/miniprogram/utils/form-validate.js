function showFormError(message) {
  wx.showToast({
    title: message,
    icon: 'none',
    duration: 2800
  })
  return message
}

function validateLoginPassword(data) {
  const username = String(data.username || '').trim()
  const password = String(data.password || '')
  if (!username) return showFormError('请输入用户名')
  if (!password) return showFormError('请输入密码')
  return ''
}

function validateRegister(data) {
  const username = String(data.regUsername || '').trim()
  const password = String(data.regPassword || '')
  const phone = String(data.phone || '').replace(/\D/g, '')
  const code = String(data.code || '').trim()
  const realName = String(data.regRealName || '').trim()
  const studentId = String(data.regStudentId || '').trim()

  if (!username) return showFormError('请填写用户名')
  if (username.length < 2 || username.length > 20) return showFormError('用户名需 2-20 位')
  if (!password) return showFormError('请填写密码')
  if (password.length < 6) return showFormError('密码至少 6 位')
  if (password.length > 32) return showFormError('密码最多 32 位')
  if (!phone) return showFormError('请填写手机号')
  if (!/^1\d{10}$/.test(phone)) return showFormError('请输入 11 位手机号')
  if (!code) return showFormError('请输入验证码')
  if (!realName) return showFormError('请填写真实姓名')
  if (realName.length < 2) return showFormError('真实姓名至少 2 个字')
  if (!studentId) return showFormError('请填写学号')
  if (!/^\d{6,20}$/.test(studentId)) return showFormError('学号需为 6-20 位数字')
  return ''
}

function validateSmsLogin(data) {
  const phone = String(data.phone || '').replace(/\D/g, '')
  const code = String(data.code || '').trim()
  if (!phone) return showFormError('请填写手机号')
  if (!/^1\d{10}$/.test(phone)) return showFormError('请输入 11 位手机号')
  if (!code) return showFormError('请输入验证码')
  return ''
}

function validateRiderApply(data) {
  const realName = String(data.realName || '').trim()
  const studentId = String(data.studentId || '').trim()
  const phone = String(data.phone || '').replace(/\D/g, '')
  const reason = String(data.reason || '').trim()

  if (!realName) return showFormError('请填写真实姓名')
  if (realName.length < 2) return showFormError('真实姓名至少 2 个字')
  if (!studentId) return showFormError('请填写学号')
  if (!/^\d{6,20}$/.test(studentId)) return showFormError('学号需为 6-20 位数字')
  if (!phone) return showFormError('请填写手机号')
  if (!/^1\d{10}$/.test(phone)) return showFormError('请输入 11 位手机号')
  if (!reason) return showFormError('请填写申请理由')
  if (reason.length > 300) return showFormError('申请理由不能超过 300 字')
  return ''
}

module.exports = {
  showFormError,
  validateLoginPassword,
  validateRegister,
  validateSmsLogin,
  validateRiderApply
}
