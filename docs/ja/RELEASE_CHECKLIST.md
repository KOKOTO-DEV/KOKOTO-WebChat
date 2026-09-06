# KOKOTO WebChat 5.2.0 リリースチェックリスト

## ソース / 設定 / 多言語
- [ ] Root/Bukkit/Fabric/NeoForge/Forge の metadata と成果物名がすべて `5.2.0` である。
- [ ] `config.yml`、`config-baselines/config-5.2.0.yml`、`distribution/config-reference-5.2.0.yml` が byte-identical である。
- [ ] 5.1.0 → 5.2.0 migration が `5.2.0_auto_migration` を記録し、既存 Relay v2 group/secret/peer を含む対応済み運用値を保持しながら retired 設定を削除し、正確な `5.2.0` では同一バージョンの設定再構築を停止する。pre-5.1.0 migration の場合だけ legacy Relay v1 trust/topology を再利用せず、明示的な Relay v2 再設定用に初期化する。
- [ ] en-US/ko-KR/ja-JP/zh-CN の key set と placeholder が完全に一致する。
- [ ] `inner.js` と 8 個すべての frontend wrapper が構文検査と embedded JS/CSS 一致検査を通過する。

## 機能 smoke test
- [ ] ゲーム↔Web 公開チャット、Reply、URL、custom emoji、pin、検索、message token、content filter が正常に動作する。
- [ ] 既存の不正な emoji pack/item 名が canonical 名へ移行され、新規 pack/item upload も同じ規則を使い、同一 pack 内の衝突は数値 suffix で解消される。
- [ ] Emoji picker は自動スペースを追加せず正確な token を挿入し、設定した newline alias の emoji-only 行は詰めて表示され、blank-line alias は実際の空行を維持する。
- [ ] Web Reply は元メッセージ全文を保持し、URL/custom emoji を読みやすく表示する。
- [ ] ゲーム内 DM/group の名前クリックは既存コマンドを入力欄へ準備し、本文クリックは Reply を準備し、URL 部分は URL を開く。
- [ ] 改ざんした `dm-...`/`group-...` Reply target は、実際の DM 参加者または現在の group member でない限り拒否される。

- [ ] 公開 message reaction が永続化され Relay 2.1 伝播が動作し、reaction-only SSE 更新で再生中 media が再起動しない。32 × 16px empty-state `+` は本文下/次 message 前に各 1px の視覚的余白を取り、reaction OFF は元の 8px spacing、実 reaction は通常の in-flow row を使う。category/search 再描画後も picker 位置と outside-click close が維持され、**Admin > Emojis > Reaction icons** は他の Admin settings と同じ rounded themed row と `emoji = search words` alias editor を提供し、alias は `reaction-search-aliases.txt` に保存される。
- [ ] public/DM/group typing は 5 秒の event-driven window で動作し、自分自身/audit viewer を除外し、長い複数 user 名は人数表示へ縮約し、polling/永続 typing state を作らない。Web Admin Settings/config.yml で server-wide の既定値 Open chat OFF / DM ON / Group ON を個別制御し、個人の Chat settings に typing switch が存在しないこと。
- [ ] 保存済み会話は server 側で range を再検証し、archive quota と private-room lock policy を適用し、通常 retention 後も snapshot を保持する一方、管理者による元 message 削除は cascade する。
- [ ] 保存済み会話 PDF/print view は display name + real name を表示し、現在の KWC appearance を使用し、原本が残る image だけを含め、video/audio/その他 file は link とし、失われた原本は unavailable と表示する。
- [ ] `chat.conversation-archive.enabled: false` では archive API を登録せず、`conversation-archives.db` を開く/作成せず、Web UI に保存済み会話関連 DOM を生成しない。
- [ ] `max-archives-per-user`、`max-messages-per-archive`、`max-messages-per-user` を既定値以外に設定した場合も server 側で実際の上限が適用され、`/archive/list` が適用値を返す。
- [ ] 開いている DM では Back/title hover の背景が inset された Settings button 領域まで header 全体を継ぎ目なく覆い、Settings button 自身の hover state は独立して維持される。
- [ ] 絵文字リアクション通知 checkbox 1 個がライブ browser notification と background/mobile Web Push の reaction 通知を共通制御し、未対応の配信経路は動作しない。
- [ ] 公開/DM/group bottom-follow は 32px を使用し、compose panel の layout change は viewport を保持し、その変化だけで即座に最下部へ強制 scroll しない。

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

> `validate-release-windows.bat` と必要な PowerShell helper は source archive に含まれています。別の `KWC-5.2.0-validation-tools.zip` には開発専用の browser regression tool のみが含まれ、release build の実行には不要です。

- [ ] `validate-release-windows.bat` が `FINAL RELEASE BUILD PASS`、45 個の deployable JAR、SHA256SUMS を生成する。
- [ ] 最終 acceptance は `--fast` なしで実行する。sequential または `--parallel` clean 実行は許可するが、cached/partial build を `FINAL RELEASE BUILD PASS` として扱わない。
