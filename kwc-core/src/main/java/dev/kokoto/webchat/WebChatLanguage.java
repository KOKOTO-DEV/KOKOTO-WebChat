package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * WebChatLanguage는 다국어 문자열과 구버전 텍스트 호환을 처리한다.
 * WebChatLanguage handles localized text and compatibility with legacy wording/data.
 *
 * 번역 key는 en-US/ko-KR/ja-JP/zh-CN parity 검증 대상이므로 새 key를 추가할 때 네 언어를 동시에 갱신한다.
 * Localization keys are parity-checked across en-US/ko-KR/ja-JP/zh-CN, so add new keys to all four languages together.
 */
import java.util.Map;

/** Loader-neutral language lookup boundary used by web/game notifications. */
public interface WebChatLanguage {
    Map<String, String> webStrings();
    Map<String, String> webStringsFor(String requestedLanguage);
    String text(String key, String fallback);
    String text(String key, String fallback, Map<String, String> values);
    String[] availableLanguages();
    String currentLanguage();
    String fallbackLanguage();
}
