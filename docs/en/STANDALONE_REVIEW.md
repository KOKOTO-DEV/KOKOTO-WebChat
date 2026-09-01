# BlueMap dependency / standalone mode review

## Summary

The Java plugin does not depend on the BlueMap API. `plugin.yml` does not declare BlueMap as a `depend` or `softdepend`, and the Java sources do not import BlueMap classes. The runtime dependency is Bukkit/Spigot-compatible server APIs. DiscordSRV integration is optional.

The chat feature can therefore run without BlueMap. The BlueMap-specific part is the optional `kwc-adapter-bluemap` that copies embedded-addon assets into the BlueMap web directory and patches `webapp.conf`. The standalone assets are packaged separately in `kwc-standalone-frontend` and are no longer loaded from the BlueMap adapter.

## Supported modes

KOKOTO WebChat currently supports both modes:

```text
BlueMap addon panel
Standalone /chat page
```

Standalone mode is disabled by default. Enable it explicitly when needed:

```yaml
  api-base-url: ""
```

`frontend.standalone.api-base-url` normally remains empty. Direct HTTP uses the internal `http.path-prefix`; through the reverse proxy it uses `http.public-prefix + http.path-prefix`, `/chat/api` by default. Set this option only when standalone must use a different public API URL.

Direct HTTP URL:

```text
http://<server-host>:8899/
```

HTTPS reverse-proxy URL example:

```text
https://<domain>/chat
```

## Standalone-only deployment

Use this when you do not want any chat UI injected into BlueMap:

```yaml
adapters:
  bluemap:
    auto-install: false
    auto-patch-webapp-conf: false

frontend:
  standalone:
    enabled: true
    path: "/"
```



## Transparency limitation

Standalone browser windows and Document Picture-in-Picture windows cannot be made true OS-level transparent windows through normal web APIs. CSS can make the chat panel itself translucent, but the browser/PIP window background and desktop pass-through transparency are controlled by the browser or operating system.

### URL setting resolution

`frontend.standalone.api-base-url` is the standalone page's own API override and normally stays empty. It does not inherit `adapters.bluemap.api-base-url`. Empty upload/emoji settings follow the canonical public API base and append `/uploads` and `/emojis`. Absolute browser paths such as `/chat/api` are used as-is. Relative values without a leading `/` are resolved against `http.cors-origin` when it is a real origin. Full `https://...` URLs are used as-is.


