# KOKOTO WebChat Dynmap adapter

This module integrates the shared KWC map frontend into Dynmap's static `webpath`. It has no compile-time Dynmap dependency. The adapter reads `webpath` from Dynmap `configuration.txt`, installs KWC assets under a dedicated directory, and owns only a marked block in Dynmap `index.html`.

Dynmap can regenerate its web files when `update-webpath-files` is enabled, so KWC re-checks the marked block on server startup and `/kchat reload`. If Dynmap's web root is copied to a different host instead of served from its normal `webpath`, set `adapters.dynmap.web-root` to the actual shared/mounted web root or install the KWC files there separately.
