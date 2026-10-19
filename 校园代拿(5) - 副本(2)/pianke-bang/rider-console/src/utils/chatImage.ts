const CHAT_IMAGE_PREFIX = '[CHAT_IMAGE]'
const MAX_IMAGE_BYTES = 900 * 1024

export type ChatImagePayload = {
  image: string
  text: string
}

export function parseChatImage(content?: string): ChatImagePayload | null {
  const raw = String(content || '')
  if (!raw.startsWith(CHAT_IMAGE_PREFIX)) return null
  try {
    const payload = JSON.parse(raw.slice(CHAT_IMAGE_PREFIX.length)) as {
      image?: string
      text?: string
    }
    const image = String(payload?.image || '').trim()
    if (!image) return null
    return {
      image,
      text: String(payload?.text || '').trim()
    }
  } catch {
    return null
  }
}

export function encodeChatImage(image: string, text = '') {
  return `${CHAT_IMAGE_PREFIX}${JSON.stringify({
    image: String(image || '').trim(),
    text: String(text || '').trim()
  })}`
}

export function chatMessageImage(content?: string) {
  return parseChatImage(content)?.image || ''
}

export function chatMessageText(content?: string) {
  const parsed = parseChatImage(content)
  if (parsed) return parsed.text
  return String(content || '')
}

function dataUrlByteSize(dataUrl: string) {
  const raw = String(dataUrl || '')
  const idx = raw.indexOf(',')
  const b64 = idx >= 0 ? raw.slice(idx + 1) : raw
  return Math.floor(b64.length * 0.75)
}

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('读取图片失败'))
    reader.readAsDataURL(file)
  })
}

function loadImage(dataUrl: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('图片解析失败'))
    img.src = dataUrl
  })
}

/** 压缩到约 1280 宽、质量递减，直到不超过体积上限 */
export async function prepareChatImageDataUrl(file: File): Promise<string> {
  const original = await readFileAsDataUrl(file)
  if (dataUrlByteSize(original) <= MAX_IMAGE_BYTES && original.startsWith('data:image/')) {
    return original
  }

  const img = await loadImage(original)
  const maxW = 1280
  const scale = img.width > maxW ? maxW / img.width : 1
  const w = Math.max(1, Math.round(img.width * scale))
  const h = Math.max(1, Math.round(img.height * scale))
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('无法处理图片')
  ctx.drawImage(img, 0, 0, w, h)

  let quality = 0.72
  let out = ''
  for (let i = 0; i < 5; i++) {
    out = canvas.toDataURL('image/jpeg', quality)
    if (dataUrlByteSize(out) <= MAX_IMAGE_BYTES) return out
    quality = Math.max(0.4, quality - 0.1)
  }
  if (dataUrlByteSize(out) > MAX_IMAGE_BYTES) {
    throw new Error('图片太大，请换一张较小的图片')
  }
  return out
}
