# Server Relay — Protocol 2.2

reaction authority、direct/multi-hop delivery、origin 障害時の outbox、作者通知の図は [REACTIONS.md](REACTIONS.md) を参照してください。

![Relay Protocol v2 のリクエスト認証と暗号化メッセージフロー](../assets/relay-v2-flow.svg)

![Reaction authority routing](../assets/reaction-authority-routing.svg)

[PNG](../assets/reaction-authority-routing.png) · [SVG](../assets/reaction-authority-routing.svg)

[Animated GIF](../assets/relay-v2-flow.gif) · [PNG](../assets/relay-v2-flow.png) · [SVG](../assets/relay-v2-flow.svg)

> **セキュリティ境界:** Relay v2 はエンドツーエンド暗号化ではなく、**hop-by-hop authenticated encryption** です。転送に参加する KWC サーバーは信頼境界内の参加者です。

KOKOTO WebChat 5.3.1 は **Relay Protocol 2.2** を引き続き使用します。 を固定 revision として使用します。Protocol major `2` が wire compatibility 境界で、2.2 は `public`, `dm`, `read`, `delete`, `reaction`, `reaction-authority`, `typing`, `game`, `profile` capability を通知します。KWC product version は診断情報であり互換性 key ではありません。reaction/reaction-authority/typing は 2.1 の feature set と互換で、5.3.0 の `delete`, `game`, `profile` は revision を上げず 2.2 内の capability negotiation で使用します。group-chat room は local のままです。

## セキュリティ上、優先してアップグレードすべき構成

この relay 固有のセキュリティ変更は、KWC 5.0.0 または互換 BMWC peer で **Relay Protocol v1 を実際に有効化/設定していた server** が対象です。server relay を使用していない server は、この relay transport/trust の弱点の対象ではありません。

- **最優先:** Relay v1 peer URL に `http://` を使用していた構成。TLS がない経路では Relay v1 payload は平文で転送されました。
- Relay v1 を HTTPS で使用していた場合、network hop 上の平文盗聴は防げますが、1つの flat peer trust set と top-level shared secret はそのままでした。そのため secret 漏えい、または意図しない peer/forwarding 設定の影響範囲は v2 の明示的な group trust boundary より広くなります。
- これは protocol design 上の exposure を説明するものであり、特定 installation が実際に攻撃された、または CVE が割り当てられたと主張するものではありません。


## 信頼モデル

Relay の **group がセキュリティ境界**です。各 group には次の要素があります。

- group の `id` 1つ
- その group 内のすべての member 関係で共有する `shared-secret` 1つ
- group 単位の `forwarding.enabled`
- `id`、`url`、`enabled` と任意の `send` / `receive` policy を持つ peer 一覧。shared secret は引き続き group 単位であり、peer ごとには保存しません。

Protocol v2 には `peers[].secret` はありません。これにより、peer entry に別 group の secret を誤って組み合わせる構成を防ぎます。

同じ peer ID を複数のローカル group に登録することはできません。KWC が重複を検出すると、その peer ID の登録をすべて無効化し、診断ログを出力します。

`shared-secret` は最終的に **32文字以上**必要ですが、operator が長い値を手作業で作る必要はありません。初回設定では **1台のサーバーだけ** `shared-secret: ""` にして KWC を起動または `/kchat reload` してください。KWC は暗号学的に安全な 32-byte URL-safe ランダム値を生成してそのサーバーの `config.yml` に書き戻し、secret 本文はログへ出力しません。生成された値を同じ group の他の全サーバーへそのままコピーします。各サーバーで個別に空値から生成すると異なる secret になり request の認証/復号に失敗するため避けてください。既存の空でない secret は自動再生成されず、32文字未満の手動値も自動置換せず invalid/fail-closed になります。secret が漏えいした場合は同じ group の全サーバーで交換してください。

## 設定例

```yaml
server-relay:
  enabled: true
  server-id: "server-1"
  server-name: "Server 1"
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
    event: true

  delivery:
    web: true
    game: true

  game-format: "&8[&b{server}&8] &f{sender}&7: &f{message}"

  groups:
    - id: "main"
      shared-secret: ""
      forwarding:
        enabled: false
      peers:
        - id: "server-2"
          url: "https://server2.example.com/api"
          enabled: true
          send:
            public-chat: true
            event: true
            dm: true
            profile: true
          receive:
            public-chat: true
            event: true
            dm: true
            profile: true
```

