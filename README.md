# Logistics Back

面向百川物流冷链运营前端的 Spring Boot API 骨架。当前版本先落地账号登录、会话认证、
个人资料和密码修改，使前端可以从本地模拟数据平滑切换到真实接口。

## 本地运行

要求 Java 17 和 Maven 3.9+。

    mvn spring-boot:run

服务默认监听 `http://localhost:8020`，健康检查为 `GET /api/health`。初始演示账号：
`admin / 123456`。

接口文档位于 `http://localhost:8020/swagger-ui.html`。

## 接口

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| POST | `/api/user/login` | 登录并返回 token 与用户资料 | 否 |
| GET | `/api/user/info` | 当前用户资料 | 是 |
| PUT | `/api/user/profile` | 修改自己的账号和资料 | 是 |
| PUT | `/api/user/password` | 验证当前密码并更新密码 | 是 |
| POST | `/api/user/logout` | 注销当前会话 | 是 |

认证请求支持标准 `Authorization: Bearer <token>`，也兼容前端现有的 `token` 请求头。
响应统一为 `{ code, data, message }`，与前端请求拦截器一致。

## 结构

- `common`: 统一响应、业务异常、全局异常处理、健康检查
- `config`: CORS 与 MVC 拦截器配置
- `auth`: BCrypt 密码服务和有过期时间的会话管理
- `user`: controller、service、JDBC repository、领域实体和 DTO

`UserRepository` 是持久化边界。默认 profile 使用文件型 H2 数据库；启用 `mysql` 或 `postgres`
profile 后通过 JDBC 直连对应数据库，表结构由 Flyway 管理。上层 controller 和 service 契约不需要
随数据库切换改写。当前登录会话保存在进程内，单实例部署可直接使用；多实例部署应迁移到
Redis，或改为带轮换机制的短期访问令牌。

## MySQL

本机已有 MySQL 时，先创建数据库和账号，再启动后端：

    mysql -uroot -p -e "create database logistics character set utf8mb4 collate utf8mb4_unicode_ci;"
    mysql -uroot -p -e "create user 'logistics'@'%' identified by '你的数据库密码'; grant all privileges on logistics.* to 'logistics'@'%'; flush privileges;"
    export DB_URL='jdbc:mysql://localhost:3306/logistics?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
    export DB_USERNAME='logistics'
    export DB_PASSWORD='你的数据库密码'
    export SPRING_PROFILES_ACTIVE='mysql'
    mvn spring-boot:run

没有本机 MySQL 时，可以使用项目内的 Compose 配置：

    export MYSQL_PASSWORD='设置一个本地密码'
    export MYSQL_ROOT_PASSWORD='设置一个 root 密码'
    docker compose -f docker-compose.mysql.yml up -d
    export DB_URL='jdbc:mysql://localhost:3306/logistics?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
    export DB_USERNAME='logistics'
    export DB_PASSWORD="$MYSQL_PASSWORD"
    export SPRING_PROFILES_ACTIVE='mysql'
    mvn spring-boot:run

启动时 Flyway 会自动创建 `users` 和迁移历史表，默认管理员为 `admin / 123456`。

## PostgreSQL

本机已有 PostgreSQL 时，先创建数据库和账号，再启动后端：

    createdb logistics
    export DB_URL='jdbc:postgresql://localhost:5432/logistics'
    export DB_USERNAME='logistics'
    export DB_PASSWORD='你的数据库密码'
    export SPRING_PROFILES_ACTIVE='postgres'
    mvn spring-boot:run

启动时 Flyway 会自动创建 `users` 和迁移历史表，默认管理员为 `admin / 123456`。

没有本机 PostgreSQL 时，可以使用项目内的 Compose 配置：

    export POSTGRES_PASSWORD='设置一个本地密码'
    docker compose -f docker-compose.postgres.yml up -d
    export DB_URL='jdbc:postgresql://localhost:5432/logistics'
    export DB_USERNAME='logistics'
    export DB_PASSWORD="$POSTGRES_PASSWORD"
    export SPRING_PROFILES_ACTIVE='postgres'
    mvn spring-boot:run

## 配置

- `CORS_ALLOWED_ORIGINS`: 允许的前端来源，逗号分隔
- `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`: 数据库连接（启用 `mysql` 或 `postgres` profile 时使用）
- `DB_POOL_MIN_IDLE`、`DB_POOL_SIZE`: 数据库连接池大小
- `SPRING_PROFILES_ACTIVE=prod`: 启用生产配置；生产环境必须显式提供数据库连接参数
- `app.auth.session-ttl`: 会话有效期，默认 `PT8H`
- `server.port`: 默认 `8020`

## 验证

    mvn test
