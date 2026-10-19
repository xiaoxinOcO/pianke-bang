/** 人工客服常用快捷语（点击填入输入框，可编辑后发送） */
export const CS_QUICK_REPLIES: Array<{ label: string; text: string }> = [
  { label: '问候', text: '您好，我是片刻帮人工客服，很高兴为您服务~' },
  { label: '请稍等', text: '请稍等，我正在为您查询，马上回复您。' },
  { label: '任务编号', text: '请问您的任务编号是多少？方便我为您核实订单情况。' },
  { label: '查进度', text: '您可以在「我的任务」中查看代拿订单的实时进度与状态。' },
  { label: '如何发布', text: '如需发布代拿任务，请前往首页点击「发布任务」，填写取件地点、送达地点和酬金即可。' },
  { label: '申诉说明', text: '若订单出现异常或需要申诉，请在任务详情页点击「申诉」并描述情况，我们会尽快处理。' },
  { label: '提现到账', text: '提现申请审核通过后，一般会在 1～3 个工作日内到账，请您耐心等待。' },
  { label: '头像审核', text: '头像修改提交后需管理员审核通过才会生效，审核期间请耐心等待。' },
  { label: '接单员申请', text: '想成为接单员，请在个人中心找到「接单员申请」，填写信息并等待管理员审核。' },
  { label: '已记录', text: '您的问题我已记录，会尽快为您处理，感谢您的耐心等待。' },
  { label: '还有问题', text: '请问还有其他需要帮助的吗？随时欢迎咨询~' },
  { label: '结束语', text: '感谢您的咨询，祝您使用愉快！' }
]

/** 会话信令（双方聊天消息约定，勿改前缀） */
export const CS_SESSION_JOIN_MARKER = '[CS_SESSION_JOIN]'
export const CS_SESSION_END_MARKER = '[CS_SESSION_END]'
export const USER_SESSION_END_MARKER = '[USER_SESSION_END]'
export const CS_RATING_MARKER = '[CS_RATING]'

export function isSessionControlMessage(content: string) {
  const text = String(content || '').trim()
  return (
    text.startsWith(CS_SESSION_JOIN_MARKER) ||
    text.startsWith(CS_SESSION_END_MARKER) ||
    text.startsWith(USER_SESSION_END_MARKER) ||
    text.startsWith(CS_RATING_MARKER)
  )
}

export function formatSessionControlMessage(content: string) {
  const text = String(content || '').trim()
  if (text.startsWith(CS_SESSION_JOIN_MARKER)) {
    const rest = text.slice(CS_SESSION_JOIN_MARKER.length).trim()
    return rest || '客服已接入会话'
  }
  if (text.startsWith(CS_SESSION_END_MARKER)) {
    const rest = text.slice(CS_SESSION_END_MARKER.length).trim()
    return rest || '客服已结束本次人工服务'
  }
  if (text.startsWith(USER_SESSION_END_MARKER)) {
    const rest = text.slice(USER_SESSION_END_MARKER.length).trim()
    return rest || '用户已结束人工服务'
  }
  if (text.startsWith(CS_RATING_MARKER)) {
    const rest = text.slice(CS_RATING_MARKER.length).trim()
    return rest ? `用户评价：${rest}` : '用户提交了客服评价'
  }
  return text
}
