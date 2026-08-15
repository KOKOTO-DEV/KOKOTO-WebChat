# BlueMapWebChat

Bukkit/Paper/Spigot 계열 서버에서 동작하는 웹 채팅 플러그인입니다. BlueMap 웹 애드온으로 띄울 수도 있고, BlueMap 없이 플러그인이 제공하는 standalone 채팅 페이지만 사용할 수도 있습니다.

## 주요 기능

- BlueMap 지도 안 채팅 패널 또는 standalone `/chat` 페이지 제공
- 게임 ↔ 웹 채팅 양방향 전달
- HMAC 서명 기반 서버 간 공개 채팅 릴레이와 서버별 웹 색상 배지, 게임/Discord 서버명 표시
- Minecraft 메시지 클릭 댓글(`/bmchat reply`)과 웹 발신자 클릭 BMChat DM(`/bmchat dm`)
- 게임 `/w`/`/msg`/`/tell`류 귓속말을 양쪽 사용자의 웹 DM으로 선택적 복제
- 게스트 채팅, 수학 캡차, 쿨다운/분당 제한
- `/bmchat auth <code>` 계정 연동, 웹 비밀번호 로그인, 로컬 관리자 계정
- 관리자/모더레이터 웹 패널, 메시지 숨김, 고정/삭제 표시 토글, 게스트/IP 뮤트, 세션 revoke
- 관리자 커스텀 이모지 관리: 이모지 폴더/파일 생성, 다중 업로드, 이름 변경, 이동, 삭제
- ImageEmojis-Bero 1.9.0 토큰·게임 댓글·서버 릴레이 호환
- 파일/클립보드 업로드, 이미지/영상/오디오/YouTube/Shorts 미리보기, 선택형 TikTok 및 X/Twitter 임베드
- DiscordSRV 연동, Discord CDN 미디어 캐시
- 답글 및 원본 메시지 점프, 게임 채팅 원문 미리보기, 고정 메시지, 가상 스크롤, 창 이동/크기조절, PIP
- 연동/저장된 플레이어 대상 1:1 대화 스레드형 메시지함, 안 읽은 배지, 스레드별 보관 설정
- 타 서버 릴레이 발신자를 기존 웹 DM 대상 검색에서 검색
- 정확한 계정 허용 목록과 감사 로그를 사용하는 선택형 읽기 전용 관리자 DM 본문 감사
- en-US, ko-KR, ja-JP, zh-CN 다국어 UI

## 빌드

```bash
mvn clean package
```

```text
target/BlueMapWebChat-4.7.0.jar
```

## 기본 설치

1. jar 파일을 `plugins/`에 넣습니다.
2. 서버를 한 번 실행해서 `plugins/BlueMapWebChat/config.yml`을 생성합니다.
3. 새로 생성된 config는 최상단 `enabled: false` 상태입니다. 설정 검토 전 웹 서버, 채팅 기능, 정리 작업이 실행되지 않게 하기 위한 안전 기본값이며, `/bmchat reload`는 계속 사용할 수 있습니다.
4. 저장 방식, 보관 기간, 업로드, 미리보기, 인증, 외부 공개 설정을 확인한 뒤 `enabled: true`로 바꿉니다.
5. BlueMap 안에 띄울 경우 `web-addon.auto-install`과 `web-addon.auto-patch-webapp-conf`를 `true`로 둡니다.
6. standalone만 쓸 경우 `standalone-web.enabled: true`, `web-addon.auto-install: false`, `web-addon.auto-patch-webapp-conf: false`로 둡니다.
7. 서버 재시작 또는 `/bmchat reload`를 실행합니다. BlueMap 쪽 웹 자원이 갱신되지 않으면 `/bluemap reload`도 실행합니다.


