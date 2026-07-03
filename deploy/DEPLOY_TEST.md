# zook.run 测试环境部署说明

本部署方案：

- 后端：Docker 容器运行，使用 `test` 环境配置。
- 前端：构建 `dist` 后放到服务器 Nginx 的 `html` 目录。
- 域名：`zook.run`。
- Nginx：同域代理 `/api` 和 `/ws` 到后端 `8080`。

## 1. 后端部署

进入服务器上的后端项目目录：

```bash
cd /data/hr/hr-interview-api
```

构建并启动容器：

```bash
docker compose -f docker-compose.test.yml up -d --build
```

查看容器状态：

```bash
docker ps | grep hr-interview-api
```

查看日志：

```bash
docker logs -f hr-interview-api
```

后端日志会挂载到项目目录：

```text
/data/hr/hr-interview-api/logs
```

## 2. 前端部署

本地或服务器进入前端项目目录：

```bash
cd /data/hr/hr-interview-web
```

构建前端：

```bash
npm install
npm run build
```

把 `dist` 内容放到 Nginx 的 html 目录：

```bash
rm -rf /usr/share/nginx/html/*
cp -r dist/* /usr/share/nginx/html/
```

如果你的 Nginx html 目录不是 `/usr/share/nginx/html`，替换成实际目录即可。

## 3. Nginx 配置

参考配置文件：

```text
deploy/nginx-zook.run.conf
```

把它复制到 Nginx 配置目录，例如：

```bash
cp deploy/nginx-zook.run.conf /etc/nginx/conf.d/zook.run.conf
```

确认 SSL 证书路径存在：

```text
/etc/nginx/ssl/zook.run.pem
/etc/nginx/ssl/zook.run.key
```

检查 Nginx 配置：

```bash
nginx -t
```

重载 Nginx：

```bash
nginx -s reload
```

## 4. 验证

浏览器访问：

```text
https://zook.run
```

接口文档：

```text
https://zook.run/doc.html
```

后端健康验证可以请求登录接口或当前用户接口：

```text
POST https://zook.run/api/auth/login
POST https://zook.run/api/auth/me
```

候选人面试链接：

```text
https://zook.run/interview/{token}
```

手机浏览器使用实时语音时，必须使用 HTTPS 地址，并允许麦克风权限。

## 5. 更新发布

后端更新：

```bash
cd /data/hr/hr-interview-api
docker compose -f docker-compose.test.yml up -d --build
```

前端更新：

```bash
cd /data/hr/hr-interview-web
npm run build
rm -rf /usr/share/nginx/html/*
cp -r dist/* /usr/share/nginx/html/
nginx -s reload
```
