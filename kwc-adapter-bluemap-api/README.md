# KOKOTO WebChat BlueMapAPI adapter

Java 25 source-only bridge used by the Fabric, NeoForge, and Forge 26.1.2/26.2 platform builds. It registers KWC web assets through BlueMapAPI `WebApp.registerScript` / `registerStyle` inside `BlueMapAPI.onEnable`, so registrations are recreated whenever BlueMap reloads. Bukkit keeps the filesystem/webapp.conf adapter path.
