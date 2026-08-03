# 服务器间公共聊天中继

`server-relay` 用于连接多个 BlueMapWebChat 服务器的公共聊天。游戏、已关联 Web 用户和访客消息可发送到远端服务器的 Web 聊天与 Minecraft 聊天，同时保留消息 ID、回复关系、发送者和来源服务器信息。

## 双服务器示例

服务器 1：

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  server-name: "服务器 1"
  shared-secret: "两台服务器使用同一个足够长的随机密钥"
  connect-timeout-seconds: 5
  request-timeout-seconds: 10
  max-clock-skew-seconds: 60
  dedupe-seconds: 300
  max-hops: 8
  sources:
    game: true
    web: true
    guest: true
    discord: false
    system: false
  delivery:
    web: true
    game: true
  game-format: "&8[&b{server}&8] &f{sender}&7: &f{message}"
  peers:
    - id: "server3"
      url: "https://server3.example.com/bmwc/api"
      secret: ""
      enabled: true
```

服务器 3 使用 `server-id: "server3"`，并在 `peers` 中添加 `id: "server1"` 和服务器 1 的公开 API URL。双方必须互相登记；接收方 peer ID 必须与发送方 `server-id` 完全一致，并且每台服务器的 ID 必须唯一。

## HTTPS 与反向代理

`url` 是远端可公开访问的 BMChat API base，`/relay/receive` 会自动追加。

```text
配置: https://server3.example.com/bmwc/api
请求: https://server3.example.com/bmwc/api/relay/receive
```

公开 HTTPS 路由必须把包含 `/relay/receive` POST 在内的整个 API 路径转发到内部 BMChat HTTP 监听器。已有 HTTPS 前端时无需公开 8899 端口。代理必须保留 `X-BMWC-Relay-Version`, `X-BMWC-Relay-From`, `X-BMWC-Relay-Timestamp`, `X-BMWC-Relay-Signature`。自签名证书需要加入 Java trust store，否则会在 TLS 验证阶段失败。

## 密钥

- `shared-secret` 是所有 peer 的默认密钥。
- `peers[].secret` 是单独连接的覆盖密钥。
- 两台服务器可使用相同的长随机 `shared-secret`，并保持 peer `secret: ""`。
- peer 密钥和公共密钥都为空时，该 peer 会从活动列表排除。

## 多服务器拓扑

全网状拓扑让每台服务器登记所有其他服务器；Hub 拓扑让 leaf 只连接 hub，hub 登记所有 leaf。relay ID 去重、来源抑制、上一跳排除和 `max-hops` 防止循环。没有持久离线队列；peer 离线期间的消息不会稍后补发。

## reload 与诊断

`/bmchat reload` 会关闭旧 relay，并使用当前配置重新创建。中继是每条消息一次 HTTP(S) 请求，不是永久连接，因此没有单独的“重新连接”操作。

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

如果 `activePeers` 少于配置数量，警告会指出重复 ID、自身 ID、空/非法 URL、不支持的 scheme 或缺少密钥等原因。

## HTTP 错误

- `403 unknown_peer`: 接收服务器的活动 peer 中没有发送方的准确 `server-id`。
- `401 bad_signature`: 实际密钥不同，或代理修改了正文/头。
- `401 expired_request`: 两台服务器时钟差超过 `max-clock-skew-seconds`。
- `404 relay_disabled`: 接收端未启用，或代理转发到了错误实例/路径。
- `426 unsupported_protocol`: 两端中继协议版本不兼容。

修改接收端 peer 或密钥后，也必须在接收端执行 `/bmchat reload`。

## 显示区分

Web 聊天省略当前服务器自身的徽章，只为其他服务器的消息显示基于 `originServerId` 的固定颜色徽章。游戏输出也省略当前服务器名；仅远端消息的旧格式缺少 `{server}` / `{server_id}` 时才自动添加 `[server-name]`。Discord 是共享外部频道，因此继续保留服务器标识。多个服务器共享同一 Discord 频道时，只有实际检测到本地游戏聊天的来源服务器才会编辑 DiscordSRV 消息，其他 peer 不会再添加自己的服务器名或重复表情链接。接收 peer 不会把中继消息重新发送到 Discord，因此不存在经由服务器代发。为避免 DiscordSRV 循环和系统事件重复，`sources.discord` 与 `sources.system` 默认关闭。
