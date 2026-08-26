package dev.kokoto.webchat;

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
