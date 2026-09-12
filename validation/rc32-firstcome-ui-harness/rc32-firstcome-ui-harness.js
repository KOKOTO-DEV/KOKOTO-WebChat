// KWC 파일 안내 / KWC file guide
// RC32/RC34 선착순 이벤트 생성 UI가 별도 참가 정원을 입력받지 않고 winnerCount만 authoritative capacity로 사용하도록 정적 검증한다.
// RC34 keeps the field visible only as a disabled mirror of winnerCount, while winnerCount remains the sole authoritative capacity.
'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function check(ok, msg) { assertions++; if (!ok) throw new Error(msg); }
function has(text, needle, msg) { check(text.includes(needle), msg); }
const src = fs.readFileSync(path.join(root, 'frontend/inner/85-chat-games.js'), 'utf8');
has(src, 'id="kwc-game-max-field"', 'participant-capacity field has a dedicated visibility wrapper');
has(src, 'typeSelect?.value === "firstcome"', 'firstcome selection drives capacity visibility');
has(src, 'maxField.hidden = false', 'firstcome may show participant capacity only as a mirrored informational field');
has(src, 'maxInput.disabled = firstCome', 'firstcome disables separate participant capacity input');
has(src, 'if (firstCome) maxInput.value = String(Math.max(1, Number(winnerInput?.value || 1)))', 'firstcome displayed participant capacity mirrors winner count');
has(src, 'const winnerCount = content.querySelector("#kwc-game-winner-count")?.value || "";', 'winner count remains the submitted authoritative size');
has(src, 'maxParticipants:selectedType === "firstcome" ? ""', 'firstcome request does not submit the hidden participant-capacity value');
const manager = fs.readFileSync(path.join(root, 'kwc-core/src/main/java/dev/kokoto/webchat/ChatGameManager.java'), 'utf8');
has(manager, 'if (type.equals("firstcome")) maxParticipants = winnerCount;', 'server normalizes firstcome capacity to winners');
has(manager, '"firstcome".equals(game.type) && game.winners.size() >= game.winnerCount', 'firstcome auto-completes when winner slots fill');
const command = fs.readFileSync(path.join(root, 'kwc-core/src/main/java/dev/kokoto/webchat/GameCommandService.java'), 'utf8');
has(command, 'create firstcome" + scope + " <winners> <title>', 'command syntax has no firstcome participant capacity and may include optional Relay scope');
console.log(`RC32_FIRSTCOME_UI_PASS assertions=${assertions}`);
