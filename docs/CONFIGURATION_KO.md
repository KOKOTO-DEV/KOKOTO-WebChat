# KOKOTO WebChat 설정 참고

## 5.0.0 금지어 필터, Web Admin 설정, 세션, 업로드 파일명

`content-filter`는 게임/웹 공개채팅, 그룹채팅, 선택적으로 DM에 공통 적용되는 로더 중립 필터입니다. 대량 필터 단어는 `plugins/KOKOTO-WebChat/filter-lists/*.txt`에 UTF-8 텍스트 파일로 저장하며 한 줄에 하나씩 적습니다. 빈 줄과 `#`으로 시작하는 주석은 무시됩니다. 각 목록은 기본 `차단`이며 Web Admin에서 목록별로 `차단` 또는 `필터링`을 선택할 수 있습니다. `필터링`은 `content-filter.mask.text`로 일치 부분을 마스킹합니다. 세밀한 차단·마스킹·치환은 `config.yml`의 `content-filter.rules` 커스텀 규칙으로 관리합니다. `replace`의 `first`는 첫 번째 치환어를 항상 사용하고 `random`은 후보 중 하나를 무작위로 선택하며, 단어별 매핑이 있으면 그 값이 우선합니다. Unicode 정규화와 compact/interleave 검사는 공백·특수문자·호환문자·`욕1설` 같은 제한된 끼워넣기 우회를 탐지합니다. 등록된 KWC 이모지 토큰은 차단/필터링에 걸리지 않도록 보호하며, 등록되지 않은 `:가짜:` 형식은 일반 텍스트로 검사됩니다.


### 커스텀 필터 빠른 사용법

단순한 금지어가 많으면 **Filter word lists**의 UTF-8 TXT 파일을 먼저 사용하세요. 한 줄에 한 단어를 적고 목록 동작을 **차단** 또는 **필터링(마스킹)**으로 고르면 됩니다. **Custom rules**는 단어마다 동작을 다르게 하거나 `replace`, 여러 치환 후보, 단어별 치환이 필요한 경우에 적합합니다.

- **Block**: 대상 단어가 하나라도 선택된 매치로 잡히면 메시지 전체 전송을 막습니다. 예: 대상 단어 `광고문구`, `사기링크`.
- **Mask**: 일치 범위만 `content-filter.mask.text`의 값(기본 `***`)으로 바꿉니다. 예: `욕설1` → `***`.
- **Replace + First**: 여러 대상 단어가 공통 치환 후보 목록을 사용하며 항상 첫 번째 후보를 씁니다.
- **Replace + Random**: 공통 치환 후보가 여러 개면 매치마다 하나를 무작위로 고릅니다.
- **Per-word replacements**: `바보 => 귀여운 사람`처럼 단어마다 다른 결과를 지정합니다. 왼쪽 단어는 **Target words에 따로 적지 않아도 자동으로 검사 대상**이 되며 공통 치환 후보보다 우선합니다.

Web Admin 입력 예:

```text
Rule ID: soften-words
Action: Replace
Target words:
바보
멍청이

Replacement candidates:
순한말
다른표현

Replacement mode: First

Per-word replacements:
멍청이 => 실수한 사람
```

위 규칙에서는 `바보`가 `순한말`로 바뀌고, `멍청이`는 단어별 매핑이 우선되어 `실수한 사람`으로 바뀝니다.

우회 탐지는 다음처럼 생각하면 됩니다.

- `compact-match`: 공백/구분 기호를 제거했을 때 같은 단어가 되는 `욕 설`, `욕-설` 같은 입력을 탐지합니다.
- `interleave-match`: `interleave-max-gap` 범위에서 문자·숫자를 끼운 `욕1설`, `욕x설` 같은 입력을 탐지합니다.
- `interleave-unlimited-gap: true`: 글자 사이 간격 제한을 없애므로 오탐 범위도 커질 수 있습니다. 특별한 이유가 없으면 기본 `false`를 권장합니다.
- 자음만 등록한 `ㅅㅂ` 같은 규칙은 **자음 축약 규칙**으로 취급합니다. `ㅅㅂ`, `ㅅ ㅂ`, 허용 gap 안의 `ㅅxㅂ` 같은 입력을 잡되, 초성이 같다는 이유만으로 `신발`, `새벽` 같은 완성형 단어를 매치시키지 않습니다.
- `시발`처럼 완성형 한글로 등록한 규칙은 완성 음절 단위로 비교하므로 `신발`과 구분됩니다.

규칙을 저장한 뒤에는 같은 Filter 화면의 **Test**를 먼저 사용하세요. 실제 메시지를 보내지 않고 TXT 목록과 커스텀 규칙을 함께 평가하며, `content-filter.enabled: false` 상태에서도 규칙 자체를 시험할 수 있습니다. 테스트 결과의 `Rule`, `Word`, `Match` 값으로 어떤 규칙이 `literal`, `compact`, `interleave` 중 어느 방식으로 잡혔는지 확인할 수 있습니다.

`mappings`의 왼쪽 값도 자동으로 `words`에 합쳐집니다. 따라서 같은 단어를 양쪽에 중복해서 적을 필요가 없습니다. 동일 범위를 TXT 목록과 커스텀 규칙이 동시에 잡으면 커스텀 규칙이 먼저 평가되지만, 메시지의 다른 위치에서 별도의 `block` 매치가 있으면 메시지 전체가 차단됩니다.


Web Admin에는 **Filter**와 **Settings** 탭이 추가됩니다. Filter에서는 적용 범위, 우회 탐지 옵션, 목록별 차단/필터링을 선택할 수 있는 필터 단어 목록, 커스텀 규칙 추가/수정/삭제, 실제 전송 없는 테스트를 관리합니다. Settings에는 실시간 변경해도 안전한 게스트/CAPTCHA, 인증·세션, 사용자 프로필, upload, Discord 관리자 알림 설정만 노출합니다. `moderation.*`와 relay/network/adapter 구조는 `config.yml` 전용으로 유지합니다. 게임에서는 `/kchat filter ...`, `/kchat settings ...`를 사용합니다.

`auth.remember-session-days`를 바꾸면 기존 USER/MODERATOR 세션을 각 세션의 최초 `createdAt` 기준으로 재계산하고, `admin.admin-session-expire-hours`는 ADMIN 세션만 독립적으로 재계산합니다. `0`은 일반 USER/MODERATOR 세션과 ADMIN 세션 모두 무제한을 뜻합니다. 이미 만료된 세션은 기간을 늘리거나 무제한으로 바꿔도 부활하지 않으며, 새 기간보다 오래된 세션은 즉시 만료됩니다. `config.yml` 수정 후 시작/reload할 때도 같은 정책을 적용합니다.

`upload.filename-mode` 기본값은 `random`입니다. `original`은 새 업로드에서 안전한 Unicode 원본 파일명을 보존하되 경로/제어/파일시스템 금지문자를 정리하며, 동명이면 `-2`, `-3` ... 접미사를 붙여 기존 파일을 덮어쓰지 않습니다. 기존 업로드 파일명은 변경하지 않습니다.


`plugins/KOKOTO-WebChat/config.yml` 기준 설명입니다.

## 설정 버전과 마이그레이션 설정 조각

`config-version`은 자동 migration 동작을 선택하는 표식입니다. migration의 **유일한 템플릿은 실행 중인 플러그인에 번들된 `config.yml`**입니다. `config-reference-<plugin-version>.yml`은 관리자가 전체 기본 설정을 확인하도록 번들 기본 config를 그대로 복사한 파일일 뿐이며, migration 원본으로 사용하지 않습니다.

