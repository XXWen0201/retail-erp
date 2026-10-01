# 中小零售门店进销存与库存预警管理系统

> 毕业设计项目 ｜ Spring Boot 3.5 + Vue 3 + MySQL 8 + Redis + Spring AI

面向便利店、文具店、小超市这类单店（或 2~3 家分店）场景，把「进货 → 卖货 → 盘点 → 预警 → 补货」
这条闭环做完整，重点解决三件事：**库存扣减在并发下不能算错**、**盘点差异必须能追溯到具体流水**、
**补货量不能靠拍脑袋**。

---

## 一、技术栈

| 层次 | 选型 |
|---|---|
| 后端 | Spring Boot 3.5.6、Spring MVC、Spring Validation、Spring Security Crypto(BCrypt) |
| 持久层 | MyBatis-Plus 3.5.12（分页 / 乐观锁 / 逻辑删除 / 防全表更新插件） |
| 数据库 | MySQL 8.0（事务 + `READ_COMMITTED` 隔离级别） |
| 缓存 | Redis 7（令牌、单号发号器、库存缓存、Lua 脚本原子扣减） |
| 认证 | JWT（access 30 分钟）+ 服务端刷新令牌（7 天、可吊销、httpOnly Cookie、轮转） |
| AI | Spring AI 1.1.0（OpenAI 兼容协议 → 阿里云百炼 DashScope，qwen-plus） |
| PC 管理端 | Vue 3.5 + Vite 6 + Element Plus 2.9 + Pinia 3 + Vue Router 4 + ECharts 5 |
| 移动端 | uni-app（Vue 3 + Vite 5）→ 微信小程序 ｜ Pinia 2 |
| 文档 | springdoc-openapi（Swagger UI） |

---

## 二、功能清单

### 1）基础资料
- **商品管理**：编码 / 条码 / 分类 / 规格 / 单位 / 进价 / 售价 / 库存上下限 / 保质期天数
- **商品分类**：树形结构（`parentId`），删除时校验是否被商品引用
- **供应商管理**：编码 / 联系人 / 电话 / 地址，支持停用

### 2）业务单据
- **采购**：录入草稿 → **入库**（生成批次、增加库存、写流水）→ 作废 / 删除
- **销售**：**开单即出库**，按 **FEFO（先到期先出）** 扣减批次；作废自动回滚库存
- **退货**：采购退货（库存减）/ 销售退货（库存增，原路退回原批次），可关联原单并校验退货数量不超原单
- **盘点**：建账抓取账面数 → 录入实盘数 → 提交后按差异自动生成盘盈 / 盘亏流水

### 3）批次与保质期
- 入库时按「生产日期 + 商品保质期」自动推算到期日，生成批次号
- 批次维度的剩余天数、效期状态（正常 / 临期 30 天内 / 已过期）
- 出库时自动跳过已过期批次

### 4）库存预警
五类预警，**幂等扫描**（同一问题只更新不重复插入，库存恢复后自动关闭）：

| 类型 | 触发条件 | 级别 |
|---|---|---|
| `OUT_OF_STOCK` 断货 | 库存 = 0 | DANGER |
| `LOW_STOCK` 低于下限 | 0 < 库存 < 下限 | WARN |
| `OVER_STOCK` 高于上限 | 库存 > 上限 | WARN |
| `NEAR_EXPIRY` 批次临期 | 距到期 ≤ 30 天且批次有剩余 | WARN |
| `EXPIRED` 批次过期 | 已过到期日且批次有剩余 | DANGER |

支持手动触发扫描，也有定时任务（每天 7:00 / 19:00）。

### 5）统计报表
- **工作台**：今日 / 本月销售额与毛利、库存成本总额、待办（待入库采购单、未处理预警）、近 30 天销售趋势、分类库存
- **采购统计**：趋势、供应商占比与明细
- **销售统计**：趋势、分类占比、商品销量 TOP
- **库存统计**：SKU 数 / 库存量 / 成本总额、分类明细、低库存清单、临期清单
- **毛利统计**：收入 / 成本 / 毛利趋势、分类毛利与毛利率

