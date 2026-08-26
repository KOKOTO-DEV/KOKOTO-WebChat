# KOKOTO WebChat Bukkit Platform

Current Bukkit/Spigot/Paper runtime for KOKOTO WebChat 5.0.0. This module owns plugin lifecycle, Bukkit YAML parsing, account/session YAML persistence, commands/listeners, Bukkit resources, DiscordSRV integration, audit/config maintenance, and Bukkit implementations of the core host/platform contracts.

`BukkitBlueMapAdapterHost`, `BukkitSquaremapAdapterHost`, `BukkitDynmapAdapterHost`, `BukkitLiveAtlasAdapterHost`, `BukkitUnminedAdapterHost`, and `BukkitOverviewerAdapterHost` bridge Bukkit filesystem/resource/version/log access into the map adapters. The final Bukkit JAR also shades `kwc-standalone-frontend`, which owns the standalone web assets independently of BlueMap.

`BukkitPlatformAdapter` handles player snapshots, permissions, main-thread scheduling, console command dispatch, plain/interactive Minecraft chat delivery, known-player lookup, and ImageEmojis runtime-symbol discovery. `BukkitConversationStoreHost`, `BukkitRelayHost`, `BukkitWebPushHost`, and `BukkitWebChatHost` adapt Bukkit services to `kwc-core`.

`WebChatServer` lives in `kwc-core`; commands/listeners/lifecycle remain Bukkit-side by design.

Build the repository root with `mvn clean package`; deploy `kwc-platform-bukkit/target/KOKOTO-WebChat-5.0.0-Bukkit-1.18-26.2.jar`.

## uNmINeD integration

Set `adapters.unmined.enabled: true` and normally set `adapters.unmined.web-root` to the existing uNmINeD export directory. The filesystem adapter patches only a positively identified uNmINeD HTML entry point and KWC-owned `kokoto-web-chat/` files. Run `/kchat reload` after re-exporting the map.

## Overviewer integration

Set `adapters.overviewer.enabled: true` and normally point `adapters.overviewer.web-root` at Minecraft Overviewer's generated `outputdir`. KWC patching is marker-checked, filesystem-only, and rejects unrelated Leaflet pages. Run `/kchat reload` after Overviewer regenerates `index.html` or updates its web assets.