`config-version`이 없거나 다른 버전이면 기존 설정값을 읽은 뒤 새 번들 기본 `config.yml`을 만들고 그 위에 기존 사용자 값을 덮어씁니다. 이전 표식에 `*_auto_migration`이 없었다면 사용자가 한 번 고정한 설정으로 보고 재구성 전에 기존 `config.yml` 전체를 백업합니다. 기존 주석·순서·공백·들여쓰기는 가져오지 않고 최신 번들 주석/레이아웃을 사용하며, 사용자 설정값은 보존합니다. 제거된 설정은 다시 복사하지 않습니다. 결과는 `<plugin-version>_auto_migration`으로 표시합니다. 이 표식이 남아 있으면 startup/reload마다 같은 방식으로 최신 번들 기본 config를 다시 뼈대로 만들고 현재 값을 덮어써서 새 설정과 최신 주석/레이아웃을 자동 반영합니다. 정확한 `<plugin-version>`은 현재 버전 설정을 고정했다는 뜻이므로 같은 버전 startup/reload에서는 `config.yml`을 수정하지 않습니다.

`config-migration-<plugin-version>.yml`은 검토/diff 보고서입니다. 이전 버전의 `config-reference-*`, `config-migration-*`, `config-upgrade-*` 생성 파일은 자동 삭제합니다. 실제 버전 업그레이드의 기본값 변경 판정에 필요한 JAR 내부 `config-baselines/*`만 유지합니다.
## 전체 활성화 스위치

새로 생성된 config는 최상단 `enabled: false` 상태입니다. 이 상태에서는 KOKOTO WebChat이 config를 생성/로드하기만 하고 `/kchat reload`만 계속 사용할 수 있으며, 웹/채팅 서비스, 리스너, Discord 연동, DM 저장소, 애드온 설치, 업로드/이모지 초기화, 정리 작업을 시작하지 않습니다. 기존 config에 이 키가 없으면 업그레이드 호환성을 위해 활성 상태로 처리합니다. 저장 방식, 보관 기간, 업로드, 미리보기, 인증, 외부 공개 설정을 확인한 뒤 `enabled: true`로 변경하세요.

## 업데이트 확인

```yaml
update-check:
  enabled: true
```

활성화하면 KOKOTO WebChat이 Bukkit, Fabric, NeoForge, Forge 모두에서 백그라운드로 Modrinth의 최신 정식 버전을 확인합니다. KWC `kokoto-webchat` 프로젝트를 먼저 조회하고 배포 전환 기간에는 기존 `bluemapwebchat` 프로젝트로 fallback합니다. OP 또는 `kwc.update.notify` 권한 보유자가 로그인하면 제한된 주기로 다시 확인하므로 새 릴리스 감지가 정기 확인 결과에만 의존하지 않습니다. 5.0.0의 CurseForge 알림 링크는 새 KWC listing이 실제 활성화되기 전까지 기존 BMWC bridge 페이지를 사용합니다. 확인 주기, 릴리스 채널, 접속 알림 지연은 내부 기본값으로 유지합니다. 업데이트 조회 실패는 서버 시작을 막지 않으며 경고 로그로 기록됩니다.

## 배포 모드

### BlueMap 애드온

```yaml
adapters:
  bluemap:
    auto-install: true
    auto-patch-webapp-conf: true
```

Bukkit에서는 BlueMap webroot 아래 `addons/kokoto-web-chat`에 파일을 설치하고 `webapp.conf`에 script/style 항목을 추가합니다. Fabric/NeoForge 및 Forge 26.1.2/26.2에서 BlueMap 모드를 사용할 때는 BlueMapAPI 2.8.0으로 web root를 얻고 JS/CSS를 등록하며, `auto-patch-webapp-conf`와 두 BlueMap 경로 override는 사용하지 않습니다.

### standalone 전용

```yaml
adapters:
  bluemap:
    auto-install: false
    auto-patch-webapp-conf: false

frontend:
  standalone:
    enabled: true
    path: "/"
```

`http://<server-host>:8899/`로 접속합니다.

### HTTPS reverse proxy

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  public-prefix: "/chat"
  cors-origin: "https://map.example.com"

frontend:
  standalone:
    enabled: true
    path: "/"
    api-base-url: ""

adapters:
  bluemap:
    enabled: true
    api-base-url: ""

upload:
  # 권장값은 빈 값입니다. 업로드 URL은 활성 API base를 자동으로 따라갑니다.
  # 기존 명시값도 동작합니다: "/chat/api" 또는 "/chat/api/uploads"
  public-base-url: ""

emoji:
  # 권장값은 빈 값입니다. 이모지 URL은 활성 API base를 자동으로 따라갑니다.
  # 기존 명시값도 동작합니다: "/chat/api" 또는 "/chat/api/emojis"
  public-base-url: ""
```

### 공개 URL 옵션 규칙

- `http.path-prefix`는 플러그인 내부 HTTP API 경로입니다. 기본값 `/api`는 그대로 둡니다.
- `adapters.bluemap.api-base-url`은 BlueMap 내장 채팅이 사용할 공개 API base입니다. HTTPS 리버스 프록시에서는 보통 `/chat/api`로 설정합니다.
- `frontend.standalone.api-base-url`은 보통 비워둡니다. 직접 HTTP에서는 `http.path-prefix`, 리버스 프록시에서는 `http.public-prefix + http.path-prefix`를 사용하며 기본 공개 API는 `/chat/api`입니다. BlueMap adapter override는 상속하지 않습니다.
- `upload.public-base-url`은 보통 비워둡니다. 비워두면 활성 API base에 `/uploads`를 붙입니다. 예: `/chat/api/uploads`.
- `emoji.public-base-url`은 보통 비워둡니다. 비워두면 활성 API base에 `/emojis`를 붙입니다. 예: `/chat/api/emojis`.
- 명시값도 허용됩니다. `/chat/api`를 넣으면 upload는 `/uploads`, emoji는 `/emojis`를 자동으로 붙이고, `/chat/api/uploads`, `/chat/api/emojis`를 넣으면 그대로 사용합니다.
- 선행 `/`가 없는 상대값, 예: `chat/api`, `chat/api/uploads`, `chat/api/emojis`는 `http.cors-origin`이 실제 origin일 때 그 origin을 앞에 붙입니다. `cors-origin: "*"`이면 같은 origin 절대경로처럼 `/chat/api...`로 처리합니다.
- `https://map.example.com/chat/api` 같은 전체 URL은 그대로 사용합니다.

### 업로드 저장 용량 제한

`upload.max-total-size-mb`는 `upload.directory` 바로 아래 일반 파일의 총 용량을 제한합니다. 기본값 `0`은 무제한입니다. 새 업로드로 제한을 넘게 되면 KOKOTO WebChat은 오래된 미참조 업로드부터 삭제합니다. 채팅 기록, SQLite 기록, DM/그룹 메시지, 보존 대상 고정 메시지에서 참조 중인 파일은 유지됩니다. 정리 후에도 공간이 부족하면 업로드가 거부됩니다.

### 이모지 용량 표시

`emoji.max-total-size-mb`는 커스텀 이모지 전체 용량을 제한합니다. 제한을 초과하면 관리자 업로드 화면에서 경고가 표시됩니다. `emoji.show-storage-usage`는 현재 이모지 용량 표시 여부, `emoji.show-storage-limit`는 전체 용량 제한 표시 여부를 제어합니다.

## 채팅 기록 저장

