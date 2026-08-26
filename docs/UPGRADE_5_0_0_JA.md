# KOKOTO WebChat 5.0.0 アップグレード

5.0.0 は、正式版 4.7.0 以後の開発内容を統合し、**BlueMapWebChat (BMWC) → KOKOTO WebChat (KWC)** の名称移行を完了する major release です。

## 配布先の移行

最も安全な公開順序は **5.0.0 を既存 BlueMapWebChat project listing に先に公開すること**です。4.7.0 の update checker は旧 `bluemapwebchat` Modrinth project を参照するため、旧 listing に 5.0.0 を置くことで既存利用者が bridge release を検出できます。

旧/移行中の URL:
- Modrinth: `https://modrinth.com/plugin/bluemapwebchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/bluemapwebchat`
- GitHub: `https://github.com/KOKOTO-DEV/BlueMapWebChat`

移行後の予定 canonical URL:
- Modrinth: `https://modrinth.com/plugin/kokoto-webchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/kokoto-webchat`
- GitHub: `https://github.com/KOKOTO-DEV/KOKOTO-WebChat`

KWC 5.0.0 は Modrinth の `kokoto-webchat` を先に確認し、存在しない間は `bluemapwebchat` に fallback します。同一 project として rename できない platform では、旧 BMWC page を終了/移行案内として残し、新 KWC page へリンクします。4.7.0 利用者が 5.0.0 を検出できる前に旧 listing を閉じないでください。

## 4.7.0 → 5.0.0 の主な変更

- 正式 identity を KOKOTO WebChat に統一: `/kchat` (`/kc`), `kwc.*`, KWC data directory, `dev.kokoto.webchat`, `kwc-*` modules。
- Bukkit/Paper/Spigot、Fabric 16 exact-target、NeoForge 12 exact-target、Forge 16 exact-target が shared core を利用。
- BlueMap、squaremap、Dynmap、Pl3xMap、LiveAtlas、uNmINeD、Overviewer adapter を追加/整理。
- standalone を既定有効、public prefix `/chat`、API `/chat/api` に統一。
- Unicode content filter、UTF-8 filter list、mask/replace、anti-evasion、Web Admin 編集/テストを追加。
- account ごとの server-side UI profile、strict JSON import/export、account 共通 keyword/notification 設定を追加。
- Web Push と live-page OS notification の同一 device 重複を抑止。
- 管理者 Discord keyword alert を追加。matching/format/mention/dedupe は KWC、DiscordSRV は JDA connection/channel mapping のみ提供。
- `update-check.enabled` は Bukkit だけでなく Fabric・NeoForge・Forge でも動作し、KWC 優先/BMWC fallback の Modrinth 確認と `kwc.update.notify` ログイン通知を使用します。
- `upload.filename-mode: random|original` を追加し、Unicode/space/`~`/`+`/`%` と Windows clipboard 8.3 alias 問題を修正。
- Discord game relay mode、server relay、cross-server DM/read status、platform 共通 `/kchat` を整理。
- 通常 UX を変えない security hardening: Bearer auth、one-time SSE ticket、body size limit、bounded HTTP worker、admin IP enforcement、Web Push SSRF 防御、Discord mention/CDN redirect 防御、iframe source validation。

## Platform support

- Bukkit/Paper/Spigot: 1.18–26.2, Java 17 bytecode。
- Fabric exact-target: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`。target ごとに JDK 17/21/25 を使用。
- NeoForge exact-target: `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`。target ごとに JDK 17/21/25 を使用。
- Forge exact target: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`。

## BMWC data/config migration

Bukkit の既存 `plugins/BlueMapWebChat` は初回 KWC 起動時の migration input として利用でき、元 directory は変更せず残します。`web-addon.* → adapters.bluemap.*`、`standalone-web.* → frontend.standalone.*` を変換します。旧 `/bmchat` 等は command alias として登録せず、旧 `bluemapwebchat.*` permission は compatibility fallback のみです。`X-BMWC-Relay-*` wire header は既存 peer 互換のため維持します。

旧 `/bmwc/api`・`/bmwc/chat` reverse proxy は新 `/chat` layout に手動変更してください。

## Config migration

4.7.0→5.0.0 reference 比較は **79 paths added / 14 removed / 2 existing values changed** です。実際の version migration では既存値を保持して不足 setting/comment を挿入し、`config-version: "5.0.0_auto_migration"` にします。same-version auto backfill を止める場合のみ exact `5.0.0` にします。

## Release acceptance

`validate-release-windows.bat` が `FINAL RELEASE BUILD PASS`、deployable JAR 45 個、static/config/i18n/document validation、主要 flow の smoke test をすべて通過した候補だけを正式配布します。
