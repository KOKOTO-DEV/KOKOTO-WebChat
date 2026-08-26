# KOKOTO WebChat BlueMap Adapter

Loader-neutral BlueMap web-map integration for KOKOTO WebChat 5.0.0.

This module owns:

- BlueMap web-root and `webapp.conf` discovery
- addon directory creation and asset installation under `addons/kokoto-web-chat`
- generated `config.js` for the current chat API base
- BlueMap `webapp.conf` script/style patching and manual example generation
- bundled BlueMap wrapper assets (`web/chat.js`, `web/chat.css`)

It depends on `kwc-core` but contains no Bukkit/Paper/Fabric/NeoForge/Forge imports. Runtime-specific filesystem/resource/version/log access is supplied through `BlueMapAdapterHost`. The Bukkit implementation is `BukkitBlueMapAdapterHost` in `kwc-platform-bukkit`.

Canonical settings live under `adapters.bluemap.*`. The old BMWC `web-addon.*` keys and `addons/bluemap-web-chat` path are accepted only by the one-time migration/cleanup compatibility layer.


Fabric/NeoForge and Forge 26.1.2/26.2 use the companion Java 25 `kwc-adapter-bluemap-api` bridge. It reuses this module's asset generation but obtains the web root and JS/CSS registration from BlueMapAPI instead of editing `webapp.conf`.
