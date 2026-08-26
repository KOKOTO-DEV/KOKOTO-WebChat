# KOKOTO WebChat — Fabric exact-target matrix

KWC 5.0.0 Fabric is built as 16 exact Minecraft targets from 1.18.2 through 26.2.
The shared KWC core/UI and loader-neutral map adapters are reused; only Minecraft/Fabric API boundaries are split into compatibility source families.

Windows: `build-all.bat` or `build-target.bat <minecraft-version>`
Linux/macOS: `./build-all.sh` or `./build-target.sh <minecraft-version>`

JDK selection: 17 for 1.18.2–1.20.4, 21 for 1.20.6–1.21.11, 25 for 26.1.2–26.2.
Set `KWC_JAVA17_HOME`, `KWC_JAVA21_HOME`, or `KWC_JAVA25_HOME` to override automatic discovery.

1.18.2 uses a minimal server chat mixin because Fabric ServerMessageEvents is a 1.19+ API.
Minecraft 1.21.5+ uses the newer typed ClickEvent/HoverEvent model; earlier targets use the classic chat-event model.

Build tooling: pre-26.x targets use Fabric's current `net.fabricmc.fabric-loom-remap` compatibility plugin; 26.x uses `net.fabricmc.fabric-loom`. The matrix uses the current Gradle 9.5.1 bootstrap while still compiling target bytecode with the JDK selected above.
