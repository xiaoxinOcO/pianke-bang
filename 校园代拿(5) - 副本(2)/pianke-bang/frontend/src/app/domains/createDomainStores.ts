import { reactive } from 'vue'
import { DOMAIN_STORE_KEYS, type DomainStoreName } from './storeKeys'

type AnyBag = Record<string | symbol, unknown>

/**
 * bag：原始绑定（含 Ref），保证各域共享同一份状态。
 * appRoot：完整 reactive；域字段表漏配时回退读取，避免整页黑屏。
 */
export function createDomainStores(bag: Record<string, unknown>, appRoot: object) {
  const root = appRoot as AnyBag

  function pick(name: DomainStoreName) {
    const keys = DOMAIN_STORE_KEYS[name] as readonly string[]
    const slice: AnyBag = {}
    for (const key of keys) {
      if (Object.prototype.hasOwnProperty.call(bag, key)) {
        slice[key] = bag[key]
      }
    }
    const local = reactive(slice) as AnyBag

    return new Proxy(local, {
      get(target, prop, receiver) {
        if (typeof prop === 'symbol') return Reflect.get(target, prop, receiver)
        if (prop in target) return Reflect.get(target, prop, receiver)
        if (typeof prop === 'string' && prop in root) return root[prop]
        return undefined
      },
      set(target, prop, value, receiver) {
        if (typeof prop === 'symbol') return Reflect.set(target, prop, value, receiver)
        if (typeof prop === 'string' && prop in root) {
          root[prop] = value
        }
        return Reflect.set(target, prop, value, receiver)
      },
      has(target, prop) {
        return prop in target || prop in root
      },
    })
  }

  return {
    hall: pick('hall'),
    publish: pick('publish'),
    wallet: pick('wallet'),
    chat: pick('chat'),
    admin: pick('admin'),
    coupons: pick('coupons'),
    feedback: pick('feedback'),
    profile: pick('profile'),
    personalInfo: pick('personalInfo'),
    riderApply: pick('riderApply'),
    shell: pick('shell'),
    auth: pick('auth'),
  }
}
