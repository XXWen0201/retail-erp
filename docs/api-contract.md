# 零售进销存系统 —— 后端 API 契约

> 前端所有请求都以此文档为准。后端端口 `8080`，前端开发端口 `5173`。

## 0. 通用约定

### 统一响应体

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { },
  "requestId": "a1b2c3d4e5f60718",
  "timestamp": 1790000000000
}
```

- `code === 200` 为成功，其余均为失败；失败时 `message` 是可直接展示给用户的中文文案。
- `requestId` 与后端日志、响应头 `X-Request-Id` 一致，报错时可用于定位。

### 分页结构

```json
{
  "records": [],
  "total": 128,
  "current": 1,
  "size": 10,
  "pages": 13
}
```

### 鉴权

- 访问令牌通过请求头传递：`Authorization: Bearer <accessToken>`
- 访问令牌有效期 **30 分钟**；刷新令牌放在 **httpOnly Cookie**（`refreshToken`）中，有效期 7 天
- **禁止把令牌写入 localStorage**：前端把 accessToken 只保存在 Pinia 内存中，刷新页面即丢失，靠刷新令牌恢复
- 收到 `code === 401` 时，前端自动调 `/api/auth/refresh` 续期并重放原请求；刷新失败则跳登录页
- 跨域请求需带 Cookie，axios 必须设置 `withCredentials: true`

### 错误码

| code | 含义 |
|---|---|
| 200 | 成功 |
| 400 | 参数不合法 |
| 401 | 未登录 / 登录过期 |
| 403 | 无权限 |
| 404 | 数据不存在 |
| 4001 | 库存不足 |
| 4002 | 库存被并发修改，请重试 |
| 4003 | 单据状态不允许该操作 |
| 4004 | 数据重复 |
| 4005 | 批次已过期，不能出库 |
| 4006 | 批次库存不足 |
| 4007 | 盘点单已完成 |
| 4008 | 退货数量超原单 |
| 4010 | 账号或密码错误 |
| 500 | 服务器内部错误 |
| 5001 | AI 服务暂不可用 |

---

## 1. 认证 `/api/auth`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/refresh` | 用 Cookie 换新的访问令牌 |
| POST | `/api/auth/logout` | 退出并吊销刷新令牌 |
| GET | `/api/auth/me` | 获取当前登录用户 |

**POST /api/auth/login**

```json
// 请求
{ "username": "admin", "password": "123456" }

// 响应 data
{
  "accessToken": "eyJhbGciOi...",
  "expiresIn": 1800,
  "user": { "userId": 1, "username": "admin", "realName": "文嘉仪", "role": "ADMIN" }
}
```

同时通过 `Set-Cookie` 下发 `refreshToken`（httpOnly）。

**GET /api/auth/me** → `data` 为 `user` 对象。

---

## 2. 分类 `/api/categories`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/categories` | 全部分类列表（不分页） |
| POST | `/api/categories` | 新增 |
| PUT | `/api/categories/{id}` | 修改 |
| DELETE | `/api/categories/{id}` | 删除（有商品引用时报错 4003） |

分类对象：
```json
{ "id": 1, "name": "文具用品", "parentId": 0, "sort": 1, "status": 1 }
```

---

## 3. 供应商 `/api/suppliers`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/suppliers` | 分页查询，参数 `keyword,status,page,size` |
| GET | `/api/suppliers/options` | 下拉选项（只返回在用的 id+name） |
| GET | `/api/suppliers/{id}` | 详情 |
| POST | `/api/suppliers` | 新增 |
| PUT | `/api/suppliers/{id}` | 修改 |
| DELETE | `/api/suppliers/{id}` | 删除 |

供应商对象：
```json
{ "id":1, "code":"SUP001", "name":"长沙晨光文具批发有限公司",
  "contact":"刘建国", "phone":"13873100011", "address":"...", "remark":null, "status":1 }
```

---

