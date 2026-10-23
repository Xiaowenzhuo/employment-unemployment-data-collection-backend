# 就业失业数据采集系统后端

第一周后端基于 Spring Boot 4.1.2、Java 21 和 Maven。接口契约见 `docs/openapi.json`。

## 代码位置

- `common`：统一响应、请求追踪和异常处理。
- `week1/identity`：账号数据访问（DAO）、仓库、登录服务和接口。
- `week1/region`：地区数据访问、仓库、服务和接口。
- `week1/security`：BCrypt、签名令牌、角色鉴权和错误响应。
- `week1/dev`：仅在 `dev` 环境初始化匿名演示数据。
- `db/migration`：Flyway 的五张基础表和三种角色迁移。

后续每周新增功能放在对应周次的包中；跨周复用的响应和追踪能力放在 `common`。

## 本地运行

先创建空的 MySQL 数据库 `employment_demo`（`utf8mb4`），并给应用使用的数据库账号授予该库的建表、迁移和读写权限。首次启动时 Flyway 自动执行 `V1`、`V2`。不要手动建这五张表。

在 IDEA 的 `BackendApplication` 运行配置中设置环境变量：

| 变量 | 用途 |
|---|---|
| `DB_URL` | JDBC 地址；默认指向本机 `employment_demo` |
| `DB_USER`、`DB_PASSWORD` | 数据库账号与密码；默认用户名 `app_user` |
| `APP_AUTH_SECRET` | 令牌签名密钥，至少 32 个 UTF-8 字节，不提交 Git |
| `SPRING_PROFILES_ACTIVE=dev` | 仅本地演示时启用匿名数据初始化 |
| `APP_DEMO_ENTERPRISE_PASSWORD`、`APP_DEMO_CITY_PASSWORD`、`APP_DEMO_PROVINCE_PASSWORD` | 三个演示账号的口令，仅在 `dev` 模式需要 |

演示账号分别是 `demo_enterprise`、`demo_city`、`demo_province`。初始化只会创建尚不存在的账号；数据库中仅保存 BCrypt 哈希。不要把上述环境变量的实际值写入仓库或截图。

在项目根目录运行 `./mvnw.cmd test`（PowerShell 可用 `.\mvnw.cmd test`）执行迁移和 HTTP 集成测试。测试使用独立的 H2 内存库，不需要启动本机 MySQL。运行 `./mvnw.cmd spring-boot:run` 或在 IDEA 中运行 `BackendApplication` 时则连接配置的 MySQL。

## 第一周联调

演示环境已部署，实际地址和演示账号密码通过组内渠道提供，不放入公开仓库。接口契约见 `docs/openapi.json`。当前演示环境仅使用匿名数据；接入真实资料前需配置 HTTPS。

## 第一周接口

| 方法与路径 | 用途 | 权限 |
|---|---|---|
| `GET /api/v1/health` | 服务状态和构建版本 | 公开 |
| `POST /api/v1/auth/login` | 账号密码登录 | 公开 |
| `GET /api/v1/auth/me` | 当前用户、角色、组织 | Bearer token |
| `GET /api/v1/meta/regions?parentCode=...` | 直接下级地区；不传参数取顶级 | Bearer token |

统一响应字段为 `code`、`message`、`data`、`traceId`。企业、市级、省级角色代码为 `ENTERPRISE`、`CITY`、`PROVINCE`。令牌有效期为两小时；账号或所属组织停用后，已有令牌也会被拒绝。前端开发服务器 `localhost:5173` 和 `127.0.0.1:5173` 已允许跨域请求。

第一周没有公开注册、企业备案、报表或完整的用户管理接口。`/api/v1/enterprise/**`、`/api/v1/city/**`、`/api/v1/province/**` 已设置对应角色的鉴权边界，后续接口需继续检查数据所属组织范围。