### 6）AI 智能助手（Spring AI）
- **智能补货建议**：移动平均 + 安全库存（Z=1.65，服务水平 95%，到货周期 3 天）算出补货点与建议补货量，
  **纯本地算法，不依赖外部服务，断网也能用**；AI 只负责写文字说明
- **库存智能问答**：Function Calling，模型自主调用库存查询工具后再回答，答案基于真实数据
- **SSE 流式输出**：补货分析与问答都支持流式（`Flux`，保留背压）
- **降级兜底**：未配置 API Key 或模型不可用时，应用照常启动，AI 部分退化为本地规则文案

### 7）移动端（微信小程序）

面向店员的移动办公端，用 uni-app 一套代码编译到微信小程序。

- **工作台**：今日销售额 / 毛利 / 订单数、本月概览、近 7 天柱状趋势、待办（未处理预警 / 待入库）、8 个快捷入口
- **商品库存**：搜索（名称 / 编码 / 条码）、分类筛选、一键「仅看低于下限」、触底加载
- **扫码查货**：`uni.scanCode` 扫条码直接定位商品并显示库存与售价
- **销售开单**：选品加购物车、改价改数量、优惠、提交即出库；库存不足（4001）在页面上就地提示并可改数量重试
- **销售单**：列表查询、详情（含出库批次与毛利）、作废并回滚库存
- **批次与保质期**：全部在用 / 临期 30 天 / 已过期 三个视图
- **库存流水**：按业务类型筛选，展示变动前后数量与来源单号
- **库存预警**：五类计数、按类型与状态筛选、处理（填说明）/ 忽略、一键手动扫描
- **AI 助手**：补货建议列表 + 单商品 AI 分析弹层；库存智能问答（对话式，显示模型调用了哪些工具）
- **我的**：当前用户、RBAC 权限说明、功能入口、退出登录

> 详情见 [`docs/miniprogram.md`](docs/miniprogram.md)。后端为适配小程序新增了
> `client=MINI` 的令牌载体模式，PC 端行为完全不变。

---

## 三、目录结构

```
retail-erp/
├── backend/                       # Spring Boot 后端
│   ├── src/main/java/com/retail/erp/
│   │   ├── common/                # 统一响应体 R、错误码、分页、异常体系、枚举、工具
│   │   ├── config/                # 配置类（AppProperties 集中校验、MyBatis、Redis、Web、OpenAPI）
│   │   ├── security/              # JWT、登录用户、ThreadLocal 上下文、鉴权拦截器、角色注解
│   │   └── module/                # 业务模块（按功能分包）
│   │       ├── system/            # 认证与用户
│   │       ├── basic/             # 商品 / 分类 / 供应商
│   │       ├── purchase/          # 采购
│   │       ├── sale/              # 销售
│   │       ├── returns/           # 退货
│   │       ├── stock/             # 库存引擎 / 盘点 / 预警 / 压测
│   │       ├── report/            # 报表
│   │       └── ai/                # Spring AI 补货与问答
│   └── src/main/resources/
│       ├── db/schema.sql          # 建表语句（18 张表）
│       ├── db/data.sql            # 种子数据（30 商品 / 6 供应商 / 90 天销售流水）
│       └── application*.yml       # 配置
├── frontend/                      # PC 管理端（Vue 3）
│   └── src/
│       ├── api/                   # axios 封装 + 各模块接口
│       ├── router/                # 路由与登录守卫
│       ├── stores/                # Pinia（用户会话）
│       ├── layout/                # 侧边栏 / 顶栏 / 布局
│       ├── composables/           # ECharts 封装
│       ├── utils/                 # 格式化与业务字典
│       └── views/                 # 23 个页面
├── miniprogram/                   # 移动端（uni-app → 微信小程序）
│   └── src/
│       ├── api/                   # 按后端模块拆分的接口
│       ├── utils/                 # 请求封装（401 续期重放）/ 令牌策略 / 格式化
│       ├── stores/                # Pinia（用户与登录态）
│       ├── config/                # 后端地址（真机调试改这里）
│       ├── static/tabbar/         # tabBar 图标（脚本生成）
│       └── pages/                 # 14 个页面
├── docs/                          # 交付文档
│   ├── api-contract.md            # 后端接口契约
│   ├── architecture.md            # 架构与关键设计决策
│   ├── defense-notes.md           # 答辩要点（含可能被问的问题）
│   ├── deployment.md              # 部署说明
│   ├── miniprogram.md             # 移动端接入与调试
│   ├── screenshots/               # PC 端系统截图
│   └── screenshots-mini/          # 移动端系统截图
└── tools/
    ├── mvn.sh                     # Windows / Git Bash 下的 Maven 启动器
    ├── smoke_test.py              # 后端端到端冒烟测试（42 项）
    ├── smoke_mini.py              # 移动端认证冒烟测试（23 项）
    ├── gen_mini_screenshots.mjs   # CDP 手机视口截图 + 横向溢出检测
    ├── gen_tabbar_icons.py        # 生成 tabBar 图标（零依赖 PNG 编码）
    ├── gen_entities.py            # 由 schema.sql 生成实体与 Mapper
    └── gen_seed_data.py           # 生成种子数据
```

