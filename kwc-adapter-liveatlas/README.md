# KOKOTO WebChat LiveAtlas adapter

This module embeds KWC into an existing LiveAtlas static frontend. LiveAtlas is a frontend rather than a Minecraft server plugin, so this adapter has no LiveAtlas compile-time dependency and does not care whether LiveAtlas is displaying Dynmap, squaremap, Pl3xMap, Overviewer, or multiple configured servers.

KWC installs only its own `kokoto-web-chat/` assets and a marked block in LiveAtlas `index.html`. Automatic detection accepts only an `index.html` that contains LiveAtlas markers such as `window.liveAtlasConfig`. For custom/external web-server layouts, set `adapters.liveatlas.web-root` to the shared/mounted directory that contains the LiveAtlas `index.html`.

LiveAtlas updates can replace `index.html`; run `/kchat reload` or restart KWC after updating LiveAtlas to restore the marked KWC block.