채팅 기록 보관은 `chat.history-storage`로 `memory`, `jsonl`, `sqlite` 중 하나를 고르고, `chat.history-size`와 `chat.history-retention-days`를 세 모드가 공통으로 사용합니다. `0`은 각각 개수/기간 제한 없음입니다. 새로 생성된 config는 최상단 `enabled: false` 상태이므로, 이 값들을 검토하고 `enabled: true`로 바꾸기 전까지 정리 작업이 실행되지 않습니다. 서버 정책상 자동 정리가 필요하면 `30`, `90` 같은 양수 보관일을 설정하세요. 업로드와 외부 미디어 캐시 보관 설정도 같은 방식으로 동작합니다. `chat.history-file`은 JSONL에서만, `chat.history-sqlite-file`은 SQLite에서만 사용됩니다.


## 메시지 토큰

`message-tokens.enabled`는 콜론으로 감싼 관리자 정의 텍스트/제어 alias를 활성화합니다. config에는 콜론 없이 alias만 적고, 예를 들어 `enter`는 채팅에서 `:enter:`로 입력합니다. 기본 alias는 영어만 제공하며 관리자가 원하는 언어로 바꾸거나 추가할 수 있습니다. 등록되지 않은 alias는 그대로 유지하므로 커스텀/ImageEmojis 토큰과 충돌하지 않습니다.

YAML 리스트 설정은 inline 형식(`aliases: [bullet, arrow]`)과 block 형식(`aliases:` 다음 줄의 `- bullet`)을 모두 사용할 수 있습니다. 들여쓰기는 일반 ASCII 공백만 사용해야 하며 tab이나 전각 공백은 YAML 들여쓰기로 사용할 수 없습니다. `/kchat reload`는 서비스를 중지하기 전에 설정 파일을 검증하므로 잘못된 YAML은 적용하지 않고 기존 실행 설정과 UI 언어를 그대로 유지합니다.

```yaml
message-tokens:
  enabled: true
  max-replacements-per-message: 24
  newline:
    aliases: [enter, newline, nextline, linebreak, br]
  blank-line:
    aliases: [blankline, emptyline, paragraphbreak]
  tab:
    aliases: [tab, indent]
    spaces: 4
  custom: {}
```

- `max-replacements-per-message: 0`은 성공한 message-token 치환 횟수를 제한하지 않습니다.
- `newline`은 다음 줄 1개를 만듭니다.
- `blank-line`은 줄바꿈 두 개를 넣어 사이에 빈 줄 1개를 만듭니다.
- `tab.spaces`는 1~16으로 제한되며 실제 tab 제어문자 대신 공백을 넣습니다.
- `custom`은 출력 가능한 일반 문자 치환만 지원하며 control character/newline은 제거됩니다.
- `:\n:` 같은 backslash escape는 의도적으로 해석하지 않습니다.
- Minecraft의 일반 CR/LF 입력은 기존 한 줄 평탄화 규칙을 유지합니다. `newline`/`blank-line` alias로 만든 줄바꿈만 별도로 추적해 최종 게임 전송 시 명시적인 여러 채팅 줄로 출력합니다. 서버간 relay에서도 수신 서버에 같은 4.7.0 token-line 지원이 필요합니다.

관리자가 언어별 alias나 일반 문자 치환을 추가하려면 예를 들어 다음처럼 `custom: {}`를 블록으로 교체할 수 있습니다.

```yaml
message-tokens:
  custom:
    separator:
      aliases: [separator, divider]
      replacement: "────────────"
```

## 1:1 메시지함 / DM 스레드

```yaml
direct-message:
  enabled: false
  allow-web-send: true
  allow-game-send: true
  capture-game-whispers: true

direct-message:
  admin-audit:
    enabled: false
```

`direct-message.enabled`를 켜면 연동되었거나 접속 기록이 있는 플레이어 사이의 저장형 1:1 스레드를 사용할 수 있습니다. A→B와 B→A는 같은 UUID 쌍의 대화로 저장됩니다. 저장방식, 보관기간, 메시지 수 제한, 알림 옵션은 기본 config의 주석을 따릅니다.

`group-chat.admin-audit.enabled`는 4.6.3에서 추가된 별도의 기본 OFF 그룹 본문 접근 스위치입니다. 이 값을 켜도 계정이 `private-chat-super-admins`에 함께 지정되어 있어야 합니다. 관리자 화면은 읽기 전용이며 방 참여 권한이 없어도 열 수 있지만 실제로 방에 참여하지 않고 읽음 상태도 변경하지 않습니다. 각 페이지 열람은 본문을 감사 로그에 복사하지 않은 채 `admin.group-audit-read`로 기록됩니다.

`direct-message.admin-audit.enabled`는 기본값이 꺼진 별도 본문 접근 스위치입니다. 이 값을 켜도 `private-chat-super-admins`에 함께 지정된 계정만 DM 본문을 읽기 전용 감사 화면에서 열 수 있습니다. 페이지 열람은 감사 로그에 남지만 메시지 본문 자체는 로그에 복사하지 않습니다. 일반 ADMIN/MODERATOR 역할은 자동으로 대상이 되지 않습니다.

`capture-game-whispers`는 취소되지 않은 `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, `/t` 명령을 송신자와 수신자의 KWC DM에 복제합니다. Minecraft 귓속말을 다시 보내거나 대체하지는 않습니다. Bukkit에서 모든 귓속말 플러그인의 최종 성공 여부를 공통으로 알 수 없으므로 정상 형식이며 알려진 플레이어를 대상으로 한 명령을 기록 기준으로 사용합니다.

## UI 타임존

`ui.time-zone`은 채팅 시간 표시 타임존을 지정합니다. `local`은 브라우저/기기 로컬 타임존을 사용하고, `UTC` 또는 `Asia/Seoul` 같은 IANA 타임존을 지정할 수 있습니다. 잘못된 값은 웹 UI에서 로컬 시간으로 fallback됩니다.

## 0 = 무제한/제한 없음인 옵션

- `chat.history-size`
- `chat.history-retention-days`
- `chat.history-page-size`
- `chat.max-message-length`
- `chat.max-url-message-length`
- `upload.max-uploads-per-minute`
- `upload.max-file-size-mb`
- `upload.max-files-per-message`
- `ui.image-preview-max-per-message`
- `ui.image-preview-max-height`
- `ui.max-width`
- `ui.max-height`
- `preview.youtube-max-embeds-per-message`
- `preview.social-embeds.max-embeds-per-message`
- `preview.external-media-cache-max-size-mb`
- `pinned.max-pins`
- `pinned.show-to-logged-out`
- `commands.max-length`
- `direct-message.retention-days`
- `direct-message.max-messages-per-thread`
- `direct-message.max-message-length`

## 게스트 채팅 제한

```yaml
guest:
  cooldown-seconds: 6
  max-messages-per-minute: 50
```

게스트 채팅은 `cooldown-seconds`와 `max-messages-per-minute` 두 설정으로 제한됩니다. 분당 메시지 기본값은 `50`입니다. 이미 생성된 서버의 설정 파일은 자동으로 덮어쓰기 되지 않으므로, 기존 설치에서 새 기본값을 쓰려면 `plugins/KOKOTO-WebChat/config.yml`을 직접 수정하세요.

## 웹→Minecraft 이름 hover

```yaml
chat:
  game-name-hover:
    enabled: false
    text: "&f{real}"
