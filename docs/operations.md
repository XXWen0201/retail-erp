# 日常运维与答辩前准备操作手册

> 本手册记录**需要手动执行的操作**，按「什么时候做」组织。
> 所有命令都在项目根目录 `retail-erp/` 下执行（除非特别说明）。

---

## 一、答辩前一天必做：刷新演示数据

### 为什么要做这件事

种子数据是按**生成当天**往前推 90 天生成的（`gen_seed_data.py` 里用的是 `date.today()`）。
所以只要过了几天，工作台上的「今日销售额」「本月销售额」就会变成 **0.00**，
近 30 天趋势曲线也会断在半路 —— 看起来就像系统坏了。

**建议在答辩前一天晚上执行一次，第二天早上再确认一遍。**

### 操作步骤

#### 第 1 步：备份当前数据库（强烈建议）

```powershell
cd D:\微信\毕设\retail-erp
$env:MYSQL_PWD='123456'

cmd /c "`"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe`" -uroot --default-character-set=utf8mb4 --skip-dump-date retail_erp > `"backend\src\main\resources\db\_backup.sql`""
```

> ⚠️ **必须用 `cmd /c` 包一层，不能用 PowerShell 的 `>` 重定向。**
> PowerShell 的 `>` 默认写 **UTF-16**，备份文件开头会变成 `255,254`（BOM）而不是 `45,45`（`--`），
> 恢复时会报 `ERROR: ASCII '\0' appeared in the statement`，**备份等于废的**。
> 这是实测踩出来的坑。

**验证备份文件是否有效**（前两个字节应该是 `45,45`）：

```powershell
$b = [System.IO.File]::ReadAllBytes('backend\src\main\resources\db\_backup.sql')
"前4字节: $($b[0..3] -join ',')   # 45,45 = 正确；255,254 = 编码错了"
```

**恢复备份**（万一改坏了）：

```powershell
cmd /c "`"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`" -uroot --default-character-set=utf8mb4 retail_erp < `"backend\src\main\resources\db\_backup.sql`""
```

> 📌 备份只在**出问题前**做才有意义。确认新数据没问题再删备份。

#### 第 2 步：重新生成种子数据

⚠️ **这一步有个坑**：`gen_seed_data.py` 在 Windows 下输出的是 **GBK 编码**。
如果直接用 `python gen_seed_data.py > data.sql`，PowerShell 会把它写成
**UTF-16 + BOM**，导入后中文全部变成乱码。

必须用下面这段（强制 UTF-8 无 BOM 写盘）：

```powershell
cd D:\微信\毕设\retail-erp\tools
$env:PYTHONIOENCODING='utf-8'

python -c "import subprocess,sys,io,os; env=dict(os.environ); env['PYTHONIOENCODING']='utf-8'; r=subprocess.run([sys.executable,'gen_seed_data.py'],capture_output=True,env=env); assert r.returncode==0, r.stderr.decode('utf-8','replace'); d=r.stdout.decode('utf-8').lstrip('\ufeff'); io.open('../backend/src/main/resources/db/data.sql','w',encoding='utf-8',newline='\n').write(d); print('OK', len(d.encode('utf-8')), 'bytes')"
```

执行后会打印 `OK 674404 bytes` 之类的信息。然后确认文件头：

```powershell
# 注意：.NET 的 ReadAllText 不解析相对路径，必须用绝对路径
[System.IO.File]::ReadAllText(
  (Resolve-Path '..\backend\src\main\resources\db\data.sql'),
  [System.Text.Encoding]::UTF8
) -split "`n" | Select-Object -First 5
```

应该看到类似：

```
--  生成日期: 2026-10-25   数据窗口: 2026-07-28 ~ 2026-10-25（90 天）
--  数据量: 商品 30 / 供应商 6 / 采购单 54 / 销售单 90 / 批次 149 / 库存流水 2902
```

**重点核对**：`数据窗口` 的结束日期必须是你要演示的那天。

#### 第 3 步：重建数据库

⚠️ **必须先建 schema，再导数据。顺序不能反。**

原因：`data.sql` 里**没有** `DELETE` / `TRUNCATE` 语句，直接重复导入会报
`ERROR 1062 Duplicate entry '1' for key 'sys_user.PRIMARY'`。
而 `schema.sql` 开头有 `DROP DATABASE IF EXISTS retail_erp`，会整库重建。

```powershell
cd D:\微信\毕设\retail-erp
$env:MYSQL_PWD='123456'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'