---

## 四、环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17+（实测 21） | |
| Maven | 3.9+ | |
| MySQL | 8.0+ | 库名 `retail_erp`，默认 `root / 123456` |
| Redis | 6+ | `localhost:6379`，无密码 |
| Node.js | 20.19+ / 22.12+ | 实测 22.22.2 |

数据库、Redis 的连接信息可通过环境变量覆盖：`MYSQL_USER`、`MYSQL_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`。
AI 的 API Key 用 `DASHSCOPE_API_KEY` 覆盖。

### 关于 AI 密钥（重要）

- Spring AI 的自动配置在 `spring.ai.openai.api-key` 为空时会直接抛异常，**应用起不来**。
  所以仓库里这份 `application-dev.yml` 只放了一个非空占位符，保证 clone 下来能直接启动。
- 真实密钥写到 **`backend/config/application-dev.yml`**（Spring Boot 会优先读该位置，
  该文件已被 `.gitignore` 排除，不会进仓库）。仓库里附了模板
  `backend/config/application-dev.yml.example`，复制改名填入即可。
- 也可以不用文件，改用环境变量注入：

  ```bash
  setx DASHSCOPE_API_KEY "sk-你的密钥"     # Windows，需新开终端生效
  export DASHSCOPE_API_KEY=sk-你的密钥     # macOS / Linux
  ```

- 没配真实密钥时应用能正常启动，但 AI 助手相关接口会返回「AI 暂不可用」。

---

## 五、启动步骤

### 1. 初始化数据库

```bash
cd backend/src/main/resources/db

# 建库 + 建表
mysql -uroot -p123456 < schema.sql

# 灌入种子数据（30 个商品、90 天销售流水、含演示用的临期/过期/低库存数据）
mysql -uroot -p123456 retail_erp < data.sql
```

> 两个脚本都是可重复执行的（建表用 `DROP TABLE IF EXISTS`，数据先清后插）。

### 2. 启动后端

```bash
cd backend

# 方式一：直接跑（推荐开发时）
mvn spring-boot:run

# 方式二：打包后运行
mvn clean package -DskipTests
java -jar target/retail-erp-1.0.0.jar
```

启动后：
- 接口地址 `http://localhost:8080`
- 接口文档 `http://localhost:8080/swagger-ui.html`
- 健康检查 `http://localhost:8080/actuator/health`

