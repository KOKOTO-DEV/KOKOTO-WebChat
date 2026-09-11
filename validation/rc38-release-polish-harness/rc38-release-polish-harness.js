#!/usr/bin/env node
'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function read(rel){ return fs.readFileSync(path.join(root, rel), 'utf8'); }
function ok(v,m){ assertions++; if(!v) throw new Error(`ASSERT FAILED: ${m}`); }
function has(t,n,m){ ok(t.includes(n), `${m} [missing: ${n}]`); }
function section53(text){ const s=text.indexOf('## 5.3.0'); const e=text.indexOf('\n## 5.2.1', s); return s>=0 ? text.slice(s, e>=0?e:text.length) : ''; }

// Standalone-only private multi-window runtime boundary.
const multi = read('frontend/inner/115-private-multiwindow.js');
has(multi, 'return state.isStandalone === true', 'private multi-window is gated to Standalone at runtime');

// Two rendered-line latest-follow boundary and shared saved-position bottom test.
const scroll = read('frontend/inner/50-public-history-virtual-scroll.js');
has(scroll, 'const configured = Number(c.uiAutoFollowBottomThresholdLines);', 'latest-follow reads line-count configuration');
has(scroll, 'return !!box && bottomGapPx(box) < autoFollowBottomThresholdPx(box);', 'latest-follow uses strict less-than line threshold');
has(scroll, 'activeChatLineHeightPx(box) * lines', 'latest-follow converts rendered line height to physical distance');
const privateUi = read('frontend/inner/120-dm-ui-typing.js');
has(privateUi, 'return isAutoFollowBottom(box);', 'saved conversation position uses the same bottom predicate');
const config = read('kwc-platform-bukkit/src/main/resources/config.yml');
has(config, 'auto-follow-bottom-threshold-lines: 2', 'current config default is two rendered lines');
ok(!config.includes('auto-follow-bottom-threshold-px:'), 'current config no longer exposes pixel threshold');

// Winner identity announcement: display name first, real username in parentheses, trophy prefix.
const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
has(server, 'shown += " (" + username + ")"', 'chat event result includes real username when different');
has(server, 'String fallback = "🏆 Event result: "', 'web/relay event result fallback starts with trophy');
has(server, 'LegacyText.LIGHT_PURPLE + "🏆 "', 'game event result starts with trophy');
const commands = read('kwc-core/src/main/java/dev/kokoto/webchat/GameCommandService.java');
has(commands, 'displayName + " (" + username + ")"', 'game command winner list uses display-name plus real-name form');
for (const lang of ['en-US','ko-KR','ja-JP','zh-CN']) {
  const y = read(`kwc-platform-bukkit/src/main/resources/lang/${lang}.yml`);
  ok(/chat\.results:\s*['"]?🏆/.test(y), `${lang} event result language starts with trophy`);
}

// Reaction search aliases are picker-search-only; the withdrawn chat-token option must not return.
const catalog = read('kwc-core/src/main/java/dev/kokoto/webchat/ReactionCatalogStore.java');
const admin = read('frontend/inner/80-pins-admin.js');
const c53 = section53(read('CHANGELOG.md'));
ok(!catalog.includes('emojiForSearchAlias' + 'Token'), 'reaction catalog has no alias-token resolver');
ok(!server.includes('applyReactionSearchAlias' + 'EmojiTokens'), 'server has no alias-token chat converter');
ok(!admin.includes('reactionAlias' + 'EmojiTokens'), 'Admin UI has no alias-token option');
ok(!admin.includes('kwc-reaction-alias-' + 'token-enabled'), 'Admin UI has no alias-token checkbox');
ok(!c53.includes('Reaction search aliases as chat ' + 'emoji tokens'), 'changelog does not advertise withdrawn alias-token feature');
has(c53, '`Display name (Real name)`', 'changelog documents event winner identity form');

// Distribution release notes stay byte-equal to the current 5.3.0 changelog section.
const modrinth = read('distribution/modrinth/RELEASE_NOTES_5.3.0.md').trim();
const curseforge = read('distribution/curseforge/CHANGELOG_5.3.0.md').trim();
ok(modrinth === c53.trim(), 'Modrinth 5.3.0 release notes equal changelog 5.3.0 section');
ok(curseforge === c53.trim(), 'CurseForge 5.3.0 changelog equals changelog 5.3.0 section');

console.log(`RC38_RELEASE_POLISH_PASS assertions=${assertions}`);
