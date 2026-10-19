const CHAT_IMAGE_PREFIX = '[CHAT_IMAGE]'
const MAX_IMAGE_BYTES = 900 * 1024

function parseChatImage(content) {
  const raw = String(content || '')
  if (!raw.startsWith(CHAT_IMAGE_PREFIX)) return null
  try {
    const payload = JSON.parse(raw.slice(CHAT_IMAGE_PREFIX.length))
    const image = String((payload && payload.image) || '').trim()
    if (!image) return null
    return {
      image,
      text: String((payload && payload.text) || '').trim()
    }
  } catch {
    return null
  }
}

function encodeChatImage(image, text) {
  return `${CHAT_IMAGE_PREFIX}${JSON.stringify({
    image: String(image || '').trim(),
    text: String(text || '').trim()
  })}`
}

function chatContentPreview(content, max) {
  const parsed = parseChatImage(content)
  if (parsed) {
    const caption = truncateText(parsed.text, max || 36)
    return caption ? `[图片] ${caption}` : '[图片]'
  }
  return truncateText(content, max || 36)
}

function truncateText(text, max) {
  const value = String(text || '').trim()
  if (!value) return ''
  return value.length > max ? `${value.slice(0, max)}…` : value
}

function dataUrlByteSize(dataUrl) {
  const raw = String(dataUrl || '')
  const idx = raw.indexOf(',')
  const b64 = idx >= 0 ? raw.slice(idx + 1) : raw
  return Math.floor(b64.length * 0.75)
}

function fileToDataUrl(path) {
  return new Promise((resolve, reject) => {
    wx.getFileSystemManager().readFile({
      filePath: path,
      encoding: 'base64',
      success: (res) => resolve(`data:image/jpeg;base64,${res.data}`),
      fail: reject
    })
  })
}

function compressImageFile(path, quality) {
  return new Promise((resolve) => {
    wx.compressImage({
      src: path,
      quality: quality || 70,
      compressedWidth: 1280,
      success: (res) => resolve(res.tempFilePath || path),
      fail: () => resolve(path)
    })
  })
}

async function prepareChatImageDataUrl(tempFilePath) {
  let path = tempFilePath
  let quality = 70
  let dataUrl = ''

  for (let i = 0; i < 4; i += 1) {
    path = await compressImageFile(path, quality)
    dataUrl = await fileToDataUrl(path)
    if (dataUrlByteSize(dataUrl) <= MAX_IMAGE_BYTES) return dataUrl
    quality = Math.max(40, quality - 12)
  }

  if (dataUrlByteSize(dataUrl) > MAX_IMAGE_BYTES) {
    throw new Error('图片太大，请换一张较小的图片')
  }
  return dataUrl
}

module.exports = {
  CHAT_IMAGE_PREFIX,
  parseChatImage,
  encodeChatImage,
  chatContentPreview,
  prepareChatImageDataUrl,
  dataUrlByteSize
}
