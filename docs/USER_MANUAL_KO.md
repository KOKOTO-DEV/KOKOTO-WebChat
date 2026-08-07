# BlueMapWebChat 4.6.1 통합 사용·운영 매뉴얼

이 문서는 BlueMapWebChat 4.6.1의 전체 기능을 사용자와 서버 운영자 관점에서 설명합니다. 단순 설정 키 목록은 `CONFIGURATION_KO.md`, 서버 간 릴레이의 상세 프로토콜은 `SERVER_RELAY_KO.md`, HTTPS 구성은 `CADDY_HTTPS_KO.md`와 `NGINX_HTTPS_KO.md`를 함께 참고하세요.

## 1. 플러그인 개요

BlueMapWebChat은 Minecraft Bukkit/Paper/Spigot 계열 서버의 게임 채팅을 웹 브라우저에 연결하는 플러그인입니다.

지원 형태:

- BlueMap 지도 안에 포함되는 채팅 애드온
- BlueMap 없이 사용하는 standalone 채팅 페이지
- BlueMap 애드온과 standalone 페이지 동시 운영
- 게임 ↔ 웹 공개 채팅
- 저장형 1:1 DM과 그룹 채팅
- DiscordSRV 연동
- 여러 Minecraft 서버 사이의 공개 채팅 릴레이

기본 HTTP 포트는 `8899`, API 기본 경로는 `/api`, standalone 기본 경로는 `/chat`입니다.

## 2. 요구사항과 권장 환경

필수:

- Java 21 이상을 사용하는 Bukkit/Paper/Spigot 호환 서버
- 플러그인 JAR을 넣을 수 있는 서버 관리 권한

선택:

- BlueMap: 지도 안에 채팅 패널을 표시할 때
- DiscordSRV: Discord 채널과 채팅을 연동할 때
- ImageEmojis-Bero 1.9.0: 게임에서 BMChat 이모지 토큰을 실제 이모지 glyph로 표시할 때
- Caddy 또는 Nginx: 공개 HTTPS 운영 시

공개 서버에서는 플러그인의 HTTP 포트 `8899`를 인터넷에 직접 공개하지 말고 `127.0.0.1:8899`로 제한한 뒤 HTTPS 리버스 프록시를 사용하는 구성을 권장합니다.

## 3. 설치와 최초 활성화

1. 빌드된 JAR을 서버의 `plugins/` 폴더에 넣습니다.
2. 서버를 한 번 시작합니다.
3. `plugins/BlueMapWebChat/config.yml`이 생성되었는지 확인합니다.
4. 새 설정의 `enabled` 기본값은 `false`입니다.
5. URL, 저장 방식, 보관 기간, 인증, 업로드 제한을 확인합니다.
6. 사용할 기능을 설정한 뒤 `enabled: true`로 변경합니다.
7. 서버를 재시작하거나 `/bmchat reload`를 실행합니다.

기본 안전 설정:

```yaml
config-version: "4.6.1"
enabled: false
```

`enabled: false`일 때는 웹 서버, 채팅 전달, 정리 작업이 시작되지 않습니다. 설정을 다시 읽기 위한 `/bmchat reload`는 관리자에게 계속 허용됩니다.

## 4. 설정 업그레이드와 마이그레이션 파일

기존 `config.yml`은 업데이트 시 자동으로 덮어쓰지 않습니다.

실행 중인 플러그인 버전과 `config-version`이 다르거나 설정에 버전이 없으면 다음 파일이 생성됩니다.

```text
plugins/BlueMapWebChat/config-migration-4.6.1.yml
```

판정 기준:

| 실제 `config.yml` 상태 | 동작 |
|---|---|
| `config-version` 없음 | 다른 차이가 0개여도 대상 버전 표식이 든 마이그레이션 파일 생성 |
| `config-version`이 플러그인 버전과 다름 | 누락·변경 설정과 대상 버전 표식이 든 마이그레이션 파일 생성·갱신 |
| `config-version`이 플러그인 버전과 같음 | 검토 완료로 간주하고 비교 및 파일 생성을 생략하며, 남은 동일 버전 안내 파일 제거 |

이 파일에는 다음 항목이 실제 YAML 설정 구조로 기록됩니다.

- 기존 설정에 없는 신규 설정
- 기존 값이 이전 기본값 그대로이며 새 버전에서 기본값이 변경된 설정
- 최종 검토 표식인 대상 `config-version`

다른 설정 차이가 없어도 설정 버전 관리를 위해 `config-version`이 포함된 파일을 생성합니다.

설명, 개수, 이전값은 `#` 주석으로만 표시됩니다. 실제 `config.yml`은 수정되지 않습니다.

적용 절차:

1. 마이그레이션 파일을 엽니다.
2. 필요한 설정 블록을 기존 `config.yml`의 같은 위치에 병합합니다.
3. 서버별 값과 사용자 지정값을 조정합니다.
4. 검토가 끝나면 실제 `config.yml`에 다음 값을 넣습니다.

```yaml
config-version: "4.6.1"
```

버전이 일치하면 이후 비교를 생략합니다.

## 5. 운영 방식 선택

### 5.1 BlueMap 애드온

```yaml
web-addon:
  auto-install: true
  auto-patch-webapp-conf: true

standalone-web:
  enabled: false
```

플러그인은 웹 자산을 BlueMap 웹 디렉터리에 설치하고 `plugins/BlueMap/webapp.conf`를 수정합니다. 웹 자산이 갱신되지 않으면 다음을 실행합니다.

