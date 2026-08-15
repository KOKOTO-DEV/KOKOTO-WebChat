# BlueMapWebChat 4.7.0 upgrade

4.7.0 expands the conservative Bukkit/Spigot compatibility baseline to Minecraft 1.18, adds administrator custom-emoji multi-file upload, and adds configurable colon-delimited message tokens.

## Compatibility

- Conservative supported Minecraft range: **1.18 through 26.2**
- Java requirement: **Java 17**
- `plugin.yml`: `api-version: '1.18'`
- Maven compile API: `spigot-api:1.18.2-R0.1-SNAPSHOT`
- Paper `AsyncChatEvent` remains reflection-detected; Bukkit `AsyncPlayerChatEvent` remains the hard-linked fallback.
- Minecraft 1.17 and older are not claimed by this release.

## Custom emoji multi-upload

Emoji upload now follows the same picker flow as normal chat file upload. The visible Upload button opens a hidden multi-file input. As soon as the picker returns a selection, BlueMapWebChat copies the selected files, clears the native input, and immediately starts sequential uploads. There is no second Upload confirmation step and no file-picker focus/visibility workaround. Progress and active-transfer cancel remain available. The existing server endpoint still performs per-file validation, storage accounting, unique-name allocation, audit logging, and PNG-sidecar generation.

## Message tokens

The default aliases are English-only and can be replaced or extended in any language. `:enter:`, `:newline:`, `:nextline:`, `:linebreak:`, and `:br:` insert a newline; `:blankline:`, `:emptyline:`, and `:paragraphbreak:` insert an empty line; `:tab:` and `:indent:` insert configurable spaces. Printable custom substitutions such as `:separator:` are also configurable. Unknown tokens are left unchanged so custom/image emoji tokens continue to work.

## Configuration

4.7.0 adds the `message-tokens` section. Existing setting defaults outside this new section are unchanged. The review marker changes to:

```yaml
config-version: "4.7.0"
```

On startup/reload, known top-level `config.yml` blocks are also reordered to the bundled 4.7.0 layout while each block's current text, values, and custom comments are preserved; unknown top-level blocks remain last in their original order.

A reviewed 4.6.3 configuration therefore receives the new `message-tokens` section plus the 4.7.0 review marker in `config-migration-4.7.0.yml`. The migration comparison is not limited to 4.6.3: older or unversioned configs are also checked for missing current settings. In addition, `config-reference-4.7.0.yml` is always written as the complete bundled 4.7.0 default configuration with all comments, so operators can compare any old config against one authoritative full file. Empty maps such as `message-tokens.custom: {}` are retained in migration output when missing. The migration file also ends with a comment-only line diff against the full reference. Unchanged lines are omitted; each difference shows the file name, then `Line` or `Lines` on a separate line, followed by only the differing text. Differing source lines are prefixed directly with `#` so original YAML indentation is preserved, with an insertion position for reference-only blocks.

### Game line-break behavior

Configured `newline` / `blank-line` tokens are preserved through Minecraft single-line sanitization and emitted as explicit Minecraft chat lines at final delivery. Ordinary CR/LF input is still flattened exactly as before. For server-relayed chat/DM output, the receiving BlueMapWebChat server must also run the 4.7.0 token-line delivery support; an older receiver flattens the normal relayed LF before it reaches the client.
