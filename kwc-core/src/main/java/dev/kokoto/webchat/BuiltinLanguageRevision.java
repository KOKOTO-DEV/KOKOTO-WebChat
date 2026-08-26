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

    private static Map<String,String> values(String... pairs) {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) out.put(pairs[i], pairs[i + 1]);
        return Map.copyOf(out);
    }

    private static Map<String, Map<String,String>> previousValues() {
        LinkedHashMap<String, Map<String,String>> all = new LinkedHashMap<>();
        all.put("en-US", values(
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
                "admin.filterTestHint", "Tests block word lists and custom rules without sending a message. This works even when the live filter is disabled."
        ));
        all.put("ko-KR", values(
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
                "admin.filterTestHint", "메시지를 보내지 않고 차단 단어 목록과 커스텀 규칙을 테스트합니다. 실제 필터가 꺼져 있어도 테스트할 수 있습니다."
        ));
        all.put("ja-JP", values(
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
                "admin.filterTestHint", "送信せずにブロック単語リストとカスタムルールをテストします。実際のフィルターが無効でもテストできます。"
        ));
        all.put("zh-CN", values(
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
                "admin.filterTestHint", "无需发送消息即可测试屏蔽词列表和自定义规则。即使实时过滤器已关闭也可以测试。"
        ));
        return Map.copyOf(all);
    }
}
