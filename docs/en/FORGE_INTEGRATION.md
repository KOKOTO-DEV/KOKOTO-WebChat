# KOKOTO WebChat - Forge

Server-side Forge platform for KOKOTO WebChat 5.2.0. Forge is built as **exact Minecraft-version JARs** rather than one broad-range binary because Forge/Minecraft APIs changed across the supported span.

## Targets

| Minecraft | Forge pin | Java | source layer | map integration |
|---|---:|---:|---|---|
| 1.18.2 | 40.2.24 | Java 17 | `compat118` | filesystem adapters |
| 1.19.2 | 43.4.2 | Java 17 | `compatClassic` | filesystem adapters |
| 1.19.4 | 45.3.12 | Java 17 | `compatClassic` | filesystem adapters |
| 1.20.1 | 47.4.23 | Java 17 | `compatClassic` | filesystem adapters |
| 1.20.2 | 48.1.0 | Java 17 | `compatClassic` | filesystem adapters |
| 1.20.4 | 49.2.9 | Java 17 | `compatClassic` | filesystem adapters |
| 1.20.6 | 50.2.0 | Java 21 | `compatClassic` | filesystem adapters |
| 1.21.1 | 52.1.16 | Java 21 | `compatClassic` | filesystem adapters |
| 1.21.3 | 53.1.0 | Java 21 | `compatClassic` | filesystem adapters |
| 1.21.4 | 54.1.14 | Java 21 | `compatClassic` | filesystem adapters |
| 1.21.5 | 55.1.0 | Java 21 | `compat1215` | filesystem adapters |
| 1.21.8 | 58.1.0 | Java 21 | `compatModern` | filesystem adapters |
| 1.21.10 | 60.1.0 | Java 21 | `compatModern` | filesystem adapters |
| 1.21.11 | 61.2.0 | Java 21 | `compatModern` | filesystem adapters |
| 26.1.2 | 64.1.0 | Java 25 | `compat26` | BlueMapAPI + filesystem adapters |
| 26.2 | 65.1.0 | Java 25 | `compat26` | BlueMapAPI + filesystem adapters |

`src/common` contains KWC runtime/storage/auth/relay/map host code. `compat118`, `compatClassic`, `compat1215`, `compatModern`, and `compat26` isolate Minecraft/Forge API changes. `compat1215` keeps the classic Forge event bus but uses the 1.21.5 chat-event API. `compatModern` starts at 1.21.8, where Forge uses per-event static buses. Its profile/operator helpers use reflective bridging across the 1.21.8 -> 1.21.9 auth/profile API transition.

BlueMap direct API integration is compiled only for Forge 26.1.2 and 26.2. Older targets still support the loader-neutral filesystem/static map adapters (squaremap/Dynmap/LiveAtlas/uNmINeD/Overviewer where the corresponding map files exist).

## Build

Forge targets do not all use the same Gradle/JDK pair. The build helpers select the required launcher JDK for each exact target:

- Minecraft 1.18.2-1.20.4: JDK 17
- Minecraft 1.20.6-1.21.11: JDK 21
- Minecraft 26.1.2-26.2: JDK 25

Gradle launcher matrix:

- Minecraft 1.18.2-1.19.4: Gradle 7.6.4
- Minecraft 1.20.1-1.20.6: Gradle 8.8
- Minecraft 1.21.1-26.2: Gradle 9.3.1 (ForgeGradle 7 requires Gradle 9.3.0 or later)

`build-target.bat`/`build-all.bat` first auto-detect common Windows JDK installations, `JAVA_HOME`, and JDKs exposed through `javac.exe` on `PATH`. If a required major is missing, the Windows resolver downloads the latest GA Eclipse Temurin JDK ZIP from the official Adoptium API into the platform-local `.jdks/` cache, verifies the published SHA-256 checksum, and uses the extracted copy without changing persistent `JAVA_HOME` or installing Java system-wide. Network lookup/download is retried and falls back to Windows `curl.exe` when the Windows PowerShell web stack fails. You can override selection with `KWC_JAVA17_HOME`, `KWC_JAVA21_HOME`, and `KWC_JAVA25_HOME`. Set `KWC_AUTO_DOWNLOAD_JDK=0` to disable this automatic download behavior.

Build one exact target from `kwc-platform-forge`:

```bat
build-target.bat 26.2
```

```bash
./build-target.sh 26.2
```

Build all declared targets:

```bat
build-all.bat
```


> `validate-release-windows.bat` and its required PowerShell helpers are included in the source archive. The separate `KWC-5.2.0-validation-tools.zip` contains development-only browser regression tooling and is not required to run release builds.

For repeat Windows builds, append `--fast` to `build-all.bat` or `build-target.bat <minecraft-version>` to skip `clean` and enable the Gradle build cache. The root `validate-release-windows.bat --parallel` option is the clean full-matrix path that builds Bukkit first, then opens separate live Fabric/NeoForge/Forge build windows while the main console aggregates progress.

```bash
./build-all.sh
```

`./gradlew buildAllForge` remains available and delegates each target to the same JDK-selecting helper instead of running every ForgeGradle generation inside one JVM. Do not run old ForgeGradle targets with the system Java 25 directly: Gradle 7.6.4 cannot run on Java 25 and fails with `Unsupported class file major version 69`.

The deployable Jar-in-Jar artifact is `KOKOTO-WebChat-5.2.0-Forge-<Minecraft>.jar`. ForgeGradle 6/7 targets also produce `KOKOTO-WebChat-5.2.0-Forge-<Minecraft>-slim.jar` as the plain input JAR used to create the final Jar-in-Jar artifact; do not deploy the `-slim.jar` file.

## Runtime

Place only the JAR matching the server's Minecraft version in `mods/`. Configuration lives at `config/KOKOTO-WebChat/config.yml`. KWC remains server-side; clients do not need this mod.
