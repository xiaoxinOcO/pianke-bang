/** Open-Meteo 校园天气（无需 API Key） */

export type CampusWeatherSnapshot = {
  temperature: number
  weatherCode: number
  label: string
  emoji: string
  note: string
  placeHint: string
}

/** 默认校园坐标：广州（可被浏览器定位覆盖） */
export const DEFAULT_CAMPUS_COORDS = { lat: 23.1291, lon: 113.2644, placeHint: '校园附近' }

const WEATHER_CODE_MAP: Array<{ codes: number[]; label: string; emoji: string }> = [
  { codes: [0], label: '晴', emoji: '☀️' },
  { codes: [1, 2], label: '多云', emoji: '⛅' },
  { codes: [3], label: '阴', emoji: '☁️' },
  { codes: [45, 48], label: '有雾', emoji: '🌫️' },
  { codes: [51, 53, 55, 56, 57], label: '毛毛雨', emoji: '🌦️' },
  { codes: [61, 63, 65, 66, 67], label: '雨', emoji: '🌧️' },
  { codes: [71, 73, 75, 77], label: '雪', emoji: '🌨️' },
  { codes: [80, 81, 82], label: '阵雨', emoji: '🌦️' },
  { codes: [85, 86], label: '阵雪', emoji: '🌨️' },
  { codes: [95, 96, 99], label: '雷雨', emoji: '⛈️' }
]

export function describeWeatherCode(code: number) {
  const hit = WEATHER_CODE_MAP.find((item) => item.codes.includes(code))
  return hit || { label: '未知', emoji: '🌤️' }
}

export function buildWeatherNote(temp: number, code: number) {
  const hour = new Date().getHours()
  const peak =
    hour >= 11 && hour < 13 ? '高峰 11:30' : hour >= 17 && hour < 19 ? '高峰 17:30' : '错峰更轻松'

  if ([61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99].includes(code)) {
    return `雨天注意防滑 · ${peak}`
  }
  if ([71, 73, 75, 77, 85, 86].includes(code)) {
    return `低温路滑 · ${peak}`
  }
  if (temp >= 33) {
    return `高温注意补水 · ${peak}`
  }
  if (temp <= 5) {
    return `天冷注意保暖 · ${peak}`
  }
  if ([0, 1, 2].includes(code) && temp >= 15 && temp <= 30) {
    return `适合跑腿 · ${peak}`
  }
  return `留意路况 · ${peak}`
}

async function resolveCoords(): Promise<{ lat: number; lon: number; placeHint: string }> {
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    return DEFAULT_CAMPUS_COORDS
  }
  try {
    const pos = await new Promise<GeolocationPosition>((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(resolve, reject, {
        enableHighAccuracy: false,
        timeout: 5000,
        maximumAge: 10 * 60 * 1000
      })
    })
    return {
      lat: Number(pos.coords.latitude.toFixed(4)),
      lon: Number(pos.coords.longitude.toFixed(4)),
      placeHint: '当前位置'
    }
  } catch {
    return DEFAULT_CAMPUS_COORDS
  }
}

export async function fetchCampusWeather(): Promise<CampusWeatherSnapshot> {
  const coords = await resolveCoords()
  const url =
    `https://api.open-meteo.com/v1/forecast` +
    `?latitude=${coords.lat}&longitude=${coords.lon}` +
    `&current=temperature_2m,weather_code` +
    `&timezone=auto`

  const res = await fetch(url)
  if (!res.ok) throw new Error('天气服务暂时不可用')
  const data = (await res.json()) as {
    current?: { temperature_2m?: number; weather_code?: number }
  }
  const tempRaw = Number(data.current?.temperature_2m)
  const code = Number(data.current?.weather_code ?? -1)
  if (!Number.isFinite(tempRaw)) throw new Error('天气数据异常')
  const temperature = Math.round(tempRaw)
  const desc = describeWeatherCode(Number.isFinite(code) ? code : -1)
  return {
    temperature,
    weatherCode: Number.isFinite(code) ? code : -1,
    label: desc.label,
    emoji: desc.emoji,
    note: buildWeatherNote(temperature, Number.isFinite(code) ? code : -1),
    placeHint: coords.placeHint
  }
}