```

웹 채팅이 Minecraft 채팅으로 전달될 때 `chat.game-name-hover.enabled`로 표시 이름에 hover 툴팁을 붙일 수 있습니다. 이 기능은 `player-display.mode`가 `display-name` 또는 `custom-name`이고, 표시 이름이 실제 Minecraft 계정명과 다를 때만 적용됩니다. 이 툴팁은 Spigot/Bungee 채팅 컴포넌트를 사용하므로 Paper 전용이 아니고 Spigot/Paper 호환 서버에서 동작합니다. `text`는 Minecraft legacy 색상 코드와 `{display}`, `{real}`, `{uuid}`, `{source}` placeholder를 지원합니다.

## Minecraft 채팅 댓글 및 발신자 클릭

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

`reply.game-click.enabled`가 켜져 있으면 KWC이 게임에 출력한 메시지의 URL이 아닌 본문을 클릭할 때 `/kchat reply <messageId> `가 자동완성됩니다. URL 조각은 기존 링크 열기가 우선합니다. `/kchat reply <messageId> <내용>`은 웹 댓글과 같은 `replyTo` 메타데이터를 가진 공개 메시지를 만듭니다.

게임 댓글의 커스텀 이모지는 사용자가 입력한 원본 토큰을 웹 기록과 서버 릴레이에 보존합니다. 댓글을 작성한 서버의 게임 출력에는 게임 이모지 플러그인이 처리한 명령 본문을 재사용해 이모지로 표시합니다. 처리된 glyph가 없고 `emoji.game-link.mode`가 `preserve`라면 인식된 토큰은 게임 이모지 플러그인이 처리할 수 있도록 일반 채팅 줄로 출력되며, 이 호환 출력에서는 KWC의 클릭·hover 정보가 붙지 않습니다.

`local-game-chat: true`는 로컬 일반 게임 채팅도 같은 클릭 가능한 컴포넌트로 교체해 게임 발신 메시지에도 댓글을 달 수 있게 합니다. 다른 채팅 포맷 플러그인이 최종 채팅 출력을 독점해야 한다면 끄세요. 이 값을 꺼도 웹→게임과 원격 릴레이 메시지의 댓글 클릭은 유지됩니다.

같은 서버의 게임 발신자 이름을 클릭하면 `/w <실제이름> `이 자동완성됩니다. 연동된 웹 사용자와 다른 서버의 게임 발신자는 `/kchat dm <실제이름> `이 자동완성됩니다. 본문 댓글 클릭과 분리되어 있으며 기존 실명 hover도 유지됩니다.

`game-preview`는 댓글 원문 미리보기를 실제 메시지 전에 표시하고, `game-prefix`는 실제 댓글 줄의 출처 라벨을 바꿉니다. legacy `&` 색상과 config에 설명된 placeholder를 지원합니다.



`server-relay`는 여러 KOKOTO WebChat 서버의 공개 채팅을 연결합니다. 게임, 연동된 웹 사용자, 게스트 메시지를 상대 서버의 웹 채팅과 Minecraft 채팅으로 전달하며 메시지 ID, 댓글 관계, 발신자 정보와 원본 서버 정보를 유지합니다.

## 서버 릴레이 설정

`peers`는 연결 세션 목록이 아니라 이 서버가 메시지를 보낼 HTTP 대상 목록입니다. 각 항목의 `id`/`secret`은 같은 서버에서 들어오는 릴레이 요청 인증에도 사용됩니다. 양방향 송수신은 양쪽 서버에 서로의 항목을 등록해야 합니다.

서버 1:

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  server-name: "서버 1"
  shared-secret: "양쪽-서버에서-동일하게-쓸-충분히-긴-임의의-비밀키"
  connect-timeout-seconds: 5
  request-timeout-seconds: 10
  max-clock-skew-seconds: 60
  dedupe-seconds: 300
  max-hops: 8
  forward-received-public-chat: true
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
    - id: "server3"
      url: "https://server3.example.com/chat/api"
      secret: ""
      enabled: true
```

서버 3:

```yaml
server-relay:
  enabled: true
  server-id: "server3"
  server-name: "서버 3"
  shared-secret: "양쪽-서버에서-동일하게-쓸-충분히-긴-임의의-비밀키"
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
    - id: "server1"
      url: "https://server1.example.com/chat/api"
      secret: ""
      enabled: true
```

피어는 반드시 서로 등록해야 합니다. 요청을 받는 서버의 `peers[].id`가 보내는 서버의 `server-id`와 정확히 같아야 합니다. 서버마다 ID는 고유해야 하며 같은 ID를 두 서버에 사용하면 안 됩니다.

## HTTPS와 리버스 프록시

`url`에는 상대 서버에서 외부 접근 가능한 KWC API 기본 주소를 입력합니다. `/relay/receive`는 자동으로 붙습니다.

```text
설정값: https://server3.example.com/chat/api
실제 요청: https://server3.example.com/chat/api/relay/receive
```

공개 HTTPS 경로가 `/relay/receive`의 POST 요청을 포함해 KWC API 전체를 내부 KWC HTTP 포트로 전달해야 합니다. 이미 HTTPS로 공개 중이면 8899 포트를 외부에 직접 열 필요가 없습니다. 프록시는 다음 헤더를 보존해야 합니다.

```text
X-BMWC-Relay-Version
X-BMWC-Relay-From
X-BMWC-Relay-Timestamp
X-BMWC-Relay-Signature
```

공인 인증서는 Java에서 보통 바로 동작합니다. 자체 서명 인증서는 Java trust store에 등록하지 않으면 요청이 KWC까지 도달하기 전에 TLS 검증에서 실패합니다.

## 비밀키

- `shared-secret`은 모든 피어에 사용할 기본 키입니다.
- `peers[].secret`은 해당 피어 연결에만 사용할 개별 키이며 공통 키보다 우선합니다.
- 서버가 2대라면 양쪽 `shared-secret`을 같은 긴 임의 문자열로 설정하고 피어의 `secret: ""`은 비워두면 됩니다.
- 피어별 키를 쓰면 양쪽의 서로 마주보는 피어 항목에 같은 전용 키를 넣어야 합니다.
- 피어 키와 공통 키가 모두 없으면 해당 피어는 활성 목록에서 제외됩니다.

## 여러 서버 연결

- 풀 메시: 모든 서버가 나머지 모든 서버를 피어로 등록합니다. 가장 단순하고 한 서버 장애에도 유리합니다.
- 허브: 각 리프 서버는 허브만 등록하고 허브가 모든 리프를 등록합니다. `forward-received-public-chat: true`이면 받은 공개 채팅을 다른 피어로 다시 전달하고, `false`이면 공개 채팅은 직접 피어 연결까지만 전달합니다. 이 옵션은 DM 다중 홉 라우팅/읽음 확인에는 영향을 주지 않습니다.

릴레이 ID 중복 제거, 원본 서버 억제, 바로 전 송신 피어 제외, `max-hops`가 순환 구조의 무한 반복을 방지합니다. 상대 서버가 꺼져 있을 때의 메시지를 나중에 재전송하는 영구 오프라인 큐는 없습니다.

## reload와 진단 로그

`/kchat reload`는 기존 릴레이 인스턴스를 닫고 현재 설정으로 새 인스턴스를 만듭니다. 릴레이는 상시 소켓 연결이 아니라 메시지마다 HTTPS 요청을 보내므로 별도 재연결 상태는 없습니다.

정상 로그 예시:

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

`activePeers`가 설정한 수보다 적으면 바로 앞뒤 경고에 제외 이유가 표시됩니다. 중복 ID, 자기 서버와 같은 ID, 빈 URL, 잘못된 URL/프로토콜, 비밀키 누락을 확인하세요.

## HTTP 오류

- `403 unknown_peer`: 받는 서버의 활성 피어 목록에 보내는 서버의 정확한 `server-id`가 없습니다. 받는 서버의 `activePeers` 로그와 양방향 설정을 확인합니다.
- `401 bad_signature`: 실제 적용되는 비밀키가 다르거나 프록시가 본문/헤더를 변경했습니다.
- `401 expired_request`: 양쪽 서버 시간이 `max-clock-skew-seconds`보다 많이 차이 납니다.
- `404 relay_disabled`: 받는 서버에서 릴레이가 꺼져 있거나 프록시가 다른 KWC 인스턴스/경로로 전달합니다.
- `426 unsupported_protocol`: 양쪽 플러그인의 릴레이 프로토콜 버전이 호환되지 않습니다.

