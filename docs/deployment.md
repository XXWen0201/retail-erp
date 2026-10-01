# 部署说明

> 面向单机部署（一台服务器跑完整套）。门店规模不大，不需要上容器编排。

---

## 一、环境准备

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | 打包时用 |
| MySQL | 8.0+ | 字符集 `utf8mb4` |
| Redis | 6+ | 用于令牌、发号器、库存缓存 |
| Nginx | 1.20+ | 托管前端静态文件 + 反向代理 API |

---

## 二、数据库初始化

```bash
mysql -uroot -p < backend/src/main/resources/db/schema.sql
mysql -uroot -p retail_erp < backend/src/main/resources/db/data.sql   # 可选：演示数据
```

生产环境**不要**导入 `data.sql`，它是给演示和答辩用的。但必须保证
`sys_user` 里有一条管理员账号（可以自己 `INSERT`，密码用 BCrypt 哈希）。

---

## 三、后端

### 打包

```bash
cd backend
mvn clean package -DskipTests
# 产物：target/retail-erp-1.0.0.jar（约 58 MB，内嵌 Tomcat）
```

### 环境变量（生产必须覆盖）

| 变量 | 说明 | 默认值 |
|---|---|---|
| `APP_JWT_SECRET` | JWT 签名密钥，**长度必须 ≥ 32 字节**，否则启动直接失败 | 开发用默认值，生产必须改 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 数据库账号 | `root` / `123456` |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址 | `localhost` / `6379` |
| `DASHSCOPE_API_KEY` | 阿里云百炼 API Key；**不配则 AI 功能自动降级**，其他功能正常 | 无 |
| `AI_MODEL` | 使用的模型 | `qwen-plus` |
| `AI_BASE_URL` | OpenAI 兼容端点 | `https://dashscope.aliyuncs.com/compatible-mode` |

> 密钥只通过环境变量注入，**不要写进代码库或配置文件**。

### 启动

```bash
export APP_JWT_SECRET="生产环境的随机长密钥-至少32字节-请勿使用示例值"
export DASHSCOPE_API_KEY="sk-xxxxxxxx"
export MYSQL_PASSWORD="你的数据库密码"

nohup java -jar retail-erp-1.0.0.jar \
  --spring.profiles.active=prod \
  --server.port=8080 \
  > app.log 2>&1 &
```

Windows 上可用 `winsw` 或「任务计划程序」注册为服务。

### 健康检查

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP","groups":["liveness","readiness"]}
```

`/actuator/health/readiness` 与 `/actuator/health/liveness` 可用于负载均衡探针。
应用配置了**优雅停机**（`server.shutdown=graceful`，超时 20s），
停止时先拒绝新请求、等在途请求处理完再退出。

---

## 四、前端

```bash
cd frontend
npm install
npm run build          # 产物在 dist/
```

把 `dist/` 下的文件拷到 Nginx 的站点目录即可。

---

## 五、Nginx 配置

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    root /var/www/retail-erp;
    index index.html;

    # SPA 路由回退：刷新 /dashboard 这类路径要能落到 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 静态资源长缓存（Vite 产物带内容哈希，可以放心长缓存）
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # SSE 流式问答必须关掉缓冲，否则前端会一直等不到数据
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }

    # 健康检查不对外暴露细节
    location /actuator/ {
        allow 127.0.0.1;
        deny all;
        proxy_pass http://127.0.0.1:8080;
    }
}
```

> **`proxy_buffering off;` 是必须的**：AI 流式问答走 SSE，
> 开着缓冲的话 Nginx 会攒够一批才转发，前端看起来就像卡死了。

---

## 六、上线前检查清单

- [ ] `APP_JWT_SECRET` 已换成随机长密钥（≥ 32 字节），不是示例值
- [ ] `DASHSCOPE_API_KEY` 用环境变量注入，没有写在 `application-dev.yml` 里
- [ ] `application-prod.yml` 里关掉 SQL 日志（`mybatis-plus.configuration.log-impl` 不要用 `StdOutImpl`）
- [ ] `spring.profiles.active=prod`
- [ ] `app.cors.allowed-origins` 只保留真实域名，**不要用通配符**（带 Cookie 的跨域请求浏览器不接受 `*`）
- [ ] `app.jwt.cookie-secure=true`（走 HTTPS 时）
- [ ] 全站 HTTPS，Cookie 的 `SameSite=Lax` + `Secure`
- [ ] MySQL 已开定时备份
- [ ] 管理员默认密码已修改
- [ ] `/actuator/`、`/swagger-ui.html` 不对外网暴露（或加访问控制）

---

## 七、备份与恢复

```bash
# 备份（每天凌晨执行）
mysqldump -uroot -p --single-transaction --routines --triggers retail_erp \
  | gzip > /backup/retail_erp_$(date +%F).sql.gz

# 恢复
gunzip < /backup/retail_erp_2026-09-30.sql.gz | mysql -uroot -p retail_erp
```

`--single-transaction` 保证备份过程中不锁表，门店白天也能跑。

Redis 里只有令牌、发号器计数和库存缓存，**丢了大不了重新登录、缓存重建**，
重建后第一次读取会从数据库回填，不影响数据正确性，所以不需要持久化备份。

---

## 八、常见问题

| 现象 | 原因与处理 |
|---|---|
| 启动报 `JWT 密钥长度不足` | `APP_JWT_SECRET` 少于 32 字节，换一个更长的 |
| 前端刷新页面 404 | Nginx 少了 `try_files $uri $uri/ /index.html` |
| 登录成功但下一步请求 401 | Cookie 没带上：检查 Nginx 有没有透传 Cookie，跨域时检查 `withCredentials` 与 `allowed-origins` |
| AI 一直转圈不出字 | Nginx 没关 `proxy_buffering`；或模型响应确实慢（默认超时 180s） |
| AI 返回 `degraded: true` | 未配置 `DASHSCOPE_API_KEY`，或额度用尽/网络不通。这是设计好的降级，不影响其他功能 |
| 压测接口返回 403 | 该接口限 `MANAGER` 及以上角色，用 `admin` 或 `manager` 登录 |
| `mvn` 报找不到主类 `Launcher` | Windows + Git Bash 的 MSYS 路径转义问题，用 `bash tools/mvn.sh backend <参数>` |
