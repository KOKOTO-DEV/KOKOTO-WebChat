# ImageEmojis-Bero integration (1.9.x)

KOKOTO WebChat 5.2.0 includes an optional **Bukkit/Paper-family** integration for [ImageEmojis-Bero](https://github.com/KOKOTO-DEV/ImageEmojis-Bero). The current compatibility path is tested against the 1.9.x Bero line (including 1.9.2) and is reflection-based, so ImageEmojis-Bero is not a hard dependency and KWC still starts when it is absent.

For general installation, commands, permissions, resource-pack generation, and ImageEmojis operation, use the [upstream ImageEmojis documentation](https://github.com/MrQuackDuck/ImageEmojis). This page only documents the settings and deployment details that matter when ImageEmojis-Bero is used together with KWC.

## Recommended KWC + ImageEmojis-Bero settings

A typical integration uses the KWC emoji directory as ImageEmojis-Bero's source directory:

```yaml
# plugins/ImageEmojis-Bero/config.yml
# Public host name or public IP that Minecraft clients can reach.
serverIp: yourdomain

# ImageEmojis-Bero starts its resource-pack HTTP server on this port.
webServerPort: 5000

# Share the same emoji asset tree with KWC.
emojisFolder: /KOKOTO-WebChat/emojis

# Choose NONE / OPTIONAL / REQUIRED according to your server policy.
enforcementPolicy: REQUIRED

# Important when emoji tokens may appear in /msg, /tell, /kchat reply, /kchat dm, etc.
replaceInCommands: true

# Keep KWC and ImageEmojis-Bero on the same canonical token style.
templateFormat: ':<emoji>:'
```

Other ImageEmojis-Bero settings such as `replaceInAnvils`, `replaceOnSigns`, `replaceInCommandBlocks`, `replaceInBooks`, `suggestionMode`, `mergeWithServerResourcePack`, and `extendedUnicodeRange` are normal ImageEmojis operating choices rather than KWC requirements. In particular, `extendedUnicodeRange` changes ImageEmojis' generated code range and should be treated according to the plugin's own migration warning.

## Shared emoji directory

With the Bero path handling, this setting:

```yaml
emojisFolder: /KOKOTO-WebChat/emojis
```

resolves below the server `plugins` directory:

```text
plugins/KOKOTO-WebChat/emojis/<pack>/<name>.png
```

This lets KWC's web emoji catalog and ImageEmojis-Bero's generated Minecraft resource pack use the same pack/name layout. ImageEmojis-Bero consumes PNG assets for the game resource pack; KWC may also retain web-oriented source formats such as GIF/JPG/JPEG/WEBP and use PNG sidecars where required.

## Resource-pack HTTP port / firewall

`serverIp` and `webServerPort` belong to **ImageEmojis-Bero's resource-pack HTTP server**, not KWC's web server. ImageEmojis-Bero builds a client-facing resource-pack URL from that host and port, so every Minecraft client that receives the pack must be able to reach them.

For the example above:

```text
serverIp: yourdomain
webServerPort: 5000
```

players must be able to reach `yourdomain:5000` over TCP. Depending on the host, that can require:

- an OS/firewall allow rule for TCP 5000;
- router/NAT port forwarding from the public address to the Minecraft server machine;
- DNS for `yourdomain` pointing to the reachable public address.

Do **not** assume that exposing KWC's `/chat` web endpoint automatically exposes the ImageEmojis resource-pack server. They are separate HTTP services unless you deliberately route them together outside the plugins.

## KWC behavior

- KWC keeps canonical emoji token text in web/history/relay data instead of forwarding another server's private-use glyph.
- Before Bukkit/Paper-family KWC sends an interactive Minecraft chat component, it can read ImageEmojis-Bero's active runtime emoji repository and replace a known token with that receiving server's current resource-pack glyph.
- `:pack/name:` and legacy `:emoji:pack/name:` forms are recognized. With `templateFormat: ':<emoji>:'` and pack-qualified emoji names, ImageEmojis-Bero normally produces `:pack/name:`.
- Sender/reply actions, clickable URLs, and ImageEmojis glyphs can coexist in the same KWC game message.
- If runtime symbol lookup is unavailable, KWC can fall back to token/plain broadcast behavior rather than making ImageEmojis-Bero a startup dependency.

The server-side runtime-symbol integration is a **Bukkit/Paper-family integration**. KWC's Fabric/NeoForge/Forge builds do not claim the Bukkit ImageEmojis plugin API. The optional ImageEmojis client picker is a separate client-side component and does not change this server-integration boundary.

## Permissions and commands

Players normally need `imageemojis.use` to use/see ImageEmojis-Bero emojis. Keep `replaceInCommands: true` when emoji tokens may be entered in commands such as `/msg`, `/tell`, `/kchat reply`, or `/kchat dm`.

## Reload sequence

After adding/replacing emoji assets:

1. Run `/emojis reload` so ImageEmojis-Bero regenerates the resource pack.
2. Have online players run `/emojis update`, or reconnect so the current pack is downloaded.
3. Allow KWC's short runtime emoji lookup cache to refresh. A KWC reload is normally unnecessary for an emoji-file-only change.

## Multi-server relay

Relay transports canonical token text; it does not copy PNG files or resource packs between servers. Every receiving server that should render an emoji must have the corresponding pack/name asset and a compatible ImageEmojis-Bero setup. This prevents a private-use glyph generated on one server from being interpreted with another server's mapping.

## Troubleshooting

- **Web emoji works but the game shows a token:** verify the matching PNG, `imageemojis.use`, resource-pack acceptance, `/emojis reload`, and `/emojis update`/reconnect.
- **Game emoji works but the web shows a token:** verify the same pack/name exists under `plugins/KOKOTO-WebChat/emojis`.
- **Resource pack does not download:** test that the client can reach the configured `serverIp:webServerPort`; check TCP firewall/NAT rules separately from the KWC web port.
- **Emoji in `/kchat reply`, `/kchat dm`, `/msg`, or `/tell` is not replaced:** keep `replaceInCommands: true`.
- **Remote-server emoji stays text:** synchronize the corresponding asset to that receiving server; relay does not synchronize resource-pack files.

## Project references

- KWC-tested fork: [ImageEmojis-Bero](https://github.com/KOKOTO-DEV/ImageEmojis-Bero)
- Original plugin / general operation: [ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis)

## Upstream references

- [ImageEmojis upstream on Modrinth](https://modrinth.com/plugin/image-emojis)
- [ImageEmojis upstream source](https://github.com/MrQuackDuck/ImageEmojis)

These links document the upstream project. KWC token conversion, shared-directory handling and the Bero-specific integration described in this file must be verified against the installed Bero/KWC versions.
