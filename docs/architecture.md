# 架构与关键设计决策

> 这份文档只讲「为什么这么设计」，接口细节看 [`api-contract.md`](api-contract.md)。

---

## 一、整体架构

```
┌──────────────────────────────────────────────────────────────┐
│                      浏览器（Vue 3 SPA）                       │
│   Element Plus 组件  ·  Pinia 会话  ·  axios(401 自动续期)      │
└───────────────────────────┬──────────────────────────────────┘
                            │  HTTP / JSON  ·  SSE(text/event-stream)
                            │  Authorization: Bearer <accessToken>
                            │  Cookie: refreshToken (httpOnly)
┌───────────────────────────▼──────────────────────────────────┐
│                     Spring Boot 应用（8080）                    │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ RequestIdFilter → AuthInterceptor → Controller          │  │
│  │                                        ↓                │  │
│  │                                     Service             │  │
│  │                                        ↓                │  │
│  │                                  Mapper / Redis         │  │
│  │  ResponseAdvice(回填 requestId) ← GlobalExceptionHandler │  │
│  └────────────────────────────────────────────────────────┘  │
└───────┬──────────────────┬───────────────────┬───────────────┘
        │                  │                   │
   ┌────▼────┐        ┌────▼────┐        ┌─────▼──────┐
   │ MySQL 8 │        │ Redis 7 │        │ 阿里云百炼  │
   │ 18 张表  │        │ 令牌/发号│        │ qwen-plus  │
   └─────────┘        │ 库存缓存 │        └────────────┘
                      └─────────┘
```

### 分层与依赖方向

```
Controller  ──►  Service  ──►  Mapper / Redis
   │                │
   │  只做三件事：      │  业务规则、事务边界、编排
   │  收参数、调服务、   │  不依赖 HttpServletRequest / ResponseEntity
   │  包响应体         │
```

- **Controller 不写业务逻辑**。`AuthController` 里没有任何密码比对逻辑，全部在 `AuthService`。
  这样换一套 HTTP 入口（比如加 gRPC）时业务代码不用重写。
- **Service 不依赖 HTTP 类型**，因此可以直接单元测试。
- **按功能分包**（`module/purchase`、`module/stock`…）而不是按技术分层分包，
  一个需求的改动集中在一个目录里，改动面小。

---

## 二、模块划分

| 模块 | 职责 |
|---|---|
| `common` | 统一响应体 `R`、错误码、分页结果、异常体系、业务枚举、单号生成器 |
| `config` | `AppProperties`（集中配置校验）、MyBatis-Plus、Redis（含 Lua 脚本）、Web（CORS + 拦截器）、OpenAPI |
| `security` | `JwtService`、`LoginUser`、`UserContext`(ThreadLocal)、`AuthInterceptor`、`@RequireRole` |
| `module/system` | 认证（登录 / 刷新 / 退出 / me） |
| `module/basic` | 商品、分类、供应商 |
| `module/purchase` | 采购单录入与入库 |
| `module/sale` | 销售开单与出库 |
| `module/returns` | 采购退货 / 销售退货 |
| `module/stock` | **库存核心引擎**（StockCoreService）、查询（含压测）、盘点、预警 |
| `module/report` | 工作台与四类报表 |
| `module/ai` | 补货算法、AI 网关、Function Calling 工具、SSE 流式 |

---

## 三、关键设计决策

### 1. 库存扣减一致性 —— 两套方案，可切换、可对比

这是整个系统最核心的技术点。同一件商品的库存被并发扣减时，如果只写
`UPDATE stock SET quantity = quantity - 5`，看似原子，但业务层拿不到「扣之前的数量」，
也无法判断是否扣成功；如果先 `SELECT` 再 `UPDATE`，两个事务可能读到同一个旧值，造成**丢失更新**（超卖）。

系统实现了两套方案，通过配置 `app.stock.deduct-mode = DB | REDIS` 切换：

**方案 A：数据库乐观锁 + `READ_COMMITTED`**

```sql
-- 带 version 条件的更新，影响行数为 0 说明被并发改动过
UPDATE stock
SET quantity = #{newQty}, version = version + 1
WHERE id = #{id} AND version = #{oldVersion} AND quantity >= #{need}
```

- 冲突时由 `StockCoreService` 重试，最多 3 次（`app.stock.max-retry`）
- **隔离级别必须显式设为 `READ_COMMITTED`**：MySQL 默认 `REPEATABLE_READ` 下，
  事务内的快照读会一直读到旧版本号，导致乐观锁重试永远失败（重试时读到的 version 不变）。
  这是踩过的坑，也是答辩时值得讲的一个点。
- 用 `quantity >= #{need}` 把「库存不足」的判断也放进 UPDATE 条件里，避免先查后扣的时间窗。

**方案 B：Redis + Lua 原子预扣减**