설정을 바꾼 쪽에서 `/kchat reload`를 실행합니다. 특히 받는 서버의 피어 목록이나 비밀키를 바꿨다면 받는 서버도 반드시 reload해야 합니다.

## 서버 구별 표시

- 웹 채팅은 `originServerId`를 기준으로 서버별 고정 색상의 배지를 표시합니다.
- 웹→게임 출력에서 `{server}`와 `{server_id}`를 사용할 수 있습니다. 현재 서버의 자체 표시는 생략하며, 다른 서버에서 온 메시지의 이전 형식에 두 placeholder가 모두 없을 때만 `[server-name]`이 자동으로 앞에 붙습니다.
- Discord 직접 전달 형식도 `{server}`, `{server_id}`를 지원하며 없으면 자동 접두사가 붙습니다.
- `sources.discord`와 `sources.system`은 DiscordSRV 순환 및 과도한 이벤트 복제를 막기 위해 기본적으로 꺼져 있습니다.

## Discord 연동 옵션

```yaml
discordsrv:
  game-relay-mode: "discordsrv"
  append-web-emoji-links: true
  append-game-emoji-links: true
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-relay-format: "[{server}] {sender}: {message}"
  reply-relay:
    enabled: false
    prefix-enabled: true
    preview-enabled: true
    preview-max-length: 120
```

Discord 형식은 `{server}`, `{server_id}`, `{sender}`, `{name}`, `{role}`, `{source}`, `{message}`, `{channel}`을 지원합니다. 서버 릴레이가 활성화된 동안 기존 사용자 형식에 `{server}`와 `{server_id}`가 모두 없으면 `[server-name]` 접두사가 자동으로 붙습니다. 여러 서버가 같은 Discord 채널을 공유할 때는 실제 로컬 Minecraft 채팅을 감지한 원본 서버의 KWC만 DiscordSRV 기본 게임 전달 메시지에 서버명과 이모지 링크를 추가하며, 다른 서버는 수정하지 않습니다. 수신 서버는 릴레이 메시지를 Discord로 다시 보내지 않으므로 원본 서버의 Discord 연동이 꺼졌거나 실패한 경우 다른 서버가 대신 보내는 경유 fallback은 없습니다.

`append-web-emoji-links`와 `append-game-emoji-links`는 Discord 미리보기용 공개 이모지 URL을 추가합니다. `discordsrv` 모드에서는 DiscordSRV가 실제 Discord에 올린 게임 메시지의 `:emoji:` 토큰을 웹→Discord와 동일한 KWC 토큰 처리 경로로 전달해 등록 이모지 URL을 붙입니다. LOWEST 단계의 게임 채팅 기록은 여러 서버가 같은 Discord 채널을 공유할 때 원본 서버를 판별하는 데만 사용하며, Minecraft용 glyph나 게임 렌더링 문자열은 Discord 이모지 변환 원본으로 사용하지 않습니다. DiscordSRV가 일반 게임 채팅을 이미 전달한다면 중복 방지를 위해 `game-relay-mode`는 `discordsrv`로 두세요. `reply-relay`는 Discord에 댓글 원문 미리보기를 추가하는 선택 기능이며 기본값은 꺼짐입니다.

## 관리자 Discord 키워드 알림

```yaml
admin-alerts:
  discord:
    enabled: false
    channel: ""
    sources:
      public-chat: true
      relay-chat: false
      dm: false
      group-chat: false
    mention: "none"
    case-sensitive: false
    keywords: ""
```

개인 사용자용 Discord 알림이 아니라 서버 관리자 공통 정책입니다. 키워드 감지, source 필터, 멘션, 포맷, 중복 제거는 KWC가 담당하고 DiscordSRV에서는 인증된 JDA 연결과 Channels 매핑만 재사용합니다. Web Admin의 `Discord 알림 채널`은 DiscordSRV 논리 채널명만 표시하며 논리명이 있으면 내부 숫자 ID는 보여주지 않습니다. `channel: ""`은 `discordsrv.channel`을 재사용하고, 숫자 ID는 논리 채널명이 없는 ID-only fallback에서만 허용합니다. Discord에서 들어온 메시지는 다시 알림 대상으로 검사하지 않으며 DM/그룹 감지는 기본적으로 꺼져 있습니다.

## 고정 메시지

`pinned.show-to-logged-out`는 웹 로그인 전에도 고정 메시지를 보여줄지 제어합니다. 고정 내용을 로그인 사용자에게만 보여주려면 `false`로 설정합니다.

## 고정/삭제 표시 토글

메시지별 고정/삭제 버튼은 실수 클릭을 막기 위해 기본적으로 숨겨져 있습니다. ADMIN/MOD 사용자는 관리자 패널의 웹 히스토리 비우기 버튼 옆에 있는 고정/삭제 활성화 토글을 켜서 버튼을 표시할 수 있습니다. 이 토글은 저장되지 않으며 새로고침하면 다시 꺼집니다.

## UI

```yaml
ui:
  language: "en-US"        # en-US, ko-KR, ja-JP, zh-CN
  language-fallback: "en-US"
  theme: "system"          # system, dark, light, high-contrast
  opacity: 0.92
```

사용자가 채팅 설정에서 바꾼 값은 브라우저 localStorage에 저장됩니다.

`ui.text-color`는 채팅 메시지 본문 글자색의 기본값입니다. `ui.ui-text-color`는 관리자/권한 표시, 웹/게임 출처 표시, 시간 표시, 입력창 placeholder, 업로드/명령 버튼, 고정 메시지 라벨 같은 UI 글자/기호 색의 기본값입니다. 비워두면 선택한 테마를 따릅니다. 사용자는 채팅 설정에서 브라우저별로 둘 다 덮어쓸 수 있습니다.

```yaml
ui:
  text-color: ""          # 메시지 본문 테마 기본값
  ui-text-color: ""       # UI 표시/기호 테마 기본값
  # text-color: "#f4f4f4"
  # ui-text-color: "#b8d8ff"
```

`ui.input-background-color`는 입력창 배경색을 전역으로 고정합니다. 비워두면 선택한 테마를 따릅니다. 사용자는 채팅 설정에서 브라우저별로 따로 덮어쓸 수도 있습니다.

```yaml
ui:
  input-background-color: ""      # 테마 기본값
  # input-background-color: "#1e1e24"
```

## 닉네임 표시

```yaml
player-display:
  mode: "name"             # name, display-name, custom-name
  strip-colors: true
```

`strip-colors: false`이면 웹 UI의 실제 채팅 발신자 이름에만 Minecraft legacy 색상 코드가 렌더링됩니다. 시스템/이벤트 메시지와 Discord 출력은 원시 Minecraft 색상 코드를 제거합니다. 저장되어 있던 표시 이름도 다시 사용할 때 현재 `strip-colors` 설정 기준으로 정규화됩니다.

## 커스텀 이모지와 게임 측 이모지 플러그인

KOKOTO WebChat은 커스텀 이모지를 `plugins/KOKOTO-WebChat/emojis` 아래에 저장합니다. 하위 폴더는 이모지 팩으로 처리됩니다.

기본값에서는 `emoji.game-link.enabled`가 `false`이므로 웹→게임 메시지의 `:pack/name:`, `:emoji:pack/name:` 같은 커스텀 이모지 토큰을 그대로 보존합니다. ImageEmojis나 다른 게임 측 이모지 플러그인이 Minecraft 채팅에서 토큰을 렌더링한다면 이 기본값을 사용하세요.

