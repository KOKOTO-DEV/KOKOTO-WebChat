# Minecraft Overviewer integration

KOKOTO WebChat can embed its shared chat overlay into an existing Minecraft Overviewer static web-map output directory.

Overviewer is an external command-line renderer rather than a Minecraft server plugin. KWC therefore does not call an Overviewer API or depend on Overviewer classes. The integration is filesystem-only and is available in Bukkit/Paper/Spigot, Fabric, NeoForge, and Forge builds.

## Enable

```yaml
adapters:
  overviewer:
    enabled: true
    auto-install: true
    auto-patch-index: true
    api-base-url: ""
    web-root: "/srv/www/overviewer"
    addon-path: "kokoto-web-chat"
```

Overviewer's `outputdir` can be anywhere, so an explicit `web-root` is recommended. With an empty `web-root`, KWC checks only a small set of conventional local directories and still requires positive Overviewer markers before modifying anything.

## Entry-point detection

Overviewer generates `index.html` and normally references `overviewerConfig.js`, `overviewer.js`, and `overviewer.css`; canonical templates also contain a `Minecraft-Overviewer` generator meta tag. KWC uses those Overviewer-specific markers/assets to distinguish an Overviewer output from an unrelated Leaflet site.

A plain Leaflet site, plain Dynmap/LiveAtlas page, uNmINeD export, or unrelated `index.html` is not modified merely because `web-root` points to it.

## Files owned by KWC

KWC writes only:

- the configured `kokoto-web-chat/` asset directory;
- a `<!-- KWC overviewer adapter:start -->` / `end` block in Overviewer's `index.html`.

Overviewer tiles, `overviewerConfig.js`, `overviewer.js`, `overviewer.css`, Leaflet assets, marker data, and other generated files are left unchanged.

## Re-render behavior

Overviewer may regenerate `index.html` during a render or when `--update-web-assets` is used. Run `/kchat reload` (or restart KWC/server) afterward to reinstall KWC assets and the marked block. Repeated reloads are idempotent and do not add duplicate blocks.

Setting `adapters.overviewer.enabled: false` and reloading removes only the KWC marked block and configured KWC addon directory from a still-detectable/previously-owned Overviewer root.

## `customwebassets`

Overviewer supports `customwebassets` in its Python configuration. Files from that directory can replace default web assets when Overviewer renders. KWC Stage 1 intentionally does not rewrite Overviewer's Python configuration or manage the operator's custom template. You can keep using `customwebassets`; after Overviewer produces the final site, `/kchat reload` ensures the KWC block/assets are present.

## External web servers

If Caddy/nginx serves an Overviewer output from another directory or host, `web-root` must point to the actual server-visible shared/mounted filesystem directory. KWC cannot patch a remote copy that is not mounted into the Minecraft server filesystem.

`api-base-url: ""` follows the same KWC browser-resolution policy as the other static map adapters: direct HTTP uses the configured KWC `http.port`, while HTTPS uses the current origin plus `http.public-prefix + http.path-prefix`. If NAT exposes KWC on a different public port or host, set the actual public API URL explicitly.

## Compatibility boundary

The original Minecraft Overviewer project is no longer actively maintained. KWC does not key compatibility to an Overviewer version number; it keys compatibility to the generated web-map structure. Original or successor/fork builds that retain the standard Overviewer generator/assets can therefore be detected without a server-side dependency, while incompatible layouts are left untouched rather than patched speculatively.
