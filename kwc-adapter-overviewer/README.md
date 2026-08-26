# KOKOTO WebChat Overviewer adapter

This loader-neutral module embeds KWC into an existing Minecraft Overviewer static web-map output directory. It has no compile-time dependency on Overviewer.

The adapter accepts only an existing `index.html` positively identified as Overviewer (the generated `Minecraft-Overviewer` generator marker and/or the standard `overviewerConfig.js` + `overviewer.js` assets). It writes only KWC's configured addon directory and a marked block in `index.html`.

Overviewer can regenerate `index.html` when rendering or updating web assets. Run `/kchat reload` after an Overviewer render if the KWC block was replaced. Overviewer's `customwebassets` mechanism can also be used by advanced operators to maintain their own customized web template; Stage 1 intentionally does not edit Overviewer's Python configuration.
