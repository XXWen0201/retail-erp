/**
 * 把解析钩子注册进当前 Node 进程
 *
 * 用法：node --import ./tests/register-loader.mjs --test tests/token.test.mjs
 *
 * 说明：Node 20+ 推荐用 module.register() 在进程内注册 loader，
 * 比 --experimental-loader 稳定，也不会在每次启动时打印实验性警告。
 */
import { register } from 'node:module'
import { pathToFileURL } from 'node:url'

register('./esm-loader.mjs', pathToFileURL(import.meta.filename))
