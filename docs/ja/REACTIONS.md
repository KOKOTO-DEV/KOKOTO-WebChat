# KOKOTO WebChat 5.3.0 — メッセージの反応

反応は公開 chat、1:1 DM、通常の group-chat message に保存される永続的な message state です。Relay された公開 message は **元メッセージを作成したサーバー**がその公開 reaction state の authority になります。cross-server DM reaction は相手 participant の server だけへ直接送られ、無関係な Relay peer へ broadcast しません。group-chat room は local のままで、join/leave event は reaction 対象ではありません。

## 動作構造の図

![reaction authority routing](../assets/reaction-authority-routing.svg)

![reaction pending/outbox lifecycle](../assets/reaction-outbox-lifecycle.svg)

![reaction author notification routing](../assets/reaction-notification-routing.svg)

上の静的図は全体構造の要約で、下の Mermaid 図は protocol 順序と state transition を詳しく示します。

## ローカル / 直接接続

```mermaid
sequenceDiagram
    participant U as ユーザー
    participant L as 現在の KWC サーバー
    participant O as 元メッセージのサーバー
    participant A as 元メッセージの作者
    U->>L: 反応追加/解除
    alt ローカルメッセージ
        L->>L: 検証 + 保存
    else origin と直接接続
        L->>O: stable eventId の reaction-request
        O->>O: 検証 + 保存
        O-->>L: 確定状態
    end
    O-->>A: 他人による実際の追加変更だけ通知
```

remote mirror は count を先に確定しません。origin と直接接続している場合は不要な peer を経由せず直接 request を送ります。

## マルチホップ Relay

```mermaid
sequenceDiagram
    participant S1 as Server 1 mirror
    participant S2 as Server 2 forwarder
    participant S3 as Server 3 origin
    S1->>S2: reaction-request target=3
    S2->>S3: 同じ eventId を転送
    S3->>S3: message/settings/current state を検証
    S3->>S3: authoritative state を保存
    S3-->>S2: committed reaction event
    S2-->>S1: committed event を転送
    Note over S1,S3: multi-hop は通常の Relay v2 HTTPS forwarding policy に従う
```

## Origin が利用できない場合

```mermaid
stateDiagram-v2
    [*] --> Ready
    Ready --> Pending: origin に到達できない
    Pending --> Pending: retryable failure
    Pending --> Pending: 同じ対象は最新 desired state に置換
    Pending --> Committed: origin が承認
    Pending --> Failed: permanent reject
    Pending --> Expired: 5分で期限切れ
    Committed --> [*]
    Failed --> [*]
    Expired --> [*]
```

管理者が reaction 機能を OFF にすると、待機中の local mutation request は破棄され、後から再送されません。

同じ target server + relay message ID + actor + reaction の request は最新 desired state にまとめられます。offline 中の add→remove が、後から stale add notification になることを防ぎます。

## 作者通知

```mermaid
flowchart TD
    A[origin が mutation を commit] --> B{実際に変更された追加?}
    B -- No --> X[作者通知なし]
    B -- Yes --> C{player author が存在?}
    C -- No / system message --> X
    C -- Yes --> D{actor は author 本人?}
    D -- Yes --> X
    D -- No --> E[Minecraft private notice]
    D -- No --> F[Web notification inbox]
    D -- No --> G[設定時 Web Push]
```

remove と self-reaction は通知しません。system message は reaction を保持できますが player author がいないため代わりに admin へ通知することもありません。

Chat settings の **絵文字リアクション** checkbox は reaction 追加に対する共通 Web 通知設定です。この 1 個の checkbox が live browser notification と background/mobile Web Push の両方を制御し、各 browser/device では対応する配信経路だけが動作します。Minecraft 内の author 向け private notice は別の server-side notice で、この Web 通知 checkbox の対象ではありません。

## Web UI と管理

通常の message 間隔は 8px です。reaction が ON でログイン user が `+` を使える empty state では、通常の 8px margin に 10px の empty reaction space を追加します。`+` button は 32 × 16px で、message 本文の下に 1px、次の message の前にも 1px の余白を確保するため、文字に重なりません。reaction OFF の場合は empty affordance 自体を描画しないため元の 8px のままです。実 reaction が付いた場合のみ通常の in-flow reaction row を使用します。

picker は emoji 文字そのもの、server が生成する Unicode 名、管理者が編集できる検索 alias、custom emoji の ID/name/pack を検索できます。**Admin > Emojis > Reaction icons** には `emoji = search words` 形式の検索 alias editor があり、runtime list は `reaction-search-aliases.txt` に保存されます。新しい Unicode icon に任意の検索語を追加できるため frontend code の変更は不要です。reaction ON/OFF と KWC custom emoji 許可は他の Admin settings と同じ rounded form/row を使い、checkbox も row 内に配置されます。

関連: [SERVER_RELAY.md](SERVER_RELAY.md), [USER_GUIDE.md](USER_GUIDE.md), [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md)

管理者の反応設定で **反応したユーザー一覧を表示** をオフにすると、反応数と現在ユーザーの参加状態は維持されますが、サーバーは反応者の名前情報を Web 応答に含めず、ツールチップも表示しません。既定値はオンです。一覧を表示する場合、反応者名は通常のチャット送信者と同じ **表示名 ↔ 元の名前** 切り替えを使い、反応者名をクリックすると全体の名前表示モードも切り替わります。UUID はサーバー内部にのみ保持されます。他のユーザーが先に付けた反応チップもクリックして同じ反応に参加でき、参加済みなら再クリックで自分の反応だけを解除します。ゲーム内の作者通知は **1 行目に原文プレビュー、2 行目に反応内容** の順で表示します。