まず `server-1` を空値のまま一度起動/リロードし、`config.yml` を開き直して生成された secret を確認します。その値を `server-2` にそのままコピーし、同じ `main` group で `server-1` を逆方向 peer として登録します。

```yaml
server-relay:
  enabled: true
  server-id: "server-2"
  server-name: "Server 2"
  groups:
    - id: "main"
      shared-secret: "<copy-the-generated-secret-from-server-1>"
      forwarding:
        enabled: false
      peers:
        - id: "server-1"
          url: "https://server1.example.com/api"
          enabled: true
```


### peer ごとの送信/受信ポリシー

各 peer の `enabled` は全体の master switch のまま維持し、その下で `send` と `receive` を独立して制限できます。省略時は両方向とすべての traffic class が `true` になるため、既存の 5.2.x / 以前の 5.3.0 peer 設定は従来どおり動作します。`send: false` または `receive: false` で方向全体を停止できます。map 形式では `public-chat`, `event`, `dm`, `profile` を個別に切り替えます。公開 reaction/typing は `public-chat`、DM reaction/typing/read/delete は `dm`、remote event の参照/参加は `event`、remote profile 参照は `profile` に従います。

既存 config は runtime default だけに依存しません。migration 時に不足している `send` / `receive` map と、その中の `public-chat`, `event`, `dm`, `profile` を `true` として実際の config に補完します。既存の明示値や scalar `send: false` / `receive: false` は保持され、同じ migration を再実行しても追加変更は発生しません。

`sources.event` は `sources.system` から分離されています。event 通知だけを Relay し、その他の system 通知を local に残せます。event 作成時の **通知範囲** は作成/結果通知を local-only にするか Relay 対象にするかを決め、peer の `send.event` / `receive.event` が上位の routing 制限として適用されます。

5.3.1 では `send.event` を許可する enabled peer が 1 つ以上構成されている場合だけ event ごとの **通知範囲**を表示し、既定値を Relay にします。該当 peer がない場合は scope UI/command を表示せず local-only に正規化します。判定は一時的な接続状態ではなく構成済み topology に基づきます。

## Request 単位の認証と任意 identity/health probe

direct relay は 5.0.0 と同じ運用モデルで、各 `/relay/v2/message` request を独立して認証します。受信側は送信元を同じ group・同じ shared secret で登録する必要があり、その情報で request を認証/復号します。逆方向は独立です。`/relay/v2/handshake` は状態を保持しない診断用 identity/health probe であり、direct route の作成・保持・有効化・無効化には使いません。任意 probe request には次の値が結び付けられます。

- protocol major `2` と protocol revision `2.2`（product version は診断情報のみ）
- `group-id`
- 送信 server ID
- 宛先 server ID
- timestamp
- nonce
- 送信側で実際に設定されている outbound transport (`http` または `https`)

受信側は、group の存在、送信 server がその group の peer であること、自身が target ID であること、timestamp の有効性、nonce が再利用されていないこと、HMAC が group shared secret と一致することを検証します。そのため片側だけの peer 設定は、どちらの方向にも利用可能になりません。

Endpoint:

```text
/relay/v2/handshake
/relay/v2/message
```

旧 v1 endpoint (`/relay/handshake`, `/relay/receive`, `/relay/dm/receive`, `/relay/dm/read`) は **HTTP 426** を返し、protocol major `2` / revision `2.2` を通知します。

## Protocol revision と capability

Relay 互換性は KWC product version に結び付きません。`X-KWC-Relay-Version: 2` は major wire family、`X-KWC-Relay-Protocol: 2.2` と `X-KWC-Relay-Capabilities` は現在の revision と optional extension を示します。KWC 5.3.1 は revision 2.2 を維持し、`delete`, `game`, `profile` を capability で区別します。必要な capability が相手 peer に無い場合、その extension だけ安全に失敗します。product version は診断情報のみです。


### Event Relay routing (2.2)

`game` capability は event action を全 peer へ broadcast しません。event announcement は event ID と origin server ID を保持し、relay された event を開く/参加する場合はその origin server に向けてのみ targeted `game-request` を送ります（必要なら許可された forwarding route を使用）。origin event に到達できない場合、receiver 自身の local event に置き換えてはいけません。旧/未対応 peer は安全に失敗するか announcement snapshot のみ表示します。

## メッセージの暗号化と認証

Relay v2 は group secret、group ID、sender ID、receiver ID から HKDF-SHA256 で **方向別 256-bit key** を導出します。各 request はランダムな 12-byte IV と 128-bit authentication tag を持つ AES-256-GCM を使用します。

