/**
 * 小程序端令牌与请求逻辑测试
 *
 * 为什么用 node:test 而不是 vitest/jest：
 *   项目本来没有前端测试框架，装 vitest 需要联网，而当前环境 npm registry 不可达。
 *   node:test 是 Node 18+ 内置的，零依赖、零安装，正好够测这两块纯逻辑。
 *
 * 怎么解决 `uni` 全局对象：
 *   token.js 和 request.js 都直接调用全局 uni 的存储与网络 API。
 *   这在真机上是微信注入的，在 Node 里没有。测试的做法是——
 *   在 import 之前先把一个假的 uni 挂到 globalThis 上，并把调用记录下来，
 *   这样既能驱动逻辑，又能断言「到底有没有真的发请求」。
 *
 * 跑法：node --test miniprogram/tests/
 */
import { test, describe, beforeEach } from 'node:test'
import assert from 'node:assert/strict'

// ----------------------------------------------------------------------
// 伪造 uni 环境
// ----------------------------------------------------------------------

/** 内存版本地存储，模拟小程序的 Storage */
const storage = new Map()

/** 记录所有 uni.request 调用，供断言使用 */
let requests = []

/** 下一次 request 要返回的响应，由各用例设置 */
let nextResponse = { statusCode: 200, data: { code: 200, data: null } }

/** 下次 request 是否要模拟网络失败 */
let nextNetworkError = false

globalThis.uni = {
  getStorageSync: (k) => (storage.has(k) ? storage.get(k) : ''),
  setStorageSync: (k, v) => storage.set(k, v),
  removeStorageSync: (k) => storage.delete(k),
  showToast: () => {},
  request: (opts) => {
    requests.push(opts)
    // 用 queueMicrotask 保证异步语义，贴近真实 uni.request
    queueMicrotask(() => {
      if (nextNetworkError) {
        opts.fail(new Error('network down'))
      } else {
        opts.success(nextResponse)
      }
    })
  }
}

// 必须在挂好 uni 之后再导入被测模块
const token = await import('../src/utils/token.js')
const req = await import('../src/utils/request.js')

const STORAGE_KEY = 'erp_refresh_token'

beforeEach(() => {
  storage.clear()
  requests = []
  nextNetworkError = false
  nextResponse = { statusCode: 200, data: { code: 200, data: null } }
  token.clearTokens()
})

// ======================================================================

describe('令牌存取', () => {
  test('访问令牌只放内存，不写本地存储', () => {
    token.saveTokens({ accessToken: 'acc-1' })
    assert.equal(token.getAccessToken(), 'acc-1')
    // 关键断言：访问令牌绝不能落盘，否则本地缓存被翻出来就是可用凭据
    assert.equal(storage.size, 0, '访问令牌不应写入本地存储')
  })

  test('刷新令牌必须持久化，冷启动才能免登录', () => {
    token.saveTokens({ refreshToken: 'ref-1' })
    assert.equal(storage.get(STORAGE_KEY), 'ref-1')
    assert.equal(token.getRefreshToken(), 'ref-1')
    assert.equal(token.hasRefreshToken(), true)
  })

  test('同时传入两个令牌时各归各位', () => {
    token.saveTokens({ accessToken: 'acc-2', refreshToken: 'ref-2' })
    assert.equal(token.getAccessToken(), 'acc-2')
    assert.equal(storage.get(STORAGE_KEY), 'ref-2')
  })

  test('只传 accessToken 时不会清掉已存的刷新令牌', () => {
    token.saveTokens({ refreshToken: 'ref-3' })
    token.saveTokens({ accessToken: 'acc-3' })
    assert.equal(token.getRefreshToken(), 'ref-3', '续期只更新访问令牌时，刷新令牌应保持不变')
  })

  test('clearTokens 同时清内存与本地存储', () => {
    token.saveTokens({ accessToken: 'acc-4', refreshToken: 'ref-4' })
    token.clearTokens()
    assert.equal(token.getAccessToken(), '')
    assert.equal(token.getRefreshToken(), '')
    assert.equal(token.hasRefreshToken(), false)
  })

  test('本地存储读取抛异常时降级为空字符串，不向上冒泡', () => {
    const origin = globalThis.uni.getStorageSync
    globalThis.uni.getStorageSync = () => {
      throw new Error('storage broken')
    }
    try {
      assert.equal(token.getRefreshToken(), '', '存储故障应降级为未登录，而不是抛异常')
    } finally {
      globalThis.uni.getStorageSync = origin
    }
  })
})

describe('请求封装', () => {
  test('成功响应只返回 data，页面不用自己解包', async () => {
    nextResponse = { statusCode: 200, data: { code: 200, data: { id: 7 } } }
    const result = await req.request('GET', '/products/7')
    assert.deepEqual(result, { id: 7 })
  })

  test('有访问令牌时自动带上 Authorization 头', async () => {
    token.saveTokens({ accessToken: 'acc-x' })
    await req.request('GET', '/products')
    assert.equal(requests[0].header.Authorization, 'Bearer acc-x')
  })

  test('没有访问令牌时不带 Authorization 头', async () => {
    await req.request('GET', '/products')
    assert.equal(requests[0].header.Authorization, undefined)
  })

  test('业务错误码透出为 ApiError，页面可按 code 分支处理', async () => {
    nextResponse = { statusCode: 200, data: { code: 4001, message: '库存不足' } }
    await assert.rejects(
      () => req.request('POST', '/sales', {}, { silent: true }),
      (err) => {
        assert.equal(err.name, 'ApiError')
        assert.equal(err.code, 4001)
        assert.equal(err.message, '库存不足')
        return true
      }
    )
  })

  test('网络异常转成 code=-1，不把原始异常抛给页面', async () => {
    nextNetworkError = true
    await assert.rejects(
      () => req.request('GET', '/products', null, { silent: true }),
      (err) => {
        assert.equal(err.code, -1)
        return true
      }
    )
  })
})

