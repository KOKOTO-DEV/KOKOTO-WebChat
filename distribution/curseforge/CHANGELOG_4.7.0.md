# KOKOTO WebChat 4.7.0

## Custom emoji multi-upload

- Administrator custom-emoji upload now follows the same picker path as normal chat file upload.
- The Upload control opens a hidden multi-file input; selecting files immediately starts sequential upload.
- There is no second confirmation Upload step and no file-picker focus/visibility workaround.
- Progress and active-transfer cancel remain available.
- Existing server-side per-file validation, storage accounting, unique-name handling, audit logging, and PNG-sidecar generation remain unchanged.

## Compatibility

- Lowered the declared Bukkit/Spigot API baseline from Minecraft 1.21 to **1.18**.
- Java requirement remains **Java 17**.
- Maven builds against `spigot-api:1.18.2-R0.1-SNAPSHOT`.
- Added configurable `:token:` message substitutions with English defaults for newline, blank-line, and indentation actions; aliases can be localized by administrators, and printable custom substitutions are supported. Unknown tokens remain untouched for emoji compatibility.
- Configured newline/blank-line tokens are delivered to Minecraft as explicit chat lines after the existing ordinary CR/LF flattening step. Relayed game output requires the receiving server to run the same 4.7.0 token-line delivery support.
- Conservative supported Minecraft range: **1.18 through 26.2**.
- Paper `AsyncChatEvent` remains reflection-detected with Bukkit `AsyncPlayerChatEvent` as the hard-linked fallback.
- Minecraft 1.17 and older are intentionally not claimed by 4.7.0.

## Configuration

4.7.0 adds the `message-tokens` section. Existing defaults outside this new section are unchanged. The configuration review marker becomes:

```yaml
config-version: "4.7.0"
```

Existing setting values are not overwritten. On startup/reload, known top-level config blocks are reordered to the bundled 4.7.0 layout while each block's current text, values, and custom comments are preserved; unknown top-level blocks remain last in their original order. KOKOTO WebChat also writes `config-reference-4.7.0.yml` as the complete bundled 4.7.0 default config with all comments, regardless of how old the installed config is. When migration review is required, `config-migration-4.7.0.yml` lists the concise missing/changed settings and review marker, and empty maps such as `message-tokens.custom: {}` are preserved when missing. Its bottom section also includes a comment-only current-vs-reference text diff that omits unchanged lines, separates `Line`/`Lines` metadata from the differing text, prefixes each differing source line directly with `#` to preserve original YAML indentation, and shows insertion locations for reference-only blocks.

Bundled comment refresh is idempotent in 4.7.0. Repeated startup/reload no longer duplicates the `Public-relay delivery destinations` comment, and exact duplicates left by an earlier refresh are reduced to one automatically.
