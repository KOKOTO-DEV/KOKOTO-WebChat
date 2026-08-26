package dev.kokoto.webchat;

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
