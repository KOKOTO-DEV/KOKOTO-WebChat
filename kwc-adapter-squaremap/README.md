# KOKOTO WebChat squaremap adapter

This module installs the shared KWC map frontend into an existing squaremap web directory and patches squaremap's generated `index.html` with a small marked KWC asset block.

It intentionally has no compile-time dependency on squaremap. The adapter discovers the configured `settings.web-directory.path` from common squaremap config locations and only patches an existing `index.html`. This keeps the integration usable from Bukkit/Paper and Fabric without coupling the KWC core to a squaremap platform artifact.

squaremap can refresh its web directory when `settings.web-directory.auto-update` is enabled, so KWC re-checks the assets and marked block at server startup and `/kchat reload`.
