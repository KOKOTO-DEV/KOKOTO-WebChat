# BlueMapWebChat 4.7.0 アップグレード

4.7.0 では Bukkit/Spigot の互換基準を Minecraft 1.18 まで下げ、管理者向けカスタム絵文字の複数ファイルアップロードと設定可能なメッセージトークン置換を追加します。

## 互換性

- 保守的な対応範囲: **Minecraft 1.18 ～ 26.2**
- Java: **17**
- `plugin.yml`: `api-version: '1.18'`
- Maven compile API: `spigot-api:1.18.2-R0.1-SNAPSHOT`
- Paper `AsyncChatEvent` は reflection で検出し、Bukkit `AsyncPlayerChatEvent` をリンク済み fallback として維持します。
- 1.17 以下はこのリリースの公式対応範囲に含めません。

## カスタム絵文字の複数アップロード

絵文字アップロードは通常のチャットファイルアップロードと同じファイル選択フローを使います。Upload ボタンで非表示の multiple file input を開き、ファイル選択後すぐに選択 `FileList` を通常配列へコピーし、native input をクリアして順番にアップロードします。選択後の追加確認ボタンや file-picker 用 focus/visibility 回避処理はありません。進捗表示と実転送中のキャンセルは維持されます。既存のサーバー endpoint がファイル単位の検証、ストレージ計算、重複名処理、監査ログ、PNG sidecar 生成を担当します。

## メッセージトークン

既定 alias は英語のみで、管理者は任意の言語へ変更・追加できます。newline / blank-line / tab の固定アクションと、印字可能文字だけを使う custom 置換を設定できます。未知の `:token:` はそのまま残るため、既存のカスタム絵文字や画像絵文字と共存できます。

## 設定

4.7.0 では `message-tokens` セクションを追加します。それ以外の既存既定値は変更せず、review marker は次のように変更されます。migration 比較は 4.6.3 だけに限定されず、さらに古い config や version marker のない config も現在の 4.7.0 設定に対して不足項目を確認します。また `config-reference-4.7.0.yml` を常に生成し、現在 JAR の完全な 4.7.0 default config と全コメントをそのまま提供します。`message-tokens.custom: {}` のような空 map も不足時は migration に保持されます。migration ファイル末尾には完全 reference との text diff も comment として追加します。同一行は出力せず、各差分は file 名、別行の `Line` または `Lines`、実際に異なる内容の順で表示します。差分 source line は元の YAML indent を保持するため行頭に `#` だけを直接付け、reference-only block は挿入位置も表示します。

```yaml
config-version: "4.7.0"
```

startup/reload 時には既知の最上位 `config.yml` block も 4.7.0 bundled default 順へ並べ替えますが、各 block の現在の text・設定値・custom comment は保持し、default にない最上位 block は最後に元の順序で残します。

### ゲーム内の改行動作

設定した `newline` / `blank-line` トークンで生成した改行だけを保護したまま Minecraft の既存の1行サニタイズを通し、最終送信時に個別のゲームチャット行として出力します。通常の CR/LF 入力は従来どおり平坦化されます。サーバー間リレーでこの意図的な改行をゲームに表示するには、受信側の BlueMapWebChat にも同じ 4.7.0 のトークン行送信対応が必要です。古い受信側は通常の LF を従来の平坦化処理で空白に変換します。
