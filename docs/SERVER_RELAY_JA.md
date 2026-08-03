# サーバー間公開チャットリレー

`server-relay` は複数の BlueMapWebChat サーバーの公開チャットを接続します。ゲーム、連携済み Web ユーザー、ゲストのメッセージを相手側の Web チャットと Minecraft チャットへ送り、メッセージ ID、返信関係、送信者、発信元サーバー情報を保持します。

## 2 サーバー構成例

サーバー 1:

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  server-name: "サーバー 1"
  shared-secret: "両方のサーバーで同じ長いランダム秘密鍵"
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

サーバー 3 側では `server-id: "server3"` とし、`peers` に `id: "server1"` とサーバー 1 の公開 API URL を登録します。受信側の peer ID は送信側の `server-id` と正確に一致し、各サーバー ID は一意でなければなりません。

## HTTPS / リバースプロキシ

`url` は相手サーバーで外部から到達できる BMChat API base です。`/relay/receive` は自動追加されます。

```text
設定: https://server3.example.com/bmwc/api
要求: https://server3.example.com/bmwc/api/relay/receive
```

公開 HTTPS ルートは `/relay/receive` の POST を含む API パス全体を内部 BMChat HTTP リスナーへ転送してください。HTTPS 経由なら 8899 を外部公開する必要はありません。プロキシは `X-BMWC-Relay-Version`, `X-BMWC-Relay-From`, `X-BMWC-Relay-Timestamp`, `X-BMWC-Relay-Signature` を保持する必要があります。自己署名証明書は Java trust store へ登録しないと TLS 検証で失敗します。

## 秘密鍵

- `shared-secret` は全 peer の既定キーです。
- `peers[].secret` はその接続だけのキーで、共通キーより優先されます。
- 2 サーバーなら同じ長い `shared-secret` を両方に設定し、peer の `secret` は空にできます。
- peer キーも共通キーもない peer は無効として除外されます。

## 複数サーバーとループ防止

フルメッシュでは全サーバーが互いを登録し、ハブ構成では leaf が hub のみを登録して hub が全 leaf を登録します。relay ID の重複排除、発信元抑止、直前 peer 除外、`max-hops` により循環構成でも無限ループを防ぎます。停止中の peer へ後から再送する永続オフラインキューはありません。

## reload と診断

`/bmchat reload` は以前の relay を閉じ、現在の設定で作り直します。常時接続ではなくメッセージごとの HTTP(S) 要求なので、別の再接続操作はありません。

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

`activePeers` が設定数より少ない場合、重複 ID、自己 ID、空/不正 URL、未対応 scheme、秘密鍵不足などの理由が警告に表示されます。

## HTTP エラー

- `403 unknown_peer`: 受信側の有効 peer に送信側 `server-id` がありません。
- `401 bad_signature`: 実効秘密鍵が異なるか、プロキシが本文/ヘッダーを変更しました。
- `401 expired_request`: サーバー時刻差が `max-clock-skew-seconds` を超えています。
- `404 relay_disabled`: 受信側で無効、またはプロキシ先のパス/インスタンスが違います。
- `426 unsupported_protocol`: relay protocol の互換性がありません。

受信側の peer 一覧や秘密鍵を変えた場合は受信側でも `/bmchat reload` を実行してください。

## 表示

Web では現在のサーバーバッジを省略し、別サーバーのメッセージだけ `originServerId` 由来の固定色バッジを表示します。ゲーム出力も現在のサーバー名を省略し、別サーバー由来の古い形式に `{server}` / `{server_id}` がなければ `[server-name]` を自動付与します。Discord は共有外部チャンネルのためサーバー表示を維持します。同じ Discord チャンネルを複数サーバーで共有する場合、ローカルゲームチャットを実際に検知した発信元サーバーだけが DiscordSRV メッセージを編集し、他の peer は自分のサーバー名や絵文字リンクを追加しません。受信 peer は relay メッセージを Discord へ再送しないため、経由サーバーによる代替送信はありません。DiscordSRV ループとイベント重複を避けるため `sources.discord` と `sources.system` は既定で無効です。