```text
/bluemap reload
```

### 5.2 standalone 전용

```yaml
web-addon:
  auto-install: false
  auto-patch-webapp-conf: false

standalone-web:
  enabled: true
  path: "/chat"
```

직접 HTTP 예시:

```text
http://server.example.com:8899/chat
```

### 5.3 두 형태 동시 사용

BlueMap 안의 패널과 standalone 페이지는 같은 계정, 기록, 알림 및 설정을 공유합니다.

```yaml
web-addon:
  auto-install: true
  auto-patch-webapp-conf: true

standalone-web:
  enabled: true
```

## 6. HTTP, HTTPS와 공개 URL

### 6.1 직접 HTTP

테스트 또는 개인망에서만 권장합니다.

```yaml
http:
  host: "0.0.0.0"
  port: 8899
  path-prefix: "/api"
  cors-origin: "*"

web-addon:
  api-base-url: ""
```

### 6.2 같은 도메인의 HTTPS 리버스 프록시

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  cors-origin: "https://map.example.com"
  trusted-proxies:
    - "127.0.0.1"
    - "::1"

web-addon:
  api-base-url: "/bmwc/api"

standalone-web:
  enabled: true
  api-base-url: ""
```

공개 경로 예시:

```text
https://map.example.com/          BlueMap
https://map.example.com/bmwc/api  BMChat API
https://map.example.com/bmwc/chat standalone 페이지
```

`standalone-web.api-base-url`, `upload.public-base-url`, `emoji.public-base-url`은 보통 비워둡니다. 비어 있으면 활성 API 기본 주소를 자동으로 사용합니다.

### 6.3 프록시 IP 신뢰

`X-Forwarded-For`는 `http.trusted-proxies`에 등록된 직접 접속 프록시에서 온 경우만 신뢰합니다. 직접 HTTP 구성에서는 목록을 비워두세요.

문제 확인용:

```yaml
http:
  log-client-ip-resolution: true
```

이 옵션은 실제 클라이언트 IP 확인이 끝나면 다시 꺼야 합니다.

## 7. 웹 UI 기본 사용법

채팅 패널의 주요 영역:

- 메시지 목록
- 메시지 입력창
- 로그인·로그아웃
- DM 및 그룹 채팅 메뉴
- 검색
- 고정 메시지
- 이모지 선택기
- 파일 업로드
- 알림 설정
- 관리자 패널

사용자 UI 설정은 브라우저의 localStorage에 저장됩니다. 같은 계정이어도 브라우저나 기기가 다르면 테마, 폰트, 알림 필터가 다를 수 있습니다. 패널은 이동·크기 조절이 가능하고 설정에 따라 크기를 기억합니다. 브라우저 로컬 알림함에서는 최근 알림 대상 이벤트를 다시 확인할 수 있습니다.

기본 UI 설정:

```yaml
ui:
  language: "en-US"
  language-fallback: "en-US"
  time-zone: "local"
  theme: "system"
  opacity: 0.92
  resizable: true
  remember-window-size: true
```

지원 언어:

- `en-US`
- `ko-KR`
- `ja-JP`
- `zh-CN`

지원 테마:

- `system`
- `dark`
- `light`
- `high-contrast`

## 8. 공개 채팅

### 8.1 게임→웹

```yaml
chat:
  broadcast-ingame-chat-to-web: true
```

게임 플레이어의 일반 채팅을 웹 공개 채팅에 전달합니다. 플레이어 이름은 `player-display.mode`에 따라 결정됩니다.

### 8.2 웹→게임

```yaml
chat:
  send-web-chat-to-game: true
  web-user-to-game-format: "[Web] {player}: {message}"
  web-guest-to-game-format: "[Web Guest] {guest}: {message}"
  web-admin-to-game-format: "[Web Admin] {player}: {message}"
```

설정 템플릿의 `&` 색상 코드는 Minecraft 색상으로 변환되지만 사용자가 입력한 메시지 안의 색상 코드는 임의 변환하지 않습니다.

### 8.3 웹→웹

```yaml
chat:
  broadcast-web-chat-to-web: true
```

웹에서 보낸 공개 메시지를 현재 연결된 다른 웹 사용자에게 SSE로 전달합니다.

### 8.4 메시지 길이

```yaml
chat:
  max-message-length: 120
  max-url-message-length: 2048
```

일반 메시지와 URL 중심 메시지를 별도로 제한할 수 있습니다. `0`은 제한 없음입니다.

## 9. 채팅 기록과 검색

권장 저장 방식은 SQLite입니다.

```yaml
chat:
  history-storage: "sqlite"
  history-sqlite-file: "history.db"
  history-retention-days: 5
  history-size: 0
  history-page-size: 80
```

저장 방식:

- `sqlite`: 검색과 장기 운영에 권장
- `jsonl`: 단일 JSONL 파일을 사용하는 레거시 방식
- `memory`: 서버 재시작 시 기록이 사라짐

`history-retention-days: 0`은 기간 제한 없음, `history-size: 0`은 개수 제한 없음입니다.

JSONL에서 SQLite로 최초 이전:

```yaml
chat:
  history-sqlite-migrate-jsonl: true
```

검색:

```yaml
search:
  enabled: true
  result-limit: 50
