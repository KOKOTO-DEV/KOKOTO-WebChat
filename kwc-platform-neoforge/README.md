# KOKOTO WebChat — NeoForge exact-target matrix

KWC 5.1.0 NeoForge is built as 12 exact targets from Minecraft 1.20.2 through 26.2.
Shared KWC services and loader-neutral adapters stay common; chat component/API differences are isolated in compatibility source families.

Deploy only the JAR whose Minecraft suffix exactly matches the server version. For example, a Minecraft 1.21.1 NeoForge server must use `KOKOTO-WebChat-5.1.0-NeoForge-1.21.1.jar`; a `NeoForge-26.2.jar` is not interchangeable with it.

Windows: `build-all.bat` / `build-target.bat <minecraft-version>`; add `--fast` to either command to skip `clean` and reuse the Gradle build cache during iteration.
Linux/macOS: `./build-all.sh` / `./build-target.sh <minecraft-version>`

JDK: 17 (1.20.2–1.20.4), 21 (1.20.6–1.21.11), 25 (26.1.2–26.2).
1.20.2–1.20.6 use NeoGradle userdev; 1.21.1 and newer targets use ModDevGradle.
