# Server-to-server public chat relay

`server-relay` connects the public chat of multiple KOKOTO WebChat servers. Game, linked web-user, and guest messages can be delivered to the remote server's web chat and Minecraft chat while preserving message IDs, replies, sender identity, and the originating server.

`peers` does not establish a persistent or reciprocal server session. Each entry's `url` is an **HTTP destination this server sends relay messages to**. The same entry's `id`/`secret` is also used to authenticate relay requests received from that server. For two-way relay, configure matching entries on both servers.

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
  forward-received-public-chat: true
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
      url: "https://server3.example.com/chat/api"
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
      url: "https://server1.example.com/chat/api"
      secret: ""
      enabled: true
```

Each peer must be reciprocal: the receiving server must list the sender's exact `server-id`. IDs are case-sensitive after normalization and must be unique. Do not use the same ID for two servers.

## HTTPS and reverse proxies

`url` is the other server's externally reachable KWC API base. KOKOTO WebChat appends `/relay/receive` automatically:

```text
Configured: https://server3.example.com/chat/api
Requested:  https://server3.example.com/chat/api/relay/receive
```

The public HTTPS route must proxy the whole KWC API path to the internal KWC HTTP listener, including POST requests to `/relay/receive`. Do not expose port 8899 publicly when HTTPS already fronts the service. The proxy must preserve these request headers:

```text
X-BMWC-Relay-Version
X-BMWC-Relay-From
X-BMWC-Relay-Timestamp
X-BMWC-Relay-Signature
```

A publicly trusted certificate works with Java normally. A private/self-signed certificate must be imported into the Java trust store or the HTTPS request will fail before reaching KWC.

## Secrets

- `shared-secret` is the default key for every peer.
- `peers[].secret` overrides the shared key for that one connection.
- With two servers, use the same long random `shared-secret` on both servers and leave each peer `secret: ""`.
- With per-peer keys, the two reciprocal entries must use the same pair-specific key.
- If neither a peer secret nor a shared secret is available, the peer is ignored.

## Topology

For three or more servers, use either:

- Full mesh: every server lists every other server. This is simplest and most resilient.
- Hub: leaf servers list a hub and the hub lists every leaf. With `forward-received-public-chat: true`, the hub forwards received public messages to the remaining peers. Set it to `false` to keep public chat limited to directly configured peer links.

`forward-received-public-chat` affects public relay only; cross-server DM routing/read receipts can still use multiple hops. Relay IDs, origin suppression, immediate-sender exclusion, and `max-hops` prevent loops in cyclic topologies. There is no persistent offline queue; a message is not replayed later when a peer was unreachable.

## Reload behavior and diagnostics

`/kchat reload` closes the previous relay instance and creates a new one from the current config. Relay uses one HTTPS request per message, not a permanent connection, so there is no separate reconnect operation.

A healthy startup log looks like:

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

If `activePeers` is lower than the configured count, nearby warnings explain which peer was rejected and why. Typical causes are duplicate IDs, a peer ID equal to the local server ID, an empty/invalid URL, an unsupported URL scheme, or a missing secret.

## HTTP errors

- `403 unknown_peer`: the receiving server does not have the sender's exact `server-id` in its active peers. Check both directions and the `activePeers` log on the receiver.
- `404 relay_disabled`: the receiving server has `server-relay.enabled: false`. After the first such response, the sender immediately suppresses further outbound requests to that peer and sends no periodic probe. If relay is later enabled on the receiver, reload or restart KWC on the sender to retry that peer.

`403 unknown_peer` is rejected before the receiver stores or publishes the direct request. After 3 `unknown_peer` responses without an intervening success, the sender places that destination in a 60-second backoff. Messages for that destination are skipped during the interval and no periodic probe is sent. The first actual relay message after the interval retries automatically; another failure starts a new 60-second interval from that failure, while a successful response immediately clears the counter and backoff. If the same message is visible through another configured hub, that is a separate forwarded delivery and does not mean the rejected direct request was accepted. Connection-refused, timeout, and similar transport failures use the same 3-strike/60-second backoff so an offline peer does not produce a warning for every forwarded message.
- `401 bad_signature`: the effective secrets differ or a proxy altered the body/headers.
- `401 expired_request`: server clocks differ by more than `max-clock-skew-seconds`.
- `404 relay_disabled`: relay is disabled on the receiver, or the proxy routes to the wrong KWC instance/path.
- `426 unsupported_protocol`: the two plugin builds use incompatible relay protocol versions.

After editing either side, run `/kchat reload` on that side. When a receiver's peer list or secret changes, reload the receiver as well.

## Display behavior

- Web chat shows a colored badge derived from `originServerId`; the same server keeps the same color.
- The web UI omits the current server badge and shows stable colored badges only for remote servers. Web-to-game output omits the current server label; if a remote message uses an older format without `{server}` or `{server_id}`, `[server-name]` is prepended automatically. Discord keeps the server label because it is a shared external channel.
- Discord direct relay formats support `{server}` and `{server_id}` and receive an automatic prefix when missing. In a shared channel, only the origin server that observed the local Minecraft chat may enhance DiscordSRV's native game relay; peers do not prepend their own labels or append duplicate emoji links. Receiving peers do not re-send relayed messages to Discord, so there is no relay-only Discord fallback.
- `sources.discord` and `sources.system` are disabled by default to avoid DiscordSRV loops and noisy cross-server event duplication.