```

검색할 수 있는 조건:

- 메시지 본문
- 작성자
- 날짜 및 시간 범위
- 메시지 출처
- 시스템·이벤트 메시지 포함 여부

검색 결과를 클릭하면 해당 메시지 주변 기록으로 이동합니다. 결과 제한을 지나치게 크게 설정하면 DB, 메모리와 응답 크기 부하가 증가합니다.

## 10. 계정 연동과 로그인

### 10.1 게임 계정 연동

웹 UI에서 연동 코드를 발급한 뒤 게임에서 실행합니다.

```text
/bmchat auth <code>
```

필요 권한:

```text
bluemapwebchat.auth
```

관련 설정:

```yaml
auth:
  enabled: true
  link-code-length: 6
  link-code-expire-seconds: 180
  link-code-cooldown-seconds: 3
  link-code-max-per-minute: 10
```

### 10.2 비밀번호 로그인

게임에서 웹 로그인 비밀번호를 설정합니다.

```text
/bmchat password <newPassword>
```

```yaml
auth:
  password-login: true
  remember-session-days: 30
```

비밀번호는 해시로 저장되지만 HTTP 전송은 암호화되지 않으므로 공개 운영에서는 HTTPS가 필요합니다.

### 10.3 역할

역할:

- `USER`
- `MODERATOR`
- `ADMIN`
- 로그인하지 않은 게스트 역할

권한 기반 자동 관리자:

```yaml
auth:
  auto-admin-from-permission: true
  admin-permission: "bluemapwebchat.admin"
```

### 10.4 로컬 관리자 계정

Minecraft UUID와 연결되지 않은 웹 관리자 계정을 만들 수 있습니다.

```yaml
admin:
  allow-local-admin-accounts: true
```

명령어:

```text
/bmchat admin create <id>
/bmchat admin password <id> <password>
/bmchat admin role <id> <user|moderator|admin>
```

### 10.5 세션 관리

```text
/bmchat sessions
/bmchat revoke <username>
```

`revoke`는 해당 사용자의 활성 웹 세션을 폐기하고 연결된 브라우저에 인증 만료를 알립니다.

## 11. 로그인 보안

```yaml
security:
  login-fail-limit: 5
  login-fail-window-seconds: 300
  login-lock-seconds: 600
  max-sse-connections-per-ip: 5
  max-sse-connections-total: 200
```

- 반복 로그인 실패 시 IP 기준 임시 잠금
- IP별 SSE 연결 수 제한
- 서버 전체 SSE 연결 수 제한

각 제한값의 `0`은 해당 제한 비활성화입니다.

관리자 로그인 IP 제한:

```yaml
admin:
  allow-admin-login-from: []
```

빈 목록은 IP 제한 없음입니다. 공개 관리자 계정은 HTTPS와 강한 비밀번호를 사용하세요.

## 12. 게스트 채팅과 캡차

```yaml
guest:
  enabled: true
  allow-custom-name: true
  name-prefix: "Guest-"
  cooldown-seconds: 6
  max-messages-per-minute: 50
  block-player-name-spoofing: true
```

`block-player-name-spoofing`은 게스트가 실제 플레이어 이름을 사칭하는 것을 제한합니다. `blocked-names`에는 관리자·서버를 사칭하기 쉬운 이름을 추가할 수 있습니다.

캡차:

```yaml
captcha:
  mode: "math"
  expire-seconds: 120
  require-on-each-message: false
  pass-valid-minutes: 120
```

`require-on-each-message: false`이면 한 번 통과한 뒤 설정 시간 동안 재사용할 수 있습니다.

게스트/IP 뮤트:

```text
/bmchat guest mute guest <name> [minutes] [reason]
/bmchat guest mute ip <address> [minutes] [reason]
/bmchat guest unmute guest <name>
/bmchat guest unmute ip <address>
/bmchat guest list
```

## 13. 플레이어 이름 표시, hover와 클릭

```yaml
player-display:
  mode: "name"
  strip-colors: true
```

모드:

- `name`: 실제 Minecraft 계정명
- `display-name`: 서버 표시명
- `custom-name`: 플러그인이 보관한 사용자 지정 표시명

웹 발신자의 게임 내 이름 hover:

```yaml
chat:
  game-name-hover:
    enabled: false
    text: "&f{real}"
```

지원 placeholder:

- `{display}`
- `{real}`
- `{uuid}`
- `{source}`

이름 클릭:

- 같은 서버 게임 플레이어: `/w <실제이름> `
- 웹 사용자: `/bmchat dm <실제이름> `
- 다른 서버 게임 플레이어: `/bmchat dm <실제이름>@<server-id> `

## 14. 공개 메시지 댓글

```yaml
reply:
  game-click:
    enabled: true
    local-game-chat: true
  game-command-format: "&8[&dReply&8] &f{player}&7: &f{message}"
  game-preview:
    enabled: true
    format: "&7{sender}: {preview}"
    max-length: 120
  game-prefix:
    enabled: true
    text: "↪ [Reply] "
