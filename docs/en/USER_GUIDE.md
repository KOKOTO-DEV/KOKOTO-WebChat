# KOKOTO WebChat 5.3.1 — User Guide

For reaction authority, direct/multi-hop delivery, offline outbox behavior and notification diagrams, see [REACTIONS.md](REACTIONS.md).

This guide covers everyday use. For server deployment and maintenance see `INSTALLATION_OPERATIONS.md`; for implementation details see `TECHNICAL_REFERENCE.md`; the long-form reference is `USER_MANUAL.md`.

## Public chat
![Public message lifecycle](../assets/public-message-flow.svg)

Use the normal chat view for game ↔ web conversation. Replies, pins, search, uploads, custom emoji, rich previews and the latest-message control work in the same timeline. Sender-name actions and message-body reply actions are separate so URLs keep their normal open-link behavior.

## Accounts and guests
Linked Minecraft accounts retain their server identity and can synchronize supported chat/notification preferences. Guest access is available only when enabled by the administrator and remains subject to name, CAPTCHA, session and moderation rules.

## Direct messages and groups

![Private Reply validation and cross-server identity](../assets/private-reply-flow.svg)

![Private conversation message/read/typing/relay flow](../assets/private-conversation-flow.svg)

[PNG](../assets/private-reply-flow.png) · [SVG](../assets/private-reply-flow.svg)

Open 1:1 DMs from the recipient UI or `/kchat dm`. Group rooms use the group interface or `/kchat group`. In Minecraft, clicking a DM/group conversation label prepares the corresponding command; clicking a private message body prepares a reply. The server always re-validates participation/membership before delivery.


Signed-in users can react to public, DM, and normal group-chat messages with Unicode or KWC custom emoji. Group join/leave event rows are intentionally not reaction targets. When a message has no reactions, the 32 × 16 px hover `+` is placed between messages with 1 px visual clearance above and below; this avoids covering either text line without reserving the normal 22 px reaction row. When reactions are disabled, the empty affordance is omitted and the original 8 px message spacing is used. Once a real reaction exists, the full reaction row/chip spacing is used. The picker searches by emoji character, server-generated Unicode name, administrator-managed aliases, or custom-emoji ID/name/pack; aliases are edited under **Admin > Emojis > Reaction icons** using `emoji = search words` lines and persist to `reaction-search-aliases.txt`. Search aliases are used only for reaction-picker search and do not transform chat input. It keeps its original screen position when categories change and still closes when the user clicks outside after any category/search rerender. Reaction hover shows all reactor names in a four-line scrollable list; reactor names use the same display-name/original-name toggle as chat senders. Administration is only under **Admin > Emojis > Reaction icons**; there is no management button in the picker. The administrator can disable the reaction feature globally or separately disallow KWC custom emoji. Disabling reactions preserves existing stored reactions and displays them read-only while blocking all local/Relay reaction mutations, including removals. Public/DM/group typing indicators are short-lived five-second hints; the server administrator controls Open chat, DM, and Group chat separately in Web Admin **Settings** or `chat.typing-indicator.*` (defaults OFF/ON/ON). `chat.typing-indicator.user-display-control` is OFF by default. If the administrator enables it, signed-in users get one account-stored **Typing indicators** checkbox in Chat settings; turning it off hides incoming typing hints on that user’s own screen across the enabled scopes but does not stop that user’s own typing activity from being sent. Typing state is not message history, and your own typing is never shown back to you. The rounded indicator floats above the composer, follows the display-name/original-name toggle, strips formatting tags from shown names, and uses about 80% of the user chat font with the base UI font as its minimum. In group chat, multiple long names automatically collapse to a participant count when they do not fit on one line.

![Saved-conversation lifecycle](../assets/archive-lifecycle.svg)

For DM/group rooms, open **Settings** for Invite (when permitted), **Save conversation**, Saved conversations, hide, and room-management actions. Saved-conversation controls are present only when the server keeps `chat.conversation-archive.enabled: true` (default); disabling it removes the controls from the DOM and disables the archive API. Room-management controls are visible only to users with the room's management permission; Leave remains in the title bar. To save a conversation, select its first and last message. The saved snapshot is personal, but administrator deletion/lock policy takes precedence. PDF export uses your current KWC appearance; images are included only while the original still exists, other files stay as links, and missing originals are marked unavailable.

The latest-message auto-follow boundary in public, DM and group chat is measured from the active rendered line height: KWC follows the newest message only while the remaining bottom gap is less than two text lines. Opening emoji/icon/attachment panels preserves the current viewport and does not by itself force the view back to the bottom. The saved conversation view is restored after refresh/reconnect when possible.

## Replies, emoji and media
Replies preserve the complete source text. Registered custom emoji render inside reply previews without turning nested content into links. The emoji picker inserts the exact token at the caret without adding spaces. Configured newline aliases use compact rows for consecutive emoji-only lines while explicit blank lines remain normal blank lines.

## Notifications and appearance
Desktop notifications, Web Push, keyword preferences, account UI profiles, theme/font/text-shadow controls and PIP are controlled from the web settings exposed by the server. Device-local window state and Push endpoints remain local to each device, but private-room attention is account-wide. If any signed-in KWC browser for the account is visibly and actively focused on the exact DM thread or group room—with the private-chat modal open and the chat window not minimized—that conversation's live browser notification, browser-local notification-inbox entry, and Web Push are suppressed across the account. The viewing browser does not need its own Push subscription. A different room, closed modal, hidden/unfocused page, or minimized chat is treated as not viewing and normal notifications resume.

## If something looks wrong
Refresh the web page after a server update, then check the server's current URL and login state. Administrators should use the installation/operations guide and `Troubleshooting.md` wiki page before changing data files manually.

## References

For implementation/security details and external standards, see [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md) and [REFERENCES.md](REFERENCES.md).
