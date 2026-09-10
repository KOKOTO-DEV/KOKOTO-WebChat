package dev.kokoto.webchat.adapter.bluemap.api;


/* KWC 파일 안내 / KWC file guide
 * BlueMapApiIntegration는 bluemap-api 웹맵/프런트엔드에 KWC asset과 설정을 설치·갱신·제거하는 adapter 계층이다.
 * BlueMapApiIntegration is an adapter layer installing, updating, and removing KWC assets/configuration for the bluemap-api web map/frontend.
 *
 * adapter가 소유한 파일만 수정하고 사용자/맵 프로그램의 다른 파일을 덮어쓰지 않으며, 반복 실행해도 같은 결과가 되는 idempotency를 유지한다.
 * Modify only adapter-owned files, never overwrite unrelated user/map files, and keep installation idempotent across repeated runs.
 */
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
