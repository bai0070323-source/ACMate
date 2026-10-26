# 邮箱验证码与 GitHub 登录

原有账号密码登录继续可用。两种新方式默认关闭，登录页读取 `GET /api/auth/methods` 显示实际可用状态。配置通过后端环境变量注入，重启后生效，密钥不能使用 `VITE_` 前缀或写入 Git。

## 邮箱验证码

配置以下环境变量（或在已忽略的 application-local.yml 内配置对应属性）：

```powershell
$env:LOGIN_EMAIL_ENABLED = 'true'
$env:MAIL_HOST = 'smtp.example.com'
$env:MAIL_PORT = '587'
$env:MAIL_USERNAME = 'your-account@example.com'
$env:MAIL_PASSWORD = '<SMTP 授权码>'
$env:MAIL_FROM = 'your-account@example.com'
$env:MAIL_STARTTLS = 'true'
$env:MAIL_SSL = 'false'
.\start-backend.ps1
```

若供应商使用 465 端口的隐式 TLS，设置 MAIL_PORT=465、MAIL_SSL=true、MAIL_STARTTLS=false。

仅支持使用已有账号的注册邮箱登录，不自动注册。输入六位验证码，无需密码。验证码有效期 5 分钟，最多尝试 5 次，成功后立即删除；重新获取使旧码失效。验证码使用 BCrypt 哈希存储，不返回前端或输出日志。每邮箱 60 秒最多发送一次，每来源 IP 每小时最多请求 10 次（包含未注册邮箱）。不信任客户端的 X-Forwarded-For，反向代理部署需按实际可信代理配置真实 IP，否则所有用户共享代理 IP 的限额。

未注册或已禁用邮箱也返回统一提示，避免查询邮箱注册情况。邮件发送失败返回服务不可用，发送限额仍生效。当前项目没有邮箱绑定/更换流程，已有用户需要在注册时填写邮箱。

## GitHub 登录

微信入口与后端授权接口已移除，改用 GitHub OAuth App。GitHub 登录默认关闭，配置完成并重启后启用。

1. 在 GitHub Settings → Developer settings → OAuth Apps 中新建 OAuth App。
2. 本地 Homepage URL 填写 `http://localhost:5173`，Authorization callback URL 填写 `http://localhost:5173/api/auth/github/callback`。
3. 配置以下后端环境变量，Client Secret 仅放在后端环境或已忽略的 application-local.yml，不可发送到聊天或提交到 Git。

```powershell
$env:LOGIN_GITHUB_ENABLED = 'true'
$env:GITHUB_CLIENT_ID = '<OAuth App Client ID>'
$env:GITHUB_CLIENT_SECRET = '<OAuth App Client Secret>'
$env:GITHUB_CALLBACK_URL = 'http://localhost:5173/api/auth/github/callback'
.\start-backend.ps1
```

本地回调必须经过前端 5173 的 `/api` 代理，回调后可直接进入前端首页。生产环境使用同站 HTTPS 域名代理前端和 `/api`，并将 GitHub OAuth App 中的回调地址同步修改为 `https://your-domain.example/api/auth/github/callback`。本地仅允许 localhost 或 127.0.0.1 使用 HTTP；正式环境使用安全 Cookie（server.servlet.session.cookie.secure=true）。

授权使用 5 分钟有效、单次消费且绑定 Session 的 state，以及 PKCE S256。后端通过 POST 换取令牌，再向 GitHub `/user` 获取可信身份；Client Secret 和 access_token 不返回浏览器、不写入应用日志或数据库。不申请仓库、组织或邮箱权限，使用公开身份信息。

首次登录自动创建普通用户，昵称优先使用 GitHub 的 name，未设置时使用 login；过长昵称截取至本站允许长度，重名时添加区分后缀。本站用户名和不可知密码仍随机生成。以 GitHub 永久数字用户 ID 查询已有绑定：存在绑定就登录绑定的本站账号，保留账号资料、权限和训练记录，不创建重复用户。之前自动生成的 GitHub_随机串占位昵称会在下次 GitHub 登录时替换为 GitHub 昵称；已经由用户自定义的昵称保留。不会根据相同昵称、邮箱或 GitHub 用户名自动绑定其他账号；当前不提供新增绑定或解绑界面。禁用账号无法登录。

## 接口与数据库

- GET /api/auth/methods：返回 email、github 是否可用，不含密钥。
- POST /api/auth/email/code：`{ "email": "..." }`。
- POST /api/auth/email/login：`{ "email": "...", "code": "123456" }`。
- POST /api/auth/github/authorize：返回 GitHub 官方授权 URL。
- GET /api/auth/github/callback：处理 code/state，成功进入首页，失败跳转 `/login?github=failed`。

POST 接口要求先调用 GET /api/auth/csrf 并传回对应请求头；前端已封装。沿用本站 Session、会话 ID 更新和管理员会话失效机制。

V17 新增 user_github 表，由 Flyway 在启动时执行。已执行的 V16 迁移和 user_wechat 历史数据保留，当前应用不再提供微信登录；不要修改旧迁移或删除用户数据。

多实例部署需会话保持或共享 Session。真实授权仍需配置运营方凭证后联调。

参考：[GitHub OAuth App 授权流程](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps)、[Spring Boot 邮件配置](https://docs.spring.io/spring-boot/reference/io/email.html)。
