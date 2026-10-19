import { apiOrigin } from '../constants/api'

export function normalizeImageUrl(url?: string) {
  const raw = (url || '').trim()
  if (!raw) return ''
  if (raw.startsWith('data:image/')) return raw
  if (raw.startsWith('http://') || raw.startsWith('https://')) return raw
  if (raw.startsWith('/9j/')) return `data:image/jpeg;base64,${raw}`
  if (raw.startsWith('iVBOR')) return `data:image/png;base64,${raw}`
  if (raw.startsWith('UklGR')) return `data:image/webp;base64,${raw}`
  if (!raw.startsWith('/') && apiOrigin) {
    const looksLikeAssetPath =
      raw.startsWith('uploads/') ||
      raw.startsWith('images/') ||
      raw.startsWith('avatar/') ||
      raw.startsWith('avatars/') ||
      raw.startsWith('files/')
    if (looksLikeAssetPath) return `${apiOrigin}/${raw}`
  }
  return raw
}