`emoji.game-link.enabled`가 `true`일 때 `emoji.game-link.mode`는 `preserve`, `link`, `label`을 지원합니다.

- `preserve`: game-link가 켜져 있어도 토큰 보존 동작을 강제합니다.
- `link`: `label-format` 텍스트와 BM Web Chat 짧은 이미지 링크를 같이 보냅니다.
- `label`: `label-format` 텍스트만 보냅니다.

`emoji.game-link.*`는 웹→Minecraft 채팅에만 적용됩니다. Discord 이미지 미리보기 링크는 웹→Discord용 `discordsrv.append-web-emoji-links`와 게임→Discord용 `discordsrv.append-game-emoji-links`로 분리해서 제어합니다. `append-game-emoji-links`는 DiscordSRV의 일반 Minecraft→Discord 릴레이 메시지를 가능한 경우 수정하며, `game-relay-mode: "kwc"`는 KWC가 게임 채팅을 Discord로 직접 보낼 때 사용하고, `discordsrv`는 DiscordSRV가 전송을 담당합니다.

BM Web Chat은 웹 기록과 릴레이 payload에는 정규 이모지 토큰을 보존합니다. ImageEmojis 또는 ImageEmojis-Bero가 활성화되어 있으면 공개된 runtime 이모지 저장소를 reflection으로 읽고, 클릭 가능한 Minecraft 컴포넌트를 만들기 전에 수신 서버의 활성 glyph로 토큰을 변환합니다. hard dependency를 추가하거나 리소스팩을 분석하지 않습니다.

상호작용 채팅에서는 ImageEmojis glyph를 먼저 넣은 뒤 발신자·댓글·URL 클릭 이벤트를 구성하므로 이모지와 클릭 가능한 링크가 동시에 동작합니다. 수신 서버에서 해결하지 못한 인식 토큰만 다른 게임 이모지 렌더러를 위한 한 줄의 plain Bukkit fallback을 사용하며, 이 fallback에는 KWC 클릭·hover metadata를 붙일 수 없습니다.

`default-pack`과 `aliases`는 flat 게임 측 토큰을 BM Web Chat의 pack/name id로 매핑할 때 사용합니다. 예:

```yaml
emoji:
  game-link:
    default-pack: "default"
    aliases:
      wave: "default/wave"
```

GIF/JPG/JPEG/WEBP 이모지 원본은 PNG만 읽는 게임 측 이모지 플러그인과의 호환을 위해 같은 폴더에 PNG sidecar를 자동 생성합니다. 웹 UI는 원본 파일을 사용하므로 GIF 애니메이션은 유지됩니다.

### ImageEmojis-Bero 1.9.x

