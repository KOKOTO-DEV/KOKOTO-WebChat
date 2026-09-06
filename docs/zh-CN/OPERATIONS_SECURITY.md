# 运维 / 安全检查清单

![管理与 HTTP 安全边界](../assets/admin-security-boundary.svg)

本文档汇总了公开部署或 HTTPS 反向代理环境中建议检查的设置。

## 推荐的公开部署方式

公开服务器建议不要直接暴露 KOKOTO WebChat 的 HTTP 服务，而是放在 Caddy/Nginx 后面。

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  cors-origin: "https://map.example.com"
  trusted-proxies:
    - "127.0.0.1"
    - "::1"
  log-client-ip-resolution: false
```

如果直接使用 HTTP，请保持 `trusted-proxies: []`。此时客户端提供的 `X-Forwarded-For` 会被忽略，只使用直接 socket IP。

KWC 会沿已配置的可信代理链从 **右向左** 解析转发的客户端地址，并在遇到第一个不可信跳点时停止。这样客户端自行注入的最左侧 `X-Forwarded-For` 值就不能覆盖真实客户端地址。随附的单边缘 Nginx 示例也会用 `$remote_addr` 覆盖 `X-Forwarded-For`，而不是追加客户端传入的值。

## 查看客户端 IP 解析日志

启用 `http.log-client-ip-resolution: true` 后，插件会在服务器控制台和 Minecraft 服务器日志中输出类似内容：

```text
[KOKOTO WebChat] Client IP resolved: socket=127.0.0.1, trustedProxy=true, xForwardedFor=203.0.113.10, result=203.0.113.10, path=/api/config
```

查看位置：

```text
服务器控制台
logs/latest.log
```

Linux 下可使用：

```bash
grep "Client IP resolved" logs/latest.log
```

实时查看：

```bash
tail -f logs/latest.log | grep "Client IP resolved"
```

确认后建议将 `log-client-ip-resolution` 改回 `false`。`/stream`、`/config`、`/history` 等请求也可能产生日志，日志量会增加。

## trusted-proxies 的预期行为

Caddy/Nginx 在同一台主机上代理时：

```text
socket=127.0.0.1
trustedProxy=true
xForwardedFor=<真实用户 IP>
result=<真实用户 IP>
```

当 `trusted-proxies` 为空，或代理 IP 不在列表中时：

```text
trustedProxy=false
result=<socket IP>
```

rate limit、登录失败限制、mute/ban、admin IP 限制都会使用 result 值。

## 命令功能

Web 命令功能权限很高。公开部署建议使用：

```yaml
commands:
  enabled: true
  allow-all: false
  min-role: ADMIN
  require-confirm: true
```

`allow-all: true` 不建议在非私有网络或非个人服务器中使用，因为 Web 管理员账号被攻破后可能导致控制台命令执行。

## SSE 连接限制

`/stream` 是浏览器长期保持的 SSE 连接。

```yaml
security:
  max-sse-connections-per-ip: 10
  max-sse-connections-total: 500
```

任一值设为 `0` 即禁用对应限制。被限制的客户端会收到 `too_many_stream_connections`。

## 已知稳定性取舍

出于稳定性考虑，目前刻意不修改以下部分：

```text
- token query/header/body 传递方式
- request body cache/limit
- pin / unpin / delete 处理流程
- command 执行流程
```

这些部分容易导致 pinned 状态问题或首条消息重复问题，因此不建议在没有较大重构的情况下与小型安全补丁混合修改。