> Windows + Git Bash 环境下 `mvn` 可能报 `找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher`，
> 这是 MSYS 路径转义问题，用仓库里的 `bash tools/mvn.sh backend <参数>` 代替即可。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev          # 开发模式，http://localhost:5173
npm run build        # 生产构建，产物在 dist/
```

前端开发模式下 `/api` 由 Vite 代理到 `http://localhost:8080`（见 `vite.config.js`），
前后端同源，httpOnly 刷新 Cookie 不涉及跨站问题。

### 4. 启动移动端（微信小程序）

```bash
cd miniprogram
npm install

npm run build:mp-weixin   # 生产产物在 dist/build/mp-weixin（已构建好，可直接导入）
npm run dev:mp-weixin     # 开发模式，产物在 dist/dev/mp-weixin（改代码自动增量编译）
npm run dev:h5            # 可选：编译成 H5 用浏览器调试，端口 5273
```

然后用微信开发者工具「导入项目」，**目录选 `miniprogram/dist/build/mp-weixin`**（不是 `miniprogram/`），
AppID 用测试号即可。`urlCheck` 已在小程序配置里关掉，本地 http 接口可以直接调通。

> 真机预览要把 `miniprogram/src/config/index.js` 里的 `API_HOST` 改成电脑的局域网 IP。
> 完整说明见 [`docs/miniprogram.md`](docs/miniprogram.md)。

### 5. 演示账号

| 账号 | 密码 | 角色 | 权限 |
|---|---|---|---|
| `admin` | `123456` | ADMIN | 全部权限 |
| `manager` | `123456` | MANAGER | 可建单、入库、盘点、压测 |
| `staff` | `123456` | STAFF | 只读查询 |

> 密码在库中以 BCrypt 哈希存储，不是明文。

### 6. 端到端冒烟测试

后端启动后执行：

```bash
python tools/smoke_test.py            # 42 项，覆盖认证 → 采购 → 销售 → 退货 → 盘点 → 预警 → 报表 → 补货算法
python tools/smoke_test.py --with-ai  # 额外跑一次真实的模型调用
python tools/smoke_mini.py            # 23 项，小程序端双令牌模式与 PC 端未受影响
```

---

## 六、几个值得说的设计点

详细说明见 [`docs/architecture.md`](docs/architecture.md)，这里先列结论：

1. **库存扣减一致性做了两套方案并可对比**
   - `DB`：`UPDATE ... WHERE version = ?` 乐观锁 + `READ_COMMITTED` 隔离级别（避免快照读让重试失效）
   - `REDIS`：Redis + Lua 脚本原子预扣减，把竞争挡在数据库之前
   - 内置压测接口，返回 QPS 与「成功次数是否等于实际扣减量」的一致性结论，可现场演示

2. **盘点以「当前库存」为准调平**，而不是用建账时的快照。这样盘点期间发生的正常业务不会被误算成盘亏，
   差异同时写入库存流水（`bizNo` 指向盘点单号），可逐笔追溯。

3. **令牌不落 localStorage，且同一套认证体系同时服务 PC 端与小程序端**
   - PC 端：刷新令牌走 httpOnly Cookie，JS 读不到，XSS 也偷不走
   - 小程序端：没有浏览器那套 Cookie 语义，刷新令牌改为随响应体返回，客户端自行存储
   - 校验、轮转、吊销这些真正的逻辑两端**完全共用**，不存在两份实现
   - 前端遇到 401 自动续期并重放原请求，用「单例 Promise」防止并发刷新风暴

4. **AI 是增强而不是依赖**：补货数量永远由本地算法给，AI 只写解释性文字；
   网关用 `ObjectProvider` 取 `ChatClient.Builder`，「Bean 不存在」被当作正常状态处理，
   而不是启动失败 —— 一个可选功能不该有拖垮整个应用的能力。

5. **补货算法参数可解释**：移动平均算日均销量 → 安全库存 = Z × 日销量标准差 × √到货周期 →
   补货点 = 日均销量 × 到货周期 + 安全库存，每个中间量都在接口里返回，答辩时可直接对着数据讲。
