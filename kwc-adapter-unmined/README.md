# KOKOTO WebChat uNmINeD adapter

This loader-neutral module embeds KWC into an existing uNmINeD static web export. uNmINeD is an external/static map generator rather than a Minecraft server plugin, so the adapter has no compile-time dependency on uNmINeD and is available from Bukkit/Paper/Spigot, Fabric, and NeoForge KWC builds.

KWC patches only a positively identified uNmINeD HTML entry point (`index.html` on current exports, with `unmined.index.html` accepted for older exports) and writes its own files under the configured addon directory. Because uNmINeD can export to any directory, `adapters.unmined.web-root` is recommended when the export is not in one of KWC's small set of conventional local paths.

Re-exporting the uNmINeD website can replace the HTML entry point or delete KWC-owned files. Run `/kchat reload` (or restart KWC/server) after an export to reinstall the marked KWC block and assets.
