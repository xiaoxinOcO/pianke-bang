export function randomCaptchaText(length = 4) {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  return Array.from({ length }, () => chars[Math.floor(Math.random() * chars.length)]).join('')
}

export function drawCaptchaOnto(canvas: HTMLCanvasElement | null, code: string) {
  if (!canvas || !code) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const w = canvas.width
  const h = canvas.height

  const gradient = ctx.createLinearGradient(0, 0, w, h)
  gradient.addColorStop(0, '#ffffff')
  gradient.addColorStop(1, '#f3f7ff')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, w, h)

  for (let i = 0; i < 120; i += 1) {
    ctx.fillStyle = `rgba(59,130,246,${0.03 + Math.random() * 0.06})`
    ctx.fillRect(Math.random() * w, Math.random() * h, 1.2, 1.2)
  }

  for (let k = 0; k < 2; k += 1) {
    ctx.beginPath()
    ctx.lineWidth = 1.2
    ctx.strokeStyle = `rgba(30,64,175,${0.22 + Math.random() * 0.12})`
    const amp = 3 + Math.random() * 2
    const freq = 0.08 + Math.random() * 0.04
    const phase = Math.random() * Math.PI * 2
    for (let x = 0; x <= w; x += 1) {
      const y = h / 2 + Math.sin(x * freq + phase + k) * amp + (k === 0 ? -8 : 8)
      if (x === 0) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    }
    ctx.stroke()
  }

  for (let i = 0; i < code.length; i += 1) {
    const ch = code[i]
    const fontSize = 27 + Math.floor(Math.random() * 4)
    const x = 14 + i * 28
    const y = 31 + Math.random() * 4
    const angle = (Math.random() - 0.5) * 0.35

    ctx.save()
    ctx.translate(x, y)
    ctx.rotate(angle)
    ctx.scale(0.95 + Math.random() * 0.12, 0.9 + Math.random() * 0.18)
    ctx.font = `700 ${fontSize}px 'Segoe UI', Arial`
    ctx.fillStyle = '#0f172a'
    ctx.shadowColor = 'rgba(15,23,42,0.22)'
    ctx.shadowBlur = 1.6 + Math.random() * 1.4
    ctx.fillText(ch, 0, 0)
    ctx.shadowBlur = 0
    ctx.restore()
  }

  for (let i = 0; i < 6; i += 1) {
    const x1 = Math.random() * w
    const y1 = Math.random() * h
    const x2 = x1 + (Math.random() * 18 - 9)
    const y2 = y1 + (Math.random() * 10 - 5)
    ctx.beginPath()
    ctx.lineWidth = 0.9
    ctx.strokeStyle = `rgba(15,23,42,${0.12 + Math.random() * 0.12})`
    ctx.moveTo(x1, y1)
    ctx.lineTo(x2, y2)
    ctx.stroke()
  }
}
