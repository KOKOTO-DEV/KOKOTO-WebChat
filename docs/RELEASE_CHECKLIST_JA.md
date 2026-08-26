# KOKOTO WebChat 5.0.0 Release Checklist

- [ ] 5.0.0 metadata/artifact/config reference が一致する。
- [ ] 4.7.0→5.0.0 migration、`5.0.0_auto_migration`、same-version backfill stop が正常。
- [ ] en-US/ko-KR/ja-JP/zh-CN key set が同一で空 translation がない。
- [ ] login/guest/public chat/reply/pin/search/filter/DM/group/upload/clipboard/profile/Push/admin Discord alert/relay/map adapters を smoke test する。
- [ ] clipboard original filename で long filename が有効、DOS 8.3 alias が broken link を作らない。
- [ ] Bearer auth、one-time SSE ticket、admin IP restriction、body limit、Web Push SSRF protection、Discord mention/CDN protection、profile strict import を確認する。
- [ ] `update-check.enabled` が Bukkit/Fabric/NeoForge/Forge すべてで実動し、KWC 優先/BMWC fallback 確認と `kwc.update.notify` login notice が正常。
- [ ] Bukkit JDK17、Fabric 16 exact-target、NeoForge 12 exact-target、Forge 16 exact-target が target 別 JDK 17/21/25 で build 成功する。
- [ ] `validate-release-windows.bat` が `FINAL RELEASE BUILD PASS`、45 deployable JAR、SHA256SUMS を生成する。
- [ ] README/Upgrade/Configuration/User Manual/Wiki/Modrinth/CurseForge が最終 5.0.0 と一致し、“rebrand later” の旧説明がない。
- [ ] AI assistance disclosure は README/description/`AI_USAGE.md` に置き、functional changelog 項目にはしない。
- [ ] 5.0.0 を旧 BMWC listing に先に公開し、4.7.0 update checker が bridge release を検出できるようにする。
- [ ] 5.0.0 の update notice の CurseForge link は、新 KWC CurseForge listing が実際に公開されるまで既存 BMWC bridge page を使用する。
- [ ] 同一 project rename ができない場合、BMWC page は終了/移行案内として残し、新 KWC listing を案内する。
- [ ] Modrinth は 1 project に loader 別 version を置き、CurseForge は既存 Bukkit Plugins project に Bukkit bridge を先に公開してから mod-loader file/class compatibility を確認する。
- [ ] GitHub は `BlueMapWebChat` → `KOKOTO-WebChat` rename を優先し、rename 後 local remote を更新する。

- [ ] ImageEmojis-Bero 使用時は shared `plugins/KOKOTO-WebChat/emojis`、`serverIp:webServerPort` 到達性、resource-pack reload/update、game↔web token rendering を確認する。
- [ ] SimpleNicks-Bero 使用時は `player-display.mode: "display-name"` の nickname 表示と KWC account/UUID identity を確認する。
