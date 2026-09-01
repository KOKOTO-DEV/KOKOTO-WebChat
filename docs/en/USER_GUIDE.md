# KOKOTO WebChat 5.1.0 — User Guide

This guide covers everyday use. For server deployment and maintenance see `INSTALLATION_OPERATIONS.md`; for implementation details see `TECHNICAL_REFERENCE.md`; the long-form reference is `USER_MANUAL.md`.

## Public chat
Use the normal chat view for game ↔ web conversation. Replies, pins, search, uploads, custom emoji, rich previews and the latest-message control work in the same timeline. Sender-name actions and message-body reply actions are separate so URLs keep their normal open-link behavior.

## Accounts and guests
Linked Minecraft accounts retain their server identity and can synchronize supported chat/notification preferences. Guest access is available only when enabled by the administrator and remains subject to name, CAPTCHA, session and moderation rules.

## Direct messages and groups

![Private Reply validation and cross-server identity](../assets/private-reply-flow.svg)

[PNG](../assets/private-reply-flow.png) · [SVG](../assets/private-reply-flow.svg)

Open 1:1 DMs from the recipient UI or `/kchat dm`. Group rooms use the group interface or `/kchat group`. In Minecraft, clicking a DM/group conversation label prepares the corresponding command; clicking a private message body prepares a reply. The server always re-validates participation/membership before delivery.

## Replies, emoji and media
Replies preserve the complete source text. Registered custom emoji render inside reply previews without turning nested content into links. The emoji picker inserts the exact token at the caret without adding spaces. Configured newline aliases use compact rows for consecutive emoji-only lines while explicit blank lines remain normal blank lines.

## Notifications and appearance
Desktop notifications, Web Push, keyword preferences, account UI profiles, theme/font/text-shadow controls and PIP are controlled from the web settings exposed by the server. Device-local window state and Push endpoints remain local to that device.

## If something looks wrong
Refresh the web page after a server update, then check the server's current URL and login state. Administrators should use the installation/operations guide and `Troubleshooting.md` wiki page before changing data files manually.

## References

For implementation/security details and external standards, see [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md) and [REFERENCES.md](REFERENCES.md).
