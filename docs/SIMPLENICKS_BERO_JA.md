# SimpleNicks-Bero 連携

KOKOTO WebChat 5.0.0 は **Bukkit/Paper 系**で [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) が Bukkit player display name に設定した nickname を利用できます。KWC は SimpleNicks の database を直接読む hard dependency ではありません。

一般的な導入、`/nick` command、permission、SQLite/MySQL、保存 nickname、保護機能などは [upstream SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks) を参照してください。

## KWC setting

```yaml
player-display:
  mode: "display-name"
```

`name` は実 username、`display-name` は Bukkit の現在 display name を使用します。KWC の linked account/UUID identity は別管理のため nickname を変更しても認証 identity は変わりません。

## SimpleNicks-Bero example

```yaml
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

これは運用例であり KWC 必須設定ではありません。MySQL、protection、save 数、tablist は SimpleNicks 自体の設定です。

MiniMessage nickname の表示は `player-display.strip-colors` の KWC policy と組み合わせてください。認証や spoof protection は表示 nickname ではなく実 player identity を基準にします。

## Project links

- KWC-used fork: [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero)
- Upstream: [SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks)