次の値は GCM Additional Authenticated Data (AAD) に含まれます。

```text
group-id
from-server-id
to-server-id
timestamp
nonce
IV
```

これらの値を1つでも変更すると認証に失敗します。暗号化 payload には message kind (`public`, `dm`, `read`) と対応する relay envelope が含まれます。

既知 peer からの成功/エラー response も HMAC-SHA256 で認証されます。Response signature は group、responder、requester、response timestamp、request nonce、HTTP status、response body に結び付けられ、未認証の中継者が成功 response を偽装することを防ぎます。

## Replay / loop 防御

Relay v2 は次を強制します。

- timestamp の許容差 (`max-clock-skew-seconds`)
- request ごとの nonce replay 拒否
- relay ID / receipt ID の重複排除
- origin-server loop 検出
- forwarding traffic の `max-hops`

## HTTP と HTTPS

直接 1-hop の HTTP peer は許可されます。**Relay payload 自体は AES-256-GCM で暗号化・認証される**ため、relay v1 のような plaintext payload ではありません。ただし HTTPS は metadata に対する transport-layer confidentiality、標準的な server identity 検証、defense in depth を提供するため、KWC は localized warning を記録します。

HTTP を forwarding hop として使用することはできません。

Forwarding が行われる条件は次のすべてです。

1. source group の `forwarding.enabled: true`
2. この server に設定された incoming peer entry の URL が HTTPS
3. 次に選ぶ peer entry の URL も HTTPS
4. 次の peer が同じ group に所属する

HTTP の除外は **peer 単位**です。`http://` peer は direct relay には利用できますが、その peer から受けた traffic はさらに forwarding せず、その peer 自体も forwarding next hop には選びません。同じ group の他の `https://` peer は引き続き候補です。

## Group 分離

ある group で受信した message を別 group へ forwarding することはありません。Forwarding candidate は **incoming group と同じ group** からだけ選択されます。公開チャット、DM、DM read receipt のすべてに同じ規則が適用されます。

Local message は local server が明示的に所属する複数 group へ publish できます。これは送信元 server で operator が定義した bridge であり、受信済み message の cross-group forwarding ではありません。

## End-to-end ではなく hop-by-hop 暗号化

Relay v2 は **hop-by-hop authenticated encryption** であり、end-to-end encryption ではありません。Forwarding を行う KWC server は incoming payload を復号し、relay envelope を検証・処理した後、次 hop 用の方向別 key で再暗号化します。

したがって forwarding server は trusted participant であり、relay payload を閲覧できます。Relay v2 を E2EE と表現しないでください。

## 5.0.0 / relay v1 からのアップグレード

5.1.0 は旧 flat relay 設定から v2 group を推測しません。最初の 5.0.0 → 5.1.0 migration では次を行います。

- `server-relay.shared-secret` を廃止
- flat `server-relay.peers` を廃止
- `server-relay.forward-received-public-chat` を廃止
- 旧 top-level forwarding 設定を推測した group へ引き継がない
- `server-relay.enabled` を `false` にリセット
- operator が明示的な v2 group を定義した後に relay を再度有効化

これにより peer を誤った trust group へ暗黙に割り当てることを防ぎます。

## 運用診断

Startup/reload 時は次を確認してください。

- `Server relay protocol v2 enabled`
- 任意 identity/health probe の結果（診断用）
- duplicate peer ID の診断
- group secret length の診断
- HTTP peer warning
- forwarding HTTPS-block warning

任意の identity/health probe が失敗する場合は、group ID、両 server ID、相互 peer entry、group secret、API base URL、clock synchronization、network reachability を確認してください。direct message 配信は probe 状態と独立しています。

## 参照規格

一次規格と公式資料の一覧は [REFERENCES.md](REFERENCES.md) を参照してください。

- [RFC 2104 — HMAC](https://www.rfc-editor.org/rfc/rfc2104.html)
- [RFC 5869 — HKDF](https://www.rfc-editor.org/info/rfc5869/)
- [NIST SP 800-38D — GCM](https://csrc.nist.gov/pubs/sp/800/38/d/final)
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)

### リモートプロフィール参照 (2.2)

`profile` capability は、他サーバーのユーザーの公開プロフィールと Presence のみを、元サーバーへの targeted `profile-request` で取得します。セッション、制限、管理者専用情報は転送しません。