# 1) 重建库结构（会删除整个 retail_erp 库再重建）
cmd /c "`"$mysql`" -uroot --default-character-set=utf8mb4 < `"backend\src\main\resources\db\schema.sql`""

# 2) 导入种子数据
cmd /c "`"$mysql`" -uroot --default-character-set=utf8mb4 retail_erp < `"backend\src\main\resources\db\data.sql`""
```

> 为什么用 `cmd /c` 加 `<` 重定向：PowerShell 不支持 `<` 输入重定向，会报
> `RedirectionNotSupported`。

#### 第 4 步：验证数据正确

```powershell
& $mysql -uroot --default-character-set=utf8mb4 -D retail_erp -e "
SELECT CONCAT('今天: ', CURDATE()) AS x;
SELECT '销售单' AS t, COUNT(*) AS n, MIN(order_date) AS 起, MAX(order_date) AS 止 FROM sale_order
UNION ALL SELECT '采购单', COUNT(*), MIN(order_date), MAX(order_date) FROM purchase_order
UNION ALL SELECT '库存流水', COUNT(*), DATE(MIN(create_time)), DATE(MAX(create_time)) FROM stock_record;
SELECT COUNT(*) AS 今日单数, IFNULL(SUM(pay_amount),0) AS 今日金额
FROM sale_order WHERE order_date=CURDATE() AND status='FINISHED';
SELECT '--- 中文是否正常（应显示中文商品名）---' AS y;
SELECT id, name FROM product LIMIT 3;"
```

**验收标准**：

- `销售单` 的「止」= 今天
- `今日单数` ≥ 1，`今日金额` > 0
- 商品名是正常中文，不是 `??????` 或乱码

#### 第 5 步：重启后端

数据换了，但后端连的是同一个库，**理论上不用重启**。
不过如果之前跑过压测或测试，缓存里可能有旧库存，稳妥起见重启一次：

```powershell
# 停掉旧进程
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force