## 4. 商品 `/api/products`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/products` | 分页查询 |
| GET | `/api/products/{id}` | 详情 |
| POST | `/api/products` | 新增（同时初始化库存记录为 0） |
| PUT | `/api/products/{id}` | 修改 |
| DELETE | `/api/products/{id}` | 删除 |

**查询参数**：`keyword`（匹配名称/编码/条码）、`categoryId`、`status`、`lowStockOnly=true`（只看低于下限）、`page`、`size`

**商品对象**：
```json
{
  "id": 1, "code": "P1001", "barcode": "690001123456", "name": "晨光中性笔 0.5mm 黑",
  "categoryId": 1, "categoryName": "文具用品", "spec": "12支/盒", "unit": "支",
  "purchasePrice": 1.20, "salePrice": 2.50,
  "stockUpper": 800, "stockLower": 150, "shelfLifeDays": 0, "status": 1,
  "stockQuantity": 823, "avgCost": 1.2000,
  "stockStatus": "NORMAL"
}
```

`stockStatus` 取值：`NORMAL` 正常 / `LOW` 低于下限 / `OVER` 高于上限 / `OUT` 零库存。

**新增/修改请求体**（全部必填除 `barcode`、`spec`、`status`）：
```json
{ "code":"P1001", "barcode":"...", "name":"...", "categoryId":1, "spec":"...",
  "unit":"支", "purchasePrice":1.20, "salePrice":2.50,
  "stockUpper":800, "stockLower":150, "shelfLifeDays":0, "status":1 }
```

---

## 5. 采购 `/api/purchases`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/purchases` | 分页，参数 `keyword(单号),supplierId,status,startDate,endDate,page,size` |
| GET | `/api/purchases/{id}` | 详情（含明细） |
| POST | `/api/purchases` | 新建采购单（草稿） |
| PUT | `/api/purchases/{id}` | 修改草稿 |
| POST | `/api/purchases/{id}/receive` | **入库**：生成批次、增加库存、写流水 |
| POST | `/api/purchases/{id}/cancel` | 作废 |
| DELETE | `/api/purchases/{id}` | 删除草稿 |

**请求体**：
```json
{
  "supplierId": 1,
  "orderDate": "2026-09-29",
  "remark": "补货",
  "items": [
    { "productId": 7, "quantity": 100, "price": 3.50,
      "productionDate": "2026-09-20", "expireDate": "2027-03-19", "batchNo": "B20260920" }
  ]
}
```
> `productionDate` / `expireDate` / `batchNo` 可空。若填了 `productionDate` 且商品配置了保质期，后端自动推算 `expireDate`。

**采购单对象**：
```json
{
  "id":1, "orderNo":"PO20260702 001", "supplierId":1, "supplierName":"...",
  "totalQuantity":100, "totalAmount":350.00, "status":"FINISHED",
  "orderDate":"2026-07-02", "receiveTime":"2026-07-02 09:20:00",
  "operatorName":"文嘉仪", "remark":"...",
  "items":[ { "id":1,"productId":7,"productName":"乐事薯片原味 70g",
              "quantity":100,"price":3.50,"amount":350.00,
              "batchNo":"B202609200011","productionDate":"2026-09-20","expireDate":"2027-03-19" } ]
}
```

`status`：`DRAFT` 草稿 / `PENDING` 待入库 / `FINISHED` 已入库 / `CANCELED` 已作废

---

## 6. 销售 `/api/sales`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/sales` | 分页，参数 `keyword(单号/客户),status,startDate,endDate,page,size` |
| GET | `/api/sales/{id}` | 详情（含明细） |
| POST | `/api/sales` | **开单并出库**：按 FEFO 扣批次库存 |
| POST | `/api/sales/{id}/cancel` | 作废并回滚库存 |
| DELETE | `/api/sales/{id}` | 删除 |

**请求体**：
```json
{
  "customerName": "散客",
  "orderDate": "2026-09-29",
  "discountAmount": 0,
  "remark": "",
  "items": [ { "productId": 12, "quantity": 20, "price": 2.00 } ]
}
```