Bukkit/Paper 계열에서는 [ImageEmojis-Bero](https://github.com/KOKOTO-DEV/ImageEmojis-Bero)가 `plugins/KOKOTO-WebChat/emojis`를 같이 읽도록 구성할 수 있습니다. 핵심 연동값은 `serverIp`, `webServerPort`, `emojisFolder: /KOKOTO-WebChat/emojis`, `templateFormat: ':<emoji>:'`, `replaceInCommands: true`입니다. ImageEmojis 리소스팩 HTTP 주소/포트는 Minecraft 클라이언트에서 접근 가능해야 하며 KWC 웹 포트와 별개입니다. 자세한 내용은 `IMAGEEMOJIS_BERO_1_9_0_KO.md`를 참고하세요. 기본 설치·일반 운영은 [원본 ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis)를 따릅니다.

### SimpleNicks-Bero

Bukkit/Paper 계열에서 [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero)의 닉네임을 KWC에 표시하려면 `player-display.mode: "display-name"`을 사용합니다. KWC의 실제 연결 계정/UUID identity는 별도로 유지됩니다. 자세한 내용은 `SIMPLENICKS_BERO_KO.md`, 기본 설치·운영은 [원본 SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks)를 참고하세요.

## 명령어 패널

```yaml
commands:
  enabled: false
  allow-all: false
  min-role: ADMIN
  run-from-chat-input: false
  max-length: 0
```

`allow-all: true`는 웹에서 임의 콘솔 명령어를 실행할 수 있으므로 HTTPS와 강한 인증이 전제되어야 합니다. `run-from-chat-input: false`면 명령어 버튼/모달에서만 실행됩니다.


## 미디어 미리보기 높이와 스크롤 안정성

`ui.image-preview-max-height`는 이미지, GIF, 비디오, iframe 계열 미리보기의 표시 높이를 제한합니다. 권장 범위는 `640-720`이며 기본값은 `720`입니다.

```yaml
ui:
  image-preview-max-height: 720
```

값을 `0`으로 설정하면 높이 제한이 없어집니다. 단, 매우 큰 미디어 또는 무제한 미리보기는 미디어 로딩 완료 시점에 스크롤 튐을 유발할 수 있습니다. 특히 virtual scroll과 미디어가 많은 긴 채팅 기록을 함께 사용할 때 더 잘 발생합니다.


## 미리보기

```yaml
preview:
  youtube-embed-enabled: true
  youtube-click-to-load: true
  media-click-to-load: true
  youtube-nocookie: true
  youtube-remember-expanded: true
  youtube-autoplay-on-open: false
  youtube-max-embeds-per-message: 1

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
      hide-media: false
      hide-thread: true
```

YouTube Shorts는 일반 YouTube 미리보기 경로에서 처리됩니다. Shorts는 세로형 플레이어로 표시되고 YouTube loop 파라미터를 사용합니다.

TikTok과 X/Twitter는 사용자의 브라우저에서 외부 콘텐츠를 불러오므로 선택 기능입니다. 서버 정책상 외부 embed 요청을 허용할 때만 켜는 것을 권장합니다. 공개 서버에서는 `social-embeds.click-to-load: true`를 유지해서 사용자가 미리보기를 열 때만 외부 플레이어가 로드되게 하는 편이 안전합니다.

TikTok은 공식 `player/v1` iframe을 사용하고 `description=0`, `music_info=0`을 적용합니다. 이렇게 하면 게시물 본문/음악 정보 길이에 따라 채팅 패널 안에 내부 스크롤바가 생기는 문제를 피할 수 있습니다. 전체 게시물 정보는 플레이어 아래의 원문 TikTok 링크로 열 수 있습니다.

`youtube-click-to-load` 또는 `media-click-to-load`를 `false`로 두면 해당 미리보기를 즉시 렌더링합니다. 자동 재생 여부는 브라우저 정책의 영향을 받습니다.


## 사용자 프로필과 계정 설정

```yaml
ui:
  user-profiles:
    enabled: true
    max-profiles: 5
    allow-import-export: true
```

로그인 사용자는 테마, 폰트, 글자 크기, 색상, 투명도, text shadow, 언어 같은 시각 설정을 서버측 계정 프로필로 여러 개 저장할 수 있습니다. `max-profiles`는 0-20이며 `0`은 서버 프로필 저장을 끕니다. JSON 가져오기/내보내기는 16 KiB 이하의 strict flat schema만 허용하고 세션 토큰, UUID, Web Push endpoint, 창 좌표는 포함하지 않습니다. 창 위치/크기·최소화 상태·Web Push 등록은 계속 기기 로컬입니다. 세 항목은 Web Admin Settings에서 조절할 수 있습니다.

## 브라우저 알림과 Web Push

`notifications`는 브라우저 웹알림과 모바일/백그라운드 Web Push가 공통으로 사용하는 알림 기본값과 서버 측 허용 상한선을 제어합니다. `notifications.enabled`가 양쪽 전달 경로의 단일 기본 ON/OFF 값이며, 기존 `browser-notifications.*`와 `web-push.notify-*` 키는 마이그레이션/호환용 입력값으로만 읽습니다. `notify-*` 값을 `true`로 두면 사용자가 채팅 설정에서 켜고 끌 수 있고, `false`로 두면 사용자가 켜도 해당 알림 종류는 차단됩니다. `notify-system`이 허용된 경우 사용자는 서버 알림을 전체, 입장/퇴장만, 끄기 중에서 고를 수 있습니다. 5.0.0부터 로그인 사용자의 알림 종류와 키워드 목록은 계정 데이터에 한 번 저장되어 여러 브라우저/기기에서 재사용되며, 최초 초기화 때 기존 브라우저 값을 계정 설정으로 승격합니다. 게스트는 브라우저 로컬 설정을 계속 사용합니다. 각 기기의 Web Push 구독에는 백그라운드 전달에 필요한 endpoint/filter 데이터만 유지합니다.

`web-push`는 VAPID 키, subject, 구독 파일, TTL, 기본 푸시 제목 같은 Web Push 전송 설정만 보관합니다. HTTPS 또는 localhost, 브라우저 알림 권한, Service Worker/Push API 지원이 맞으면 백그라운드/모바일 푸시를 보낼 수 있습니다. Android/데스크톱 브라우저는 현재 origin이 Service Worker + Push API를 지원하면 BlueMap addon 또는 standalone 페이지 어디서든 푸시를 켤 수 있습니다. iOS/iPadOS의 일반 브라우저 탭은 Web Push를 지원하지 않으므로 홈 화면에 추가한 웹앱으로 연 페이지에서만 시도하고, 지원되지 않는 동작은 플랫폼 제한으로 봅니다. `notifications.enabled: true` 상태에서 VAPID 키를 비워두면 플러그인이 `web-push-vapid.properties`에 지속 키를 생성합니다. `web-push.subject`는 `mailto:admin@example.com` 또는 `https://map.example.com`처럼 실제 연락처/운영자 식별용 VAPID URI로 두는 것을 권장합니다. 임의 문자열은 권장하지 않으며 일부 push 서비스에서 거부되거나 신뢰도가 낮게 처리될 수 있습니다. 모바일의 “스팸일 수 있음” 같은 경고는 브라우저/OS가 표시하는 것이므로 플러그인에서 끌 수 없습니다. 안정적인 HTTPS 도메인, 의미 있는 알림 제목/본문, 보수적인 알림 필터, 반복 테스트 알림 최소화로 가능성을 줄이는 쪽으로 관리합니다. 테스트는 모바일 브라우저에서 현재 HTTPS 채팅 페이지를 열고 로그인한 뒤 설정 > 알림에서 알림을 켜고 테스트를 누릅니다. iOS/iPadOS에서는 먼저 이 페이지를 홈 화면에 추가한 뒤 웹앱으로 열어야 합니다.

## PIP

```yaml
ui:
  picture-in-picture:
    enabled: false
```

이 하나의 옵션이 PIP 버튼 표시와 PIP 실행을 모두 제어합니다. 브라우저가 제공하는 URL/닫기 UI, OS 창 투명도, 외부 PIP 창 이동은 채팅 설정 제목이 아니라 브라우저/운영체제가 제어합니다.

## 로그인 실패 제한

`security.login-fail-limit`, `security.login-fail-window-seconds`, `security.login-lock-seconds`는 웹 비밀번호 로그인 반복 실패를 제한합니다. `login-fail-limit: 0`이면 제한을 끕니다. 이 설정은 웹 비밀번호 로그인에만 적용됩니다.
## 링크 코드 발급 제한

`auth.link-code-cooldown-seconds`와 `auth.link-code-max-per-minute`는 웹 UI에서 `/kchat auth <code>`용 링크 코드를 원격 IP별로 얼마나 자주 발급할 수 있는지 제한합니다. 각 값을 `0`으로 두면 해당 제한을 끕니다.

## HTTP 프록시 / 클라이언트 IP

`http.trusted-proxies`는 `X-Forwarded-For`를 신뢰할 프록시를 지정합니다. 직접 HTTP로 공개할 때는 비워두세요. 같은 서버의 Caddy/Nginx 뒤에서 사용할 때는 `127.0.0.1`, `::1`을 블록형 YAML 목록으로 넣으세요. `http.log-client-ip-resolution: true`는 소켓 IP, forwarded 헤더, 최종 클라이언트 IP를 서버 콘솔과 `logs/latest.log`에 찍어 확인할 때만 임시로 사용하세요. 자세한 확인 방법은 `docs/OPERATIONS_SECURITY_KO.md`를 참고하세요.

## SSE 연결 수 제한

`security.max-sse-connections-per-ip`와 `security.max-sse-connections-total`은 오래 유지되는 `/stream` 연결 수를 제한합니다. 각 값은 `0`으로 두면 비활성화됩니다.

### 시스템 메시지 번역

내장 announcement와 웹 명령어 결과 메시지는 i18n 키를 포함합니다. `announcements.*.message`는 fallback/사용자 지정 문구로 유지하세요. 해당 키가 언어 파일에 있으면 사용자는 선택한 언어의 번역 문구를 보게 됩니다.

접혀 있는 고정 메시지 바의 글자도 설정한 채팅 폰트와 메시지 글자 크기를 따릅니다.

### Text shadow / readability

- `ui.text-shadow-mode`: `none`, `auto`, `dark`, `light`, `custom` 중 하나입니다. 글자색/배경색 대비가 낮을 때 가독성을 보강합니다.
- `ui.text-shadow-custom`: 모드가 `custom`일 때 사용할 CSS `text-shadow` 값입니다. 채팅 설정 화면에서는 색상 선택기와 가로 위치, 세로 위치, 흐림, 불투명도 조절바로 편집하며, 저장값은 표준 CSS 형식으로 유지됩니다. 예: `0 1px 2px rgba(0, 0, 0, 0.85)`.

> 테마는 브라우저별 채팅 설정에서도 변경할 수 있습니다. 테마를 바꾸면 글자색/배경색/그림자 같은 시각 설정은 해당 테마의 기본값으로 초기화됩니다.


관리자 커스텀 이모지 참고: 이모지 파일명이나 폴더명을 변경하면 `:emoji:pack/name:` 토큰도 바뀝니다. 기존 토큰을 사용한 과거 채팅은 기존 파일/폴더명을 유지하지 않는 한 더 이상 렌더링되지 않을 수 있습니다.

## 메시지 검색

저장된 기록을 사용할 때 채팅 패널 우측 상단 플로팅 영역의 돋보기 버튼과 `/history/search` API로 메시지 내용과 작성자를 검색할 수 있습니다. 검색 옵션에서 날짜/시간 범위, 작성자, 출처, 시스템/이벤트 포함 여부를 지정할 수 있습니다. 검색 결과는 스크롤 가능한 목록으로 표시되며, 채팅 테마와 폰트 설정을 따릅니다. 검색 결과를 클릭하면 기존 주변 기록 로드 방식으로 해당 메시지로 이동합니다. i18n 키가 있는 시스템/이벤트 메시지는 가능한 경우 요청된 웹 UI 언어 기준으로 검색되고 표시됩니다. 검색은 `search.enabled`로 끄거나 켤 수 있고, `search.result-limit` 하나가 웹 UI 결과 수와 `/history/search` API 제한을 모두 제어합니다. 별도 내부 최대치는 없어서 2000으로 설정하면 최대 2000개, 10으로 설정하면 최대 10개가 반환됩니다. 10000이나 100000처럼 매우 큰 값도 허용되지만, 검색 속도 저하, 응답 크기 증가, CPU/메모리/DB 부하 증가를 일으킬 수 있습니다. 기본값은 50이며 일반 사용은 50~200을 권장합니다. `config-version: "5.0.0_auto_migration"` 상태에서는 누락된 검색 설정이 startup/reload 때 자동 삽입됩니다. 정확한 `config-version: "5.0.0"`으로 같은 버전 자동 migration을 끈 경우에만 누락 키를 직접 추가하거나 `_auto_migration`을 다시 활성화해야 합니다.

## 그룹 채팅

`group-chat.enabled`는 웹 그룹 채팅 기능을 켭니다. 공개/비공개 방, 해시 저장되는 선택 비밀번호, 초대, 방 나가기, 방 숨김/다시 표시, 방 설정, 안 읽음 추적, 사용자별 메시지 숨김, 멤버 강퇴/차단/차단 해제, 방장 이전을 지원합니다. 그룹 메시지는 `group-chat.sqlite-file`(기본 `group-messages.db`)에 저장됩니다. `group-chat.retention-days: 0`은 기간 정리 없음이고, 양수 값은 오래된 그룹 메시지를 물리 삭제합니다.


## 비공개 채팅 메타데이터 최고관리자

`private-chat-super-admins: []`에는 DM/그룹채팅 메타데이터를 관리/용량 확인용으로 볼 수 있는 정확한 UUID 또는 마인크래프트 이름을 지정합니다. 기본 메타데이터 화면은 참여자/제목, 메시지 수, 대략적인 저장 용량, 보관 상태와 관리 동작을 제공합니다. DM 본문은 `direct-message.admin-audit.enabled: true`, 그룹채팅 본문은 `group-chat.admin-audit.enabled: true`일 때만 읽기 전용으로 열 수 있으며 두 감사 화면 모두 모든 페이지 열람이 감사 로그에 기록됩니다.


`frontend.standalone.app-name`과 `frontend.standalone.app-short-name`은 standalone 페이지/PWA 이름을 제어합니다. 모바일 홈 화면 웹앱으로 설치한 뒤 값을 바꿨다면 다시 설치해야 반영됩니다. `web-push.notification-title`은 테스트/시스템/백그라운드 푸시의 기본 제목을 제어하며, 비워두면 `frontend.standalone.app-name`을 사용합니다.


기존 config에 `KOKOTO WebChat` 또는 `KOKOTO WebChat` 같은 예전 기본 이름이 남아 있으면 레거시 기본값으로 보고 새 fallback을 사용합니다.

### Dynmap 어댑터

`adapters.dynmap`은 Dynmap 자체 웹채팅 전송 기능을 사용하지 않고 Dynmap 화면에 KWC 프론트엔드를 삽입합니다. `enabled: true`이면 일반적인 `configuration.txt`에서 Dynmap의 `webpath`를 읽고 `kokoto-web-chat/` 에셋을 설치한 뒤 `index.html`의 KWC 마커 블록만 관리합니다. Dynmap은 `update-webpath-files: true`일 때 웹 파일을 다시 생성할 수 있으므로 Dynmap 작업 뒤 페이지가 원복되면 `/kchat reload`로 다시 적용할 수 있습니다. Dynmap 웹 디렉터리를 다른 웹 서버로 복사해서 쓰는 경우에는 실제 공유/마운트된 웹 루트를 `web-root`로 지정해야 하며, 서버 파일시스템에서 보이지 않는 원격 복사본은 KWC가 직접 수정할 수 없습니다. `api-base-url: ""`이면 다른 지도 어댑터와 동일하게 IP 직접 HTTP와 HTTPS 경로를 자동 판별합니다.


### LiveAtlas 어댑터

`adapters.liveatlas`는 기존 LiveAtlas 정적 프론트엔드에 KWC를 삽입합니다. LiveAtlas가 Dynmap, squaremap, Pl3xMap, Overviewer 또는 여러 서버를 표시해도 동일하게 동작하며 Bukkit/Fabric/NeoForge/Forge에서 사용할 수 있습니다. `web-root: ""`이면 일반적인 로컬 지도 웹 디렉터리를 확인하되 `window.liveAtlasConfig` 같은 LiveAtlas 표식이 있는 `index.html`만 대상으로 인정합니다. Caddy/nginx가 별도 디렉터리의 LiveAtlas를 서비스한다면 서버에서 접근 가능한 실제 공유/마운트 경로를 `web-root`에 지정합니다. KWC는 `addon-path` 디렉터리와 LiveAtlas `index.html`의 표시된 KWC 블록만 관리합니다. LiveAtlas 업데이트가 `index.html`을 교체한 뒤에는 `/kchat reload`를 실행하면 됩니다. 같은 실제 웹루트에 LiveAtlas adapter와 Dynmap/squaremap/Pl3xMap KWC adapter를 동시에 켜지 않습니다.

### uNmINeD 어댑터

`adapters.unmined`는 기존 uNmINeD 정적 웹 내보내기에 KWC를 삽입합니다. uNmINeD는 Minecraft 서버 플러그인이 아니라 외부 지도 생성기이므로 Bukkit/Fabric/NeoForge/Forge에서 별도 uNmINeD 런타임 의존성 없이 같은 파일시스템 어댑터를 사용합니다. 현재 내보내기의 `index.html`과 구형 `unmined.index.html`을 지원하며, 자동 탐지는 `unmined.map.properties.js`와 uNmINeD 런타임 같은 표식을 확인한 경우에만 적용합니다. 임의의 내보내기 위치나 Caddy/nginx 문서 루트는 서버에서 볼 수 있는 실제 공유/마운트 경로를 `web-root`로 지정합니다. 지도를 다시 내보내면 HTML 또는 KWC 전용 파일이 교체될 수 있으므로 이후 `/kchat reload`를 실행합니다.

### Overviewer 어댑터

`adapters.overviewer`는 기존 Minecraft Overviewer 정적 웹 지도 출력에 KWC를 삽입합니다. Overviewer는 서버 플러그인이 아니라 외부 렌더러이므로 Bukkit/Fabric/NeoForge/Forge에서 Overviewer 런타임 의존성 없이 같은 파일시스템 어댑터를 사용합니다. KWC는 `Minecraft-Overviewer` generator 메타데이터, `overviewerConfig.js`, `overviewer.js`, `overviewer.css` 같은 Overviewer 전용 표식/파일을 확인한 기존 `index.html`만 대상으로 하며 일반 Leaflet 페이지는 수정하지 않습니다. 임의의 출력 위치나 Caddy/nginx 문서 루트는 서버에서 볼 수 있는 실제 공유/마운트 Overviewer `outputdir`을 `web-root`로 지정합니다. Overviewer 렌더 또는 `--update-web-assets`가 `index.html`을 다시 만들 수 있으므로 이후 `/kchat reload`를 실행합니다. 자체 템플릿을 유지하는 운영자는 Overviewer의 `customwebassets` 기능을 그대로 사용할 수 있으며 KWC Stage 1은 Overviewer Python 설정 파일을 수정하지 않습니다.

> **IP / 공유기 포트포워딩:** 지도 어댑터의 direct HTTP 자동 판별은 외부에서 접근하는 KWC 포트가 `http.port`(기본 8899)와 같다고 가정합니다. 공유기에서 외부 `8900` → 서버 `8899`처럼 포트를 변환하면 브라우저가 NAT 변환을 알 수 없으므로 해당 지도 어댑터의 `api-base-url`을 `http://공인IP:8900/api`처럼 명시하세요. standalone을 포워딩된 포트로 직접 열 때는 현재 origin을 사용하므로 `api-base-url: ""`를 유지할 수 있습니다.