```

메시지의 URL이 아닌 본문을 클릭하면 다음 명령이 자동완성됩니다.

```text
/bmchat reply <messageId> 
```

전송:

```text
/bmchat reply <messageId> <message>
```

필요 권한:

```text
bluemapwebchat.reply
```

`local-game-chat: true`는 일반 로컬 게임 채팅도 클릭 가능한 컴포넌트로 다시 출력합니다. 채팅 포맷 플러그인과 충돌하면 `false`로 변경하세요. 이 값을 꺼도 웹→게임과 서버 릴레이 메시지의 댓글 기능은 유지됩니다.

URL 조각은 링크 열기 동작이 우선하며 나머지 본문만 댓글 명령을 제안합니다.

## 15. 1:1 DM 메시지함

기본값은 비활성화입니다.

```yaml
direct-message:
  enabled: true
  storage: "auto"
  retention-days: 0
  max-messages-per-thread: 0
  max-message-length: 500
  allow-web-send: true
  allow-game-send: true
  capture-game-whispers: true
  notify-on-login: true
  notify-on-message: true
  web-unread-badge: true
  confirm-hide: true
```

대상은 서버가 UUID를 알고 있는 플레이어입니다. 로컬 접속·계정 연동 기록뿐 아니라 서버 릴레이로 받은 게임/연동 웹 메시지에 `playerUuid`가 있으면 해당 발신자의 표시 이름과 실제 Minecraft 이름을 DM의 새 대화 대상 검색에 등록합니다. 따라서 타 서버 메시지에서 본 이름을 DM 검색창에 입력해 기존 전송 경로로 대화를 시작할 수 있습니다. 최근 이름은 `known-display-names.yml`에 보존되며 재시작 후에도 검색됩니다. UUID가 없는 게스트·Discord 메시지는 DM 대상에 등록하지 않습니다. `storage: auto`는 공개 채팅 저장방식이 `jsonl`일 때 DM도 JSONL을 사용하고, 그 외에는 SQLite를 사용합니다. 필요하면 `sqlite` 또는 `jsonl`을 직접 지정할 수 있습니다.

게임 명령어:

```text
/bmchat dm
/bmchat dm list [pageSize]
/bmchat dm unread [pageSize]
/bmchat dm list next
/bmchat dm list prev
/bmchat dm <player> <message>
/bmchat dm read <player> [pageSize]
/bmchat dm next
/bmchat dm prev
/bmchat dm hide <messageId>
```

필요 권한:

```text
bluemapwebchat.dm
```

### 15.1 게임 귓속말 복제

`capture-game-whispers: true`이면 다음 명령의 내용을 같은 BMChat DM 스레드에도 저장합니다.

```text
/w /msg /tell /whisper /m /pm /message /t
```

같은 서버의 일반 Minecraft 귓속말은 대체하지 않고 기록만 복제합니다. 송신자와 수신자 모두 웹 DM에서 볼 수 있습니다. 타 서버 대상은 `이름@server-id`로 지정하며, 위 별칭들은 `/bmchat dm 이름@server-id <메시지>`로 변환되어 서명된 서버 간 DM 릴레이로 전송됩니다. 대상이 없는 `/r`, `/reply`는 기존 귓속말 플러그인의 최근 상대 상태와 충돌할 수 있으므로 가로채지 않습니다.

## 16. 그룹 채팅

```yaml
group-chat:
  enabled: true
  allow-web-send: true
  allow-public-rooms: true
  allow-room-passwords: true
  retention-days: 30
  max-messages-per-room: 1000
  max-message-length: 500
  max-rooms-per-user: 20
  max-members-per-room: 50
  max-room-name-length: 32
  invite-expire-hours: 72
  sqlite-file: "group-messages.db"
```

웹 기능:

- 공개·비공개 방 생성
- 비밀번호는 평문이 아니라 PBKDF2 해시로 저장
- 선택적 비밀번호
- 플레이어 초대
- 초대 수락·거절
- 방 나가기
- 내 목록에서 숨김·복원
- 방 설정 변경
- 멤버 강퇴·차단·차단 해제
- 방장 이전
- 안 읽음 수 추적

게임 명령어:

```text
/bmchat group
/bmchat group list
/bmchat group rooms
/bmchat group <room|id> <message>
/bmchat group send <room|id> <message>
/bmchat group read <room|id> [pageSize]
/bmchat group next
/bmchat group prev
```

별칭:

```text
/bmchat gc ...
```

필요 권한:

```text
bluemapwebchat.group
```

## 17. 시스템·이벤트 알림

```yaml
announcements:
  broadcast-to-web-chat: true
```

개별 이벤트를 켜거나 끌 수 있습니다.

기본 활성:

- 플레이어 입장
- 플레이어 퇴장
- 최초 입장
- 사망
- 발전 과제
- 서버 시작
- 서버 종료

기본 비활성:

- 월드 이동
- 게임 모드 변경
- 레벨 변경
- 침대 사용
- 웹 로그인
- 웹 로그아웃

메시지 템플릿은 설정에 적힌 fallback 문구이며 해당 i18n 키가 있으면 웹 사용자의 선택 언어로 번역됩니다.

## 18. 고정 메시지

```yaml
pinned:
  enabled: true
  max-pins: 20
  show-to-logged-out: true
  preserve-uploads: true
```

- 고정 메시지는 일반 기록과 별도로 저장됩니다.
- 상단의 축약 바에서 열 수 있습니다.
- `preserve-uploads: true`이면 고정 메시지가 참조하는 업로드 파일은 보관 정리에서 제외됩니다.
- 관리자·모더레이터의 고정/삭제 버튼은 실수 방지를 위해 관리 패널 토글을 켰을 때만 표시됩니다.

## 19. 파일과 클립보드 업로드

```yaml
upload:
  enabled: true
  allow-guest-upload: false
  allow-user-upload: true
  allow-moderator-upload: true
  allow-admin-upload: true
  cooldown-seconds: 5
  max-uploads-per-minute: 4
  max-file-size-mb: 20
  max-total-size-mb: 0
  max-files-per-message: 3
  directory: "uploads"
  retention-days: 5
  clipboard-upload-enabled: true
  clipboard-upload-send-mode: "insert"