기존 설정값은 자동으로 덮어쓰지 않습니다. startup/reload 시 현재 `config.yml`의 알려진 최상위 블록은 4.7.0 기본 순서에 맞춰 재정렬되며, 각 블록의 현재 내용·설정값·사용자 지정 주석은 그대로 보존합니다. 기본 설정에 없는 최상위 블록은 알려진 블록 뒤에 기존 순서대로 유지합니다. 기존 설정을 확인할 때마다 BlueMapWebChat은 현재 JAR의 완전한 기본 설정을 주석까지 그대로 복사한 `plugins/BlueMapWebChat/config-reference-4.7.0.yml`을 생성하거나 최신 상태로 갱신합니다. 이 기준 파일은 기존 설정 버전과 관계없이 만들어지므로 4.5.x, 4.6.x, `config-version`이 없는 오래된 설정도 4.7.0 전체 구조와 직접 비교할 수 있습니다. `config-version`이 없거나 실행 중인 플러그인 버전과 다르면 추가로 `config-migration-4.7.0.yml`을 생성해 누락 설정, 변경된 기본값, 최종 `config-version` 검토 표식을 표시합니다. `message-tokens.custom: {}` 같은 빈 map도 실제 설정으로 취급해 누락된 경우 migration에 포함합니다. migration 파일 하단에는 현재 설정과 reference의 텍스트 diff를 주석으로 추가합니다. 동일한 줄은 출력하지 않으며, 각 차이는 파일명을 먼저 표시한 뒤 `Line` 또는 `Lines`를 별도 줄에 표시하고 그 아래에 실제로 다른 내용만 보여줍니다. 실제 차이 줄은 원본 YAML 들여쓰기를 그대로 유지하도록 줄 앞에 `#`만 직접 붙여 표시하며, reference에만 있는 블록은 현재 config에 넣을 위치도 따로 표시합니다. `config-version: "4.7.0"`이 이미 일치하면 migration 비교는 생략하지만 완전한 reference 파일은 계속 현재 기본값으로 유지합니다. 이번 업데이트는 `docs/UPGRADE_4_7_0_KO.md`, 이전 업데이트는 `docs/UPGRADE_4_6_3_KO.md`, 이전 업데이트는 `docs/UPGRADE_4_6_2_KO.md`, 이전 DM/감사 업데이트는 `docs/UPGRADE_4_6_1_KO.md`를 참고하세요.

## 4.7.0 이모지 다중 업로드 및 호환 범위 확대

4.7.0은 설정 가능한 `:token:` 메시지 치환도 추가합니다. 기본 alias는 영어만 제공하고, 관리자는 어떤 언어든 추가하거나 바꿀 수 있습니다. 줄바꿈/빈 줄/들여쓰기 action과 일반 문자 custom 치환을 지원하며, 알 수 없는 토큰은 기존 이모지 호환을 위해 그대로 둡니다.

4.7.0부터 관리자 이모지 업로드도 일반 채팅 파일 업로드와 같은 파일 선택 흐름을 사용합니다. 화면의 업로드 버튼이 숨겨진 다중 파일 입력창을 열고, 파일을 선택하면 선택 목록을 즉시 복사한 뒤 native input을 비우고 바로 순차 업로드를 시작합니다. 별도의 선택 확인용 업로드 단계는 없습니다. 진행률과 실제 전송 중 취소는 유지되며, 파일당 제한, 전체 이모지 용량 제한, 파일명 중복 처리, 감사 로그, PNG sidecar 생성은 기존 서버 업로드 경로를 그대로 사용합니다.

Bukkit/Spigot API 기준을 1.21에서 1.18로 낮추고 Java 17은 그대로 유지합니다. 이번 릴리스의 보수적인 Minecraft 지원 범위는 **1.18 ~ 26.2**입니다. Paper 전용 `AsyncChatEvent`는 계속 reflection으로 감지하고 Bukkit 구형 채팅 이벤트를 fallback으로 사용합니다.

자세한 내용은 `docs/UPGRADE_4_6_4_KO.md`를 참고하세요.

## 4.6.3 관리자 그룹채팅 감사

