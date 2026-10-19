/** 模板 :ref 回调：兼容组件实例上的 $el */
export function elFromTemplateRef(el: unknown): HTMLElement | null {
  if (el == null) return null
  if (el instanceof HTMLElement) return el
  const o = el as { $el?: unknown }
  if (o.$el instanceof HTMLElement) return o.$el
  return null
}