**销售单对象**：
```json
{
  "id":90, "orderNo":"SO20260929 090", "customerName":"散客",
  "totalQuantity":120, "totalAmount":486.00, "discountAmount":20.00,
  "payAmount":466.00, "totalCost":310.50, "grossProfit":155.50,
  "status":"FINISHED", "orderDate":"2026-09-29", "operatorName":"张伟",
  "items":[ { "id":1,"productId":12,"productName":"农夫山泉 550ml",
              "batchId":5,"batchNo":"B...","quantity":20,"price":2.00,
              "amount":40.00,"costPrice":1.0000,"costAmount":20.00 } ]
}
```

> 库存不足时返回 `code:4001`，message 形如「商品【农夫山泉 550ml】库存不足，当前 3，需要 20」。

---

## 7. 退货 `/api/returns`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/returns` | 分页，参数 `returnType(PURCHASE_RETURN/SALE_RETURN),keyword,startDate,endDate,page,size` |
| GET | `/api/returns/{id}` | 详情（含明细） |
| POST | `/api/returns` | 新建退货并**立即生效**（回退库存） |
| POST | `/api/returns/{id}/cancel` | 作废（反向回滚） |

**请求体**：
```json
{
  "returnType": "SALE_RETURN",
  "sourceOrderId": 90,
  "returnDate": "2026-09-29",
  "reason": "顾客买错口味",
  "items": [ { "productId": 12, "quantity": 2, "price": 2.00 } ]
}
```
> `sourceOrderId` 可空。填了则校验退货数量不超过原单数量（超了返回 `4008`）。

`returnType`：`PURCHASE_RETURN` 采购退货（库存减）/ `SALE_RETURN` 销售退货（库存增）

---

## 8. 库存 `/api/stock`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/stock/overview` | 库存总览卡片数据 |
| GET | `/api/stock/batches` | 批次分页，参数 `productId,expiringSoon,expired,page,size` |
| GET | `/api/stock/records` | 流水分页，参数 `productId,bizType,bizNo,startDate,endDate,page,size` |
| POST | `/api/stock/benchmark` | **并发扣减压测**，参数 `threads,timesPerThread,productId,mode(DB/REDIS)` |

**overview 返回**：
```json
{ "skuCount":30, "totalQuantity":8421, "totalCostValue":12345.67,
  "lowStockCount":2, "outOfStockCount":1, "nearExpiryBatchCount":1, "expiredBatchCount":1 }
```

**批次对象**：
```json
{ "id":5, "productId":12, "productName":"农夫山泉 550ml", "batchNo":"B202608010005",
  "purchaseOrderId":3, "productionDate":"2026-07-30", "expireDate":"2027-07-30",
  "initQuantity":810, "outQuantity":620, "stockQuantity":190,
  "costPrice":1.00, "status":1,
  "daysToExpire": 304, "expireStatus": "NORMAL" }
```
`expireStatus`：`NORMAL` / `NEAR_EXPIRY`（30 天内）/ `EXPIRED`（已过期）

**流水对象**：
```json
{ "id":1, "productId":1, "productName":"...", "batchId":1, "batchNo":"B...",
  "bizType":"PURCHASE_IN", "bizTypeLabel":"采购入库",
  "changeQuantity":810, "beforeQuantity":0, "afterQuantity":810,
  "unitCost":1.2000, "bizNo":"PO20260702001", "operatorName":"文嘉仪",
  "remark":"采购入库", "createTime":"2026-07-02 09:20:00" }
```

**benchmark 返回**：
```json
{ "mode":"DB", "threads":20, "timesPerThread":50, "totalRequests":1000,
  "successCount":998, "failCount":2, "costMs":1420,
  "qps": 704.2, "finalStock": 823, "consistent": true, "message":"..." }
```

---

## 9. 盘点 `/api/stock-checks`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/stock-checks` | 分页，参数 `status,startDate,endDate,page,size` |
| GET | `/api/stock-checks/{id}` | 详情（含明细） |
| POST | `/api/stock-checks` | 新建盘点单（自动抓取账面数） |
| PUT | `/api/stock-checks/{id}/items` | 录入实盘数 |
| POST | `/api/stock-checks/{id}/finish` | **提交盘点**：盘盈入库、盘亏出库，写流水 |
| POST | `/api/stock-checks/{id}/cancel` | 作废 |