4.6.3은 그룹채팅 메시지 본문을 확인할 수 있는 선택적 읽기 전용 관리자 감사 기능을 추가합니다. DM 감사와 같은 `private-chat-super-admins` 정확 계정 목록을 사용하지만 `group-chat.admin-audit.enabled`는 독립적으로 켜야 합니다. 감사자는 방에 가입하지 않으며 읽음 상태·미확인 수를 변경하지 않고, 메시지 전송·업로드·숨김·멤버 관리도 할 수 없습니다. 모든 감사 페이지 열람은 본문을 복사하지 않고 `admin.group-audit-read`로 기록됩니다.

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

group-chat:
  admin-audit:
    enabled: true
```

4.6.3은 DM/그룹채팅 실시간 갱신 중 영상·오디오가 처음부터 다시 재생되는 문제도 수정합니다. 비공개 채팅 메시지 목록은 일반채팅처럼 stable key 기준으로 기존 메시지를 유지하고 새 메시지와 전달/읽음 메타데이터만 갱신하므로, 이미 로드된 미디어 DOM이 계속 유지됩니다.

자세한 내용은 `docs/UPGRADE_4_6_3_KO.md`를 참고하세요.

## 4.6.2 DM·그룹채팅 전송 상태 및 재시도

타 서버 DM은 수신 서버가 실제 저장을 확인하기 전까지 `pending` 상태로 유지되며, 최종 저장 확인 후에만 `delivered`가 됩니다. 전송 실패는 `failed`로 표시되고 같은 relay ID로 재시도하므로 응답만 유실된 경우에도 수신 메시지가 중복 저장되지 않습니다. 웹 DM과 그룹채팅도 client message ID를 사용해 브라우저 요청 결과가 불확실한 경우 같은 요청을 안전하게 재시도합니다. 서버가 지정되지 않은 DM 이름은 현재 서버 사용자만 대상으로 하며, 타 서버 사용자는 명시적으로 서버가 포함된 대상을 선택해야 합니다. DM과 그룹채팅의 모든 메시지는 시간 옆에 읽음 상태를 표시합니다. 1:1 DM은 상대가 읽기 전 `미확인`, 읽은 뒤 `✓`로 표시하고, 그룹채팅은 기존처럼 미확인 수신자 수를 숫자로 표시하다 0명이 되면 `✓`로 바뀝니다. 전송 상태도 짧게 `전송중`, 실패 시 `실패 · 재시도`만 표시합니다.

서버 간 DM을 주고받는 모든 서버는 BlueMapWebChat 4.6.2 이상을 사용하는 것을 권장합니다.

자세한 내용은 `docs/UPGRADE_4_6_2_KO.md`를 참고하세요.

## 4.6.1 타 서버 DM 대상·전송 경로 분리

타 서버 DM 대상은 이제 `서버 ID + 플레이어 UUID` 조합으로 식별합니다. 로컬 서버에 같은 UUID의 사용자가 있어도 `서버명 · 종류`를 누르면 해당 타 서버 사용자의 대화가 열리고, 서명된 전용 DM 릴레이를 통해 대상 서버로 전달됩니다.

서버 간 DM을 사용하는 모든 연결 서버는 BlueMapWebChat 4.6.1 이상을 사용해야 합니다.

## 4.6.1 타 서버 DM 검색과 관리자 감사 열람

서버 릴레이 메시지에 플레이어 UUID가 있으면 해당 타 서버 발신자를 기존 DM 새 대화 검색에서 표시합니다. 별도 DM 버튼은 추가하지 않으며, UUID가 없는 게스트·Discord 발신자는 제외합니다.

DM 본문 감사 기능은 기본적으로 꺼져 있습니다. 다음 두 조건을 모두 설정한 계정만 읽기 전용으로 열람할 수 있습니다.

```yaml
private-chat-super-admins:
  - "정확한마인크래프트이름또는UUID"

direct-message:
  admin-audit:
    enabled: true
