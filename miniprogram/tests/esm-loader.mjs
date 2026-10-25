/**
 * Node ESM 解析与转换钩子
 *
 * 为什么需要它：小程序源码是给 uni-app / Vite 打包的，有两处 Node 不认的写法 ——
 *   1. 目录导入 `from '../config'`，由 Vite 补全成 `../config/index.js`；
 *      Node 的 ESM 解析器会直接报 ERR_UNSUPPORTED_DIR_IMPORT
 *   2. 条件编译注释 `// #ifdef H5 ... // #endif`，
 *      在 uni-app 里是「按平台裁剪代码」的指令，在 Node 里只是普通注释，
 *      于是 `export const API_BASE` 会被声明两次，直接语法错误
 *
 * 这里做最小限度的补全与裁剪，只服务于测试，不改动业务源码 ——
 * 为了跑测试去改产品代码，是把测试的需求倒灌进了产品。
 */
import { existsSync, readFileSync, statSync } from 'node:fs'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { dirname, resolve as resolvePath } from 'node:path'

export async function resolve(specifier, context, nextResolve) {
  try {
    return await nextResolve(specifier, context)
  } catch (err) {
    // 只处理相对路径的目录导入，其它情况原样抛出
    if (!specifier.startsWith('.') || !context.parentURL) {
      throw err
    }
    const baseDir = dirname(fileURLToPath(context.parentURL))
    const target = resolvePath(baseDir, specifier)

    const candidates = [
      resolvePath(target, 'index.js'),
      `${target}.js`,
      `${target}.mjs`
    ]
    for (const file of candidates) {
      if (existsSync(file) && statSync(file).isFile()) {
        return { url: pathToFileURL(file).href, shortCircuit: true }
      }
    }
    throw err
  }
}

/**
 * 处理 uni-app 的条件编译注释
 *
 * 测试按 H5 平台语义执行（API_BASE 走相对路径 /api），
 * 与 `npm run dev:h5` 的行为一致，这样也能顺带验证 H5 那条分支。
 */
const IFDEF = /^\s*\/\/\s*#ifdef\s+(\w+)\s*$/
const IFNDEF = /^\s*\/\/\s*#ifndef\s+(\w+)\s*$/
const ENDIF = /^\s*\/\/\s*#endif\s*$/

const PLATFORM = 'H5'

function stripConditionalCompilation(source) {
  if (!source.includes('#ifdef') && !source.includes('#ifndef')) {
    return source
  }
  const lines = source.split('\n')
  const out = []
  // 栈里存「当前块是否要保留」
  const stack = []

  for (const line of lines) {
    const ifdef = line.match(IFDEF)
    const ifndef = line.match(IFNDEF)
    if (ifdef) {
      stack.push(ifdef[1] === PLATFORM)
      continue
    }
    if (ifndef) {
      stack.push(ifndef[1] !== PLATFORM)
      continue
    }
    if (ENDIF.test(line)) {
      stack.pop()
      continue
    }
    // 只有所有外层块都保留时，这行才保留
    if (stack.every(Boolean)) {
      out.push(line)
    }
  }
  return out.join('\n')
}

export async function load(url, context, nextLoad) {
  const isProjectSource = url.includes('/src/') || url.includes('/tests/')
  if (!isProjectSource) {
    return nextLoad(url, context)
  }
  if (!url.endsWith('.js') && !url.endsWith('.mjs')) {
    return nextLoad(url, context)
  }

  // 直接自己读文件、自己声明 format: module，
  // 不走 nextLoad —— 否则 Node 会先按 package.json 里没有 type 字段去猜，
  // 猜错后打印一条 MODULE_TYPELESS_PACKAGE_JSON 警告再重新按 ESM 解析
  const filePath = fileURLToPath(url)
  const source = readFileSync(filePath, 'utf8')

  return {
    format: 'module',
    source: stripConditionalCompilation(source),
    shortCircuit: true
  }
}

