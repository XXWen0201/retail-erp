#!/usr/bin/env node
/**
 * 移动端页面截图工具（Chrome DevTools Protocol）
 *
 * 为什么不直接用 `chrome --screenshot`：
 *   headless Chrome 的窗口有最小尺寸约束（实测约 500px），
 *   `--window-size=390` 会被强行抬高到 489 宽，
 *   而截图仍按 390 裁剪 —— 结果是页面右侧被切掉一截，
 *   看起来像布局溢出，实际是截图参数的问题。
 *
 * 走 CDP 的 Emulation.setDeviceMetricsOverride 才能真正模拟
 * 「390 x 844、2 倍像素密度、移动端」的视口，顺带还能：
 *   1. 直接量 scrollWidth / clientWidth，客观判断有没有横向溢出
 *   2. 抓 console 报错
 *
 * 用法：
 *   node tools/gen_mini_screenshots.mjs [输出目录]
 *   前置：H5 端已启动（npm run dev:h5，端口 5273）
 */

import { spawn } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { tmpdir } from 'node:os'
import { setTimeout as sleep } from 'node:timers/promises'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const PORT = 9333
const BASE = process.env.MINI_BASE || 'http://localhost:5273'
const OUT_DIR = resolve(process.argv[2] || 'docs/screenshots-mini')

const VIEW = { width: 390, height: 844, dpr: 2 }

/** 登录页必须先截（未登录状态），其余页面在建立登录态之后截 */
const LOGIN_PAGE = {
  file: '01-login',
  hash: '#/pages/login/index',
  keywords: ['账号', '演示账号', '零售进销存']
}

const PAGES = [
  { file: '02-dashboard', hash: '#/pages/dashboard/index', keywords: ['今日销售额', '快捷操作', '本月概览'] },
  { file: '03-stock', hash: '#/pages/stock/index', keywords: ['仅看低于下限', '文具用品', '晨光'] },
  { file: '04-alert', hash: '#/pages/alert/index', keywords: ['待处理预警', '立即扫描'] },
  { file: '05-mine', hash: '#/pages/mine/index', keywords: ['我的操作权限', '销售单记录'] },
  { file: '06-sale-list', hash: '#/pages/sale/list', keywords: ['销售单', '开单'] },
  { file: '07-sale-create', hash: '#/pages/sale/create', keywords: ['确认开单', '添加商品'] },
  { file: '08-batch', hash: '#/pages/stock/batch', keywords: ['已过期', '到期'] },
  { file: '09-record', hash: '#/pages/stock/record', keywords: ['采购入库', '流水'] },
  { file: '10-scan', hash: '#/pages/stock/scan', keywords: ['条码', '查询'] },
  { file: '11-ai', hash: '#/pages/ai/index', keywords: ['智能补货建议', '库存智能问答'] },
  { file: '12-ai-logs', hash: '#/pages/ai/logs', keywords: ['调用记录', 'ms'] },
  { file: '13-detail', hash: '#/pages/stock/detail?id=1', keywords: ['零售价', '库存批次'] }
]

// ----------------------------------------------------------------------
// CDP 最小客户端
// ----------------------------------------------------------------------

class Cdp {
  constructor(ws) {
    this.ws = ws
    this.seq = 0
    this.pending = new Map()
    this.logs = []
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve: res, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : res(msg.result)
      } else if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
        this.logs.push(msg.params.args.map((a) => a.value || a.description || '').join(' '))
      } else if (msg.method === 'Runtime.exceptionThrown') {
        this.logs.push('EXCEPTION: ' + (msg.params.exceptionDetails.text || ''))
      }
    })
  }

  send(method, params = {}) {
    const id = ++this.seq
    return new Promise((res, reject) => {
      this.pending.set(id, { resolve: res, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
    })
  }
}

async function waitForDevtools() {
  for (let i = 0; i < 60; i++) {
    try {
      const r = await fetch(`http://127.0.0.1:${PORT}/json/list`)
      const list = await r.json()
      const page = list.find((t) => t.type === 'page' && t.webSocketDebuggerUrl)
      if (page) return page.webSocketDebuggerUrl
    } catch (e) {
      /* 还没起来，继续等 */
    }
    await sleep(300)
  }
  throw new Error('等待 DevTools 就绪超时')
}

function connect(url) {
  return new Promise((res, reject) => {
    const ws = new WebSocket(url)
    ws.addEventListener('open', () => res(new Cdp(ws)))
    ws.addEventListener('error', reject)
  })
}

// ----------------------------------------------------------------------

async function evaluate(cdp, expression) {
  const { result } = await cdp.send('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true
  })
  return result.value
}

async function shot(cdp, file) {
  const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' })
  const path = join(OUT_DIR, file + '.png')
  writeFileSync(path, Buffer.from(data, 'base64'))
  return path
}

/**
 * 导航到指定路径并等待页面把数据渲染出来
 *
 * path 传 `#/pages/xxx` 会拼成 `host/#/pages/xxx`（SPA 路由），
 * 传 `static/xxx.html` 会拼成 `host/static/xxx.html`（真实静态文件）。
 * 注意两者都不能带前导斜杠，否则会出现 `host//xxx` 这种双斜杠路径，
 * vite 会把整段当成未知路径回退到 index.html，静态页就永远打不开了。
 */