```lua
-- stockDeductScript：判断与扣减在同一次 Lua 执行中完成，Redis 单线程保证原子性
local cur = tonumber(redis.call('GET', KEYS[1]) or '-1')
if cur < 0 then return -1 end          -- 缓存未预热
if cur < tonumber(ARGV[1]) then return -2 end  -- 库存不足
redis.call('DECRBY', KEYS[1], ARGV[1])
return cur - tonumber(ARGV[1])
```

- 把竞争挡在数据库之前，再由事务落库；缓存写后在事务提交后同步，避免回滚造成脏缓存。
- 缓存一律带 TTL（`app.stock.cache-ttl-seconds`）。

**对比手段**：`POST /api/stock/benchmark` 用线程池 + `CountDownLatch` 制造瞬时并发，
两种模式各跑一遍，返回：

```json
{ "mode":"DB", "threads":20, "timesPerThread":50, "totalRequests":1000,
  "successCount":998, "failCount":2, "costMs":1420, "qps":704.2,
  "finalStock":823, "consistent":true }
```

`consistent` 的判定逻辑是：**成功次数 × 每次扣减量 == 实际库存减少量**。
这个接口可以现场演示，两种模式的 QPS 差异和正确性差异一目了然。

---

### 2. 批次与保质期：FEFO 扣减

零售的货是「一批一批」进来的，同一商品不同批次的成本价和到期日都不一样：

```
stock_batch: id | product_id | batch_no | production_date | expire_date
             | init_quantity | out_quantity | stock_quantity | cost_price
stock:       product_id | quantity | avg_cost | version        ← 汇总层
```

出库时的批次选择规则（`StockCoreService.outbound`）：

```
WHERE product_id = ? AND stock_quantity > 0 AND deleted = 0
  AND (expire_date IS NULL OR expire_date >= CURDATE())   ← 已过期批次直接排除
ORDER BY expire_date IS NULL, expire_date ASC, id ASC     ← 先到期先出
```

- 一个销售行可能需要跨多个批次才能扣够（`BatchDeductResult` 记录每一段的扣减明细）
- 每扣一个批次写一条库存流水，`batchId` 落到 `stock_record`，销售明细也记录实际出库批次
- 退货时**原路退回原批次**（`restoreToBatch`），而不是新建批次 —— 否则批次账会和实物对不上

---

### 3. 成本核算：移动加权平均

```
新成本 = (原库存量 × 原成本 + 本次入库量 × 本次进价) / (原库存量 + 本次入库量)
```

- 保留 4 位小数，`HALF_UP` 舍入；只在**入库**时重算
- 出库时用当前 `avg_cost` 计价，销售单同时记录 `total_cost` 与 `gross_profit`，
  毛利报表就是对这个字段做区间汇总
- 分母为 0（原库存为 0）时直接取本次进价，避免除零

---

### 4. 盘点差异追溯

**关键决策：提交盘点时以「当前库存」为准调平，而不是建账时的快照。**

如果按快照调平，那么在盘点期间（比如上午建账、下午提交）发生的正常销售会被算成盘亏。
实际做法是：

```
提交时：diff = 实盘数 − 当前库存
  diff > 0 → 盘盈入库（CHECK_PROFIT）
  diff < 0 → 盘亏出库（CHECK_LOSS）
```

同时把 **账面数快照** 和 **提交时的实际差异** 都记录下来，`stock_record.biz_no`
写入盘点单号，于是：

- 每一笔盘盈盘亏都能在「库存流水」里按盘点单号筛出来
- 盘点明细里保留了「账面 → 实盘 → 差异 → 原因」，原因字段由录入人填写（如「货架串位」）

---

### 5. 库存预警：幂等扫描

预警不能每次扫描都插一条，否则一天扫两次，同一条低库存就会堆两条。

```
先按 (productId, alertType) 或 (batchId, alertType) 查未处理的预警：
  不存在   → 插入新预警
  已存在   → 只更新 currentValue / thresholdValue（不新建）
库存恢复正常 → 把未处理预警状态改为 HANDLED（自动关闭）
```

- 唯一索引兜底 + 应用层先查后写，保证扫描可重复执行不产生脏数据
- 预警类型分商品维度（断货 / 低于下限 / 超储）和批次维度（临期 / 过期）
- 手动触发（`POST /api/alerts/scan`）+ 定时任务（`app.alert.cron`，默认 7:00 / 19:00）

---

### 6. 认证：JWT + 可吊销的刷新令牌

| | 方案 | 有效期 | 存放位置 |
|---|---|---|---|
| Access Token | JWT（HS256，密钥长度启动时校验 ≥ 32 字节） | 30 分钟 | 前端**内存**（Pinia） |
| Refresh Token | 随机串 + 服务端落库（`sys_user_token`，可吊销） | 7 天 | httpOnly Cookie |

几个刻意的选择：

