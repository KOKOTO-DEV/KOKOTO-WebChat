package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * MessageTokenConfig는 KWC 설정을 core가 사용할 수 있는 형태로 읽거나 보관하는 설정 계층이다.
 * MessageTokenConfig is part of the configuration layer that reads or carries KWC settings in a core-friendly form.
 *
 * 설정 키를 바꿀 때는 canonical config, 과거 baseline, migration, 다국어 template, 문서 reference가 함께 움직여야 한다.
 * When changing a setting key, update canonical config, historical baselines, migration, localized templates, and documentation references together.
 */
import java.util.List;
import java.util.Map;

/**
 * Platform-neutral view of the message-token settings used by the core token
 * processor. Platform modules adapt their native configuration objects to this
 * interface instead of making the core depend on Bukkit/Fabric/NeoForge APIs.
 */
public interface MessageTokenConfig {
    boolean messageTokensEnabled();

    int messageTokensMaxReplacements();

    List<String> messageTokenNewlineAliases();

    List<String> messageTokenBlankLineAliases();

    List<String> messageTokenTabAliases();

    int messageTokenTabSpaces();

    Map<String, String> messageTokenCustomReplacements();
}
