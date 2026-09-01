# KOKOTO WebChat 5.1.0 リリースチェックリスト

## ソース / 設定 / 多言語
- [ ] Root/Bukkit/Fabric/NeoForge/Forge の metadata と成果物名がすべて `5.1.0` である。
- [ ] `config.yml`、`config-baselines/config-5.1.0.yml`、`distribution/config-reference-5.1.0.yml` が byte-identical である。
- [ ] 5.0.0 → 5.1.0 migration が `5.1.0_auto_migration` を記録し、対応している運用値は保持する一方で retired 設定は削除し、Relay v1 の trust/topology は推測せず明示的な再設定用に無効な Relay v2 として再構築し、正確な `5.1.0` では同一バージョンの設定再構築を停止する。
- [ ] en-US/ko-KR/ja-JP/zh-CN の key set と placeholder が完全に一致する。
- [ ] `inner.js` と 8 個すべての frontend wrapper が構文検査と embedded JS/CSS 一致検査を通過する。

## 機能 smoke test
- [ ] ゲーム↔Web 公開チャット、Reply、URL、custom emoji、pin、検索、message token、content filter が正常に動作する。
- [ ] 既存の不正な emoji pack/item 名が canonical 名へ移行され、新規 pack/item upload も同じ規則を使い、同一 pack 内の衝突は数値 suffix で解消される。
- [ ] Emoji picker は自動スペースを追加せず正確な token を挿入し、設定した newline alias の emoji-only 行は詰めて表示され、blank-line alias は実際の空行を維持する。
- [ ] Web Reply は元メッセージ全文を保持し、URL/custom emoji を読みやすく表示する。
- [ ] ゲーム内 DM/group の名前クリックは既存コマンドを入力欄へ準備し、本文クリックは Reply を準備し、URL 部分は URL を開く。
- [ ] 改ざんした `dm-...`/`group-...` Reply target は、実際の DM 参加者または現在の group member でない限り拒否される。

## Relay / セキュリティ
- [ ] 任意の signed `/relay/v2/handshake` identity/health probe は、双方が同じ group ID と group shared secret で互いを登録した場合に成功し、probe は route 状態を作らず direct relay は各 request を独立認証する。
- [ ] 片側だけの peer 設定は両方向とも使用不可である。
- [ ] `server-relay.forwarding.enabled` の既定値が `false` である。
- [ ] forwarding 有効時も inbound/outbound の両 forwarding hop が HTTPS の場合だけ許可され、HTTP forwarding は拒否される。
- [ ] 直接 HTTP peer は 1-hop 互換を維持しながら、明示的な多言語 WARNING/警告を出力する。
- [ ] KWC 内蔵 HTTP listener が loopback 以外へ公開される場合、HTTP 公開警告を出力する。
- [ ] 公開 relay と server 間 1:1 DM/read receipt が動作し、group chat は local 機能のままで server relay されない。

## 配布 / ビルド
- [ ] Modrinth updater が URL 移行中に `kokoto-webchat` を優先し `bluemapwebchat` へ fallback し、両方の取得失敗時だけ警告する。
- [ ] CurseForge URL が `bukkit-plugins/kokoto-webchat` を指す。
- [ ] Windows path preflight が full validator と Fabric/NeoForge/Forge の build-all/build-target entry point 全てで有効である。
- [ ] Bukkit は JDK 17、Fabric 16 / NeoForge 12 / Forge 16 の exact target は対象ごとの JDK 17/21/25 でビルドされる。
- [ ] release validator が全 NeoForge JAR を開き、target が選択した `META-INF/mods.toml` または `META-INF/neoforge.mods.toml`、`modLoader`、`loaderVersion`、KWC ID/version、exact Minecraft dependency、未展開 template placeholder がないことを検証する。

> `validate-release-windows.bat` と必要な PowerShell helper は source archive に含まれています。別の `KWC-5.1.0-validation-tools.zip` には開発専用の browser regression tool のみが含まれ、release build の実行には不要です。

- [ ] `validate-release-windows.bat` が `FINAL RELEASE BUILD PASS`、45 個の deployable JAR、SHA256SUMS を生成する。
- [ ] 最終 acceptance は `--fast` なしで実行する。sequential または `--parallel` clean 実行は許可するが、cached/partial build を `FINAL RELEASE BUILD PASS` として扱わない。
