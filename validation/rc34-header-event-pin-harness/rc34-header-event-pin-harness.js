// KWC file guide / KWC 파일 안내
// RC34 locks the live-browser header/event/pinned-identity corrections reported after RC33.
'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function read(rel){ return fs.readFileSync(path.join(root, rel), 'utf8'); }
function check(ok,msg){ assertions++; if(!ok) throw new Error(msg); }
function has(t,n,m){ check(t.includes(n), `${m} [missing: ${n}]`); }
function notHas(t,n,m){ check(!t.includes(n), `${m} [unexpected: ${n}]`); }

const frame = read('frontend/inner/30-reply-identity-frame.js');
has(frame, 'secondaryCompactWidth', 'responsive header computes compact icon geometry');
has(frame, 'requiredCompactOneRowWidth', 'responsive header retains explicit compact-fit geometry');
has(frame, 'requiredCompactOneRowWidth', 'one-row-to-wrapped threshold uses compact geometry');
has(frame, 'if (wrapped)', 'header uses directional hysteresis instead of one symmetric threshold');
has(frame, 'contentWidth + 1 >= requiredCompactOneRowWidth', 'wrapped header returns as soon as the current compact row fits');
has(frame, 'contentWidth + 1 < requiredCompactOneRowWidth', 'one-row header wraps only after compact row no longer fits');

const auth = read('frontend/inner/40-root-auth.js');
has(auth, 'function headerAccountDisplayName(value, maxCodePoints = 16)', 'account label has code-point aware truncation');
has(auth, 'chars.length > max ? chars.slice(0, max).join("") + "…"', 'only names over 16 characters receive ellipsis');
has(auth, 'btn.textContent = headerAccountDisplayName(accountButtonName, 16)', 'header uses truncated display label');

const css = read('kwc-standalone-frontend/src/main/resources/standalone/chat.css');
has(css, '5.3.0 RC34: public-header live-feedback correction.', 'RC34 final CSS block exists');
has(css, 'min-width: 30px !important;', 'one-row icon actions may shrink to PIP/minimize width');
has(css, 'flex: 0 1 36px !important;', 'one-row icon actions shrink continuously before wrapping');
has(css, '#kwc-root #kwc-login.kwc-login-user,', 'account width override exists');
has(css, 'width: auto !important;', 'account button uses content width rather than reserved 16ch width');
has(css, 'top: -6px !important;', 'wrapped unread badge overhangs button');
has(css, 'right: -6px !important;', 'wrapped unread badge overhangs upper-right edge');
has(css, 'scrollbar-gutter: auto !important;', 'event list no longer reserves empty right gutter');
has(css, '.kwc-game-max-field-firstcome-hidden', 'firstcome participant field has a hard CSS hide guard');

const games = read('frontend/inner/85-chat-games.js');
has(games, 'function chatGameLocalizedText(key, fallbacks)', 'event UI has locale-aware fallback when old lang files miss keys');
has(games, 'function chatGameDeletedNotice()', 'deleted-event notice has localized fallback');
has(games, 'ko:"삭제되어 더 이상 열 수 없는 이벤트입니다."', 'deleted-event Korean fallback exists');
has(games, 'ja:"このイベントは削除されたため、開くことができません。"', 'deleted-event Japanese fallback exists');
has(games, 'zh:"此活动已被删除，无法再打开。"', 'deleted-event Chinese fallback exists');
has(games, 'function chatGameTypeFieldLabel()', 'Type field label has locale-aware fallback');
has(games, 'function chatGameDisplayCapacity(game)', 'event capacity display normalizes firstcome to winner count');
has(games, 'return Math.max(0, Number(game.winnerCount || 0));', 'firstcome displayed participant capacity equals winner count');
has(games, 'maxInput.value = String(Math.max(1, Number(winnerInput?.value || 1)))', 'hidden firstcome participant value mirrors winners');
has(games, 'winnerInput?.addEventListener("input", syncGameCapacityFields)', 'firstcome mirror updates when winner count changes');
notHas(games, 't("game.type", "Type")', 'event form no longer depends on English Type fallback');
has(games, 'winners.map(item => directMessageIdentityHtml({displayName:item.displayName || item.label || item.username || item.uuid || "", username:item.username || "", uuid:item.uuid || ""}, "kwc-sender"))', 'event winner rows use the shared display/real-name identity renderer');
has(games, 'username:item.username || ""', 'event participant rows pass the real username into the identity renderer');

const history = read('frontend/inner/50-public-history-virtual-scroll.js');
has(history, 'chatGameDeletedNotice()', 'deleted public-chat event card uses localized notice');
has(history, 'chatGameDeletedLabel()', 'deleted public-chat event card uses localized label');

const pins = read('frontend/inner/80-pins-admin.js');
has(pins, 'function pinnedByIdentityHtml(pin)', 'pinned-by has dedicated global identity renderer');
has(pins, 'data-kwc-identity-toggle="pinned-by"', 'pinned-by participates in global Display/Real name mode');
has(pins, 'data-display-sender=', 'pinned-by carries display name');
has(pins, 'data-real-sender=', 'pinned-by carries real account name');

const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
has(server, 'storage.findKnownLocalPlayer(legacyPinner)', 'legacy public pins attempt local identity enrichment');
has(server, 'storage.findKnownPlayerByUuid(pinnerUuid)', 'structured pinner identity can refresh without a web account lookup');
has(server, 'actorUsername', 'remote event join forwards the real username');
check(!server.includes('enrichChatGameIdentities'), 'final event identity path has no intermediate RC enrichment');
const manager = read('kwc-core/src/main/java/dev/kokoto/webchat/ChatGameManager.java');
has(manager, 'participantUsernames', 'event persistence keeps real usernames separately from display labels');
has(manager, 'participant.put("username"', 'event participant snapshots expose the real username');
has(manager, 'winner.put("username"', 'event winner snapshots expose the real username');

const revision = read('kwc-core/src/main/java/dev/kokoto/webchat/BuiltinLanguageRevision.java');
check((revision.match(/"game\.deletedNotice", "This event has been deleted and can no longer be opened\."/g) || []).length >= 3,
  'non-English language files migrate accidental English deleted-event notices');
check((revision.match(/"game\.type", "Type"/g) || []).length >= 3,
  'non-English language files migrate old English Type labels');

for (const lang of ['en-US','ko-KR','ja-JP','zh-CN']) {
  const y = read(`kwc-platform-bukkit/src/main/resources/lang/${lang}.yml`);
  has(y, 'deletedNotice:', `${lang} ships deleted-event notice`);
  has(y, '    type:', `${lang} ships event Type/format label`);
}

console.log(`RC34_HEADER_EVENT_PIN_PASS assertions=${assertions}`);
