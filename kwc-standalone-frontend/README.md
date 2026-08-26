# KOKOTO WebChat Standalone Frontend

`kwc-standalone-frontend` owns the map-independent web assets served by the standalone page. The primary standalone document embeds the bootstrap bundle directly, so a reverse proxy only needs to publish the standalone page route itself; the optional internal `/chat.js` resource remains available for diagnostics but is not required for normal page startup.

It has no dependency on BlueMap or `kwc-adapter-bluemap`. Server platform modules can bundle this module together with `kwc-core` to expose the same standalone chat UI without installing any map plugin/mod.

Resources:

- `standalone/chat.js` — standalone page bootstrap and chat application bundle
- `standalone/chat.css` — matching chat stylesheet source used by the bundle

The BlueMap embedded addon keeps its own assets under `kwc-adapter-bluemap`. The two deployment modes do not resolve frontend resources from each other.
