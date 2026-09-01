# uNmINeD integration

KOKOTO WebChat can embed the shared KWC chat overlay into an existing uNmINeD static web export.

uNmINeD is an external map generator rather than a Minecraft server plugin. KWC therefore does not call a uNmINeD API or depend on uNmINeD classes. The integration is filesystem-only and is available in Bukkit/Paper/Spigot, Fabric, NeoForge, and Forge builds.

## Enable

```yaml
adapters:
  unmined:
    enabled: true
    auto-install: true
    auto-patch-index: true
    api-base-url: ""
    web-root: "/srv/www/unmined"
    addon-path: "kokoto-web-chat"
```

uNmINeD can export to an arbitrary directory, so an explicit `web-root` is recommended. With an empty `web-root`, KWC checks only a small set of conventional local directories and still requires positive uNmINeD markers before changing anything.

## Entry-point detection

Current uNmINeD web exports use `index.html`. KWC also accepts the legacy `unmined.index.html` name used by older exports. A file is considered a uNmINeD entry point only when it contains uNmINeD-specific markers such as `unmined.map.properties.js` / `UnminedMapProperties` together with the uNmINeD runtime (`unmined.js`, `new Unmined(...)`, or equivalent generated code).

A plain OpenLayers site, plain Dynmap/LiveAtlas page, or unrelated `index.html` is not modified merely because `web-root` points to it.

## Files owned by KWC

KWC writes only:

- the configured `kokoto-web-chat/` asset directory;
- a `<!-- KWC unmined adapter:start -->` / `end` block in the detected HTML entry point.

Map tiles, uNmINeD metadata, OpenLayers libraries, player markers, custom markers, and other export files are left unchanged.

## Re-export behavior

A new uNmINeD web export may replace the HTML entry point or remove/recreate the export directory. Run `/kchat reload` (or restart KWC/server) after re-exporting to reinstall KWC assets and the marked block. Repeated reloads are idempotent and do not add duplicate blocks.

Setting `adapters.unmined.enabled: false` and reloading removes only the KWC marked block and configured KWC addon directory from a still-detectable/previously-owned export root.

## External web servers

If Caddy/nginx serves a uNmINeD export from another directory or host, `web-root` must point to the actual server-visible shared/mounted filesystem directory. KWC cannot patch a remote copy that is not mounted into the Minecraft server filesystem.

`api-base-url: ""` follows the same KWC browser-resolution policy as the other static map adapters: direct HTTP uses the configured KWC `http.port`, while HTTPS uses the current origin plus `http.public-prefix + http.path-prefix`. If NAT exposes KWC on a different public port or host, set the actual public API URL explicitly.

## uNmINeD versions

uNmINeD 0.19.40-dev renamed the generated web entry point from `unmined.index.html` to `index.html`; KWC supports both names. The adapter deliberately relies on generated-file markers rather than a uNmINeD version number so compatible newer static exports can continue to work without a server-side dependency.
