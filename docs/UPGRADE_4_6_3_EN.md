# Upgrade to KOKOTO WebChat 4.6.3

4.6.3 adds optional read-only administrator access to group-chat message bodies. It does not change DM audit behavior.

## New configuration

```yaml
group-chat:
  admin-audit:
    enabled: false

config-version: "4.6.3"
```

For a config already marked `4.6.2`, the migration fragment contains this new switch plus the 4.6.3 review marker unless other settings are actually missing. Existing `config.yml` is not overwritten.

## Access requirements

Both are required:

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

group-chat:
  admin-audit:
    enabled: true
```

Ordinary ADMIN/MODERATOR roles are not sufficient. The audit view is read-only, does not require or create room membership, does not mark messages read or change unread counts, and cannot send/upload/hide messages or modify membership. Each page read is logged as `admin.group-audit-read` without copying message bodies into the audit log.

## Configuration comments

4.6.3 refreshes the bundled `config.yml` comments so they describe the current update checker, cross-server DM relay/read acknowledgements, group read status, and both administrator audit switches. On startup or `/kchat reload`, an existing config may have comment text refreshed **only when that comment block still exactly matches an older bundled KOKOTO WebChat comment**. Setting values are never changed by this comment refresh, custom comments are preserved, and `config-version` is still changed only by the administrator after reviewing the migration fragment.
## Private-chat media playback

DM and group-chat message/status refreshes now reuse the existing native video/audio element instead of destroying and recreating it. Active playback therefore continues from the same position without a second `play()` call or media reload. Leaving or switching a private conversation now destroys that conversation's message/media DOM and clears its private media-open state. Re-entering starts from the unopened click-to-load card again (or a newly created non-playing media element when click-to-load is disabled); no previous media DOM is reused and no automatic `play()` occurs.

