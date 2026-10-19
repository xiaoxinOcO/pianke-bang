export type QuickReplyItem = { id: string; label: string; text: string }
export type QuickReplyGroup = { id: string; name: string; items: QuickReplyItem[] }

function id(prefix: string) {
  return `${prefix}_${Math.random().toString(36).slice(2, 9)}`
}

/** 默认分组快捷话术（可本地编辑后持久化） */
export const DEFAULT_QUICK_REPLY_GROUPS: QuickReplyGroup[] = [
  {
    id: 'greet',
    name: '接待',
    items: [
      { id: id('q'), label: '问候', text: '您好，我是片刻帮人工客服，很高兴为您服务~' },
      { id: id('q'), label: '请稍等', text: '请稍等，我正在为您查询，马上回复您。' },
      { id: id('q'), label: '已收到', text: '已收到，正在为您核实，请稍候。' },
      { id: id('q'), label: '结束语', text: '感谢您的咨询，祝您使用愉快！' }
    ]
  },
  {
    id: 'order',
    name: '找单',
    items: [
      { id: id('q'), label: '任务编号', text: '请问您的任务编号是多少？方便我为您核实订单情况。' },
      { id: id('q'), label: '查进度', text: '您可以在「我的任务」中查看代拿订单的实时进度与状态。' },
      { id: id('q'), label: '如何发布', text: '如需发布代拿任务，请前往首页点击「发布任务」，填写取件地点、送达地点和酬金即可。' }
    ]
  },
  {
    id: 'refund',
    name: '退款',
    items: [
      { id: id('q'), label: '申诉说明', text: '若订单出现异常或需要申诉，请在任务详情页点击「申诉」并描述情况，我们会尽快处理。' },
      { id: id('q'), label: '退款核实', text: '关于退款/赏金问题，我先帮您核实订单状态与钱包流水，请稍等。' }
    ]
  },
  {
    id: 'urgent',
    name: '加急',
    items: [
      { id: id('q'), label: '加急说明', text: '加急任务时限更紧，请关注截止时间；如需加急可在发布页开启加急选项。' }
    ]
  },
  {
    id: 'account',
    name: '账号',
    items: [
      { id: id('q'), label: '提现到账', text: '提现申请审核通过后，一般会在 1～3 个工作日内到账，请您耐心等待。' },
      { id: id('q'), label: '头像审核', text: '头像修改提交后需管理员审核通过才会生效，审核期间请耐心等待。' },
      { id: id('q'), label: '接单员申请', text: '想成为接单员，请在个人中心找到「接单员申请」，填写信息并等待管理员审核。' },
      { id: id('q'), label: '还有问题', text: '请问还有其他需要帮助的吗？随时欢迎咨询~' }
    ]
  }
]

export function flattenQuickReplies(groups: QuickReplyGroup[]): Array<{ label: string; text: string }> {
  return groups.flatMap((g) => g.items.map((i) => ({ label: i.label, text: i.text })))
}

export function newQuickReplyItem(label = '新话术', text = ''): QuickReplyItem {
  return { id: id('q'), label, text }
}

export function newQuickReplyGroup(name = '新分组'): QuickReplyGroup {
  return { id: id('g'), name, items: [newQuickReplyItem()] }
}
