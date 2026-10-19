/** 小程序可跳转页面（用于大厅活动「自定义页面」） */
export interface MiniprogramPageOption {
  path: string
  name: string
  desc: string
  /** tabBar 页需 switchTab */
  tab?: boolean
  /** 需要额外查询参数，不适合单独作为轮播目标 */
  needsQuery?: boolean
}

export const MINIPROGRAM_PAGE_OPTIONS: MiniprogramPageOption[] = [
  {
    path: '/pages/hall/hall',
    name: '任务大厅',
    desc: '首页任务列表，浏览/筛选可接任务',
    tab: true
  },
  {
    path: '/pages/publish/publish',
    name: '发布任务',
    desc: '填写并发布跑腿任务、支付赏金',
    tab: true
  },
  {
    path: '/pages/chat/chat',
    name: '消息',
    desc: '聊天与系统消息列表',
    tab: true
  },
  {
    path: '/pages/profile/profile',
    name: '我的',
    desc: '个人中心：资料、钱包入口、接单员状态等',
    tab: true
  },
  {
    path: '/pages/rider-apply/rider-apply',
    name: '申请接单员',
    desc: '提交接单员认证申请'
  },
  {
    path: '/pages/wallet/wallet',
    name: '我的钱包',
    desc: '余额、充值、提现、流水'
  },
  {
    path: '/pages/coupon-center/coupon-center',
    name: '领券中心',
    desc: '领取活动券、兑换码入口'
  },
  {
    path: '/pages/coupons/coupons',
    name: '我的优惠券',
    desc: '查看已领优惠券及使用状态'
  },
  {
    path: '/pages/mine/mine',
    name: '我的任务',
    desc: '我发布/接取的任务列表'
  },
  {
    path: '/pages/notices/notices',
    name: '平台公告',
    desc: '查看平台公告列表'
  },
  {
    path: '/pages/system-notice/system-notice',
    name: '系统通知',
    desc: '系统类通知详情流'
  },
  {
    path: '/pages/feedback/feedback',
    name: '意见反馈',
    desc: '向平台提交意见/问题反馈'
  },
  {
    path: '/pages/login/login',
    name: '登录',
    desc: '账号登录/注册页'
  },
  {
    path: '/pages/task-detail/task-detail',
    name: '任务详情',
    desc: '单条任务详情（需带任务 ID，一般不适合轮播）',
    needsQuery: true
  },
  {
    path: '/pages/chat-room/chat-room',
    name: '聊天室',
    desc: '与某位用户私聊（需会话参数，一般不适合轮播）',
    needsQuery: true
  }
]

export function findMiniprogramPage(path: string) {
  const p = (path || '').trim()
  return MINIPROGRAM_PAGE_OPTIONS.find((x) => x.path === p) || null
}
