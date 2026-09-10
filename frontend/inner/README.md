# KWC inner frontend source layout

`inner.js` is the generated runtime bundle used by the KWC frontend wrappers. Do not treat the root bundle as the primary editing surface.

The maintainable source is split into ordered fragments listed in `manifest.txt`. The fragments intentionally share one IIFE lexical scope after concatenation; this avoids changing runtime semantics, Document PiP self-source behavior, or the eight wrapper bootstrap paths while still separating the 20k-line frontend by responsibility.

## Source areas

- `00-bootstrap-state.js` — bootstrap, runtime mode, shared state
- `10-text-media-api.js` — text rendering, links/media, API/error helpers
- `20-emoji-reactions.js` — custom emoji core and reactions
- `30-reply-identity-frame.js` — replies, identities, locale/time, frame/resize/PiP
- `40-root-auth.js` — root UI creation and auth/guest state
- `50-public-history-virtual-scroll.js` — public history paging and virtual scrolling
- `60-theme-config-stream.js` — theme/font config, history loading, SSE lifecycle
- `70-emoji-upload-compose.js` — emoji UI, upload/drop/paste, commands and public compose
- `80-pins-admin.js` — public/group pins and administrator UI
- `90-notifications.js` — notification inbox, browser notifications and Web Push
- `100-preferences-search.js` — chat profiles/preferences and public/private search UI
- `110-auth-dm-core.js` — login and direct-message rendering/core paging
- `120-dm-ui-typing.js` — DM interaction/UI, uploads and typing indicators
- `130-group-rooms-archive.js` — group room list/opening and conversation archive
- `140-group-management.js` — group management, group compose/delete and modal wiring
- `150-startup.js` — account bootstrap and startup entrypoint

## Regenerate / verify

From the repository root:

```text
node tools/build-inner-bundle.js --write
node tools/build-inner-bundle.js --check
```

`--write` regenerates root `inner.js` and replaces only `KWC_EMBEDDED_INNER_TEXT` in all eight wrapper `chat.js` files. `--check` fails if the manifest fragments, generated `inner.js`, or any wrapper payload diverge.

The root `inner.js` remains checked in as a generated compatibility artifact so source packages and existing validation/deployment paths do not need a Node step during normal Maven/Gradle release builds.
