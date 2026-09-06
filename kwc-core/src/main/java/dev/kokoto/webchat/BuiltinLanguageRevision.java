package dev.kokoto.webchat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Known previous KWC bundled strings that changed during the 5.0.0 release
 * validation cycle. Built-in language files are operator-editable, so callers
 * must only replace a value when it still exactly matches KWC's previous
 * bundled text. Custom translations are therefore preserved.
 */
public final class BuiltinLanguageRevision {
    private static final Map<String, Map<String,String>> PREVIOUS = previousValues();

    private BuiltinLanguageRevision() {}

    public static Map<String,String> previousValues(String language) {
        Map<String,String> values = PREVIOUS.get(String.valueOf(language == null ? "" : language));
        return values == null ? Map.of() : values;
    }

    public static boolean isKnownPreviousValue(String language, String key, String current) {
        if (current == null) return false;
        String lang = String.valueOf(language == null ? "" : language);
        String previous = previousValues(lang).get(key);
        if (current.equals(previous)) return true;
        if (isKnownReactionLegacyVariant(lang, key, current)) return true;
        if (isKnownArchiveLegacyVariant(lang, key, current)) return true;
        if (!"preferences.note".equals(key)) return false;
        return switch (lang) {
            case "ko-KR" -> current.contains("이 설정은 이 브라우저에만 저장됩니다.");
            case "ja-JP" -> current.contains("この設定はこのブラウザにのみ保存されます。")
                    || current.contains("この設定はこのブラウザーにのみ保存されます。");
            case "zh-CN" -> current.contains("此设置仅保存在此浏览器中。");
            default -> current.contains("These settings are stored only in this browser.")
                    || current.contains("This setting is stored only in this browser.");
        };
    }

    private static boolean isKnownReactionLegacyVariant(String lang, String key, String current) {
        if (key == null || !key.startsWith("reaction.") && !key.startsWith("admin.reaction") && !"admin.reactions".equals(key)) return false;
        return switch (lang) {
            case "ko-KR" -> switch (key) {
                case "admin.reactionCatalog" -> current.equals("리엑션 아이콘 관리") || current.equals("리액션 아이콘 관리") || current.equals("리액션 아이콘");
                case "reaction.gameNotice" -> current.equals("{user}님이 {reaction} 반응을 남겼습니다. 원문: {message}")
                        || current.equals("{user}님이 {reaction} 리액션을 남겼습니다.");
                default -> false;
            };
            case "ja-JP" -> switch (key) {
                case "admin.reactionCatalog" -> current.equals("リアクションアイコン管理");
                case "reaction.gameNotice" -> current.equals("{user}さんが {reaction} の反応をしました。元のメッセージ: {message}");
                default -> false;
            };
            case "zh-CN" -> switch (key) {
                case "admin.reactionCatalog" -> current.equals("回应图标管理") || current.equals("反应图标管理");
                case "reaction.gameNotice" -> current.equals("{user} 使用 {reaction} 作出了反应。原消息：{message}");
                default -> false;
            };
            default -> "reaction.gameNotice".equals(key) && current.equals("{user} reacted with {reaction}. Original: {message}");
        };
    }

    private static boolean isKnownArchiveLegacyVariant(String lang, String key, String current) {
        if (!"archive.accountButton".equals(key)) return false;
        return switch (lang) {
            case "ko-KR" -> current.equals("저장한 대화");
            case "ja-JP" -> current.equals("保存した会話");
            case "zh-CN" -> current.equals("已保存的对话");
            default -> current.equals("Saved conversations");
        };
    }

