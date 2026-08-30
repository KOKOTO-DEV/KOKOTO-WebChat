# Operations / Security checklist

This document summarizes settings to review for public deployments or HTTPS reverse-proxy setups.

## Recommended public deployment

For public servers, keep the KOKOTO WebChat HTTP server behind Caddy/Nginx instead of exposing it directly.

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

For direct HTTP use, keep `trusted-proxies: []`. In that mode, client-supplied `X-Forwarded-For` is ignored and the direct socket IP is used.

## Checking client IP resolution logs

When `http.log-client-ip-resolution: true` is enabled, the plugin writes lines like this to the server console and Minecraft server log:

```text
[KOKOTO WebChat] Client IP resolved: socket=127.0.0.1, trustedProxy=true, xForwardedFor=203.0.113.10, result=203.0.113.10, path=/api/config
```

Where to check:

```text
Server console
logs/latest.log
```

On Linux:

```bash
grep "Client IP resolved" logs/latest.log
```

For live checking:

```bash
tail -f logs/latest.log | grep "Client IP resolved"
```

Turn `log-client-ip-resolution` back to `false` after checking. Requests such as `/stream`, `/config`, and `/history` may produce many log lines.

## Expected trusted-proxies behavior

When Caddy/Nginx runs on the same host:

```text
socket=127.0.0.1
trustedProxy=true
xForwardedFor=<real user IP>
result=<real user IP>
```

When `trusted-proxies` is empty or the proxy IP is not listed:

```text
trustedProxy=false
result=<socket IP>
```

Rate limits, login failure limits, mute/ban, and admin IP restrictions use the result value.

## Command feature

The web command feature is powerful. For public deployments, prefer:

```yaml
commands:
  enabled: true
  allow-all: false
  min-role: ADMIN
  require-confirm: true
```

`allow-all: true` is not recommended outside private networks or personal servers because a compromised web admin account may lead to console command execution.

## SSE connection limits

`/stream` is a long-lived SSE connection kept open by browsers.

```yaml
security:
  max-sse-connections-per-ip: 10
  max-sse-connections-total: 500
```

Set either value to `0` to disable that limit. Limited clients receive `too_many_stream_connections`.

## Known stability trade-offs

The following areas are intentionally left unchanged for stability:

```text
- token query/header/body transport
- request body cache/limit
- pin / unpin / delete flow
- command execution flow
```

Previous experiments in these areas caused pinned-state issues or repeated first-message behavior, so avoid mixing them with small security patches unless doing a larger refactor.