```

지원 기본 확장자:

- 이미지: PNG, JPG, JPEG, GIF, WEBP
- 영상: MP4, WEBM
- 오디오: MP3, M4A, OGG, WAV, FLAC

`max-total-size-mb: 0`은 전체 제한 없음입니다. 제한을 사용하면 오래된 미참조 업로드를 먼저 정리하고, 그래도 부족하면 새 업로드를 거부합니다.

클립보드 모드:

- `insert`: URL을 입력창에 삽입
- `send`: 업로드 후 즉시 전송

자세한 보안 기준은 `UPLOAD_SECURITY_KO.md`를 참고하세요.

## 20. 미디어와 링크 미리보기

기본 업로드 미리보기:

```yaml
upload:
  preview-images: true
  preview-videos: true
  preview-audio: true
```

YouTube:

```yaml
preview:
  youtube-embed-enabled: true
  youtube-click-to-load: true
  youtube-nocookie: true
  youtube-max-embeds-per-message: 1
```

YouTube Shorts도 일반 YouTube 경로로 처리하며 세로 비율을 사용합니다. `ui.image-preview-max-per-message`와 `ui.image-preview-max-height`로 메시지별 미리보기 수와 최대 높이를 제한할 수 있습니다.

Google Drive 이미지 미리보기는 `ui.google-drive-image-preview`와 `ui.google-drive-preview-mode`로 선택적으로 켤 수 있습니다.

TikTok/X:

```yaml
preview:
  social-embeds:
    enabled: true
    click-to-load: true
    max-embeds-per-message: 2
    tiktok:
      enabled: false
    x:
      enabled: false
      theme: "auto"
      dnt: true
```

외부 임베드는 사용자 브라우저가 제3자 서비스에 요청하므로 공개 서버에서는 `click-to-load: true`를 권장합니다.

Discord CDN 캐시:

```yaml
preview:
  external-media-cache-enabled: true
  cache-discord-cdn: true
  external-media-cache-retention-days: 5
```

만료되는 Discord 첨부 URL을 서버에 임시 보관해 미리보기를 유지합니다.

## 21. 커스텀 이모지

```yaml
emoji:
  enabled: true
  show-button: true
  directory: "emojis"
  max-file-size-kb: 512
  max-total-size-mb: 64
  render-size-px: 32
  picker-size-px: 44
  message-token-limit: 12
  token-format: "short"
```

폴더 예시:

```text
plugins/BlueMapWebChat/emojis/default/wave.png
plugins/BlueMapWebChat/emojis/reaction/happy.gif
```

토큰:

```text
:default/wave:
:reaction/happy:
:emoji:default/wave:
```

`token-format: short`는 선택기에서 `:pack/name:`을 삽입하고 `legacy`는 `:emoji:pack/name:`을 삽입합니다. 두 형식 모두 읽을 수 있습니다.

관리자는 웹 이모지 관리 화면에서 폴더 생성, 파일 업로드, 이름 변경과 삭제를 할 수 있습니다. 이름을 바꾸면 과거 메시지 토큰이 더 이상 파일을 찾지 못할 수 있습니다.

### 21.1 게임 측 이모지 처리

기본값:

```yaml
emoji:
  game-link:
    enabled: false
```

`false`는 웹→게임 토큰을 그대로 보존합니다. ImageEmojis-Bero 같은 게임 플러그인을 사용할 때 권장됩니다.

BMChat 자체 변환을 사용할 때:

```yaml
emoji:
  game-link:
    enabled: true
    mode: "link"
    label-format: ":{id}:"
    max-links-per-message: 4
```

모드:

- `preserve`: 토큰 그대로
- `label`: 라벨만 출력
- `link`: 라벨과 짧은 이미지 URL 출력

## 22. ImageEmojis-Bero 1.9.0 연동

권장 ImageEmojis-Bero 설정:

```yaml
emojisFolder: "/BlueMapWebChat/emojis"
templateFormat: ":<emoji>:"
replaceInCommands: true
```

플레이어 권한:

```text
imageemojis.use
```

`replaceInCommands: true`는 `/bmchat reply`, `/bmchat dm`, `/bmchat group` 안의 토큰을 게임 glyph로 변환하는 데 필요합니다.

서버 릴레이 환경에서는 각 서버에 동일한 팩 이름과 파일 이름을 배치해야 합니다. BMChat은 웹 기록과 릴레이에는 원본 토큰을 보존하고 게임 출력 시 수신 서버의 runtime token→glyph 매핑을 사용합니다.

이모지 변경 후 권장 순서:

```text
/emojis reload
/emojis update
```

필요하면 재접속하여 리소스 팩을 갱신합니다. 상세 내용은 `IMAGEEMOJIS_BERO_1_9_0_KO.md`를 참고하세요.

## 23. 브라우저 알림과 Web Push

```yaml
notifications:
  enabled: true
  only-when-hidden: true
  notify-normal-chat: true
  notify-dm: true
  notify-group-chat: true
  notify-mentions: true
  notify-replies: true
  notify-system: true
  notify-keywords: true
  notify-own-messages: true
  show-message-preview: true
