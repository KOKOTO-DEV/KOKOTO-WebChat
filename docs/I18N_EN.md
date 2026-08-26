# KOKOTO WebChat internationalization guide

## Built-in languages

* `en-US.yml` - English, default
* `ko-KR.yml` - Korean
* `ja-JP.yml` - Japanese
* `zh-CN.yml` - Simplified Chinese

Language files are copied to `plugins/KOKOTO-WebChat/lang/` on first run. During later updates, missing built-in keys are merged automatically. Existing customized values are kept, so old text may remain until you edit the copied lang file or delete it and let the plugin regenerate it.

## UI language settings

```yaml
ui:
  language: "en-US"
  language-fallback: "en-US"
```

`language` is the server default. Users can still choose a per-browser language from Chat settings; that choice is stored in localStorage.

Supported values:

```text
en-US, ko-KR, ja-JP, zh-CN
```

## Fallback behavior

If a selected language misses a key, `ui.language-fallback` is used. If the fallback also misses it, the built-in English default is used.

## Translation coverage

Language files cover most web UI strings:

* window title and status
* buttons and placeholders
* login/link/password modals
* account and user preference modals
* admin/moderator panel, including the pin/delete action toggle
* upload progress/cancel/error labels
* media preview and social embed labels
* PIP unsupported/open-failed messages
* command panel and command responses
* pinned message UI

Game-side chat formats are configured in `config.yml`, not in language files.

```yaml
chat:
  web-user-to-game-format: "\[Web] {player}: {message}"
  web-guest-to-game-format: "\[Web Guest] {guest}: {message}"
```


## System messages

Built-in server announcements and web command result messages are sent with an i18n key. The web UI displays them in the viewer's selected language when the key exists in the language file. The `announcements.*.message` text in `config.yml` is still used as custom/fallback text, so server-specific wording is preserved even when a translation key is missing.

Collapsed pinned messages use the same configured chat font and message font size as normal messages.

## Add a new language

1. Copy `plugins/KOKOTO-WebChat/lang/en-US.yml`.
2. Rename it, for example `fr-FR.yml`.
3. Translate strings under `web:`.
4. Set `ui.language` to the new file name without `.yml`.
5. Run `/kchat reload` and refresh the page.

