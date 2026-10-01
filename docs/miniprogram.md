# 移动端（微信小程序）—— 接入与调试说明

> 面向店员的移动办公端。一套 uni-app 代码，主要产出微信小程序，
> 同时可以编译成 H5 便于在浏览器里调试。

---

## 1. 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| 框架 | uni-app（Vue 3 + Vite） | 一套代码编译到小程序与 H5 |
| 状态 | Pinia | 用户信息与登录态 |
| 请求 | `uni.request` 自封装 | 401 单例刷新 + 原请求重放 |
| 样式 | SCSS + 主题变量 | 与 PC 管理端同一套色板 |

> 之所以不用 axios、ECharts 这类库：小程序里没有 DOM，
> 它们跑不起来。图表改用 `view` 拼柱状图，请求改用 `uni.request`。

---

## 2. 目录结构

```
miniprogram/
├── package.json            依赖与编译脚本
├── vite.config.js          H5 端代理配置
├── index.html              H5 端入口
└── src/
    ├── main.js             入口（createSSRApp + pinia）
    ├── App.vue             冷启动恢复登录态 + 全局样式
    ├── manifest.json       小程序配置（AppID、urlCheck 等）
    ├── pages.json          页面与 tabBar 声明
    ├── uni.scss            主题变量（自动注入所有页面）
    ├── config/index.js     后端地址（真机调试要改这里）
    ├── api/                按后端模块拆分的接口
    ├── utils/
    │   ├── request.js      请求封装（核心：401 续期重放）
    │   ├── token.js        令牌存取策略
    │   └── format.js       金额 / 日期 / 枚举 → 中文文案
    ├── stores/user.js      用户与登录态
    ├── static/tabbar/      tabBar 图标（脚本生成，见 tools/gen_tabbar_icons.py）
    └── pages/              各业务页面
```

---

## 3. 快速开始

### 3.1 安装依赖

```bash
cd miniprogram
npm install
```

### 3.2 编译

```bash
# 生产构建：产物在 dist/build/mp-weixin（本项目已构建好，可直接导入）
npm run build:mp-weixin

# 开发模式：产物在 dist/dev/mp-weixin，改代码自动增量编译
npm run dev:mp-weixin

# H5 调试：起在 5273 端口，可直接用浏览器打开
npm run dev:h5
```

### 3.3 用微信开发者工具打开

1. 打开「微信开发者工具」→ 导入项目
2. 目录选择 **`miniprogram/dist/build/mp-weixin`**（注意不是 `miniprogram/`）
   —— 如果你跑的是 `npm run dev:mp-weixin`，则选 `miniprogram/dist/dev/mp-weixin`

> **也可以直接导入 `miniprogram/` 工程根目录**：根目录的 `project.config.json` 里已经配了
> `"miniprogramRoot": "dist/build/mp-weixin/"`，开发者工具会顺着这个字段去找真正的小程序产物。
> 两种方式效果一样，选哪个都行。**唯一不能做的是**：在根目录里改代码后指望它自动生效 ——
> 根目录是 uni-app 源码，改完必须 `npm run build:mp-weixin`（或跑 dev 模式）重新编译，
> 产物更新了小程序才会跟着变。
3. AppID 选择「测试号」或用你自己的 AppID
   （`manifest.json` 里留空，编译产物中为 `touristappid` 游客模式，本地调试够用）
4. 打开后确认右上角「详情 → 本地设置」里
   **「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」已勾选**

> 第 4 步不用手动操作：`manifest.json` 里已配 `mp-weixin.setting.urlCheck: false`，
> 编译出的 `project.config.json` 里就是关掉的。这里只是提供一个排查点 ——
> 如果请求报「不在以下 request 合法域名列表中」，就是这一项被重置了。

### 3.4 演示账号

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | 123456 | 管理员 |
| manager | 123456 | 店长 |
| staff | 123456 | 店员 |

---

## 4. 后端接口适配

### 4.1 为什么需要适配

PC 端的刷新令牌走 **httpOnly Cookie**，这是为浏览器设计的：
JS 读不到，XSS 也偷不走。但小程序没有浏览器那套 Cookie 语义，
硬塞 Cookie 容易出现「真机正常、工具异常」这类难查的问题。

改造方案不是另起一套认证，而是**让同一套逻辑支持两种令牌载体**：

| | Web（PC 管理端） | 小程序端 |
|---|---|---|
| 登录请求 | `{username, password}` | `{username, password, client:"MINI"}` |
| 访问令牌 | 响应体 `accessToken` | 同左 |
| 刷新令牌 | `Set-Cookie`（httpOnly） | 响应体 `refreshToken` |
| 刷新请求 | 不带 body，后端读 Cookie | `{refreshToken}` |
| 退出 | 不带 body | `{refreshToken}` |

校验、轮转、吊销这些**真正的业务逻辑两端完全共用**，不存在两份实现。

### 4.2 涉及的后端文件

| 文件 | 改动 |
|---|---|
| `module/system/dto/LoginDTO.java` | 新增可选字段 `client` |
| `module/system/dto/LoginVO.java` | 新增 `refreshToken` / `refreshExpiresIn`（仅小程序端有值） |
| `module/system/dto/RefreshDTO.java` | 新增，承载刷新 / 退出的 body 令牌 |
| `module/system/service/AuthService.java` | 登录 / 刷新 / 退出支持双载体 |
| `module/system/controller/AuthController.java` | refresh / logout 接受可选 body |
| `config/WebConfig.java` | `/api/auth/logout` 加入白名单（令牌过期也能登出） |

