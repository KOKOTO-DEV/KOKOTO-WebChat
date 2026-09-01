# KOKOTO WebChat 5.1.0 — Visual Documentation Assets

The `docs/assets/` directory contains reusable diagrams for manuals, README pages, Modrinth/CurseForge descriptions, and release posts. The `wiki/assets/` directory contains copies intended for a GitHub Wiki repository where `../docs/assets/...` would not resolve.

## Asset set

| Topic | Static | Animated | Editable source |
| --- | --- | --- | --- |
| 5.1.0 architecture | `architecture-5.1.0.svg` / `.png` | — | `source/architecture-5.1.0.dot` |
| Relay Protocol v2 | `relay-v2-flow.svg` / `.png` | `relay-v2-flow.gif` | `source/relay-v2-flow.dot` |
| DM/group Reply | `private-reply-flow.svg` / `.png` | — | `source/private-reply-flow.dot` |
| Config language/migration | `config-language-migration.svg` / `.png` | `config-language-migration.gif` | `source/config-language-migration.dot` |
| Deployment modes | `deployment-modes.svg` / `.png` | — | `source/deployment-modes.dot` |
| Upload security | `upload-security-pipeline.svg` / `.png` | — | `source/upload-security-pipeline.dot` |
| Web Push | `web-push-flow.svg` / `.png` | — | `source/web-push-flow.dot` |

## Source and synchronization rule

- `docs/assets/source/*.dot` is the editable canonical source for Graphviz diagrams.
- Rendered `docs/assets/*` files are the canonical published assets; matching `wiki/assets/*` files must be byte-identical copies.
- When logic changes, update the DOT source first, regenerate SVG/PNG (and GIF where listed), then synchronize the Wiki copies.
- Migration diagrams must distinguish preserved supported operator values from retired settings and intentional Relay v1 → v2 trust reset behavior.
- Upload diagrams distinguish the web composer's `max-files-per-message` selection limit from the server `/upload` endpoint, which validates one multipart file per request.

## Rendering policy

- GitHub Markdown/Wiki: prefer Mermaid for live diagrams and SVG/PNG/GIF for visual summaries or animated flows.
- Modrinth: use uploaded PNG/GIF/SVG assets in the project description as appropriate.
- CurseForge: upload the corresponding PNG/GIF as project images and place them near the matching section in the description/editor.
- Keep the text explanation next to every figure. A diagram is supplemental, not the sole source of an operational or security requirement.
- Use meaningful alt text and do not embed secrets, actual server addresses, real user data, or tokens in screenshots/figures.

See `REFERENCES.md` for the official rendering and protocol references used by the documentation.
