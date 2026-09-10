// KWC 파일 안내 / KWC file guide
// 사용자 업로드 경로가 메타데이터 제거 결과를 quota/저장/응답 크기에 일관되게 사용하는지 정적으로 검증한다.
// Statically verifies that the user-upload path consistently uses metadata-stripped bytes for quota, storage, and response size.
'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
const server = fs.readFileSync(path.join(root, 'kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java'), 'utf8');
let assertions = 0;
function check(v, name) { assertions++; if (!v) throw new Error('FAIL: ' + name); }
const call = 'byte[] uploadData = ImageMetadataStripper.stripForUpload(file.data, ext);';
check(server.includes(call), 'user upload invokes metadata stripper after extension validation');
const at = server.indexOf(call);
const tail = server.slice(at, at + 2200);
check(tail.includes('ensureUploadQuotaAvailable(dir, uploadData.length, config)'), 'quota uses stripped byte length');
check(tail.includes('writeUploadWithNamePolicy(dir, original, ext, uploadData, config)'), 'storage writes stripped bytes');
check(tail.includes('res.put("size", uploadData.length)'), 'upload response reports stripped byte length');
check(!tail.includes('writeUploadWithNamePolicy(dir, original, ext, file.data, config)'), 'raw metadata-bearing bytes are not written');
console.log(`IMAGE_METADATA_INTEGRATION_PASS assertions=${assertions}`);
