# SimpleNicks-Bero 連携

KOKOTO WebChat 5.1.0 は **Bukkit/Paper 系**で [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) が Bukkit の player display name に適用した nickname を利用できます。KWC は SimpleNicks を hard dependency とせず、plugin の database も直接読みません。

通常の導入、`/nick` command、permission、SQLite/MySQL、保存 nickname、nickname protection、PlaceholderAPI/MiniPlaceholders などの一般運用は [upstream SimpleNicks documentation](https://github.com/Simplexity-Development/SimpleNicks) を参照してください。この文書は KWC 側に関係する設定だけを扱います。

## KWC 設定

KWC に Bukkit display name を表示させます。

```yaml
player-display:
  mode: "display-name"
```

`name` は常に実際の Minecraft username を使用し、`display-name` は SimpleNicks-Bero が描画した nickname を含む現在の Bukkit display name を使用します。KWC は linked account/UUID identity を別に保持するため、表示 nickname を変更しても認証済み identity は変わりません。

## SimpleNicks-Bero の代表設定例

次は多言語 KWC 環境での例であり、**KWC の必須設定ではありません**。

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

上の Unicode 対応 `nickname-regex` は Latin、韓国語、日本語、CJK、space と記載した記号を許可します。実際の moderation/compatibility policy に合わせて調整してください。`tablist-nick`、MySQL、protection rule、保存上限は SimpleNicks 側の設定で、KWC を制御するものではありません。

## 色 / formatting

SimpleNicks-Bero は MiniMessage nickname を Bukkit display name に描画します。Minecraft の名前 color/format code を KWC がどのように扱うかは、引き続き次で制御します。

```yaml
player-display:
  mode: "display-name"
  strip-colors: true
```

KWC Web UI で対応している player-name formatting を保持したいかどうかに応じて `strip-colors` を設定してください。Authentication、guest-name spoof protection、relay identity、実アカウントの照合は formatted nickname を信用せず、基礎となる player identity を使用します。

## 対象範囲

この連携は Bukkit player display name に依存するため、Bukkit/Paper 系 KWC を対象とします。Fabric/NeoForge/Forge の KWC build はそれぞれの platform player abstraction を使用し、Bukkit SimpleNicks plugin API の対応を表明しません。

## Project links

- KWC で使用する fork: [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero)
- Original plugin / 一般運用: [SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks)

## Upstream 参照資料

- [SimpleNicks upstream source](https://github.com/Simplexity-Development/SimpleNicks)
- [SimpleNicks upstream on Modrinth](https://modrinth.com/plugin/simplenicks)

これらは upstream project の資料です。この文書に記載する KWC 連携と SimpleNicks-Bero fork の動作は version 固有であり、upstream 資料だけから保証されるものではありません。
