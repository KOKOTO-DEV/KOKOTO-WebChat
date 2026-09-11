# KOKOTO WebChat 5.3.0 リリースチェックリスト

## ソース / 設定 / 多言語
- [ ] Root/Bukkit/Fabric/NeoForge/Forge の metadata と成果物名がすべて `5.3.0` である。
- [ ] 現在の `config.yml`、`config-baselines/config-5.3.0.yml`、多言語 config template、`distribution/config-reference-5.3.0.yml` の parsed setting path/default が一致し、表示用 comment と `_auto_migration` marker の差だけが許容される。過去 baseline は migration 入力としてのみ保持する。
- [ ] 5.2.1 → 5.3.0 migration が `5.3.0_auto_migration` を記録し、対応済み運用値と Relay v2 trust 設定を保持し、廃止された hide 確認設定を delete 確認設定へ移行し、正確な `5.3.0` は同一 version の再構築を停止する。
- [ ] en-US/ko-KR/ja-JP/zh-CN の key set と placeholder が完全に一致する。
- [ ] `inner.js` と 8 個すべての frontend wrapper が構文検査と embedded JS/CSS 一致検査を通過する。
- [ ] `node tools/build-inner-bundle.js --check` が成功し、`frontend/inner/manifest.txt`、生成済み `inner.js`、8 個の wrapper の embedded payload がすべて一致する。

## 機能 smoke test
- [ ] ゲーム↔Web 公開チャット、Reply、URL、custom emoji、pin、検索、message token、content filter が正常に動作する。
- [ ] 既存の不正な emoji pack/item 名が canonical 名へ移行され、新規 pack/item upload も同じ規則を使い、同一 pack 内の衝突は数値 suffix で解消される。
- [ ] Emoji picker は自動スペースを追加せず正確な token を挿入し、設定した newline alias の emoji-only 行は詰めて表示され、blank-line alias は実際の空行を維持する。
- [ ] Web Reply は元メッセージ全文を保持し、URL/custom emoji を読みやすく表示する。
- [ ] ゲーム内 DM/group の名前クリックは既存コマンドを入力欄へ準備し、本文クリックは Reply を準備し、URL 部分は URL を開く。
- [ ] 改ざんした `dm-...`/`group-...` Reply target は、実際の DM 参加者または現在の group member でない限り拒否される。
- [ ] Chat Event は First come/抽選を複数同時に保持し、First come は当選人数の定員到達で自動完了し、参加者/当選者一覧は表示名 / 実名 mode に従い、結果通知は `🏆` で始まり名前が異なる場合は `表示名 (実名)` と表示する。

- [ ] 公開 message reaction が永続化され Relay 2.2 伝播が動作し、reaction-only SSE 更新で再生中 media が再起動しない。32 × 16px empty-state `+` は本文下/次 message 前に各 1px の視覚的余白を取り、reaction OFF は元の 8px spacing、実 reaction は通常の in-flow row を使う。category/search 再描画後も picker 位置と outside-click close が維持され、**Admin > Emojis > Reaction icons** は他の Admin settings と同じ rounded themed row と `emoji = search words` alias editor を提供し、alias は `reaction-search-aliases.txt` に保存される。 検索 alias は picker 検索専用で chat 入力を変換しない。
- [ ] public/DM/group typing は 5 秒の event-driven window で動作し、自分自身/audit viewer を除外し、長い複数 user 名は人数表示へ縮約し、polling/永続 typing state を作らない。Web Admin Settings/config.yml で server-wide の既定値 Open chat OFF / DM ON / Group ON を個別制御する。`chat.typing-indicator.user-display-control` は既定 OFF で、管理者が有効にした場合はアカウント保存の個人 switch が受信表示だけを隠し、自分の typing 送信は継続する。
- [ ] 保存済み会話は server 側で range を再検証し、archive quota と private-room lock policy を適用し、通常 retention 後も snapshot を保持する一方、管理者による元 message 削除は cascade する。
- [ ] 保存済み会話 PDF/print view は display name + real name を表示し、現在の KWC appearance を使用し、原本が残る image だけを含め、video/audio/その他 file は link とし、失われた原本は unavailable と表示する。
- [ ] `chat.conversation-archive.enabled: false` では archive API を登録せず、`conversation-archives.db` を開く/作成せず、Web UI に保存済み会話関連 DOM を生成しない。
- [ ] `max-archives-per-user`、`max-messages-per-archive`、`max-messages-per-user` を既定値以外に設定した場合も server 側で実際の上限が適用され、`/archive/list` が適用値を返す。
- [ ] 開いている DM では Back/title hover の背景が inset された Settings button 領域まで header 全体を継ぎ目なく覆い、Settings button 自身の hover state は独立して維持される。
- [ ] 絵文字リアクション通知 checkbox 1 個がライブ browser notification と background/mobile Web Push の reaction 通知を共通制御し、未対応の配信経路は動作しない。
- [ ] 公開/DM/group latest-follow は実際の line-height 基準で **最下部 2 行未満**の場合だけ動作し、それより上では新着 message / compose panel layout change で最下部へ強制 scroll せず、更新/再接続後は可能な場合に保存済みの閲覧位置を復元する。

## Relay / セキュリティ
- [ ] 任意の signed `/relay/v2/handshake` identity/health probe は、双方が同じ group ID と group shared secret で互いを登録した場合に成功し、probe は route 状態を作らず direct relay は各 request を独立認証する。
- [ ] 片側だけの peer 設定は両方向とも使用不可である。
- [ ] `server-relay.forwarding.enabled` の既定値が `false` である。
- [ ] forwarding 有効時も inbound/outbound の両 forwarding hop が HTTPS の場合だけ許可され、HTTP forwarding は拒否される。
- [ ] 直接 HTTP peer は 1-hop 互換を維持しながら、明示的な多言語 WARNING/警告を出力する。
- [ ] KWC 内蔵 HTTP listener が loopback 以外へ公開される場合、HTTP 公開警告を出力する。
- [ ] 公開 relay と server 間 1:1 DM/read receipt が動作し、group chat は local 機能のままで server relay されない。

## 配布 / ビルド
- [ ] Modrinth updater が canonical `kokoto-webchat` project のみを照会し、旧 BMWC project URL を照会しないことを確認する。
- [ ] CurseForge URL が `bukkit-plugins/kokoto-webchat` を指す。
- [ ] Windows path preflight が full validator と Fabric/NeoForge/Forge の build-all/build-target entry point 全てで有効である。
- [ ] Bukkit は JDK 17、Fabric 16 / NeoForge 12 / Forge 16 の exact target は対象ごとの JDK 17/21/25 でビルドされる。
- [ ] release validator が全 NeoForge JAR を開き、target が選択した `META-INF/mods.toml` または `META-INF/neoforge.mods.toml`、`modLoader`、`loaderVersion`、KWC ID/version、exact Minecraft dependency、未展開 template placeholder がないことを検証する。
- [ ] release validator が security + Relay/reaction/typing regression harness を実行し、完成した shaded Bukkit JAR に対して保存済み会話 SQLite runtime harness を実行する。

> `validate-release-windows.bat` と必要な PowerShell helper は source archive に含まれています。開発用 regression harness も source archive の `validation/` に含まれるため、別の validation-tools archive は不要です。

- [ ] `validate-release-windows.bat` が `FINAL RELEASE BUILD PASS`、45 個の deployable JAR、SHA256SUMS を生成する。
- [ ] 最終 acceptance は `--fast` なしで実行する。sequential または `--parallel` clean 実行は許可するが、cached/partial build を `FINAL RELEASE BUILD PASS` として扱わない。
