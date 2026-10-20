const supportService = require('../../services/support')
const chatService = require('../../services/chat')
const { getUser, useLoginGuard, checkLoginGuard } = require('../../utils/auth')
const { resolveUserAvatar } = require('../../utils/format')

const HANDOFF_RE = /转人工|转接人工|转接客服|找人工|要人工|接通人工/
const RATING_TAGS = ['解决了问题', '态度很好', '回复及时', '专业耐心', '有待改进']
const SCORE_LABELS = {
  1: '非常不满意',
  2: '不满意',
  3: '一般',
  4: '满意',
  5: '非常满意'
}

let seq = 1

function nextId() {
  seq += 1
  return seq
}

Page({
  data: {
    messages: [
      {
        id: 1,
        role: 'assistant',
        content: '我是片刻小新，可以问我发布、接单、钱包和认证。需要人工时，点上面的「转人工」。'
      }
    ],
    draft: '',
    sending: false,
    transferring: false,
    anchor: 'm1',
    botAvatar: '/images/logo.png',
    myAvatar: '',
    humanAgent: '',
    humanJoined: false,
    ratingVisible: false,
    ratingAgent: '',
    ratingScore: 5,
    ratingScoreLabel: '非常满意',
    ratingStars: [1, 2, 3, 4, 5],
    ratingTagItems: RATING_TAGS.map((tag) => ({ tag, on: false })),
    ratingComment: '',
    ratingSubmitting: false
  },

  onLoad() {
    this._seenChatIds = {}
    useLoginGuard(this, '/pages/support/support')
  },

  onUnload() {
    this.stopHumanPoll()
  },

  onShow() {
    if (!checkLoginGuard(this)) return
    const user = getUser()
    this.setData({ myAvatar: resolveUserAvatar(user) || '' })
  },

  onInput(e) {
    this.setData({ draft: e.detail.value || '' })
  },

  onChip(e) {
    const text = String((e.currentTarget.dataset && e.currentTarget.dataset.text) || '').trim()
    if (!text || this.data.sending) return
    this.setData({ draft: text }, () => this.onSend())
  },

  pushMessage(role, content) {
    const id = nextId()
    const messages = this.data.messages.concat([{ id, role, content }])
    this.setData({ messages, anchor: 'm' + id })
    return messages
  },

  historyPayload(messages) {
    return (messages || this.data.messages)
      .filter((item) => item.role === 'user' || item.role === 'assistant')
      .map((item) => ({ role: item.role, content: item.content }))
  },

  async onSend() {
    const text = String(this.data.draft || '').trim()
    if (!text || this.data.sending || this.data.transferring) return
    if (HANDOFF_RE.test(text)) {
      this.setData({ draft: '' })
      this.pushMessage('user', text)
      await this.transferToHuman()
      return
    }
    if (this.data.humanAgent) {
      await this.sendToHuman(text)
      return
    }
    const history = this.historyPayload()
    this.setData({ draft: '', sending: true })
    this.pushMessage('user', text)
    try {
      const res = await supportService.supportChat(text, history)
      let reply = (res && (res.reply || res.content) || '').trim()
      if (!reply) reply = '智能客服暂时不可用，请稍后再试，或转人工客服。'
      const actions = res && Array.isArray(res.actions) ? res.actions : []
      if (actions.includes('suggest_handoff') && reply.indexOf('转人工') < 0) {
        reply += '\n\n如需人工协助，可以直接说「转人工」。'
      }
      this.pushMessage('assistant', reply)
    } catch (e) {
      this.pushMessage('assistant', (e && e.message) || '发送失败，请稍后再试')
    } finally {
      this.setData({ sending: false })
    }
  },

  async onTransfer() {
    if (this.data.transferring || this.data.sending) return
    await this.transferToHuman()
  },

  async transferToHuman() {
    this.setData({ transferring: true })
    try {
      const availability = await supportService.fetchAgentAvailability()
      if (availability && availability.online === false) {
        this.pushMessage('assistant', availability.message || '人工客服当前不在线，请稍后再试，或继续问我。')
        return
      }
      const assigned = await supportService.assignAgent()
      const agent = String((assigned && assigned.username) || '').trim()
      if (!agent) throw new Error('暂无可用的客服账号，请稍后再试')
      await chatService.sendMessage(agent, '[来自智能客服] 用户请求人工客服协助。')
      await this.markExistingHumanHistory(agent)
      this.setData({ humanAgent: agent, humanJoined: false })
      this.pushMessage('assistant', `已为你转接人工客服「${agent}」，请在这里继续说明问题。`)
      this.startHumanPoll()
    } catch (e) {
      this.pushMessage('assistant', (e && e.message) || '转人工失败，请稍后再试')
    } finally {
      this.setData({ transferring: false })
    }
  },

  async markExistingHumanHistory(agent) {
    const rows = await chatService.fetchHistory(agent).catch(() => [])
    this._seenChatIds = this._seenChatIds || {}
    ;(Array.isArray(rows) ? rows : []).forEach((row) => {
      if (row && row.id) this._seenChatIds[row.id] = true
    })
  },

  startHumanPoll() {
    this.stopHumanPoll()
    this._humanPoll = setInterval(() => {
      this.pollHumanReplies()
    }, 2500)
  },

  stopHumanPoll() {
    if (this._humanPoll) {
      clearInterval(this._humanPoll)
      this._humanPoll = null
    }
  },

  async sendToHuman(text) {
    const agent = this.data.humanAgent
    if (!agent) return
    this.setData({ draft: '', sending: true })
    this.pushMessage('user', text)
    try {
      const sent = await chatService.sendMessage(agent, text)
      if (sent && sent.id) {
        this._seenChatIds = this._seenChatIds || {}
        this._seenChatIds[sent.id] = true
      }
    } catch (e) {
      this.pushMessage('assistant', (e && e.message) || '发送给人工客服失败，请稍后再试')
    } finally {
      this.setData({ sending: false })
    }
  },

  async pollHumanReplies() {
    const agent = String(this.data.humanAgent || '').trim()
    if (!agent) return
    let rows = []
    try {
      rows = await chatService.fetchHistory(agent)
    } catch {
      return
    }
    this._seenChatIds = this._seenChatIds || {}
    const list = Array.isArray(rows) ? rows : []
    for (let i = 0; i < list.length; i += 1) {
      const row = list[i]
      const id = row && row.id
      if (!id || this._seenChatIds[id]) continue
      this._seenChatIds[id] = true
      const from = String((row && row.fromUsername) || '')
      if (from.toLowerCase() !== agent.toLowerCase()) continue
      const text = String((row && row.content) || '').trim()
      if (!text || text.indexOf('[来自智能客服]') === 0) continue
      if (text.indexOf('[CS_SESSION_END]') === 0) {
        const tip = text.replace(/^\[CS_SESSION_END\]\s*/, '').trim() || '客服已结束本次人工服务，您可继续咨询片刻小新。'
        const wasJoined = !!this.data.humanJoined
        const endedAgent = agent
        this.pushMessage('assistant', tip)
        this.setData({ humanAgent: '', humanJoined: false })
        this.stopHumanPoll()
        if (wasJoined && endedAgent) {
          this.openRating(endedAgent)
        }
        return
      }
      if (text.indexOf('[CS_SESSION_JOIN]') === 0) {
        const tip = text.replace(/^\[CS_SESSION_JOIN\]\s*/, '').trim() || '您好，人工客服已接入。'
        this.setData({ humanJoined: true })
        this.pushMessage('assistant', tip)
        continue
      }
      this.setData({ humanJoined: true })
      this.pushMessage('assistant', text)
    }
  },

  openRating(agent) {
    const name = String(agent || '').trim()
    if (!name) return
    this.setData({
      ratingVisible: true,
      ratingAgent: name,
      ratingScore: 5,
      ratingScoreLabel: SCORE_LABELS[5],
      ratingTagItems: RATING_TAGS.map((tag) => ({ tag, on: false })),
      ratingComment: '',
      ratingSubmitting: false,
      anchor: 'rating-card'
    })
  },

  onPickScore(e) {
    const score = Math.max(1, Math.min(5, Number((e.currentTarget.dataset && e.currentTarget.dataset.score) || 5)))
    this.setData({
      ratingScore: score,
      ratingScoreLabel: SCORE_LABELS[score] || ''
    })
  },

  onToggleTag(e) {
    const tag = String((e.currentTarget.dataset && e.currentTarget.dataset.tag) || '').trim()
    if (!tag) return
    const items = (this.data.ratingTagItems || []).map((item) => {
      if (item.tag !== tag) return item
      return { tag: item.tag, on: !item.on }
    })
    const selectedCount = items.filter((item) => item.on).length
    if (selectedCount > 5) return
    this.setData({ ratingTagItems: items })
  },

  onRatingComment(e) {
    this.setData({ ratingComment: String((e.detail && e.detail.value) || '').slice(0, 200) })
  },

  onSkipRating() {
    if (this.data.ratingSubmitting) return
    this.setData({
      ratingVisible: false,
      ratingAgent: '',
      ratingComment: '',
      ratingTagItems: RATING_TAGS.map((tag) => ({ tag, on: false }))
    })
    this.pushMessage('assistant', '已跳过客服评价')
  },

  async onSubmitRating() {
    if (this.data.ratingSubmitting || !this.data.ratingVisible) return
    const agent = String(this.data.ratingAgent || '').trim()
    if (!agent) return
    const score = Math.max(1, Math.min(5, Math.round(Number(this.data.ratingScore) || 5)))
    const tags = (this.data.ratingTagItems || []).filter((item) => item.on).map((item) => item.tag).slice(0, 5)
    const comment = String(this.data.ratingComment || '').trim().slice(0, 200)
    this.setData({ ratingSubmitting: true })
    try {
      await supportService.submitRating({
        agentUsername: agent,
        score,
        tags,
        comment
      })
      const parts = [`评分：${score}星（${SCORE_LABELS[score] || ''}）`]
      if (tags.length) parts.push(`标签：${tags.join('、')}`)
      if (comment) parts.push(`评价：${comment}`)
      await chatService.sendMessage(agent, `[CS_RATING] ${parts.join(' | ')}`).catch(() => {})
      this.setData({
        ratingVisible: false,
        ratingAgent: '',
        ratingComment: '',
        ratingTagItems: RATING_TAGS.map((tag) => ({ tag, on: false }))
      })
      this.pushMessage('assistant', `感谢你的评价（${score} 星）！我们会继续改进服务～`)
    } catch (e) {
      this.pushMessage('assistant', (e && e.message) || '评价提交失败，请稍后再试')
    } finally {
      this.setData({ ratingSubmitting: false })
    }
  }
})