1. **Access Token 不放 localStorage**。localStorage 可被任意 JS 读取，
   一旦 XSS 就是长期凭据泄露；放内存里刷新页面就丢，靠刷新令牌恢复。
2. **刷新令牌落库而不是纯 JWT**：纯 JWT 无法主动吊销（用户点「退出」后旧令牌依然有效到过期）。
   落库后退出即删记录，令牌立即失效。
3. **令牌轮转**：每次刷新都换发新的刷新令牌并删除旧的，旧令牌被重放时直接拒绝。
4. **前端 401 自动续期**：axios 响应拦截器发现 `code=401` 就调 `/auth/refresh` 再重放原请求；
   刷新请求本身用**单例 Promise**，避免页面上 8 个并发请求打出 8 次刷新。
5. **`UserContext` 必须在 `afterCompletion` 里 `clear()`**。Tomcat 线程复用，
   漏掉清理会把 A 用户的身份带给 B 用户 —— 这是最危险的一类越权 bug。
6. 令牌**不接受从 URL 查询参数传递**（会进浏览器历史、Nginx access log、Referer 头）。

---

### 7. AI 集成：增强而非依赖

AI 在这个系统里有两个用途，但都不是「必要路径」：

**智能补货**：算法在本地，AI 只负责写文字。

```
日均销量 D = 最近 N 天销量 / N
安全库存 SS = Z × σ(日销量) × √(到货周期)      Z=1.65（服务水平 95%）
补货点 ROP = D × 到货周期 + SS
建议补货量 = max(ROP × 2 − 当前库存, 0)，向上取整
紧急度：可支撑天数 = 当前库存 / D，< 3 天或已断货 → HIGH
```

接口把 `avgDailySales / safetyStock / reorderPoint / suggestQuantity / stockDays / reason`
全部返回给前端，**每个数字都能解释来源**，答辩时可以对着数据讲。

**库存问答**：Function Calling。把 `InventoryAiTools`（查询库存、查临期批次、查低库存商品等）
注册为工具，模型自己决定调哪个，答案严格基于查询结果，`toolsUsed` 会返回本次实际调用的工具名，
可用来验证答案不是编的。

**降级设计**：`ChatClient.Builder` 用 `ObjectProvider` 注入 ——
没配 API Key 时容器里根本没有这个 Bean，应用照常启动；调用时捕获异常并返回
`degraded=true` + 本地规则文案，功能不中断。

**流式输出用 `Flux`** 而不是 `SseEmitter`：Spring AI 的流本身就是 Reactor `Flux`，
直接透传比手工订阅再 re-emit 少一层转换，也不会在中间层丢掉背压信号。
前端因为 `EventSource` 不能自定义 `Authorization` 头，改用 `fetch + ReadableStream` 手工解析 SSE。

---

### 8. 工程规范化

| 关注点 | 做法 |
|---|---|
| 配置 | 全部集中在 `AppProperties`，启动时用 `@Validated` 校验（JWT 密钥长度、线程数上下限等），配错直接启动失败 |
| 响应 | 统一 `R{code, message, data, requestId, timestamp}`；业务失败也返回 HTTP 200 + 业务码，前端只看 `code` |
| 异常 | `BizException` 语义化工厂方法（`stockNotEnough` / `statusIllegal` / `duplicate`…）+ 全局处理器；**绝不返回堆栈** |
| 日志 | 结构化 JSON，每条带 `requestId`；不记录密码、令牌 |
| 参数 | 所有入参在边界校验（`@Valid` + `@Validated`），分页 `size` 上限 500 防止拉库 |
| SQL | 逻辑删除（`deleted` 字段）+ MyBatis-Plus 防全表更新插件，杜绝 `UPDATE` 漏 `WHERE` |
| 前端错误 | 4xx 不重试，5xx 才重试；网络断开显示明确提示而不是白屏 |

---

## 四、数据库设计（18 张表）

| 分类 | 表 |
|---|---|
| 系统 | `sys_user`、`sys_user_token` |
| 基础资料 | `category`、`supplier`、`product` |
| 采购 | `purchase_order`、`purchase_order_item` |
| 销售 | `sale_order`、`sale_order_item` |
| 退货 | `return_order`、`return_order_item` |
| 库存 | `stock`（商品维度汇总）、`stock_batch`（批次）、`stock_record`（流水）、`stock_check_order`、`stock_check_item`、`stock_alert` |
| AI | `ai_log` |

几个设计要点：

- `stock` 与 `stock_batch` 是**汇总与明细**的关系，`stock.quantity` 是各批次剩余量之和，
  用 `version` 字段支持乐观锁。
- `stock_record` 是**流水账**，记录 `before_quantity / after_quantity / change_quantity`，
  它是盘点差异追溯和库存对账的唯一凭据，任何改库存的操作都必须写它。
- 所有表都有 `deleted`（逻辑删除）、`create_time`、`update_time`；单据表另有 `operator_id / operator_name`
  便于按人追责。