**新建请求体**：
```json
{ "checkDate": "2026-09-29", "remark": "月度盘点",
  "productIds": [1,7,12] }
```
> `productIds` 不传则对全部在用商品建账。

**录入实盘数请求体**：
```json
{ "items": [ { "id":1, "actualQuantity": 820, "reason": "货架串位" } ] }
```

**盘点单对象**：
```json
{ "id":2, "checkNo":"PC20260929002", "status":"FINISHED", "checkDate":"2026-09-29",
  "totalDiffQuantity":7, "diffItemCount":3, "operatorName":"张伟",
  "finishTime":"2026-09-29 21:00:00", "remark":"月度盘点",
  "items":[ { "id":1,"productId":1,"productName":"...","batchId":null,"batchNo":null,
              "bookQuantity":823,"actualQuantity":820,"diffQuantity":-3,"reason":"货架串位" } ] }
```

---

## 10. 预警 `/api/alerts`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/alerts` | 分页，参数 `alertType,alertLevel,status,keyword,page,size` |
| GET | `/api/alerts/summary` | 各类型未处理数量统计 |
| POST | `/api/alerts/scan` | 手动触发全量扫描 |
| PUT | `/api/alerts/{id}/handle` | 处理，body `{ "handleRemark":"已下单补货" }` |
| PUT | `/api/alerts/{id}/ignore` | 忽略 |

**预警对象**：
```json
{ "id":1, "productId":3, "productName":"得力订书机 12 号",
  "batchId":null, "batchNo":null,
  "alertType":"LOW_STOCK", "alertTypeLabel":"低于库存下限",
  "alertLevel":"WARN", "currentValue":9, "thresholdValue":15,
  "expireDate":null, "status":"UNHANDLED",
  "handleRemark":null, "handleTime":null, "handleUser":null,
  "createTime":"2026-09-29 08:00:00" }
```

`alertType`：`LOW_STOCK` / `OVER_STOCK` / `OUT_OF_STOCK` / `NEAR_EXPIRY` / `EXPIRED`
`alertLevel`：`WARN` / `DANGER`
`status`：`UNHANDLED` / `HANDLED` / `IGNORED`

---

## 11. 报表 `/api/reports`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/reports/dashboard` | 工作台汇总 |
| GET | `/api/reports/purchase` | 采购统计 |
| GET | `/api/reports/sales` | 销售统计 |
| GET | `/api/reports/stock` | 库存统计 |
| GET | `/api/reports/profit` | 毛利统计 |
| GET | `/api/reports/top-products` | 商品销量排行 |

**通用查询参数**：`startDate`、`endDate`（默认最近 30 天）、`groupBy=day|month`（默认 day）

**dashboard 返回**：
```json
{ "todaySalesAmount":1865.50, "todaySalesCount":3, "todayGrossProfit":620.30,
  "monthSalesAmount":42310.00, "monthGrossProfit":14200.00,
  "pendingPurchaseCount":2, "unhandledAlertCount":4, "totalStockValue":12345.67,
  "salesTrend": [ { "date":"2026-09-01", "amount":1500.00, "quantity":420 } ],
  "categoryStock": [ { "categoryName":"饮料酒水", "skuCount":5, "quantity":2600, "costValue":3200.00 } ] }
```

**purchase 返回**：
```json
{ "totalAmount":12800.00, "totalQuantity":3200, "orderCount":12,
  "trend":[ { "date":"2026-09-01","amount":800.00,"quantity":200,"orderCount":1 } ],
  "bySupplier":[ { "supplierId":1,"supplierName":"...","amount":5000.00,"quantity":1200 } ] }
```

