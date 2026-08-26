# Pl3xMap integration

KOKOTO WebChat can embed the same web-chat frontend into Pl3xMap's static website.

## Supported Pl3xMap platforms

Current Pl3xMap v3 lists CraftBukkit/Spigot/Paper/Purpur and Fabric as its supported platforms, and current 26.2 releases are published for Bukkit/Paper-family and Fabric/Quilt. KWC therefore enables this integration on Bukkit-family and Fabric. NeoForge is not currently advertised by Pl3xMap as a supported target, so KWC does not claim that combination even though the loader-neutral filesystem adapter code is shared.

## Configuration

```yaml
adapters:
  pl3xmap:
    enabled: true
    auto-install: true
    auto-patch-index: true
    api-base-url: ""
    web-root: ""
    addon-path: "kokoto-web-chat"
```

KWC reads `settings.web-directory.path` from common Pl3xMap `config.yml` locations. Current Pl3xMap v3 stores its main settings in `config.yml`; KWC also accepts `settings.yml` as a fallback for older/forked layouts. Relative web-directory paths are resolved against the Pl3xMap data directory. The normal web directory is `web/`. KWC never rewrites Pl3xMap's configuration file.

KWC owns only a marked block in `index.html` and the configured addon directory. If Pl3xMap rewrites its generated site while `settings.web-directory.read-only: false`, run `/kchat reload` (or restart KWC/server) to restore the KWC block.

## Direct HTTP / IP operation

With Pl3xMap on its default web port `25576` and KWC on `8899`:

- Pl3xMap: `http://SERVER_IP:25576/`
- KWC API: `http://SERVER_IP:8899/api`

Leave `api-base-url: ""` for the normal same-host/direct-port setup. If router/NAT port forwarding changes the public KWC port, set the actual public API URL explicitly, for example `http://PUBLIC_IP:8900/api`.

## HTTPS reverse proxy

Under HTTPS, an empty adapter API URL follows KWC's canonical `http.public-prefix + http.path-prefix` public API path.
