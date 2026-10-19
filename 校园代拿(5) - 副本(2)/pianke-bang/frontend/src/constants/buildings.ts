export const BUILDING_OPTIONS = [
  ...Array.from({ length: 40 }, (_, i) => `${i + 1}栋`),
  '第一综合楼',
  '第二综合楼',
  '第三综合楼',
  '第四综合楼',
  '第一实验楼',
  '第二实验楼',
  '第一教学楼',
  '第二教学楼'
] as const

export const CLASSROOM_BUILDING_SET = new Set<string>([
  '第一实验楼',
  '第二实验楼',
  '第一教学楼',
  '第二教学楼'
])

export function buildRoomOptions(buildingName: string): string[] {
  if (!buildingName) return []
  const list: string[] = []
  const isClassroom = CLASSROOM_BUILDING_SET.has(buildingName)

  if (isClassroom) {
    const sections = ['A', 'B', 'C', 'D']
    for (let floor = 1; floor <= 5; floor += 1) {
      for (const section of sections) {
        for (let no = 1; no <= 6; no += 1) {
          list.push(`${section}${floor}${String(no).padStart(2, '0')}`)
        }
      }
    }
    return list
  }

  for (let floor = 1; floor <= 6; floor += 1) {
    for (let no = 1; no <= 14; no += 1) {
      list.push(`${floor}${String(no).padStart(2, '0')}宿舍`)
    }
  }
  return list
}
