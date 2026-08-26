# KOKOTO WebChat - Forge Stage 1

Server-side Forge platform for KOKOTO WebChat 5.0.0. Forge is built as **exact Minecraft-version JARs** rather than one broad-range binary because Forge/Minecraft APIs changed across the supported span.

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
| 1.21.5 | 55.1.0 | Java 21 | `compatClassic` | filesystem adapters |
| 1.21.8 | 58.1.0 | Java 21 | `compatModern` | filesystem adapters |
| 1.21.10 | 60.1.0 | Java 21 | `compatModern` | filesystem adapters |
| 1.21.11 | 61.2.0 | Java 21 | `compatModern` | filesystem adapters |
| 26.1.2 | 64.1.0 | Java 25 | `compat26` | BlueMapAPI + filesystem adapters |
| 26.2 | 65.1.0 | Java 25 | `compat26` | BlueMapAPI + filesystem adapters |

`src/common` contains KWC runtime/storage/auth/relay/map host code. `compat118`, `compatClassic`, `compatModern`, and `compat26` isolate Minecraft/Forge API changes. The classic Forge event bus remains in use through 1.21.5; `compatModern` starts at 1.21.8, where Forge uses per-event static buses. Its profile/operator helpers use reflective bridging across the 1.21.8 -> 1.21.9 auth/profile API transition.

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

`build-target.bat`/`build-all.bat` first auto-detect common Windows JDK installations. If the required major is missing, the Windows resolver downloads the latest GA Eclipse Temurin JDK ZIP from the official Adoptium API into `kwc-platform-forge/.jdks/`, verifies the published SHA-256 checksum, extracts it locally, and uses that copy without changing the system Java installation or persistent `JAVA_HOME`. You can override selection with `KWC_JAVA17_HOME`, `KWC_JAVA21_HOME`, and `KWC_JAVA25_HOME`. Set `KWC_AUTO_DOWNLOAD_JDK=0` only if you intentionally want missing JDKs to fail instead of being downloaded.

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

```bash
./build-all.sh
```

`./gradlew buildAllForge` remains available and delegates each target to the same JDK-selecting helper instead of running every ForgeGradle generation inside one JVM. Do not run old ForgeGradle targets with the system Java 25 directly: Gradle 7.6.4 cannot run on Java 25 and fails with `Unsupported class file major version 69`.

The deployable Jar-in-Jar artifact is `KOKOTO-WebChat-5.0.0-Forge-<Minecraft>.jar`. ForgeGradle 6/7 targets also produce `KOKOTO-WebChat-5.0.0-Forge-<Minecraft>-slim.jar` as the plain input JAR used to create the final Jar-in-Jar artifact; do not deploy the `-slim.jar` file.

## Runtime

Place only the JAR matching the server's Minecraft version in `mods/`. Configuration lives at `config/KOKOTO-WebChat/config.yml`. KWC remains server-side; clients do not need this mod.


Windows JDK auto-downloads are validated from the standard JDK `release` metadata before use, avoiding PowerShell 5.x native-stderr false failures.