```

일반 ADMIN/MODERATOR 역할만으로는 본문을 볼 수 없습니다. 감사 화면에서는 전송·숨김이 불가능하고, 페이지를 읽을 때마다 대화 ID와 조회 건수가 날짜별 감사 로그에 기록됩니다. 메시지 본문 자체는 감사 로그에 복사하지 않습니다.

## 사용 형태

### BlueMap 애드온 + standalone 동시 사용

```yaml
web-addon:
  auto-install: true
  auto-patch-webapp-conf: true

standalone-web:
  enabled: true
  path: "/chat"
```

### standalone 전용

```yaml
web-addon:
  auto-install: false
  auto-patch-webapp-conf: false

standalone-web:
  enabled: true
  path: "/chat"
```

standalone URL:

```text
http://<server-host>:8899/chat
```

## HTTPS / Caddy 권장 구성

공개 운영에서는 HTTP API를 직접 외부에 열지 말고, BlueMap과 BlueMapWebChat을 내부 HTTP로 두고 HTTPS 리버스 프록시 뒤에 두는 구성을 권장합니다.

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  cors-origin: "https://map.example.com"

web-addon:
  api-base-url: "/bmwc/api"


standalone-web:
  enabled: true
  path: "/chat"
  # 권장값은 빈 값입니다. web-addon.api-base-url을 따라갑니다.
  # 같은 경로를 명시하려면 "/bmwc/api"를 넣어도 됩니다.
  api-base-url: ""

upload:
  # 권장값은 빈 값입니다. 업로드 URL은 자동으로 /bmwc/api를 따라갑니다.
  # 기존 명시 방식도 동작합니다: "/bmwc/api" 또는 "/bmwc/api/uploads"
  public-base-url: ""
  # 0 = 무제한. 양수는 upload.directory 전체 파일 용량을 제한합니다.
  max-total-size-mb: 0

emoji:
  # 권장값은 빈 값입니다. 이모지 URL은 자동으로 /bmwc/api를 따라갑니다.
  # 기존 명시 방식도 동작합니다: "/bmwc/api" 또는 "/bmwc/api/emojis"
  public-base-url: ""
  max-total-size-mb: 64
  show-storage-usage: true
  show-storage-limit: true
```

예시 경로:

```text
https://map.example.com/          # BlueMap
https://map.example.com/bmwc/api  # BlueMapWebChat API
https://map.example.com/bmwc/chat # standalone 채팅
```


URL 설정 참고: HTTPS 리버스 프록시에서는 `web-addon.api-base-url`을 `/bmwc/api` 같은 공개 API 경로로 설정합니다. `standalone-web.api-base-url`, `upload.public-base-url`, `emoji.public-base-url`은 보통 비워둡니다. 비워두면 standalone은 `web-addon.api-base-url`을 재사용하고, 업로드/이모지는 각각 `/uploads`, `/emojis`를 자동으로 붙입니다. 기존 명시 방식인 `/bmwc/api`, `/bmwc/api/uploads`, `/bmwc/api/emojis`도 허용됩니다. 선행 `/`가 없는 상대값은 `http.cors-origin`이 실제 origin일 때 그 origin을 앞에 붙입니다.

자세한 내용은 `docs/CADDY_HTTPS_KO.md`를 참고하세요.

## 자주 쓰는 설정

- `ui.language`: 기본 UI 언어. `en-US`, `ko-KR`, `ja-JP`, `zh-CN`
- `ui.theme`: `system`, `dark`, `light`, `high-contrast`
- `player-display.mode`: `name`, `display-name`, `custom-name`
- `player-display.strip-colors`: `false`면 실제 채팅 작성자 이름에 Minecraft legacy 색상 코드를 렌더링합니다. 시스템/event 메시지는 항상 색상 코드를 제거합니다.
- `commands.enabled`: 웹 명령어 패널 사용 여부
- `commands.allow-all`: 프리셋 외 임의 콘솔 명령어 허용 여부
- `commands.run-from-chat-input`: 채팅 입력창의 `/command` 실행 허용 여부
- `ui.picture-in-picture.enabled`: PIP 버튼과 PIP 실행을 함께 제어합니다.

