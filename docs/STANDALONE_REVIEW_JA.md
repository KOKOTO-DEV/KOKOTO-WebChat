# BlueMap 依存性 / standalone モード確認

## 概要

Java プラグイン本体は BlueMap API に依存していません。`plugin.yml` には BlueMap の `depend` / `softdepend` がなく、Java ソースにも BlueMap API の import はありません。実行時の依存先は Bukkit/Spigot 系サーバー API です。DiscordSRV 連携は任意です。

そのため、チャット機能自体は BlueMap なしでも動作します。BlueMap 固有の部分は `kwc-adapter-bluemap` が担当し、BlueMap の web ディレクトリへ埋め込み addon 用アセットをコピーして `webapp.conf` をパッチします。standalone のアセットは別の `kwc-standalone-frontend` モジュールに格納され、BlueMap adapter からは読み込みません。

## 対応モード

KOKOTO WebChat は現在、次の 2 つの形態をサポートします。

```text
BlueMap addon panel
standalone page
```

standalone モードはデフォルトでは無効です。必要な場合のみ明示的に有効化してください。

```yaml
  api-base-url: ""
```

`frontend.standalone.api-base-url` は通常空のままにします。直接 HTTP では内部 `http.path-prefix`、リバースプロキシ経由では `http.public-prefix + http.path-prefix` を使用するため、既定の公開 API は `/chat/api` です。standalone だけ別の公開 API URL が必要な場合にのみ設定します。

直接 HTTP URL:

```text
http://<server-host>:8899/
```

HTTPS リバースプロキシ URL 例:

```text
https://<domain>/chat
```

## standalone 専用運用

BlueMap 地図内にチャット UI を挿入しない場合は、次のように設定します。

```yaml
adapters:
  bluemap:
    auto-install: false
    auto-patch-webapp-conf: false

frontend:
  standalone:
    enabled: true
    path: "/"
```



## 透過ウィンドウの制限

standalone のブラウザーウィンドウや Document Picture-in-Picture ウィンドウは、通常の Web API だけでは OS レベルの真の透過ウィンドウにはできません。CSS でチャットパネル自体を半透明にすることはできますが、ブラウザー/PIP ウィンドウ背景やデスクトップ透過はブラウザーまたは OS 側の制御になります。

### URL 設定の解決規則

`frontend.standalone.api-base-url` は standalone ページ専用の API override で、通常は空のままにします。`adapters.bluemap.api-base-url` は継承しません。upload/emoji を空にすると共通の公開 API base に `/uploads`、`/emojis` を追加します。`/chat/api` のような絶対ブラウザパスはそのまま使います。先頭 `/` のない相対値は `http.cors-origin` が実際の origin のときその origin に対して解決されます。`https://...` の完全 URL はそのまま使います。


