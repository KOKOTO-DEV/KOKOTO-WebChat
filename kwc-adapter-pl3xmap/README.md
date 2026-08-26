# KOKOTO WebChat Pl3xMap adapter

This loader-neutral module installs the shared KWC map frontend into an existing Pl3xMap static web directory. It has no compile-time dependency on Pl3xMap.

The adapter discovers `settings.web-directory.path` from current Pl3xMap `config.yml` locations, with `settings.yml` accepted only as a legacy/fork fallback. It only patches an existing `index.html` using a marked KWC block and writes KWC-owned files under the configured addon directory.

Pl3xMap may overwrite generated website files when `settings.web-directory.read-only: false`, so KWC re-checks the assets and marked block at startup and `/kchat reload`. Current Pl3xMap releases support Bukkit/Paper-family and Fabric/Quilt; KWC does not claim NeoForge Pl3xMap support because Pl3xMap does not currently advertise a NeoForge target.