async function goto(cdp, path, settle = 4200) {
  const url = BASE + '/' + path
  await cdp.send('Page.navigate', { url })
  await sleep(settle)
  return evaluate(cdp, 'document.body.innerText')
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })

  const profile = join(tmpdir(), 'chrome-cdp-mini-' + Date.now())
  const chrome = spawn(
    CHROME,
    [
      '--headless=new',
      '--disable-gpu',
      '--no-sandbox',
      '--hide-scrollbars',
      '--no-first-run',
      '--disable-extensions',
      `--remote-debugging-port=${PORT}`,
      `--user-data-dir=${profile}`,
      'about:blank'
    ],
    { stdio: 'ignore' }
  )

  let cdp
  let pass = 0
  let fail = 0

  try {
    cdp = await connect(await waitForDevtools())
    await cdp.send('Page.enable')
    await cdp.send('Runtime.enable')
    await cdp.send('Log.enable')
    await cdp.send('Emulation.setDeviceMetricsOverride', {
      width: VIEW.width,
      height: VIEW.height,
      deviceScaleFactor: VIEW.dpr,
      mobile: true
    })

    // ---------- 1. 登录页（必须是未登录状态）----------
    console.log('=== 登录页（未登录）===')
    let text = await goto(cdp, LOGIN_PAGE.hash, 5000)
    let ok = LOGIN_PAGE.keywords.every((k) => text.includes(k))
    ok ? pass++ : fail++
    console.log('  %s %s', ok ? '[OK]  ' : '[FAIL]', LOGIN_PAGE.file)
    await shot(cdp, LOGIN_PAGE.file)

    // ---------- 2. 建立登录态 ----------
    // 直接在页面上下文里调登录接口并把令牌写进 localStorage。
    // 不额外往静态目录塞一个验证用 HTML —— 那样会被一起编译进小程序产物里。
    console.log('\n=== 建立登录态 ===')
    const verify = await evaluate(
      cdp,
      "(async () => {" +
        "  const res = await fetch('/api/auth/login', {" +
        "    method: 'POST'," +
        "    headers: { 'Content-Type': 'application/json' }," +
        "    body: JSON.stringify({ username: 'admin', password: '123456', client: 'MINI' })" +
        "  });" +
        "  const body = await res.json();" +
        "  if (body.code !== 200) return 'VERIFY_FAIL ' + body.message;" +
        "  localStorage.setItem('erp_refresh_token', body.data.refreshToken);" +
        "  return 'VERIFY_OK user=' + body.data.user.username + ' role=' + body.data.user.role;" +
        '})()'
    )
    const verified = String(verify).includes('VERIFY_OK')
    verified ? pass++ : fail++
    console.log('  %s 登录接口联通：%s', verified ? '[OK]  ' : '[FAIL]', String(verify).slice(0, 80))

    // ---------- 3. 业务页 ----------
    console.log('\n=== 业务页 ===')
    console.log(
      '  ' + '页面'.padEnd(16) + '关键词'.padEnd(8) + '视口'.padEnd(8) + '内容'
    )

    for (const p of PAGES) {
      text = await goto(cdp, p.hash)
      const metrics = await evaluate(
        cdp,
        'JSON.stringify({sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth, dpr: devicePixelRatio})'
      )
      const m = JSON.parse(metrics)
      // 内容宽度超过视口 2px 以上就算横向溢出
      const overflow = m.sw > m.cw + 2
      // 页面被踢回登录页是个隐蔽的失败：关键词可能因为静态文案仍然命中，
      // 但数据其实一条没拉到，所以单独判一下
      const kickedToLogin = text.includes('演示账号（点击填充）')
      const hit = p.keywords.every((k) => text.includes(k))
      const good = hit && !overflow && !kickedToLogin
      good ? pass++ : fail++
      await shot(cdp, p.file)
      console.log(
        '  ' +
          p.file.padEnd(16) +
          (hit ? 'OK' : 'MISS').padEnd(8) +
          String(m.cw).padEnd(8) +
          m.sw +
          (overflow ? '  ← 横向溢出!' : '') +
          (kickedToLogin ? '  ← 被踢回登录页!' : '')
      )
      if (!hit) {
        const missing = p.keywords.filter((k) => !text.includes(k))
        console.log('        缺少关键词: %s', missing.join(' / '))
      }
    }

    // ---------- 4. 控制台错误 ----------
    if (cdp.logs.length) {
      console.log('\n=== 控制台错误 %d 条 ===', cdp.logs.length)
      cdp.logs.slice(0, 10).forEach((l) => console.log('  ' + l.slice(0, 200)))
    } else {
      console.log('\n=== 控制台无报错 ===')
    }

    console.log('\n通过 %d 项，失败 %d 项', pass, fail)
  } finally {
    try {
      await cdp?.send('Browser.close')
    } catch (e) {
      /* 忽略 */
    }
    await sleep(600)
    if (!chrome.killed) {
      chrome.kill()
    }
    await sleep(400)
    try {
      rmSync(profile, { recursive: true, force: true })
    } catch (e) {
      /* profile 目录可能还被占用，留给系统清理 */
    }
  }

  process.exit(fail === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('执行失败：', e.message)
  process.exit(1)
})