describe('401 自动续期', () => {
  test('401 后用刷新令牌换新令牌并重放原请求', async () => {
    token.saveTokens({ accessToken: 'old-acc', refreshToken: 'ref-ok' })

    // 第一次请求返回 401，刷新接口返回新令牌，重放后返回 200
    let call = 0
    globalThis.uni.request = (opts) => {
      requests.push(opts)
      call += 1
      queueMicrotask(() => {
        if (opts.url.endsWith('/auth/refresh')) {
          opts.success({ statusCode: 200, data: { code: 200, data: { accessToken: 'new-acc' } } })
        } else if (call === 1) {
          opts.success({ statusCode: 200, data: { code: 401, message: '过期' } })
        } else {
          opts.success({ statusCode: 200, data: { code: 200, data: { ok: true } } })
        }
      })
    }

    const result = await req.request('GET', '/products')
    assert.deepEqual(result, { ok: true })
    assert.equal(token.getAccessToken(), 'new-acc', '重放前应已换上新令牌')
    // 重放的那次请求必须带新令牌，否则等于没续期
    assert.equal(requests.at(-1).header.Authorization, 'Bearer new-acc')
  })

  test('并发多个 401 只触发一次刷新（单例 Promise）', async () => {
    token.saveTokens({ accessToken: 'old', refreshToken: 'ref' })

    let refreshCount = 0
    globalThis.uni.request = (opts) => {
      requests.push(opts)
      queueMicrotask(() => {
        if (opts.url.endsWith('/auth/refresh')) {
          refreshCount += 1
          opts.success({ statusCode: 200, data: { code: 200, data: { accessToken: 'fresh' } } })
        } else if (opts.header.Authorization === 'Bearer old') {
          opts.success({ statusCode: 200, data: { code: 401 } })
        } else {
          opts.success({ statusCode: 200, data: { code: 200, data: { ok: true } } })
        }
      })
    }

    await Promise.all([
      req.request('GET', '/a'),
      req.request('GET', '/b'),
      req.request('GET', '/c')
    ])
    assert.equal(refreshCount, 1, '并发 401 只应刷新一次，否则会打出刷新风暴')
  })

  test('刷新失败则清除令牌并触发过期回调', async () => {
    token.saveTokens({ accessToken: 'old', refreshToken: 'ref-bad' })
    let expiredCalled = false
    req.onExpired(() => {
      expiredCalled = true
    })

    globalThis.uni.request = (opts) => {
      requests.push(opts)
      queueMicrotask(() => {
        if (opts.url.endsWith('/auth/refresh')) {
          opts.success({ statusCode: 200, data: { code: 401 } })
        } else {
          opts.success({ statusCode: 200, data: { code: 401 } })
        }
      })
    }

    await assert.rejects(() => req.request('GET', '/x', null, { silent: true }))
    assert.equal(expiredCalled, true, '刷新失败必须通知上层跳登录页')
    assert.equal(token.getRefreshToken(), '', '刷新令牌失效后应清除')
  })

  test('刷新接口自身 401 不会递归触发刷新', async () => {
    token.saveTokens({ accessToken: 'old', refreshToken: 'ref' })
    let refreshCalls = 0
    globalThis.uni.request = (opts) => {
      requests.push(opts)
      if (opts.url.endsWith('/auth/refresh')) refreshCalls += 1
      queueMicrotask(() => {
        opts.success({ statusCode: 200, data: { code: 401 } })
      })
    }

    await assert.rejects(() => req.request('GET', '/y', null, { silent: true }))
    assert.equal(refreshCalls, 1, '刷新接口只能调用一次，否则会死循环')
  })
})

describe('冷启动恢复', () => {
  test('内存无令牌但本地有刷新令牌时，能静默换到新令牌', async () => {
    storage.set(STORAGE_KEY, 'ref-cold')
    globalThis.uni.request = (opts) => {
      requests.push(opts)
      queueMicrotask(() => {
        opts.success({ statusCode: 200, data: { code: 200, data: { accessToken: 'warm' } } })
      })
    }

    const ok = await req.ensureAccessToken()
    assert.equal(ok, true)
    assert.equal(token.getAccessToken(), 'warm')
  })

  test('本地也没有刷新令牌时直接返回 false，不发任何请求', async () => {
    const ok = await req.ensureAccessToken()
    assert.equal(ok, false)
    assert.equal(requests.length, 0, '没有刷新令牌就不该白跑一次网络请求')
  })

  test('已有访问令牌时不再重复刷新', async () => {
    token.saveTokens({ accessToken: 'alive' })
    const ok = await req.ensureAccessToken()
    assert.equal(ok, true)
    assert.equal(requests.length, 0)
  })
})