PC 端**无需改动**，行为完全不变（已用冒烟测试覆盖验证）。

### 4.3 令牌存储策略

| 令牌 | 存放位置 | 理由 |
|---|---|---|
| accessToken | **只放内存** | 有效期 30 分钟，持久化收益小、风险大 |
| refreshToken | 本地缓存 `wx.setStorageSync` | 不存就要每次冷启动重新输密码；它落库可吊销且每次刷新轮转 |

冷启动流程：本地有 refreshToken → 静默换一张 accessToken → 拉用户信息 → 直接进工作台。
用户完全无感，失败则回到登录页。

---

## 5. 页面清单

### tabBar（4 个）

| 页面 | 路径 | 内容 |
|---|---|---|
| 工作台 | `pages/dashboard/index` | 今日销售 / 毛利 / 订单数、本月概览、近 7 天柱状趋势、待办、快捷入口 |
| 库存 | `pages/stock/index` | 商品搜索、分类筛选、仅看低于下限、扫码入口、触底加载 |
| 预警 | `pages/alert/index` | 五类预警计数、按类型/状态筛选、处理（填说明）/ 忽略、手动扫描 |
| 我的 | `pages/mine/index` | 用户信息、权限说明、功能入口、退出登录 |

### 二级页

| 页面 | 路径 | 内容 |
|---|---|---|
| 登录 | `pages/login/index` | 账号密码、演示账号一键填充、已登录自动跳转 |
| 商品详情 | `pages/stock/detail` | 库存大数、上下限、价格与毛利率、批次与保质期、跳开单 |
| 扫码查货 | `pages/stock/scan` | `uni.scanCode` 扫码 → 定位商品；无摄像头环境退化为手动输入 |
| 批次保质期 | `pages/stock/batch` | 全部在用 / 临期 30 天 / 已过期 三个视图 |
| 库存流水 | `pages/stock/record` | 按业务类型筛选、变动前后数量、来源单号 |
| 销售单 | `pages/sale/list` | 单号搜索、金额与毛利、触底加载 |
| 销售开单 | `pages/sale/create` | 选品加购物车、改价改数量、优惠、提交出库 |
| 销售单详情 | `pages/sale/detail` | 明细与出库批次、成本与毛利、作废（回滚库存） |
| AI 助手 | `pages/ai/index` | 补货建议列表 + 单商品 AI 分析；库存智能问答（对话式） |
| AI 调用记录 | `pages/ai/logs` | 提问、回答、模型、耗时、调用工具 |

---

## 6. 真机调试

小程序真机连不上 `localhost` —— 那是手机自己。需要改成电脑的局域网 IP：

1. 查电脑 IP：`ipconfig`，找 IPv4 地址（如 `192.168.1.7`）
2. 改 `src/config/index.js`：

```js
export const API_HOST = 'http://192.168.1.7:8080'
```

3. 确认手机与电脑在**同一 WiFi**
4. 确认电脑防火墙放行 8080（首次连接 Windows 会弹窗询问，选允许）
5. 重新 `npm run dev:mp-weixin`，在开发者工具点「预览」扫码

> 正式上线才需要 HTTPS + 已备案域名。答辩演示用开发模式完全够。

---

## 7. 自动化验证

两个脚本，都在项目根的 `tools/` 下：

```bash
# 接口层面：双令牌模式、轮转、重放拦截、退出吊销、PC 端未受影响（23 项）
python tools/smoke_mini.py

# 页面层面：模拟手机视口逐页检查关键词、量横向溢出、抓控制台报错，
# 同时产出 docs/screenshots-mini/ 里的截图
node tools/gen_mini_screenshots.mjs docs/screenshots-mini
```

> `gen_mini_screenshots.mjs` 走的是 Chrome DevTools Protocol，而不是命令行 `--screenshot`。
> 原因是 headless Chrome 的窗口有最小尺寸约束（实测约 500px）：`--window-size=390` 会被
> 强行抬高到 489 宽，而截图仍按 390 裁剪，看起来像页面布局溢出，实际是截图参数的问题。
> 走 CDP 的 `Emulation.setDeviceMetricsOverride` 才能真正模拟
> 「390 × 844、2 倍像素密度、移动端」的视口，顺带能客观地量出 `scrollWidth` 有没有超。

前置条件：H5 端已启动（`npm run dev:h5`），后端已启动。

---

## 8. 常见问题

**Q：请求报「不在以下 request 合法域名列表中」**
A：开发者工具「详情 → 本地设置」里勾上「不校验合法域名…」。
真机调试时同样需要在该模式下的「预览」入口进入。

**Q：登录后过一会儿就掉线**
A：检查 `config/index.js` 的后端地址是否可达。
令牌续期逻辑在 `utils/request.js` 里：任意请求拿到 401 会先静默续期再重放，
续期也失败才会跳登录页。

**Q：H5 端调接口报 `Invalid CORS request`**
A：`vite.config.js` 的代理里已经 `removeHeader('origin')`。
如果仍出现，检查后端 `app.cors.allowed-origins` 是否包含了 H5 的调试端口。

**Q：扫码没反应**
A：H5 端和部分环境没有摄像头权限，会提示退化为手动输入条码；
小程序端需在真机或开发者工具里授予摄像头权限。