**sales 返回**：
```json
{ "totalAmount":42310.00,"totalQuantity":12000,"orderCount":90,
  "trend":[ { "date":"2026-09-01","amount":1500.00,"quantity":420,"orderCount":1 } ],
  "byCategory":[ { "categoryName":"饮料酒水","amount":18000.00,"quantity":6000 } ],
  "topProducts":[ { "productId":12,"productName":"农夫山泉 550ml","quantity":4000,"amount":8000.00 } ] }
```

**stock 返回**：
```json
{ "skuCount":30,"totalQuantity":8421,"totalCostValue":12345.67,
  "byCategory":[ { "categoryName":"文具用品","skuCount":6,"quantity":1800,"costValue":2100.00 } ],
  "lowStockList":[ { "productId":3,"productName":"得力订书机 12 号",
                     "quantity":9,"stockLower":15 } ],
  "expiringList":[ { "batchId":80,"productId":25,"productName":"蒙牛益生菌酸奶 100g",
                     "batchNo":"B...","stockQuantity":12,"expireDate":"2026-10-03","daysToExpire":4 } ] }
```

**profit 返回**：
```json
{ "totalAmount":42310.00,"totalCost":28110.00,"totalProfit":14200.00,"grossMargin":33.56,
  "trend":[ { "date":"2026-09-01","amount":1500.00,"cost":1000.00,
              "profit":500.00,"margin":33.33 } ],
  "byCategory":[ { "categoryName":"饮料酒水","amount":18000.00,"cost":12000.00,
                   "profit":6000.00,"margin":33.33 } ] }
```

---

## 12. AI 智能助手 `/api/ai`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ai/replenish/suggestions` | 补货建议列表（本地算法计算，不依赖外部服务） |
| POST | `/api/ai/replenish/analyze` | 针对某个商品生成 AI 分析文字 |
| POST | `/api/ai/replenish/analyze/stream` | 同上，SSE 流式 |
| POST | `/api/ai/chat` | 库存智能问答（Function Calling），返回完整回答 |
| POST | `/api/ai/chat/stream` | 同上的 SSE 流式版本 |
| GET | `/api/ai/logs` | AI 调用记录分页 |

**GET /api/ai/replenish/suggestions?days=30**

```json
{
  "algorithm": "移动平均 + 安全库存（Z=1.65 服务水平 95%）",
  "generatedAt": "2026-09-29 21:30:00",
  "items": [
    {
      "productId": 3, "productName": "得力订书机 12 号", "unit": "个",
      "currentStock": 9, "stockLower": 15, "stockUpper": 80,
      "avgDailySales": 3.1, "avgWeeklySales": 21.7,
      "safetyStock": 8, "leadTimeDays": 3,
      "reorderPoint": 17, "suggestQuantity": 62,
      "estCost": 527.00, "stockDays": 2.9,
      "urgency": "HIGH",
      "reason": "日均销量 3.1 个，当前仅可支撑 2.9 天，已低于补货点 17 个"
    }
  ]
}
```
`urgency`：`HIGH` 紧急（可支撑天数 < 3 或已断货）/ `MEDIUM` / `LOW`

**POST /api/ai/replenish/analyze** → body `{ "productId": 3, "days": 30 }`
```json
{ "productId":3, "productName":"得力订书机 12 号", "analysis":"...", "model":"qwen-plus", "degraded":false }
```
> `degraded: true` 表示 AI 调用失败，`analysis` 是本地规则生成的兜底文案。

**POST /api/ai/chat** → body `{ "question": "哪些商品快过期了？" }`
```json
{ "answer":"...", "model":"qwen-plus", "toolsUsed":["queryExpiringBatches"],
  "costMs":2340, "degraded":false }
```

**POST /api/ai/chat/stream** → `text/event-stream`，每行 `data: {"content":"片段"}`，结束发 `data: [DONE]`

---

## 13. 健康检查

| 路径 | 说明 |
|---|---|
| `/actuator/health` | 存活 + 依赖健康（含 MySQL、Redis） |
| `/actuator/health/readiness` | 就绪探针 |
| `/actuator/health/liveness` | 存活探针 |
| `/swagger-ui.html` | 接口文档 UI |