## 채팅 기록 보관기간

새로 생성된 config는 최상단 `enabled: false` 상태이므로, 보관 기간과 정리 관련 값을 검토하고 `enabled: true`로 바꾸기 전까지 자동 정리 작업이 실행되지 않습니다. 서버 정책에 맞게 채팅 기록, 업로드, 외부 미디어 캐시 보관 기간을 확인한 뒤 활성화하세요.

## 그룹 채팅방

`group-chat.enabled`를 켜면 웹 그룹 채팅방 기능을 사용할 수 있습니다. 사용자는 방을 만들고, 공개/비공개를 선택하고, 선택적으로 방 비밀번호를 설정하고, 저장된 플레이어를 초대하고, 초대 수락/거절, 방 나가기, 내 목록에서 방 숨김/다시 표시, 방 설정 변경, 멤버 강퇴/차단/차단 해제, 방장 이전, 웹 메시지 전송을 할 수 있습니다. 공개방은 방 목록에 보이고, 비공개방은 초대받은 사용자만 들어갈 수 있습니다. 방 비밀번호는 평문이 아니라 PBKDF2 해시로 저장됩니다.

그룹 채팅은 전용 SQLite 저장소를 사용합니다(`group-chat.sqlite-file`, 기본 `group-messages.db`). `group-chat.retention-days: 0`은 보관 기한 없음이고, 양수 값은 그룹 채팅 제목 옆에 보관 기간으로 표시되며 해당 기간이 지난 그룹 메시지는 물리 삭제됩니다. `group-chat.max-messages-per-room: 0`은 방별 개수 정리 없음입니다. 이번 릴리스는 웹 중심이며, 게임 명령어 `/bmchat group`, 방 음소거, 소유자/멤버 동작을 넘어선 세부 역할 관리 UI, 그룹 JSONL 저장은 아직 포함하지 않았습니다.

## 1:1 메시지함 / DM 스레드

`direct-message.enabled`를 켜면 1:1 대화 스레드형 메시지함을 사용할 수 있습니다. 대상은 UUID/이름이 저장된 연동·접속 기록 플레이어와, 서버 릴레이 메시지에서 UUID가 확인된 타 서버 플레이어입니다. 릴레이로 받은 표시 이름과 실제 Minecraft 이름도 웹 DM의 새 대화 대상 검색에 반영되므로 별도 DM 버튼 없이 이름을 검색해 대화를 시작할 수 있습니다. UUID가 없는 게스트·Discord 발신자는 대상에 포함되지 않습니다. A→B와 B→A는 같은 스레드를 사용하며, 저장은 UUID 기준으로 하고 UI는 가능하면 `표시명 (실제 계정명)` 형태로 표시합니다.

DM은 공개 채팅 기록과 분리된 전용 저장소를 사용합니다. `direct-message.storage: auto`는 공개 채팅이 `jsonl` 저장방식일 때 DM도 JSONL을 사용하고, 그 외에는 SQLite를 사용합니다. 필요하면 `direct-message.storage`를 `sqlite` 또는 `jsonl`로 직접 지정하고 `direct-message.sqlite-file` 또는 `direct-message.jsonl-file`을 사용할 수 있습니다. `direct-message.retention-days: 0`은 보관 기한 없음이며, 그 외 값은 DM 메시지함 제목 옆에 보관 기간으로 표시되고 해당 일수가 지난 DM 원문은 물리 삭제됩니다. `direct-message.max-messages-per-thread: 0`은 스레드별 개수 정리 없음입니다. `direct-message.confirm-hide`는 웹 UI에서 DM을 내 화면에서 숨길 때 확인창을 띄울지 정합니다. 개인 메시지가 서버에 저장되는 기능이므로 기본값은 비활성화이며, 서버 정책에 맞게 보관 주기를 정한 뒤 켜는 것을 권장합니다.


