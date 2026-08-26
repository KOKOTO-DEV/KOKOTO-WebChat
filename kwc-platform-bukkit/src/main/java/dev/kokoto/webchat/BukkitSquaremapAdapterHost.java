package dev.kokoto.webchat;

import dev.kokoto.webchat.adapter.squaremap.SquaremapAdapterHost;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

/** Bukkit resource/filesystem/logging bridge for the loader-neutral squaremap adapter. */
public final class BukkitSquaremapAdapterHost implements SquaremapAdapterHost {
    private final KokotoWebChatPlugin plugin;
    private final CoreLogger logger;

    public BukkitSquaremapAdapterHost(KokotoWebChatPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = CoreLogger.of(plugin.getLogger()::info, plugin.getLogger()::warning);
    }

    @Override public ConfigValues configValues() { return plugin.configValues(); }
    @Override public Path dataDirectory() { return plugin.getDataFolder().toPath(); }
    @Override public InputStream resource(String name) { return plugin.getResource(name); }
    @Override public String version() { return plugin.getDescription().getVersion(); }
    @Override public CoreLogger logger() { return logger; }
}