    private static Map<String,String> values(String... pairs) {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) out.put(pairs[i], pairs[i + 1]);
        return Map.copyOf(out);
    }

    private static Map<String, Map<String,String>> previousValues() {
        LinkedHashMap<String, Map<String,String>> all = new LinkedHashMap<>();
        all.put("en-US", values(
                "button.clearHistory", "Clear web history",
                "preferences.showTypingIndicator", "Show typing indicators",
                "alert.confirmClearHistory", "Clear web chat history?",
                "preferences.webPushEnabledStatus", "Enabled in this browser.",
                "preferences.notificationsEnabledStatus", "Enabled in this browser.",
                "preferences.presetServerHelp", "Signed-in profiles are stored with your KWC account. This browser only remembers which profile you selected.",
                "preferences.note", "Signed-in users can save visual profiles to their KWC account. Device-specific window state and Web Push registration remain in this browser.",
                "preferences.noteDrag", "Drag the title to move this window.",
                "admin.filterWordLists", "Block word lists",
                "admin.filterWordListsHint", "UTF-8 .txt files in filter-lists/. Use one blocked word per line; blank lines and lines beginning with # are ignored.",
                "admin.filterWordListsEmpty", "No block word list files are registered.",
                "admin.filterListWords", "Blocked words",
                "admin.filterListWordsHint", "One blocked word per line. # starts a comment.",
                "admin.filterListBlockOnly", "List files are block-only. A matching custom rule is evaluated first and can provide mask/replace behavior.",
                "admin.filterTestHint", "Tests block word lists and custom rules without sending a message. This works even when the live filter is disabled.",
                "reaction.gameNotice", "{user} reacted with {reaction}. Original: {message}",
                "admin.reactionShowActorsHint", "When disabled, reactor display names are not included in reaction responses or shown in tooltips.",
                "archive.accountButton", "Saved conversations",
                "link.commandHint", "/kwc auth <code>",
                "link.statusWaiting", "Waiting for /kwc auth {code} in game..."
        ));
        all.put("ko-KR", values(
                "button.clearHistory", "웹 히스토리 비우기",
                "preferences.showTypingIndicator", "입력 중 표시 보기",
                "alert.confirmClearHistory", "웹 채팅창 히스토리를 비울까요?",
                "preferences.webPushEnabledStatus", "이 브라우저에서 켜짐.",
                "preferences.notificationsEnabledStatus", "이 브라우저에서 켜짐.",
                "preferences.presetServerHelp", "로그인한 사용자의 프로필은 KWC 계정에 저장됩니다. 이 브라우저에는 마지막으로 선택한 프로필만 기억합니다.",
                "preferences.note", "로그인 사용자는 시각 설정 프로필을 KWC 계정에 저장할 수 있습니다. 창 위치/크기와 Web Push 등록은 기기별로 이 브라우저에 남습니다.",
                "preferences.noteDrag", "제목을 잡고 이동할 수 있습니다.",
                "admin.filterWordLists", "차단 단어 목록",
                "admin.filterWordListsHint", "filter-lists/ 폴더의 UTF-8 .txt 파일을 사용합니다. 한 줄에 차단 단어 하나를 입력하며, 빈 줄과 #으로 시작하는 줄은 무시합니다.",
                "admin.filterWordListsEmpty", "등록된 차단 단어 목록 파일이 없습니다.",
                "admin.filterListWords", "차단 단어",
                "admin.filterListWordsHint", "한 줄에 차단 단어 하나. #으로 시작하면 주석입니다.",
                "admin.filterListBlockOnly", "목록 파일 자체는 차단 전용입니다. 같은 단어의 커스텀 규칙이 있으면 커스텀 규칙이 먼저 적용되어 마스킹/치환할 수 있습니다.",
                "admin.filterTestHint", "메시지를 보내지 않고 차단 단어 목록과 커스텀 규칙을 테스트합니다. 실제 필터가 꺼져 있어도 테스트할 수 있습니다.",
                "reaction.remove", "리액션 취소",
                "reaction.add", "리엑션 추가",
                "reaction.noActors", "리엑션 사용자 정보 없음",
                "reaction.searchEmpty", "일치하는 리엑션이 없습니다.",
                "reaction.notificationTitle", "리엑션",
                "reaction.notificationBody", "{user}님이 {reaction} 리엑션을 남겼습니다.",
                "reaction.gameNotice", "{user}님이 {reaction} 반응을 남겼습니다. 원문: {message}",
                "admin.reactionShowActorsHint", "끄면 반응 개수와 내 반응 여부는 유지하지만 반응한 사람의 표시이름 목록은 서버 응답과 툴팁에서 숨깁니다.",
                "archive.accountButton", "저장한 대화",
                "link.commandHint", "/kwc auth <code>",
                "link.statusWaiting", "게임에서 /kwc auth {code} 입력 대기 중...",
                "reaction.pending", "원문 서버가 다시 연결되면 리엑션을 적용합니다.",
                "reaction.failed", "리엑션을 적용하지 못했습니다: {error}",
                "reaction.expired", "원문 서버가 다시 연결되기 전에 리엑션 요청이 만료되었습니다.",
                "reaction.manage", "리엑션 설정",
                "admin.reactions", "리엑션",
                "admin.reactionCatalog", "리엑션 아이콘",
                "admin.reactionCatalogHint", "리엑션 선택창에 표시할 Unicode 이모지를 관리합니다. 아이콘을 제거해도 기존 리엑션 기록은 삭제되지 않고 새 추가만 막힙니다.",
                "admin.reactionEnabled", "리엑션 아이콘 기능 사용",
                "admin.reactionEnabledHint", "끄면 기존 리엑션 기록은 보존한 채 새 리엑션 동작을 막고 추가/선택창 버튼을 숨깁니다.",
                "admin.reactionCustomEmoji", "KWC 커스텀 이모지를 리엑션에 허용",
                "admin.reactionSearchAliasesHint", "한 줄에 하나씩 `이모지 = 검색어` 형식으로 입력합니다. 새 Unicode 리엑션 아이콘을 추가할 때 이곳에 검색어도 함께 추가하세요. Unicode 이름은 자동으로도 검색됩니다.",
                "admin.reactionSaved", "리엑션 아이콘을 저장했습니다.",
                "admin.reactionResetConfirm", "리엑션 아이콘 목록을 기본값으로 초기화할까요?"
        ));
        all.put("ja-JP", values(
                "button.clearHistory", "Web履歴を消去",
                "preferences.showTypingIndicator", "入力中表示を表示",
                "alert.confirmClearHistory", "Webチャット履歴を消去しますか？",
                "preferences.webPushEnabledStatus", "このブラウザーで有効です。",
                "preferences.notificationsEnabledStatus", "このブラウザーで有効です。",
                "preferences.presetServerHelp", "ログイン中のプロファイルは KWC アカウントに保存されます。このブラウザには最後に選択したプロファイルだけを記憶します。",
                "preferences.note", "ログインユーザーは表示設定プロファイルを KWC アカウントに保存できます。ウィンドウ状態と Web Push 登録は端末ごとにこのブラウザへ残ります。",
                "preferences.noteDrag", "タイトルをドラッグして移動できます。",
                "admin.filterWordLists", "ブロック単語リスト",
                "admin.filterWordListsHint", "filter-lists/ のUTF-8 .txtファイルを使用します。1行に1語を入力し、空行と#で始まる行は無視します。",
                "admin.filterWordListsEmpty", "ブロック単語リストが登録されていません。",
                "admin.filterListWords", "ブロック単語",
                "admin.filterListWordsHint", "1行に1語。#で始まる行はコメントです。",
                "admin.filterListBlockOnly", "リストファイル自体はブロック専用です。同じ語のカスタムルールがある場合はカスタムルールが先に適用され、マスク/置換できます。",
                "admin.filterTestHint", "送信せずにブロック単語リストとカスタムルールをテストします。実際のフィルターが無効でもテストできます。",
                "reaction.remove", "リアクションを取り消す",
                "reaction.add", "リアクションを追加",
                "reaction.noActors", "リアクションしたユーザー情報はありません",
                "reaction.searchEmpty", "一致するリアクションはありません。",
                "reaction.notificationTitle", "リアクション",
                "reaction.notificationBody", "{user}さんが {reaction} でリアクションしました。",
                "reaction.gameNotice", "{user}さんが {reaction} の反応をしました。元のメッセージ: {message}",
                "admin.reactionShowActorsHint", "オフにすると反応数と自分の反応状態は維持しますが、反応したユーザーの表示名一覧をサーバー応答とツールチップから非表示にします。",
                "archive.accountButton", "保存した会話",
                "link.commandHint", "/kwc auth <code>",
                "link.statusWaiting", "ゲーム内で /kwc auth {code} の入力待ち...",
                "reaction.pending", "元メッセージのサーバーが再接続するとリアクションを適用します。",
                "reaction.failed", "リアクションを適用できませんでした: {error}",
                "reaction.expired", "元メッセージのサーバーが再接続する前にリアクション要求が期限切れになりました。",
                "reaction.manage", "リアクション設定",
                "admin.reactions", "リアクション",
                "admin.reactionCatalog", "リアクションアイコン",
                "admin.reactionCatalogHint", "リアクション選択画面に表示する Unicode 絵文字を管理します。削除しても既存のリアクションは消えず、新規追加だけができなくなります。",
                "admin.reactionEnabled", "リアクションアイコン機能を有効化",
                "admin.reactionEnabledHint", "オフにすると既存のリアクション記録を保持したまま、新しいリアクション操作を禁止し、追加・選択画面の操作を非表示にします。",
                "admin.reactionCustomEmoji", "KWC カスタム絵文字をリアクションで許可",
                "admin.reactionSearchAliasesHint", "1 行に 1 件、`絵文字 = 検索語` の形式で入力します。新しい Unicode リアクションアイコンを追加するときは、ここに検索語も追加してください。Unicode 名も自動的に検索対象になります。",
                "admin.reactionSaved", "リアクションアイコンを保存しました。",
                "admin.reactionResetConfirm", "リアクションアイコン一覧を初期値に戻しますか？"
        ));
        all.put("zh-CN", values(
                "button.clearHistory", "清空网页历史",
                "preferences.showTypingIndicator", "显示输入中提示",
                "alert.confirmClearHistory", "要清空网页聊天历史吗？",
                "preferences.webPushEnabledStatus", "已在此浏览器启用。",
                "preferences.notificationsEnabledStatus", "已在此浏览器启用。",
                "preferences.presetServerHelp", "登录用户的配置文件保存在 KWC 账户中。此浏览器只记住最后选择的配置文件。",
                "preferences.note", "登录用户可将视觉设置配置文件保存到 KWC 账户。窗口状态和 Web Push 注册仍按设备保存在此浏览器中。",
                "preferences.noteDrag", "可拖动标题移动此窗口。",
                "admin.filterWordLists", "屏蔽词列表",
                "admin.filterWordListsHint", "使用 filter-lists/ 目录中的 UTF-8 .txt 文件。每行一个屏蔽词；空行和以 # 开头的行会被忽略。",
                "admin.filterWordListsEmpty", "没有已注册的屏蔽词列表文件。",
                "admin.filterListWords", "屏蔽词",
                "admin.filterListWordsHint", "每行一个屏蔽词。以 # 开头的行为注释。",
                "admin.filterListBlockOnly", "列表文件本身仅用于屏蔽。若同一词存在自定义规则，则优先应用自定义规则，可执行遮罩/替换。",
                "admin.filterTestHint", "无需发送消息即可测试屏蔽词列表和自定义规则。即使实时过滤器已关闭也可以测试。",
                "reaction.add", "添加回应",
                "reaction.noActors", "没有回应用户信息",
                "reaction.searchEmpty", "没有匹配的回应。",
                "reaction.notificationTitle", "回应",
                "reaction.notificationBody", "{user} 使用 {reaction} 作出了回应。",
                "reaction.gameNotice", "{user} 使用 {reaction} 作出了反应。原消息：{message}",
                "admin.reactionShowActorsHint", "关闭后仍保留反应数量和自己的反应状态，但服务器响应和工具提示不会包含反应用户的显示名称列表。",
                "archive.accountButton", "已保存的对话",
                "link.commandHint", "/kwc auth <code>",
                "link.statusWaiting", "等待在游戏内输入 /kwc auth {code} ...",
                "reaction.pending", "原消息服务器重新连接后将应用该回应。",
                "reaction.failed", "无法应用回应：{error}",
                "reaction.expired", "原消息服务器重新连接前，回应请求已过期。",
                "reaction.manage", "回应设置",
                "admin.reactions", "回应",
                "admin.reactionCatalog", "回应图标",
                "admin.reactionCatalogHint", "管理回应选择器中显示的 Unicode 表情。移除图标不会删除已有回应，只会禁止新增。",
                "admin.reactionEnabled", "启用回应图标功能",
                "admin.reactionEnabledHint", "关闭后会保留已有回应记录，同时禁止新的回应操作并隐藏新增/选择器控件。",
                "admin.reactionCustomEmoji", "允许 KWC 自定义表情用于回应",
                "admin.reactionSearchAliasesHint", "每行一项，格式为 `表情 = 搜索词`。新增 Unicode 回应图标时，也请在此添加搜索词。Unicode 名称也会自动用于搜索。",
                "admin.reactionSaved", "回应图标已保存。",
                "admin.reactionResetConfirm", "将回应图标列表重置为默认值吗？"
        ));
        return Map.copyOf(all);
    }
}