# 重新启动（在 backend 目录下）
cd D:\微信\毕设\retail-erp\backend
mvn spring-boot:run
```

#### 第 6 步：刷新预警（重要）

新建的库里**没有任何预警记录**，工作台会显示「未处理预警 0 条」，
但演示需要看到预警。手动触发一次扫描：

```powershell
# 登录拿令牌
$body = '{"username":"admin","password":"123456"}'
$tok = (Invoke-RestMethod 'http://localhost:8080/api/auth/login' -Method Post `
  -Body $body -ContentType 'application/json').data.accessToken

# 触发扫描
Invoke-RestMethod 'http://localhost:8080/api/alerts/scan' -Method Post `
  -Headers @{ Authorization = "Bearer $tok" }
```

预期返回类似：`新增 5 条 / 断货 1 / 低库存 2 / 临期 1 / 过期 1`。

> 也可以打开前端，在「库存预警」页面点「手动扫描」按钮，效果一样。

#### 第 7 步：清理备份文件

确认一切正常后再删，别急着删：

```powershell
Remove-Item backend\src\main\resources\db\_backup.sql
```

---

## 二、启动与停止服务

### 启动后端

```powershell
cd D:\微信\毕设\retail-erp\backend
mvn spring-boot:run
```

- 接口地址：<http://localhost:8080>
- 接口文档：<http://localhost:8080/swagger-ui.html>
- 健康检查：<http://localhost:8080/actuator/health>（返回 `{"status":"UP"}` 即正常）

> **Git Bash 环境下**如果报
> `找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher`，
> 这是 MSYS 路径转义问题，改用：`bash tools/mvn.sh backend spring-boot:run`

### 启动前端

```powershell
cd D:\微信\毕设\retail-erp\frontend
npm run dev
```

访问 <http://localhost:5173>。

### 停止服务

```powershell
# 停后端
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force

# 停前端（node 进程）
Get-NetTCPConnection -State Listen -LocalPort 5173 -ErrorAction SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

### 检查服务是否在跑

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8080,5173 -ErrorAction SilentlyContinue |
  Select-Object LocalAddress, LocalPort
```

两个端口都有输出 = 都起来了。

---

## 三、答辩前的自检清单

按顺序跑一遍，全绿再上场。

### 1. 单元测试（不需要启动服务）

```powershell
# 后端 26 个用例
cd D:\微信\毕设\retail-erp\backend
mvn test

# 移动端 18 个用例
cd D:\微信\毕设\retail-erp\miniprogram
npm test
```

预期：`Tests run: 26, Failures: 0, Errors: 0` 和 `pass 18 / fail 0`。

### 2. 端到端冒烟测试（需要后端已启动）

```powershell
cd D:\微信\毕设\retail-erp
python tools/smoke_test.py
```

预期：`通过 42 项，失败 0 项`。

### 3. 移动端认证测试（需要后端已启动）

```powershell
python tools/smoke_mini.py
```

预期：`通过 23 项，失败 0 项`。

### 4. 前端能否正常构建

```powershell
cd D:\微信\毕设\retail-erp\frontend
npm run build
```

预期：`✓ built in ...`，无红色报错。

### 5. 数据是否新鲜

```powershell
cd D:\微信\毕设\retail-erp
$env:MYSQL_PWD='123456'
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -D retail_erp -e "SELECT MAX(order_date) AS 最新销售日期, CURDATE() AS 今天 FROM sale_order;"
```

**最新销售日期必须等于今天**，否则回去做第一节。

---

## 四、答辩现场注意事项

### ⚠️ 不要用 admin 演示「登录失败锁定」

连续输错 5 次会把 admin **锁 10 分钟**，后面所有演示都做不了。

- 要演示就用 `staff`
- 或者只口头讲、不实际操作

**万一锁了，立刻解锁**：

```powershell
redis-cli del erp:login:fail:admin
```

> 也可以用 `redis-cli --scan --pattern 'erp:login:fail:*'` 查看当前有哪些账号被计数。

### 压测会真实扣库存

「并发扣减压测」页面跑完会真的扣掉库存、写入流水（`bizNo` 为 `BENCHMARK`）。

- 建议**最后演示**这一项
- 或者演示完用盘点把库存调回来
- 事后想清理压测流水：`DELETE FROM stock_record WHERE biz_no='BENCHMARK';`
  但**库存数量不会自动恢复**，需要重新导数据

### 演示完不要跑单元测试

`StockConcurrencyTest` 会临时改动商品 id=12 的库存（结束自动恢复），
但如果测试中途被打断，库存可能停在中间状态。

**建议**：答辩前跑完所有测试，答辩时只做界面演示。

---

## 五、常见问题排查

### 后端启动失败

| 现象 | 原因 | 解决 |
|---|---|---|
| `Communications link failure` | MySQL 没启动 | `Get-Service MySQL80`，状态应为 `Running` |
| `Unable to connect to Redis` | Redis 没启动 | `Get-Service Redis`，或 `redis-cli ping` 应返回 `PONG` |
| `Access denied for user 'root'` | 密码不对 | 设 `$env:MYSQL_PASSWORD='你的密码'` 后重启 |
| `Table 'retail_erp.xxx' doesn't exist` | 没建表 | 执行第一节的第 3 步 |
| 端口 8080 被占用 | 有旧进程 | `Get-Process java \| Stop-Process -Force` |

### 页面数据不对

| 现象 | 原因 | 解决 |
|---|---|---|
| 今日销售额 0.00 | 数据过期 | 执行第一节 |
| 中文显示成乱码 | 导入时编码错 | 重新按第一节第 2、3 步操作 |
| 未处理预警 0 条 | 新库没有预警记录 | 执行第一节第 6 步触发扫描 |
| 前端白屏 / 请求全 404 | 后端没起 | 检查 8080 端口 |
| 登录后立刻跳回登录页 | 令牌失效 | 清浏览器缓存，或确认 Redis 正常 |
| 商品名全是 `??????` | `data.sql` 被写成了 UTF-16 | 按第一节第 2 步重新生成，确认文件前 3 字节是 `45,45,32` |

### 数据库操作报错

| 报错 | 原因 | 解决 |
|---|---|---|
| `Duplicate entry '1' for key 'sys_user.PRIMARY'` | 只跑了 `data.sql` 没跑 `schema.sql` | 两条都要跑，顺序不能反（第一节第 3 步） |
| `ERROR: ASCII '\0' appeared in the statement` | 备份/导入时被 PowerShell 写成 UTF-16 | 用 `cmd /c "..."` 包一层，别用 PowerShell 的 `>` |
| `RedirectionNotSupported` | PowerShell 不支持 `<` 输入重定向 | 同上，用 `cmd /c` |
| `Table 'retail_erp.xxx' doesn't exist` | 没建表或建表失败 | 重新执行 `schema.sql` |

> 💡 **一句话记住**：只要涉及 `>` 或 `<` 重定向，一律用 `cmd /c "..."` 包起来。
> PowerShell 的 `>` 默认写 UTF-16，中文和 SQL 都会坏掉。

### AI 助手不可用

这是**预期行为**，不影响其他功能。

- 没配密钥时，补货建议仍然能算（本地算法），只是 AI 分析文字降级成本地文案
- 真实密钥在 `backend/config/application-dev.yml`（已被 `.gitignore` 排除，不会进仓库）
- 想确认密钥是否生效：看该文件里 `api-key` 是否还是 `sk-REPLACE_WITH_YOUR_OWN_DASHSCOPE_KEY`

---

## 六、演示账号

| 账号 | 密码 | 角色 | 权限 |
|---|---|---|---|
| `admin` | `123456` | ADMIN | 全部权限 |
| `manager` | `123456` | MANAGER | 可建单、入库、盘点、压测 |
| `staff` | `123456` | STAFF | 只读查询 |

---

## 七、代码改动后的提交习惯

改代码前先存一个还原点，出问题随时能退：

```powershell
cd D:\微信\毕设\retail-erp
git status                                    # 先看改了哪些文件
git add -A
git commit -m "这次改了什么"
git push
```

### 想退回去

```powershell
git log --oneline                     # 找到目标提交号
git diff v1.0                         # 看当前和基线差在哪
git checkout v1.0 -- .                # 只把文件内容退回基线，提交历史保留
git commit -m "回滚到 v1.0"
```

彻底丢弃当前改动（⚠️ 未提交的内容会一起丢）：

```powershell
git reset --hard <提交号>
```

### 基线标签

`v1.0` 打在「功能完整、界面未经改版」这个状态上：

```powershell
git checkout -b restore-v1.0 v1.0     # 从基线拉个分支对照着看
git checkout main                      # 看完切回来
```

---

## 八、不会进仓库的东西

以下路径都在 `.gitignore` 里，**不会上传到 GitHub**：

- `target/`、`node_modules/`、`frontend/dist/`、`miniprogram/dist/`
- 日志文件 `*.log`
- **真实密钥** `backend/config/application-dev.yml`
- `miniprogram/project.private.config.json`

> 仓库里的 `application-dev.yml` 只有占位符，真实密钥放在
> `backend/config/application-dev.yml`（同目录的 `.example` 是模板）。