`direct-message.capture-game-whispers`를 켜면 게임의 `/w`, `/msg`, `/tell`류 명령을 같은 웹 DM 스레드에 복제할 수 있습니다. 같은 서버의 게임 발신자 이름을 클릭하면 `/w <실제이름> `, 웹 발신자는 `/bmchat dm <실제이름> `, 다른 서버의 게임 발신자는 `/bmchat dm <실제이름>@<server-id> `가 자동완성됩니다. 또한 `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, `/t`에서 대상에 `이름@server-id`를 사용하면 같은 타 서버 BMChat DM 릴레이로 전송됩니다. 서버를 붙이지 않은 `/bmchat dm <이름>`은 항상 현재 서버 사용자만 찾으며, 타 서버 대상은 `@server-id`를 명시하거나 웹 UI에서 해당 서버 사용자를 직접 선택해야 합니다.

## 커스텀 이모지와 게임 측 이모지 플러그인

BlueMapWebChat은 커스텀 이모지를 `plugins/BlueMapWebChat/emojis` 아래에 저장합니다. 하위 폴더는 이모지 팩으로 처리됩니다.

기본값에서는 웹→게임 채팅이 `:default/wave:`, `:emoji:default/wave:` 같은 커스텀 이모지 토큰을 그대로 보존합니다. ImageEmojis나 다른 게임 측 이모지 플러그인이 Minecraft 채팅에서 같은 토큰 텍스트를 렌더링한다면 이 기본값을 사용하세요.

`emoji.game-link.enabled`를 켠 경우 `emoji.game-link.mode`는 `preserve`, `link`, `label`을 지원합니다.

- `preserve`: 원래 토큰 텍스트를 변경하지 않습니다.
- `link`: 설정된 토큰 텍스트와 BM Web Chat 짧은 이미지 링크를 같이 보냅니다.
- `label`: 설정된 토큰 텍스트만 보냅니다.

`emoji.game-link.*`는 웹→Minecraft 채팅에만 적용됩니다. Discord 이미지 미리보기 링크는 별도 설정으로 분리됩니다. `discordsrv.append-web-emoji-links`는 웹→Discord 메시지용이고, `discordsrv.append-game-emoji-links`는 가능한 경우 DiscordSRV의 일반 Minecraft→Discord 릴레이 메시지를 수정해서 게임→Discord 토큰 URL을 붙입니다. 여러 서버가 같은 Discord 채널을 공유할 때는 실제 로컬 게임 채팅을 감지한 원본 서버만 그 DiscordSRV 메시지를 수정하고, 다른 서버는 서버명이나 이모지 링크를 중복해서 붙이지 않습니다. 수신 릴레이 서버는 해당 메시지를 Discord로 다시 보내지 않습니다. DiscordSRV가 일반 Minecraft 채팅을 이미 릴레이하고 있다면 중복 게시 방지를 위해 `game-to-discord`는 꺼두세요.

BM Web Chat은 웹 기록과 서버 릴레이 payload에는 원본 이모지 토큰을 그대로 보존합니다. ImageEmojis 또는 ImageEmojis-Bero가 활성화되어 있으면 공개된 runtime 이모지 저장소를 reflection으로 읽어, 클릭 가능한 Minecraft 컴포넌트를 만들 때 수신 서버의 현재 token→glyph 매핑을 사용합니다. 플러그인 hard dependency나 리소스팩 분석은 필요하지 않으며, 매핑하지 못한 토큰은 기존 게임 측 렌더링 경로로 fallback합니다.

GIF/JPG/JPEG/WEBP 이모지를 업로드하면, PNG만 읽는 게임 측 이모지 플러그인과의 호환을 위해 같은 폴더에 PNG sidecar도 생성합니다.

```text
plugins/BlueMapWebChat/emojis/default/wave.gif
plugins/BlueMapWebChat/emojis/default/wave.png
```

웹 UI는 원본 파일을 사용하므로 GIF 애니메이션은 유지됩니다. 게임 측 이모지 플러그인이 같은 이모지 디렉터리를 감시한다면 PNG sidecar를 사용할 수 있습니다. 이모지 추가/변경 후에는 해당 플러그인의 reload 명령을 실행하세요.

ImageEmojis-Bero 1.9.0 공용 폴더, 권한, 명령어 변환, 서버 릴레이 및 문제 해결은 [`docs/IMAGEEMOJIS_BERO_1_9_0_KO.md`](docs/IMAGEEMOJIS_BERO_1_9_0_KO.md)를 참고하세요.

## YouTube Shorts, TikTok, X/Twitter 미리보기

YouTube Shorts URL은 일반 YouTube 미리보기로 처리되고, 세로형 플레이어와 반복 재생을 사용하며 기본 활성화됩니다. TikTok과 X/Twitter는 선택형 social embed로 제공되며, 외부 콘텐츠를 브라우저에서 불러오므로 기본값은 비활성화입니다. TikTok은 채팅창 안에서 긴 본문/음악 정보가 내부 스크롤바를 만들지 않도록 공식 `player/v1` iframe을 사용하고 본문/음악 정보는 숨깁니다. 전체 정보는 원문 TikTok 링크에서 열 수 있습니다.

```yaml
preview:
  youtube-embed-enabled: true
  social-embeds:
    enabled: true
    click-to-load: true
    max-embeds-per-message: 2
    tiktok:
      enabled: false
    x:
      enabled: false
