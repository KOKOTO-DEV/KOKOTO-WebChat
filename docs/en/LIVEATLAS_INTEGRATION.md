# LiveAtlas integration

KOKOTO WebChat can embed its shared chat frontend into an existing LiveAtlas static site. LiveAtlas is a frontend, not a Minecraft server plugin, so KWC does not depend on LiveAtlas classes or on the map backend selected inside LiveAtlas. The same adapter works when LiveAtlas displays Dynmap, squaremap, Pl3xMap, Overviewer, or multiple configured servers.

## Enable

```yaml
adapters:
  liveatlas:
    enabled: true
    auto-install: true
    auto-patch-index: true
    api-base-url: ""
    web-root: ""
    addon-path: "kokoto-web-chat"
```

With an empty `web-root`, KWC checks common local map web directories but accepts a directory only when its `index.html` contains a LiveAtlas marker such as `window.liveAtlasConfig`. This prevents KWC from treating an ordinary Dynmap/squaremap/Pl3xMap frontend as LiveAtlas.

If LiveAtlas is served by Caddy/nginx from a separate directory, set `web-root` to the actual shared or mounted filesystem directory containing LiveAtlas `index.html`. KWC cannot patch files that exist only on a remote web host.

## Files owned by KWC

KWC writes only:

- the configured `kokoto-web-chat/` asset directory;
- a `<!-- KWC liveatlas adapter:start -->` / `end` block in `index.html`.

LiveAtlas updates can replace `index.html`. After updating LiveAtlas, run `/kchat reload` or restart the server to restore the KWC block.

## API URL

`api-base-url: ""` uses the same automatic rule as the other embedded adapters: direct HTTP uses the configured KWC `http.port`, while HTTPS uses the current origin plus `http.public-prefix + http.path-prefix`. If NAT changes the externally visible KWC port, or if KWC is exposed on another host, set the actual public API URL explicitly.

## Adapter selection

LiveAtlas can itself be a replacement frontend for Dynmap/squaremap/Pl3xMap. Enable `adapters.liveatlas` for the LiveAtlas page rather than enabling a backend-specific KWC adapter against the same physical web root; otherwise two KWC blocks can be installed into one page. Separate map sites may use separate adapters at the same time.

## Platforms

The LiveAtlas adapter is filesystem-only and is included in Bukkit/Paper/Spigot, Fabric/NeoForge exact-target and Forge KWC builds. It has no compile-time dependency on Dynmap, squaremap, Pl3xMap, Overviewer, or LiveAtlas itself.

## Dynmap internal web server note

LiveAtlas must be able to load its map backend independently of KWC. With Dynmap's built-in Jetty web server, a working backend commonly exposes JSON at `/up/configuration` and world updates under `/up/world/...`. Some Dynmap layouts do not provide `standalone/config.js`; its absence alone does not mean Dynmap is broken. If LiveAtlas itself does not finish loading the map, verify the LiveAtlas/Dynmap backend configuration first, then enable the KWC LiveAtlas adapter.

KWC does not proxy, rewrite, or depend on LiveAtlas's Dynmap/squaremap/Pl3xMap/Overviewer backend routes. The LiveAtlas adapter only installs the KWC-owned frontend assets and marked `index.html` block described above.
