# 運用 / セキュリティチェックリスト

この文書は、公開運用または HTTPS リバースプロキシ構成で確認する設定をまとめたものです。

## 公開運用の推奨構成

公開サーバーでは、KOKOTO WebChat の HTTP サーバーを直接外部公開せず、Caddy/Nginx の背後に置く構成を推奨します。

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

直接 HTTP で使う場合は `trusted-proxies: []` を維持してください。この場合、クライアントが送った `X-Forwarded-For` は無視され、実際のソケット IP が使われます。

## クライアント IP ログの確認

`http.log-client-ip-resolution: true` を有効にすると、サーバーコンソールと Minecraft サーバーログに次のような行が出力されます。

```text
[KOKOTO WebChat] Client IP resolved: socket=127.0.0.1, trustedProxy=true, xForwardedFor=203.0.113.10, result=203.0.113.10, path=/api/config
```

確認場所:

```text
サーバーコンソール
logs/latest.log
```

Linux では次のように確認できます。

```bash
grep "Client IP resolved" logs/latest.log
```

リアルタイム確認:

```bash
tail -f logs/latest.log | grep "Client IP resolved"
```

確認後は `log-client-ip-resolution` を `false` に戻すことを推奨します。`/stream`、`/config`、`/history` などでもログが出るため、ログ量が増えることがあります。

## trusted-proxies の期待動作

Caddy/Nginx が同じホストで動作している場合:

```text
socket=127.0.0.1
trustedProxy=true
xForwardedFor=<実際のユーザー IP>
result=<実際のユーザー IP>
```

`trusted-proxies` が空、またはプロキシ IP が一覧にない場合:

```text
trustedProxy=false
result=<socket IP>
```

rate limit、ログイン失敗制限、mute/ban、admin IP 制限は result の値を基準に動作します。

## コマンド機能

Web コマンド機能は強力です。公開運用では次のように制限することを推奨します。

```yaml
commands:
  enabled: true
  allow-all: false
  min-role: ADMIN
  require-confirm: true
```

`allow-all: true` は、Web 管理者アカウントが奪われた場合にコンソールコマンド実行へつながるため、閉域網や個人用以外では推奨しません。

## SSE 接続数制限

`/stream` はブラウザーが開き続ける SSE 接続です。

```yaml
security:
  max-sse-connections-per-ip: 5
  max-sse-connections-total: 200
```

各値は `0` で無効化できます。制限されたクライアントには `too_many_stream_connections` が返ります。

## 既知の安定性上の trade-off

安定性のため、現在は次の領域を意図的に変更していません。

```text
- token query/header/body の伝達方式
- request body cache/limit
- pin / unpin / delete 処理フロー
- command 実行フロー
```

これらの領域は過去の検証で pinned 状態の問題や最初のメッセージが繰り返される挙動を引き起こしたため、大きなリファクタリングなしに小さなセキュリティパッチへ混ぜることは推奨しません。
