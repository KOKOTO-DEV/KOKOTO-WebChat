# SimpleNicks-Bero integration

KOKOTO WebChat 5.3.0 can use nicknames from [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) on **Bukkit/Paper-family** servers through Bukkit's player display name. KWC does not require SimpleNicks as a hard dependency and does not parse the plugin's database directly.

For normal installation, `/nick` commands, permissions, SQLite/MySQL setup, saved nicknames, nickname protection, PlaceholderAPI/MiniPlaceholders, and general plugin operation, use the [upstream SimpleNicks documentation](https://github.com/Simplexity-Development/SimpleNicks). This page covers only the KWC-facing settings.

## KWC setting

Use Bukkit display names as the visible KWC player name:

```yaml
player-display:
  mode: "display-name"
```

`name` always uses the real Minecraft username. `display-name` uses Bukkit's current display name, which is where SimpleNicks-Bero applies its rendered nickname. KWC keeps the linked account/UUID identity separately, so changing the visible nickname does not change the authenticated player identity.

## Typical SimpleNicks-Bero settings

The following values are examples from a multilingual KWC deployment; they are **not KWC requirements**:

```yaml
mysql:
  enabled: false
  ip: localhost:3306
  name: simplenicks
  username: username1
  password: badpassword!

max-nickname-length: 30
nickname-regex: '[A-Za-z0-9_가-힣 ぁ-ゔァ-ヴー々〆〤一-龥?!]+'

require-permission:
  nick: false
  color: false
  format: false
  who: false

tablist-nick: true
nickname-prefix: ''
```

The Unicode-capable `nickname-regex` above permits Latin, Korean, Japanese, CJK characters, spaces and the listed punctuation. Adjust it to your own moderation/compatibility policy. `tablist-nick`, MySQL, protection rules, and save limits are SimpleNicks choices and do not control KWC.

## Colors / formatting

SimpleNicks-Bero renders MiniMessage nicknames into the player's Bukkit display name. KWC's handling of Minecraft name color/format codes is still controlled by:

```yaml
player-display:
  mode: "display-name"
  strip-colors: true
```

Set `strip-colors` according to whether you want KWC's web UI to preserve supported player-name formatting. Authentication, guest-name spoof protection, relay identity and real account matching continue to use the underlying player identity rather than trusting a formatted nickname.

## Scope

This documented integration is for Bukkit/Paper-family KWC because it relies on Bukkit player display names. Fabric/NeoForge/Forge KWC builds have their own platform player abstraction and do not claim the Bukkit SimpleNicks plugin API.

## Project references

- KWC-used fork: [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero)
- Original plugin / general operation: [SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks)

## Upstream references

- [SimpleNicks upstream source](https://github.com/Simplexity-Development/SimpleNicks)
- [SimpleNicks upstream on Modrinth](https://modrinth.com/plugin/simplenicks)

These links document the upstream project. KWC integration and SimpleNicks-Bero fork behavior described here are version-specific and are not implied by the upstream documentation.
