# KOKOTO WebChat 多言語ガイド

## 内蔵言語

- `en-US.yml` - 英語、既定
- `ko-KR.yml` - 韓国語
- `ja-JP.yml` - 日本語
- `zh-CN.yml` - 簡体字中国語

初回起動時に `plugins/KOKOTO-WebChat/lang/` へコピーされます。更新時には不足している内蔵キーが自動で追加されます。既に変更した値は保持されるため、古い文言が残る場合はコピー済みの lang ファイルを編集するか、削除して再生成してください。

## UI 言語

```yaml
ui:
  language: "ja-JP"
  language-fallback: "en-US"
```

`language` はサーバー既定値です。ユーザーはチャット設定からブラウザごとの言語を選択でき、その値は localStorage に保存されます。

対応値:

```text
en-US, ko-KR, ja-JP, zh-CN
```

## fallback

選択言語にキーがない場合は `ui.language-fallback`、それでもない場合は内蔵英語を使用します。

## 翻訳範囲

言語ファイルは、ウィンドウ表示、ボタン、placeholder、ログイン/連携/パスワード、アカウント/設定、固定/削除表示トグルを含む管理パネル、アップロード、メディアプレビュー/social embed ラベル、PIP、コマンドパネル、固定メッセージ、サーバー command 応答を含みます。

ゲーム側へ送るチャット形式は `config.yml` で設定します。


## システムメッセージ

組み込みのサーバー announcement と Web コマンド結果メッセージは i18n キー付きで送信されます。Web UI は、言語ファイルに該当キーがある場合、閲覧者が選択した言語で表示します。`config.yml` の `announcements.*.message` はカスタム/フォールバック文として保持されるため、翻訳キーがない場合やサーバー固有の文言が必要な場合も利用できます。

折りたたまれた固定メッセージも、通常メッセージと同じチャットフォントとメッセージ文字サイズ設定に従います。

## 新しい言語を追加する

1. `plugins/KOKOTO-WebChat/lang/en-US.yml` をコピーします。
2. 例: `fr-FR.yml` のように名前を変更します。
3. `web:` 以下の文字列を翻訳します。
4. `ui.language` を `.yml` を除いた新しいファイル名に設定します。
5. `/kchat reload` を実行し、ページを再読み込みします。