```

사용자는 브라우저별 설정에서 허용된 종류를 다시 켜고 끌 수 있습니다. 서버 설정이 `false`인 종류는 사용자가 활성화할 수 없습니다. 브라우저 로컬 알림함은 최근 알림 대상 이벤트를 보관하고, 알림 클릭은 가능한 경우 공개 메시지·댓글·DM 스레드·그룹방으로 이동합니다.

Web Push:

```yaml
web-push:
  vapid-public-key: ""
  vapid-private-key: ""
  subject: "mailto:admin@example.com"
  notification-title: ""
  ttl-seconds: 300
```

VAPID 키가 비어 있으면 플러그인이 지속 키 파일을 생성합니다. 공개 운영에서는 실제 관리자 메일 또는 사이트 URL을 `subject`로 사용하세요.

플랫폼 주의:

- Android·데스크톱: HTTPS 및 Push API 지원 브라우저에서 가능
- iOS/iPadOS: 홈 화면에 추가한 웹앱으로 열었을 때만 지원되는 경우가 많음

## 24. PWA와 PIP

standalone 앱 이름:

```yaml
standalone-web:
  app-name: "Web Chat"
  app-short-name: "Web Chat"
```

홈 화면 설치 후 이름을 변경했다면 웹앱을 다시 설치해야 반영될 수 있습니다.

Picture-in-Picture:

```yaml
ui:
  picture-in-picture:
    enabled: false
```

브라우저가 PIP를 지원해야 하며 창의 외부 제어는 브라우저와 운영체제가 담당합니다.

## 25. DiscordSRV 연동

```yaml
discordsrv:
  enabled: true
  channel: "global"
  web-to-discord: true
  game-to-discord: false
  discord-to-web: true
  ignore-bot-messages: true
  suppress-game-echo: true
  suppress-game-echo-seconds: 5
  send-web-user-chat-to-discord: true
  send-web-guest-chat-to-discord: false
  send-web-admin-chat-to-discord: true
  append-web-emoji-links: true
  append-game-emoji-links: true
  max-emoji-links-per-message: 4
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-to-discord-format: "[{server}] {sender}: {message}"
  discord-to-web-sender-format: "Discord:{sender}"
  discord-to-web-message-format: "{message}"
```

DiscordSRV가 일반 게임 채팅을 이미 Discord로 전달한다면 BMChat의 `game-to-discord`는 `false`로 유지해 중복을 막습니다.

공용 Discord 채널을 여러 서버가 사용할 때:

- 실제 원본 게임 채팅을 본 서버만 DiscordSRV 메시지를 가공
- 수신 릴레이 서버는 Discord로 다시 전송하지 않음
- `[Server]`, `[Web]` 접두사를 다른 서버가 반복해서 붙이지 않음

선택적 댓글 미리보기:

```yaml
discordsrv:
  reply-relay:
    enabled: false
    prefix-enabled: true
    preview-enabled: true
    preview-max-length: 120
```

## 26. 여러 서버 채팅 릴레이

서버마다 고유한 `server-id`를 사용합니다.

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  server-name: "Server1"
  shared-secret: "충분히-긴-공통-비밀키"
  connect-timeout-seconds: 5
  request-timeout-seconds: 10
  max-clock-skew-seconds: 60
  dedupe-seconds: 300
  max-hops: 8
  sources:
    game: true
    web: true
    guest: true
    discord: false
    system: false
  delivery:
    web: true
    game: true
  game-format: "&8[&b{server}&8] &f{sender}&7: &f{message}"
  peers:
    - id: "server2"
      url: "https://server2.example.com/bmwc/api"
      secret: ""
      enabled: true
```

실제 요청 경로:

```text
https://server2.example.com/bmwc/api/relay/receive
```

핵심 규칙:

- 받는 서버의 `peers[].id`는 보내는 서버의 `server-id`와 같아야 함
- 공통 키를 쓸 때는 연결된 서버의 `shared-secret`이 같아야 함
- `peers[].secret`이 비어 있지 않으면 공통 키보다 우선
- 서버 시간이 허용 오차보다 크게 다르면 요청 거부
- 오프라인 메시지를 나중에 보내는 영구 큐는 없음

표시:

- 현재 서버에서 발생한 메시지: 서버명 생략
- 다른 서버 메시지: 웹 색상 배지와 게임 `[서버명]` 표시

정상 로그:

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

오류:

- `403 unknown_peer`: 받는 서버에 발신 서버 ID가 활성 피어로 없음
- `401 bad_signature`: 비밀키 또는 요청 서명 불일치
- `401 expired_request`: 서버 시간 차이
- `404 relay_disabled`: 받는 서버 릴레이가 꺼짐 또는 프록시 경로 오류
- `426 unsupported_protocol`: 프로토콜 버전 불일치

자세한 구성은 `SERVER_RELAY_KO.md`를 참고하세요.

## 27. 웹 콘솔 명령어 패널

기본값은 비활성화입니다.

```yaml
commands:
  enabled: false
  allow-all: false
  min-role: "ADMIN"
  show-button: true
  run-from-chat-input: false
  show-when-input-starts-with-slash: true
  require-confirm: true
  max-length: 0
  broadcast-result-to-web-chat: false
```

`allow-all: true`는 웹 계정에서 임의 서버 콘솔 명령을 실행할 수 있으므로 매우 위험합니다. HTTPS, 관리자 IP 제한, 강한 비밀번호와 최소 역할 설정 없이 사용하지 마세요.

