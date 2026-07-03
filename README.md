# hr-interview-api

AI 面试初筛 MVP 后端。

## 技术栈

- Java 17
- Spring Boot 2.7
- MyBatis-Plus
- Swagger2 / Knife4j
- MySQL
- Druid 数据库连接池
- Redis

## 本项目约定

- 所有业务接口使用 `POST`
- 所有接口统一返回 `ApiResponse`
- 所有业务异常抛 `BusinessException`
- 所有异常通过 `GlobalExceptionHandler` 统一响应
- ORM 使用 MyBatis-Plus，尽可能不创建 XML mapper
- Controller、实体、请求 DTO、响应 DTO 使用 Swagger2 注解
- 所有实体/DTO 字段必须有 `@ApiModelProperty`
- 所有带 `controller` 的业务模块统一放在 `interfaces` 包下，方便一眼识别接口领域层
- Service 接口放在 `service`，实现类统一放在 `service/impl`
- Service 注入统一使用 `@Resource`
- 工具类统一放在 `utils` 包，不放在 `common` 包
- 日志统一使用 `logback-spring.xml` 标准化输出，日志字段包含时间、级别、线程、traceId、logger、消息和异常堆栈

## 环境配置

默认启用 `test` 环境：

```yaml
spring:
  profiles:
    active: test
```

配置文件：

- `application.yml`：公共配置
- `application-test.yml`：测试环境配置
- `application-prod.yml`：生产环境配置，需要发布生产时把 `application.yml` 中的 `active` 改为 `prod`

数据库、Redis、Realtime 和 JWT 参数直接在 `application-test.yml` / `application-prod.yml` 中填写。

## 目录结构

带接口入口的业务模块统一放在 `interfaces`：

```text
com.zook.hrinterview
├── interfaces
│   ├── auth
│   │   ├── controller
│   │   ├── dto
│   │   ├── entity
│   │   ├── mapper
│   │   └── service
│   │       └── impl
│   ├── candidate
│   ├── interview
│   └── job
├── common
├── config
├── utils
├── realtime
├── report
└── storage
```

测试环境默认连接：

```text
jdbc:mysql://localhost:3306/hr_interview_test
redis://localhost:6379/0
```

## 本地启动

先手动执行建表脚本：

```text
sql/init_schema.sql
```

登录账号需要手动初始化到 `hr_user` 表。密码必须使用 BCrypt 哈希，可以用下面这个类生成：

```text
com.zook.hrinterview.interfaces.auth.security.PasswordHashTool
```

登录接口：

```text
POST /api/auth/login
```

请求体：

```json
{
  "email": "admin@example.com",
  "password": "Admin@123456"
}
```

登录成功后，后续后台接口请求头带上：

```text
Authorization: Bearer <token>
```

当前用户：

```text
POST /api/auth/me
```

退出登录：

```text
POST /api/auth/logout
```

```bash
mvn spring-boot:run
```

接口文档：

```text
http://localhost:8080/doc.html
```

测试环境 Realtime 模型参数直接在 `application-test.yml` 中填写。

## 日志规范

日志配置文件：

```text
src/main/resources/logback-spring.xml
```

输出位置：

```text
logs/hr-interview-api.log
logs/hr-interview-api-error.log
```

每个请求会读取或生成 `X-Trace-Id`，接口响应头和接口响应体都会带上同一个 traceId，日志中也会输出这个 traceId。
