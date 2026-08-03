# Server-to-server public chat relay

`server-relay` connects the public chat of multiple BlueMapWebChat servers. Game, linked web-user, and guest messages can be delivered to the remote server's web chat and Minecraft chat while preserving message IDs, replies, sender identity, and the originating server.

## Two-server example

Server 1:

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  server-name: "Server 1"
  shared-secret: "replace-with-one-long-random-secret-used-on-both-servers"
  connect-timeout-seconds: 5
  request-timeout-seconds: 10
  max-clock-skew-seconds: 60
  dedupe-seconds: 300
  max-hops: 8
  sources:
    game: true
    web: true
    guest: true
    discord: false
    system: false
  delivery:
    web: true
    game: true
  game-format: "&8[&b{server}&8] &f{sender}&7: &f{message}"
  peers:
    - id: "server3"
      url: "https://server3.example.com/bmwc/api"
      secret: ""
      enabled: true
```

Server 3:

```yaml
server-relay:
  enabled: true
  server-id: "server3"
  server-name: "Server 3"
  shared-secret: "replace-with-one-long-random-secret-used-on-both-servers"
  sources:
    game: true
    web: true
    guest: true
    discord: false
    system: false
  delivery:
    web: true
    game: true
  game-format: "&8[&b{server}&8] &f{sender}&7: &f{message}"
  peers:
    - id: "server1"
      url: "https://server1.example.com/bmwc/api"
      secret: ""
      enabled: true
```

Each peer must be reciprocal: the receiving server must list the sender's exact `server-id`. IDs are case-sensitive after normalization and must be unique. Do not use the same ID for two servers.

## HTTPS and reverse proxies

`url` is the other server's externally reachable BMChat API base. BlueMapWebChat appends `/relay/receive` automatically:

```text
Configured: https://server3.example.com/bmwc/api
Requested:  https://server3.example.com/bmwc/api/relay/receive
```

The public HTTPS route must proxy the whole BMChat API path to the internal BMChat HTTP listener, including POST requests to `/relay/receive`. Do not expose port 8899 publicly when HTTPS already fronts the service. The proxy must preserve these request headers:

```text
X-BMWC-Relay-Version
X-BMWC-Relay-From
X-BMWC-Relay-Timestamp
X-BMWC-Relay-Signature
```

A publicly trusted certificate works with Java normally. A private/self-signed certificate must be imported into the Java trust store or the HTTPS request will fail before reaching BMChat.

## Secrets

- `shared-secret` is the default key for every peer.
- `peers[].secret` overrides the shared key for that one connection.
- With two servers, use the same long random `shared-secret` on both servers and leave each peer `secret: ""`.
- With per-peer keys, the two reciprocal entries must use the same pair-specific key.
- If neither a peer secret nor a shared secret is available, the peer is ignored.

## Topology

For three or more servers, use either:

- Full mesh: every server lists every other server. This is simplest and most resilient.
- Hub: leaf servers list a hub and the hub lists every leaf. The hub forwards messages to the remaining peers.

Relay IDs, origin suppression, immediate-sender exclusion, and `max-hops` prevent loops in cyclic topologies. There is no persistent offline queue; a message is not replayed later when a peer was unreachable.

## Reload behavior and diagnostics

`/bmchat reload` closes the previous relay instance and creates a new one from the current config. Relay uses one HTTPS request per message, not a permanent connection, so there is no separate reconnect operation.

A healthy startup log looks like:

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

If `activePeers` is lower than the configured count, nearby warnings explain which peer was rejected and why. Typical causes are duplicate IDs, a peer ID equal to the local server ID, an empty/invalid URL, an unsupported URL scheme, or a missing secret.

## HTTP errors

- `403 unknown_peer`: the receiving server does not have the sender's exact `server-id` in its active peers. Check both directions and the `activePeers` log on the receiver.
- `401 bad_signature`: the effective secrets differ or a proxy altered the body/headers.
- `401 expired_request`: server clocks differ by more than `max-clock-skew-seconds`.
- `404 relay_disabled`: relay is disabled on the receiver, or the proxy routes to the wrong BMChat instance/path.
- `426 unsupported_protocol`: the two plugin builds use incompatible relay protocol versions.

After editing either side, run `/bmchat reload` on that side. When a receiver's peer list or secret changes, reload the receiver as well.

## Display behavior

- Web chat shows a colored badge derived from `originServerId`; the same server keeps the same color.
- The web UI omits the current server badge and shows stable colored badges only for remote servers. Web-to-game output omits the current server label; if a remote message uses an older format without `{server}` or `{server_id}`, `[server-name]` is prepended automatically. Discord keeps the server label because it is a shared external channel.
- Discord direct relay formats support `{server}` and `{server_id}` and receive an automatic prefix when missing. In a shared channel, only the origin server that observed the local Minecraft chat may enhance DiscordSRV's native game relay; peers do not prepend their own labels or append duplicate emoji links. Receiving peers do not re-send relayed messages to Discord, so there is no relay-only Discord fallback.
- `sources.discord` and `sources.system` are disabled by default to avoid DiscordSRV loops and noisy cross-server event duplication.