```

TikTok 또는 X/Twitter는 사용자 브라우저에서 외부 embed 요청이 발생해도 되는 서버에서만 켜는 것을 권장합니다. 공개 서버에서는 `click-to-load: true`를 유지해서 사용자가 미리보기를 열 때만 외부 콘텐츠가 로드되게 하는 편이 안전합니다.

## 명령어

```text
/bmchat dm <플레이어> <메시지>
/bmchat reply <메시지ID> <메시지>
/bmchat auth <code>
/bmchat password <newPassword>
/bmchat reload
/bmchat admin create <id>
/bmchat admin password <id> <password>
/bmchat admin role <id> <user|moderator|admin>
/bmchat guest mute <guest|ip> <value> [minutes] [reason]
/bmchat guest unmute <guest|ip> <value>
/bmchat guest list
/bmchat sessions
/bmchat revoke <username>
```

## 권한

```text
bluemapwebchat.auth
bluemapwebchat.webchat
bluemapwebchat.dm
bluemapwebchat.reply
bluemapwebchat.group
bluemapwebchat.admin
bluemapwebchat.update.notify
```

## 문서

- `docs/USER_MANUAL_KO.md` - 전체 기능 사용자·운영자 통합 매뉴얼
- `docs/CONFIGURATION_KO.md` - 설정 참고
- `docs/SERVER_RELAY_KO.md` - 서버 간 공개 채팅 릴레이
- `docs/UPGRADE_4_6_2_KO.md` - 4.6.1→4.6.2 업그레이드
- `docs/UPGRADE_4_6_1_KO.md` - 4.6.0→4.6.1 업그레이드
- `docs/UPGRADE_4_6_0_KO.md` - 4.5.5→4.6.0 설정/DB 업그레이드
- `docs/CADDY_HTTPS_KO.md` - HTTPS 리버스 프록시
- `docs/I18N_KO.md` - 다국어 파일과 fallback
- `docs/INSTALL_TROUBLESHOOTING_KO.md` - 설치/업그레이드/문제 해결
- `docs/UPLOAD_SECURITY_KO.md` - 업로드 보안
- `docs/RELEASE_CHECKLIST_KO.md` - 릴리스 체크리스트
- `docs/STANDALONE_REVIEW_KO.md` - BlueMap 의존성/standalone 모드 점검
- `docs/OPERATIONS_SECURITY_KO.md` - 공개 운영, trusted proxy 로그, 보안 체크리스트

## 주의

HTTP 전용 사용은 개인/테스트 용도로만 권장합니다. 비밀번호는 서버에 해시로 저장되지만, HTTP 로그인 트래픽 자체는 암호화되지 않습니다. 공개 운영에서는 HTTPS를 사용하세요.

폰트 참고: 설치된 글꼴은 CSS font-family 이름으로 입력해야 합니다. 채팅 설정의 확인 버튼으로 권한 요청 없이 현재 브라우저에서 해당 이름이 적용 가능한지 추정할 수 있습니다.

## SQLite 기록 검색

SQLite 기록 저장소를 사용할 때 채팅 패널 우측 상단 플로팅 영역의 돋보기 버튼으로 메시지 내용과 작성자를 검색할 수 있습니다. 검색 옵션에서 날짜/시간 범위, 작성자, 출처, 시스템/이벤트 포함 여부도 지정할 수 있습니다. 검색 결과는 스크롤 가능한 목록으로 표시되며, 채팅 테마와 폰트 설정을 따릅니다. 검색 결과를 클릭하면 기존 주변 기록 로드 방식으로 해당 메시지 위치로 이동합니다. i18n 키가 있는 시스템/이벤트 메시지는 가능한 경우 선택한 웹 UI 언어 기준으로 검색되고 표시됩니다. `search.result-limit` 하나가 웹 UI 결과 수와 `/history/search` API 제한을 모두 제어하며 별도 내부 최대치는 없습니다. 10000이나 100000처럼 매우 큰 값도 허용되지만, 검색 속도 저하, 응답 크기 증가, CPU/메모리/DB 부하 증가를 일으킬 수 있습니다.

### 비공개 채팅 메타데이터 최고관리자

`config.yml`의 `private-chat-super-admins`에 정확한 UUID 또는 마인크래프트 이름을 지정하면 관리/용량 확인용 DM/그룹채팅 메타데이터를 볼 수 있습니다. 기본 화면은 제목/참여자, 메시지 수, 대략적인 저장 용량, 보관기간 상태와 정리 미리보기를 표시합니다. `direct-message.admin-audit.enabled: true`를 함께 설정한 경우 같은 명시 계정이 DM 본문을 읽기 전용으로 열 수 있습니다. 4.6.3에서는 `group-chat.admin-audit.enabled: true`를 별도로 켜면 같은 명시 계정이 방에 참여하거나 읽음 상태를 변경하지 않고 그룹채팅 본문도 읽기 전용으로 열 수 있습니다. 일반 ADMIN/MODERATOR 역할은 자동으로 대상이 되지 않으며 감사 페이지 열람은 모두 감사 로그에 기록됩니다. 최고관리자는 DM/그룹 세션 잠금과 자동삭제 제외도 관리할 수 있습니다.

관리적으로 영향을 주는 행동은 기본적으로 `plugins/BlueMapWebChat/audit` 아래 날짜별 텍스트 로그에 append 됩니다. audit 로그는 서버 운영자 확인용이며 웹 UI에는 표시하지 않습니다.


참고: `standalone-web.app-name`/`standalone-web.app-short-name`으로 모바일 홈 화면 웹앱 이름을 바꿀 수 있고, `web-push.notification-title`로 기본 푸시 제목을 바꿀 수 있습니다. `web-push.notification-title`을 비워두면 `standalone-web.app-name`을 사용합니다. Android/데스크톱 브라우저는 HTTPS와 Push API 지원이 맞으면 BlueMap addon 또는 standalone 페이지 어디서든 푸시를 켤 수 있습니다. iOS/iPadOS 푸시는 일반 탭이 아니라 홈 화면에 추가한 웹앱에서만 시도하세요.


기존 config에 `BlueMapWebChat` 또는 `BM WebChat` 같은 예전 기본 이름이 남아 있으면 새 기본 이름처럼 처리해서 푸시 제목에 그대로 노출되지 않게 했습니다.
