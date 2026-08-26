package dev.kokoto.webchat.adapter.bluemap.api;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.WebApp;
import dev.kokoto.webchat.adapter.bluemap.BlueMapAdapter;
import dev.kokoto.webchat.adapter.bluemap.BlueMapAdapterHost;

import java.util.Objects;
import java.util.function.Consumer;

/** BlueMapAPI 2.8 integration used only by Java 25 Fabric/NeoForge platform builds. */
public final class BlueMapApiIntegration implements AutoCloseable {
    private final BlueMapAdapterHost host;
    private final Consumer<BlueMapAPI> enableListener = this::onBlueMapEnable;
    private boolean registered;

    public BlueMapApiIntegration(BlueMapAdapterHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public synchronized void register() {
        if (registered) return;
        registered = true;
        BlueMapAPI.onEnable(enableListener);
        host.logger().info("BlueMapAPI integration listener registered.");
    }

    private void onBlueMapEnable(BlueMapAPI api) {
        try {
            WebApp webApp = api.getWebApp();
            new BlueMapAdapter(host).installApi(webApp.getWebRoot(), new BlueMapAdapter.WebAppRegistration() {
                @Override public void registerScript(String url) { webApp.registerScript(url); }
                @Override public void registerStyle(String url) { webApp.registerStyle(url); }
            });
        } catch (Throwable ex) {
            host.logger().warn("BlueMapAPI integration failed while BlueMap was enabling: " + ex.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        if (!registered) return;
        BlueMapAPI.unregisterListener(enableListener);
        registered = false;
    }
}
