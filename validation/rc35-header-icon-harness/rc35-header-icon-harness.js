// KWC file guide / KWC 파일 안내
// RC35 locks the public-header shrink order and glyph legibility requested after RC34.
'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function read(rel){ return fs.readFileSync(path.join(root, rel), 'utf8'); }
function check(ok,msg){ assertions++; if(!ok) throw new Error(msg); }
function has(t,n,m){ check(t.includes(n), `${m} [missing: ${n}]`); }

const auth = read('frontend/inner/40-root-auth.js');
has(auth, 'kwcFaIcon("user", "kwc-status-icon")', 'logged-in count uses shared user icon');

const frame = read('frontend/inner/30-reply-identity-frame.js');
// RC37 supersedes RC34/RC35 directional hysteresis: both wrap and unwrap now
// use the compact threshold. Keep RC35's title-protection and icon-legibility
// assertions, but validate the current threshold contract rather than the old one.
has(frame, 'contentWidth + 1 >= requiredCompactOneRowWidth', 'wrapped header returns when compact action geometry fits');
has(frame, 'contentWidth + 1 < requiredCompactOneRowWidth', 'one-row wraps only after compact action geometry no longer fits');
has(frame, 'width = Math.max(46, width);', 'current normal header measurement uses the wrapped-row 46px footprint');
has(frame, 'if (compactChatWidth > 0) width = Math.min(width, compactChatWidth);', 'compact header measurement still allows PIP-size floor');

const css = read('kwc-standalone-frontend/src/main/resources/standalone/chat.css');
has(css, '5.3.0 RC35: public-header shrink order and icon legibility hotfix.', 'RC35 CSS block exists');
has(css, '#kwc-root:not(.kwc-minimized):not(.kwc-header-wrapped) .kwc-header-identity {', 'one-row identity has an explicit protected rule');
has(css, 'min-width: max-content !important;', 'one-row identity/title cannot be squeezed into the action buttons');
has(css, '#kwc-root:not(.kwc-minimized):not(.kwc-header-wrapped) .kwc-actions-secondary {', 'secondary actions are the shrinkable header region');
has(css, 'flex: 0 1 auto !important;', 'secondary action region is allowed to shrink before wrapping');
has(css, '#kwc-root:not(.kwc-minimized):not(.kwc-header-wrapped) .kwc-action-cluster-account > #kwc-login {', 'account label has a dedicated no-shrink rule');
has(css, 'flex: 0 0 auto !important;', 'account label does not consume icon shrink budget');
has(css, '#kwc-root:not(.kwc-minimized) #kwc-dm,', 'header icon content-size override exists');
has(css, 'font-size: max(15px, var(--kwc-button-font-size, 12px)) !important;', 'header action glyphs are slightly larger');
has(css, '#kwc-root .kwc-emoji-button {', 'emoji composer icon content-size override exists');
has(css, 'font-size: max(17px, var(--kwc-button-font-size, 12px)) !important;', 'emoji glyph is slightly larger');
has(css, '#kwc-root .kwc-button.kwc-upload {', 'upload glyph content-size override exists');
has(css, 'font-size: max(16px, var(--kwc-button-font-size, 12px)) !important;', 'upload glyph is slightly larger');
has(css, '#kwc-root .kwc-send {', 'send text content-size override exists');
has(css, 'font-size: max(13px, var(--kwc-button-font-size, 12px)) !important;', 'send/account text is slightly larger');

// RC35 still owns the content-size/title-protection rules; RC37 intentionally
// supersedes only the responsive threshold/normal action footprint.
check(!frame.includes('RC35') && !frame.includes('rc35'), 'responsive-header JS remains release-neutral rather than checkpoint-branched');

console.log(`RC35_HEADER_ICON_PASS assertions=${assertions}`);