`presets`로 허용할 명령을 제한하는 구성이 권장됩니다.

```yaml
commands:
  presets:
    - id: "day"
      label: "낮으로 변경"
      description: "현재 월드 시간을 낮으로 설정합니다."
      command: "time set day"
      confirm: true
```

## 28. 관리자와 모더레이터 기능

웹 관리 기능은 설정과 역할에 따라 다음을 제공합니다.

- 메시지 숨김·삭제 표시
- 고정 메시지 관리
- 게스트 및 IP 뮤트
- 활성 세션 확인 및 폐기
- 커스텀 이모지 폴더·파일 관리
- 업로드와 저장 사용량 확인
- 비공개 채팅 메타데이터 확인
- 서버 콘솔 명령 패널

```yaml
moderation:
  enabled: true
  allow-web-admin-panel: true
  allow-moderator-message-delete: true
  allow-moderator-guest-mute: true
  default-mute-minutes: 60
```

### 28.1 비공개 채팅 메타데이터 최고관리자

```yaml
private-chat-super-admins:
  - "ExactMinecraftName"
  - "00000000-0000-0000-0000-000000000000"
```

기본적으로 볼 수 있는 정보:

- DM 스레드와 그룹방 제목·참여자
- 메시지 수
- 대략적인 저장 용량
- 보관 정책 상태
- 정리 미리보기와 메타데이터 관리

DM 본문 감사가 필요한 경우 다음 설정을 추가로 켭니다.

```yaml
direct-message:
  admin-audit:
    enabled: true
```

`private-chat-super-admins`와 `direct-message.admin-audit.enabled`가 모두 적용된 계정만 관리자 메타데이터 목록의 DM 세션을 눌러 읽기 전용으로 본문을 볼 수 있습니다. 일반 ADMIN/MODERATOR 역할만으로는 본문 접근 권한이 생기지 않습니다. 감사 화면에서는 메시지 전송, 참여자별 숨김, 읽음 처리 기능을 제공하지 않습니다. 페이지를 불러올 때마다 `admin.dm-audit-read` 기록이 감사 로그에 추가되며 본문 자체는 감사 로그에 복사하지 않습니다.

### 28.2 감사 로그

```yaml
audit:
  enabled: true
  directory: "audit"
```

관리 동작은 기본적으로 `plugins/BlueMapWebChat/audit` 아래 날짜별 로그에 추가됩니다. 웹 UI에는 표시되지 않습니다.

## 29. 웹 폰트와 표시 조정

```yaml
web-fonts:
  enabled: false
  directory: "fonts"
  items: []
```

예시:

```yaml
web-fonts:
  enabled: true
  items:
    - family: "Pretendard"
      file: "Pretendard.woff2"
      weight: 400
      style: "normal"
```

지원 확장자:

- WOFF2
- WOFF
- TTF
- OTF

UI 시각 설정:

```yaml
ui:
  font-size: 13
  message-font-size: 13
  input-font-size: 13
  text-color: ""
  ui-text-color: ""
  input-background-color: ""
  text-shadow-mode: "auto"
```

비어 있는 색상은 테마 기본값을 사용합니다.

## 30. 가상 스크롤과 성능

```yaml
ui:
  virtual-scroll:
    enabled: true
    overscan-screens: 0.75
    min-rendered-messages: 30
    preserve-visible-media: false
    preserve-playing-media: true
  history-preload:
    screens: 0.7
    min-px: 200
```

긴 채팅 기록에서는 가상 스크롤이 브라우저 렌더링 부하를 줄입니다. 미디어를 많이 유지하면 메모리 사용이 증가할 수 있습니다.

복귀 새로고침:

```yaml
ui:
  resume-refresh:
    enabled: true
    min-interval-seconds: 5
    skip-while-media-active: true
    skip-unchanged: true
```

모바일에서 앱으로 돌아왔을 때 누락 메시지를 갱신하되 재생 중 미디어를 방해하지 않도록 합니다.

## 31. 주요 명령어 전체 목록

사용자:

```text
/bmchat auth <code>
/bmchat password <newPassword>
/bmchat dm
/bmchat dm list [pageSize]
/bmchat dm unread [pageSize]
/bmchat dm <player> <message>
/bmchat dm read <player> [pageSize]
/bmchat dm next
/bmchat dm prev
/bmchat dm hide <messageId>
/bmchat reply <messageId> <message>
/bmchat group list
/bmchat group <room> <message>
/bmchat group send <room> <message>
/bmchat group read <room> [pageSize]
/bmchat group next
/bmchat group prev
```

관리자:

```text
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

명령 별칭:

```text
/bmc
/bluemapchat
```

그룹 별칭:

```text
/bmchat gc
```

## 32. 권한 전체 목록

```text
bluemapwebchat.auth      웹 계정 연동
bluemapwebchat.webchat   인증된 웹 채팅 사용
bluemapwebchat.dm        DM 송수신 및 조회
bluemapwebchat.reply     게임에서 공개 댓글 작성
bluemapwebchat.group     그룹 채팅 사용
bluemapwebchat.admin     플러그인 관리
bluemapwebchat.update.notify  업데이트 알림 수신(OP 기본)
```

기본값:

- 사용자 기능 권한은 기본 허용
- `bluemapwebchat.admin`, `bluemapwebchat.update.notify`는 OP 기본

## 33. 데이터 파일과 백업

주요 파일:

```text
plugins/BlueMapWebChat/config.yml
plugins/BlueMapWebChat/history.db
plugins/BlueMapWebChat/direct-messages.db
plugins/BlueMapWebChat/group-messages.db
plugins/BlueMapWebChat/web-push-subscriptions.jsonl
plugins/BlueMapWebChat/emojis/
plugins/BlueMapWebChat/uploads/
plugins/BlueMapWebChat/audit/
```

설정에 따라 이름은 달라질 수 있습니다.

권장 백업 대상:

- `config.yml`
- SQLite/JSONL 기록 파일
- `emojis/`
- 보존해야 하는 `uploads/`
- Web Push 구독과 VAPID 키 파일

SQLite 파일은 서버를 정상 종료한 뒤 복사하는 것이 가장 안전합니다.

## 34. reload와 재시작 구분

`/bmchat reload`로 적용 가능한 것:

- 대부분의 `config.yml` 변경
- HTTP 서비스와 서버 릴레이 재생성
- UI 기본 설정 갱신

서버 재시작이 필요한 것:

- JAR 교체
- Java 클래스 변경
- 다른 플러그인의 로드 순서 변경
- 환경에 따라 포트 점유나 웹 서버 자원 잠금이 남은 경우

BlueMap 웹 자산만 갱신되지 않으면 `/bluemap reload`를 추가로 사용합니다.

## 35. 문제 해결 빠른 표

### 웹 페이지가 열리지 않음

- `enabled: true` 확인
- `http.host`, `http.port` 확인
- 포트 충돌 확인
- 프록시 upstream이 `127.0.0.1:8899`인지 확인
- standalone이 필요하면 `standalone-web.enabled: true` 확인

### BlueMap 안에 버튼이 없음

- `web-addon.auto-install: true`
- `web-addon.auto-patch-webapp-conf: true`
- BlueMap 경로 설정 확인
- 서버 로그의 설치/패치 메시지 확인
- `/bluemap reload`
- 브라우저 캐시 새로고침

### 로그인 실패

- HTTPS/도메인 cookie 경로 확인
- 링크 코드 만료 여부 확인
- 로그인 잠금 로그 확인
- `auth.password-login` 확인
- 관리자 IP 제한 확인

### 웹 채팅은 보이지만 게임으로 안 감

- `chat.send-web-chat-to-game: true`
- 플레이어가 온라인인지 확인
- 다른 채팅 포맷 플러그인 충돌 확인
- 릴레이 메시지라면 `server-relay.delivery.game: true`

### 게임 채팅이 웹에 안 보임

- `chat.broadcast-ingame-chat-to-web: true`
- 플레이어 권한과 채팅 이벤트 취소 여부 확인
- 채팅 플러그인이 이벤트를 독점하는지 확인

### 댓글 클릭이 안 됨

- `reply.game-click.enabled: true`
- 로컬 게임 메시지면 `local-game-chat: true`
- 다른 채팅 포맷 플러그인과 충돌 여부 확인
- URL 부분은 댓글이 아니라 링크 열기가 정상

### 이모지 토큰이 그대로 보임

- BMChat 이모지 파일 존재 확인
- ImageEmojis-Bero의 공용 폴더와 권한 확인
- `replaceInCommands: true`
- `/emojis reload`, `/emojis update`
- 릴레이 서버마다 동일한 이모지 파일 확인

### 서버 릴레이 403

- 받는 서버의 `peers[].id`와 보내는 서버의 `server-id` 비교
- 받는 서버에서도 `/bmchat reload`
- 활성 피어 로그 확인

### 서버 릴레이 401

- 양쪽 실제 적용 비밀키 확인
- 프록시가 본문이나 HMAC 헤더를 변경하지 않는지 확인
- 서버 시간 동기화 확인

### Discord 서버명이 중복됨

- 모든 서버가 같은 수정 버전인지 확인
- 릴레이 수신 메시지를 별도 Discord 플러그인이 다시 보내는지 확인
- DiscordSRV가 게임 채팅을 전달하면 `game-to-discord: false`

### Web Push가 안 됨

- HTTPS 확인
- 브라우저 알림 권한 확인
- Service Worker/Push API 지원 확인
- iOS는 홈 화면 웹앱인지 확인
- VAPID subject와 키 파일 확인

## 36. 관련 문서

- `CONFIGURATION_KO.md`: 설정별 상세 설명
- `SERVER_RELAY_KO.md`: 릴레이 토폴로지, 인증과 오류
- `UPGRADE_4_6_1_KO.md`: 4.6.0→4.6.1 업그레이드
- `UPGRADE_4_6_0_KO.md`: 4.5.5→4.6.0 업그레이드
- `CADDY_HTTPS_KO.md`: Caddy HTTPS 구성
- `NGINX_HTTPS_KO.md`: Nginx HTTPS 구성
- `IMAGEEMOJIS_BERO_1_9_0_KO.md`: ImageEmojis-Bero 연동
- `INSTALL_TROUBLESHOOTING_KO.md`: 설치 문제 해결
- `UPLOAD_SECURITY_KO.md`: 업로드 보안
- `OPERATIONS_SECURITY_KO.md`: 공개 운영 보안
- `I18N_KO.md`: 다국어 파일 관리
- `RELEASE_CHECKLIST_KO.md`: 배포 전 검사
