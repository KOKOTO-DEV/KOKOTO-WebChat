package dev.kokoto.webchat;

import dev.kokoto.webchat.adapter.pl3xmap.Pl3xMapAdapterHost;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

/** Bukkit resource/filesystem/logging bridge for the loader-neutral pl3xmap adapter. */
public final class BukkitPl3xMapAdapterHost implements Pl3xMapAdapterHost {
    private final KokotoWebChatPlugin plugin;
    private final CoreLogger logger;

    public BukkitPl3xMapAdapterHost(KokotoWebChatPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = CoreLogger.of(plugin.getLogger()::info, plugin.getLogger()::warning);
    }

    @Override public ConfigValues configValues() { return plugin.configValues(); }
    @Override public Path dataDirectory() { return plugin.getDataFolder().toPath(); }
    @Override public InputStream resource(String name) { return plugin.getResource(name); }
    @Override public String version() { return plugin.getDescription().getVersion(); }
    @Override public CoreLogger logger() { return logger; }
}
