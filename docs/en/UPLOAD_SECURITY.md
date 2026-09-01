# KOKOTO WebChat Upload Security Notes


![Upload security pipeline](../assets/upload-security-pipeline.svg)

[PNG](../assets/upload-security-pipeline.png) · [SVG](../assets/upload-security-pipeline.svg)

File upload is convenient but can be abused on public servers. The default configuration keeps guest uploads disabled.

## Recommended defaults

```yaml
upload:
  enabled: true
  allow-guest-upload: false
  allow-user-upload: true
  allow-moderator-upload: true
  allow-admin-upload: true
  max-file-size-mb: 20
  max-files-per-message: 3
  cooldown-seconds: 5
  max-uploads-per-minute: 4
  retention-days: 5
```

## Public servers

For public servers, keep guest uploads disabled unless you have a separate moderation process. If guest uploads are enabled, lower file size limits and use a short retention period.

## Public URL

Direct HTTP example:

```yaml
upload:
  public-base-url: ""
```

Same-domain HTTPS reverse proxy example:

```yaml
http:
  path-prefix: "/api"
  public-prefix: "/chat"

upload:
  # Recommended: leave empty so uploads follow /chat/api automatically.
  public-base-url: ""

emoji:
  # Recommended: leave empty so emoji files follow /chat/api automatically.
  public-base-url: ""
```

Explicit overrides, when intentionally needed:

```yaml
upload:
  public-base-url: "/chat/api"        # plugin appends /uploads
  # or: "/chat/api/uploads"

emoji:
  public-base-url: "/chat/api"        # plugin appends /emojis
  # or: "/chat/api/emojis"
```

A leading slash is treated as a same-origin browser path. You do not need to use a full FQDN for same-domain proxy deployments.

If the upload endpoint uses the same reverse-proxied API path as the BlueMap addon, keep `upload.public-base-url` empty or set it to the shared API base such as `/chat/api`. The legacy full resource path `/chat/api/uploads` is still accepted.

For custom emoji files, the same rule applies: keep `emoji.public-base-url` empty, or use `/chat/api` or `/chat/api/emojis` for legacy/custom deployments.


## Allowed extensions

Only allow file types you actually want to display or share in chat.

```yaml
upload:
  allowed-extensions:
    - png
    - jpg
    - jpeg
    - gif
    - webp
    - mp4
    - webm
    - mp3
    - m4a
    - ogg
    - wav
    - flac
```

KOKOTO WebChat limits by extension and size, but it is still recommended to serve uploads from a constrained directory and use HTTPS for public deployment. `upload.max-total-size-mb` can additionally cap the total size of regular files stored directly in `upload.directory`; `0` disables the quota. When enabled, the server deletes the oldest unreferenced uploads first, and rejects the new upload if protected/referenced files still keep the folder over the limit.

Archive formats such as `zip` are not included in the default public-server example. Add them manually only when you intentionally want general file sharing.

### URL setting resolution

`http.public-prefix + http.path-prefix` is the canonical HTTPS public API path (`/chat/api` by default). Adapter and standalone API overrides normally stay empty and are independent. Empty upload/emoji values follow the canonical API base and append `/uploads` and `/emojis`.


Admin custom emoji manager note: renaming an emoji file or folder changes the `:emoji:pack/name:` token. Existing chat messages that reference the old token may no longer render unless the old file/folder name is kept.

Custom emoji uploads also honor `emoji.max-total-size-mb`. If the total storage limit is exceeded, the admin UI shows a warning. `emoji.show-storage-usage` and `emoji.show-storage-limit` control whether the current and maximum total emoji storage values are displayed.
