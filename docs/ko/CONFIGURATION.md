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

`config-version`은 자동 migration 동작을 선택하는 표식입니다. 재구성에는 `ui.language`가 선택한 **현재 버전의 번들 표시 템플릿**을 사용합니다. `en-US`는 `config.yml`, `ko-KR`/`ja-JP`/`zh-CN`은 각각의 번들 지역화 템플릿을 사용합니다. `config-reference-<plugin-version>.yml`은 선택된 템플릿으로 렌더링한 관리자용 기본 설정이며 migration 입력으로 사용하지 않습니다. 기본값의 의미 비교는 canonical 영문 `config.yml`을 기준으로 하고 네 내장 템플릿은 파싱된 값이 완전히 같아야 합니다.

`config-version`이 없거나 다른 버전이면 기존 설정값을 읽은 뒤 새 번들 기본 `config.yml`을 만들고 그 위에 기존 사용자 값을 덮어씁니다. 이전 표식에 `*_auto_migration`이 없었다면 사용자가 한 번 고정한 설정으로 보고 재구성 전에 기존 `config.yml` 전체를 백업합니다. 기존 주석·순서·공백·들여쓰기는 가져오지 않고 최신 번들 주석/레이아웃을 사용하며, 사용자 설정값은 보존합니다. 제거된 설정은 다시 복사하지 않습니다. 결과는 `<plugin-version>_auto_migration`으로 표시합니다. 이 표식이 남아 있으면 startup/reload마다 같은 방식으로 최신 번들 기본 config를 다시 뼈대로 만들고 현재 값을 덮어써서 새 설정과 최신 주석/레이아웃을 자동 반영합니다. 정확한 `<plugin-version>`은 같은 버전의 자동 **설정** 재구성을 끕니다. 단, 고정 상태에서도 `ui.language` 표시 언어가 바뀌면 실제 설정값을 모두 overlay해 보존한 채 선택된 내장 템플릿으로 주석/레이아웃만 다시 구성할 수 있습니다.

`config-migration-<plugin-version>.yml`은 검토/diff 보고서입니다. 이전 버전의 `config-reference-*`, `config-migration-*`, `config-upgrade-*` 생성 파일은 자동 삭제합니다. 실제 버전 업그레이드의 기본값 변경 판정에 필요한 JAR 내부 `config-baselines/*`만 유지합니다.
5.1.0에서는 `ui.language`가 Web UI뿐 아니라 KWC가 `config.yml`을 재구성할 때 사용할 주석/표현 언어, `config-reference-5.1.0.yml`, migration/difference 보고서 언어도 선택합니다. 번들 template은 `en-US`, `ko-KR`, `ja-JP`, `zh-CN`이며 언어를 바꿔도 주석/레이아웃만 바뀌고 Relay group/secret/peer를 포함한 기존의 파싱된 운영 설정값은 그대로 overlay해 보존합니다. Difference 판정은 주석, 공백, 들여쓰기, 따옴표 방식, 줄번호, 키 순서가 아니라 파싱된 YAML setting path와 value만 비교합니다.

## 전체 활성화 스위치

새로 생성된 config는 최상단 `enabled: false` 상태입니다. 이 상태에서는 KOKOTO WebChat이 config를 생성/로드하기만 하고 `/kchat reload`만 계속 사용할 수 있으며, 웹/채팅 서비스, 리스너, Discord 연동, DM 저장소, 애드온 설치, 업로드/이모지 초기화, 정리 작업을 시작하지 않습니다. 기존 config에 이 키가 없으면 업그레이드 호환성을 위해 활성 상태로 처리합니다. 저장 방식, 보관 기간, 업로드, 미리보기, 인증, 외부 공개 설정을 확인한 뒤 `enabled: true`로 변경하세요.

## 업데이트 확인

```yaml
update-check:
  enabled: true
```

활성화하면 KOKOTO WebChat이 Bukkit, Fabric, NeoForge, Forge 모두에서 백그라운드로 Modrinth의 최신 정식 버전을 확인합니다. 현재 프로젝트 주소 전환 기간에는 canonical KWC `kokoto-webchat`을 먼저 조회하고 사용할 수 없으면 기존 `bluemapwebchat`으로 fallback합니다. BMWC도 전환 완료 전까지 실제 업데이트 소스로 사용하므로 더 최신 버전이 있으면 정상 알림을 표시하며, 두 소스가 모두 실패한 경우에만 경고합니다. OP 또는 `kwc.update.notify` 권한 보유자가 로그인하면 제한된 주기로 다시 확인하므로 새 릴리스 감지가 정기 확인 결과에만 의존하지 않습니다.
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

채팅 기록 보관은 `chat.history-storage`로 `memory`, `jsonl`, `sqlite` 중 하나를 고르고, `chat.history-size`와 `chat.history-retention-days`를 세 모드가 공통으로 사용합니다. `0`은 각각 개수/기간 제한 없음입니다. 새로 생성된 config는 최상단 `enabled: false` 상태이므로, 이 값들을 검토하고 `enabled: true`로 바꾸기 전까지 정리 작업이 실행되지 않습니다. 서버 정책상 자동 정리가 필요하면 `30`, `90` 같은 양수 보관일을 설정하세요. 업로드와 외부 미디어 캐시 보관 설정도 같은 방식으로 동작합니다. `chat.history-file`은 JSONL에서만, `chat.history-sqlite-file`은 SQLite에서만 사용됩니다. `chat.history-sqlite-migrate-jsonl: true`이고 SQLite DB가 비어 있으면 기존 `chat.history-file`을 한 번 가져옵니다. 수동 편집, 대규모 정리, 마이그레이션 전에는 `history.db`를 정상적으로 백업하세요.


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
  newline:
    aliases: [enter, newline, nextline, linebreak, br, next]
  custom:
    separator:
      aliases: [separator, divider, line]
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

`direct-message.admin-audit.enabled`는 별도의 기본 OFF DM 본문 감사 스위치입니다. 이 값을 켜도 `private-chat-super-admins`에 함께 정확히 지정된 계정만 DM 본문을 읽기 전용 감사 화면에서 열 수 있습니다. 감사 화면에서는 전송, 답글, 숨김, 읽음 처리를 할 수 없으며 각 페이지 열람은 본문을 복사하지 않고 감사 로그에 기록됩니다.

`capture-game-whispers`는 취소되지 않은 `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, `/t` 명령을 송신자와 수신자의 KWC DM에 복제합니다. Minecraft 귓속말을 다시 보내거나 대체하지는 않습니다. Bukkit에서 모든 귓속말 플러그인의 최종 성공 여부를 공통으로 알 수 없으므로 정상 형식이며 알려진 플레이어를 대상으로 한 명령을 기록 기준으로 사용합니다.

## UI 타임존

`ui.time-zone`은 채팅 시간 표시 타임존을 지정합니다. `local`은 브라우저/기기 로컬 타임존을 사용하고, `UTC` 또는 `Asia/Seoul` 같은 IANA 타임존을 지정할 수 있습니다. 잘못된 값은 웹 UI에서 로컬 시간으로 fallback됩니다.

## 중요한 0 값 의미

`0`은 모든 설정에서 같은 뜻이 아닙니다. 아래 내용은 현재 5.1.0 loader/runtime 동작 기준이며, 실제 설명이 다른 설정을 임의로 “무제한”이라고 해석하면 안 됩니다.

- `chat.history-size`: 개수 기준으로 보존할 공개 채팅 기록 최대 행 수이며 기간 보존 정책과 함께 적용됩니다. 0은 개수 제한을 없앱니다.
- `chat.history-retention-days`: 공개 채팅 기록의 기간 보존 일수입니다. 0은 기간 기반 만료를 끕니다.
- `chat.history-page-size`: 기록 페이지 한 번에 요청할 기본 메시지 수입니다. 0이면 memory/JSONL 기록에는 명시적 페이지 제한을 두지 않지만 SQLite는 내장 쿼리 안전 상한 500개를 적용합니다.
- `chat.max-message-length`: KWC가 허용하는 일반 공개 채팅 메시지 최대 길이입니다. 0은 이 길이 제한을 없앱니다.
- `chat.max-url-message-length`: URL이 포함된 공개 채팅 메시지 최대 길이입니다. 양수이면 양수인 일반 메시지 제한보다 작지 않도록 보정됩니다. 0은 이 길이 제한을 없앱니다.
- `message-tokens.max-replacements-per-message`: 한 메시지에서 수행할 token 치환 최대 횟수로 치환 작업량을 제한합니다. 0은 개수 제한을 없앱니다.
- `reply.game-preview.max-length`: Minecraft Reply 인용 미리보기에 표시할 원문 최대 길이입니다. 0은 미리보기 길이 자르기를 하지 않습니다.
- `pinned.max-pins`: 동시에 고정 상태로 유지할 수 있는 공개 메시지 최대 개수입니다. 0은 개수 제한을 없앱니다.
- `direct-message.retention-days`: 저장된 DM 메시지의 기간 보존 일수입니다. 0은 기간 기반 만료를 끕니다.
- `direct-message.max-messages-per-thread`: 각 DM thread에서 개수 기준으로 보존할 최대 메시지 수입니다. 0은 개수 제한을 없앱니다.
- `direct-message.max-message-length`: 웹/게임에서 전송할 수 있는 DM 메시지 최대 길이입니다. 0은 이 길이 제한을 없앱니다.
- `group-chat.retention-days`: 저장된 그룹 방 메시지의 기간 보존 일수입니다. 0은 기간 기반 만료를 끕니다.
- `group-chat.max-messages-per-room`: 각 그룹 방에서 개수 기준으로 보존할 최대 메시지 수입니다. 0은 개수 제한을 없앱니다.
- `group-chat.max-message-length`: 그룹 방에서 허용하는 메시지 최대 길이입니다. 0은 이 길이 제한을 없앱니다.
- `group-chat.max-rooms-per-user`: 방 관리 검사에서 사용자 한 명이 소유/참여할 수 있는 그룹 방 최대 개수입니다. 0은 개수 제한을 없앱니다.
- `group-chat.max-members-per-room`: 그룹 방 하나에 허용할 최대 멤버 수입니다. 0은 개수 제한을 없앱니다.
- `group-chat.invite-expire-hours`: 그룹 방 초대 유효시간(시간)입니다. 0은 무제한이 아닙니다. runtime 최소값은 1이며 더 작은 값은 최소값으로 보정합니다.
- `guest.cooldown-seconds`: 같은 resolved client identity/IP에서 게스트 메시지를 연속 전송할 때 요구하는 최소 간격(초)입니다. 0은 이 cooldown 제한 요소를 끕니다.
- `guest.max-messages-per-minute`: 같은 resolved client identity/IP에 적용하는 분당 게스트 메시지 제한입니다. 0은 이 분당 제한 요소를 끕니다.
- `captcha.expire-seconds`: 발급한 captcha 문제의 유효시간(초)입니다. 이 값은 clamp하지 않으므로 0/음수는 새 문제를 즉시 또는 사실상 즉시 만료시킵니다.
- `captcha.pass-valid-minutes`: 메시지마다 captcha를 요구하지 않을 때 한 번 성공한 captcha 상태를 재사용할 수 있는 시간(분)입니다. runtime 최소값은 1이며 더 작은 값은 최소값으로 보정합니다.
- `auth.link-code-cooldown-seconds`: 같은 client/user가 계정 연동 코드 발급을 반복할 때 요구하는 최소 간격(초)입니다. 0은 이 제한 요소를 끕니다.
- `auth.link-code-max-per-minute`: 발급 rate limiter에서 분당 허용할 계정 연동 코드 최대 발급 횟수입니다. 0은 이 제한 요소를 끕니다.
- `auth.remember-session-days`: 일반 USER/MODERATOR 웹 세션의 만료 기간(일)입니다. 0 이하는 expiry timestamp를 두지 않습니다.
- `security.login-fail-limit`: 설정된 실패 집계 구간 안에서 IP 기반 임시 잠금을 발생시키는 로그인 실패 횟수입니다. 0은 이 제한 요소를 끕니다.
- `security.login-lock-seconds`: 로그인 실패 제한을 넘은 뒤 IP 기반 로그인 잠금을 유지할 시간(초)입니다. 0은 이 제한 요소를 끕니다.
- `security.max-sse-connections-per-ip`: resolved client IP 하나당 허용할 동시 /stream SSE 연결 최대 개수입니다. 리버스 프록시 사용 시 모든 client가 proxy IP로 보이지 않도록 http.trusted-proxies를 정확히 설정합니다. 0은 이 제한 요소를 끕니다.
- `security.max-sse-connections-total`: KWC 서버 전체에서 허용할 동시 /stream SSE 연결 최대 개수입니다. 0은 이 제한 요소를 끕니다.
- `admin.admin-session-expire-hours`: ADMIN 웹 세션의 만료 기간(시간)입니다. 0 이하는 관리자 세션에 expiry timestamp를 두지 않습니다.
- `moderation.default-mute-minutes`: 기간을 생략한 mute의 기본 시간(분)입니다. 0 이하는 영구 mute이며 config 전용 설정입니다.
- `commands.max-length`: 웹 command 실행에서 허용할 command text 최대 길이입니다. 0은 이 길이 제한을 없앱니다.
- `ui.image-preview-max-per-message`: 한 메시지에서 렌더링할 inline 이미지 미리보기 최대 개수입니다. 0은 개수 제한을 없앱니다.
- `ui.image-preview-max-height`: 이미지 미리보기에 설정할 높이 상한(px)입니다. 양수여도 채팅 viewport 안전 상한이 함께 적용되며, 0은 이 명시적 px 상한만 없애고 자동 viewport 상한을 사용하므로 완전 무제한이 아닙니다.
- `ui.max-width`: 설정상 KWC panel 최대 너비(px)입니다. 0은 설정상 최대값만 없애며 브라우저/viewport 제약은 계속 적용될 수 있습니다.
- `ui.max-height`: 설정상 KWC panel 최대 높이(px)입니다. 0은 설정상 최대값만 없애며 브라우저/viewport 제약은 계속 적용될 수 있습니다.
- `ui.user-profiles.max-profiles`: 계정당 서버에 저장할 preference profile 최대 개수입니다. runtime 범위는 0-20이며 범위를 벗어나면 경계값으로 보정합니다. 0은 서버 저장 프로필 기능을 끕니다.
- `ui.virtual-scroll.overscan-screens`: virtual-scroll 가시 범위 위/아래에 추가로 렌더링할 viewport screen 거리입니다. 0도 유효한 최소 동작값입니다.
- `ui.virtual-scroll.min-rendered-messages`: viewport 계산상 더 적게 필요해도 렌더 상태로 유지할 최소 메시지 행 수입니다. 0도 유효한 최소 동작값입니다.
- `discordsrv.max-emoji-links-per-message`: Discord 메시지 한 건에 추가할 custom-emoji 이미지 URL 최대 개수입니다. emoji.game-link.max-links-per-message와 달리 이 설정의 0은 비활성화 의미입니다. 0은 Discord에 emoji 이미지 URL을 추가하지 않습니다.
- `discordsrv.reply-relay.preview-max-length`: Discord Reply preview에 포함할 Reply 대상 원문 최대 길이입니다. 0은 미리보기 길이 자르기를 하지 않습니다.
- `upload.cooldown-seconds`: 같은 resolved client IP의 업로드 시도 사이에 요구하는 최소 간격(초)입니다. 0은 이 제한 요소를 끕니다.
- `upload.max-uploads-per-minute`: resolved client IP 하나에 적용하는 분당 업로드 시도 제한입니다. 0은 이 제한 요소를 끕니다.
- `upload.max-file-size-mb`: 업로드 파일 한 개에 허용할 최대 크기(MiB)입니다. 0은 이 크기/용량 제한을 없앱니다.
- `upload.max-total-size-mb`: upload.directory 전체 저장 quota(MiB)입니다. 초과 시 가장 오래된 미참조 업로드부터 제거하며 그래도 공간을 확보하지 못하면 새 업로드를 거부합니다. 0은 이 크기/용량 제한을 없앱니다.
- `upload.max-files-per-message`: composer 업로드 동작 한 번에 선택/첨부할 최대 파일 수입니다. 0은 개수 제한을 없앱니다.
- `upload.retention-days`: 보존 중인 메시지/pin에서 더 이상 참조하지 않는 업로드 파일만 이 일수보다 오래됐을 때 삭제합니다. 0은 기간 기반 정리를 끕니다.
- `preview.youtube-max-embeds-per-message`: 한 메시지에서 렌더링할 YouTube embed 최대 개수입니다. 0은 개수 제한을 없앱니다.
- `preview.social-embeds.max-embeds-per-message`: 한 메시지에서 렌더링할 지원 social embed 최대 개수입니다. 0은 개수 제한을 없앱니다.
- `preview.external-media-cache-max-size-mb`: KWC가 fetch/cache할 외부 media 객체 한 개의 최대 크기(MiB)입니다. 0은 이 크기/용량 제한을 없앱니다.
- `preview.external-media-cache-retention-days`: 참조되지 않는 external-media cache 파일을 삭제할 기간 기준 일수입니다. 0은 기간 기반 정리를 끕니다.
- `emoji.max-file-size-kb`: custom emoji 파일 한 개의 크기 제한(KiB)이며 초과 파일은 사용하는 경로에 따라 관리 catalog 처리에서 거부/제외됩니다. 0은 이 크기/용량 제한을 없앱니다.
- `emoji.max-total-size-mb`: 관리 emoji 파일 전체 storage/catalog quota(MiB)입니다. quota 초과 업로드를 거부하고 catalog scan도 설정 총량을 넘는 파일을 노출하지 않습니다. 0은 이 크기/용량 제한을 없앱니다.
- `emoji.message-token-limit`: 한 메시지에서 허용할 custom emoji token 최대 개수입니다. token은 canonical pack/name 경로를 포함할 수 있습니다. 0은 개수 제한을 없앱니다.
- `emoji.game-link.max-links-per-message`: link mode에서 게임 메시지 한 건에 추가할 emoji 이미지 링크 최대 개수입니다. 0은 개수 제한을 없앱니다.

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
DM/그룹 메시지도 같은 게임 클릭 모델을 사용합니다. 대화 이름/그룹 영역은 기존 `/kchat dm ...` 또는 `/kchat group ...` 명령을 입력창에 올리고, 본문은 내부 private reply target을 준비합니다. 실제 전송 전 DM 참여 여부/현재 그룹 멤버십을 다시 확인하므로 내부 ID 자체는 권한 토큰이 아닙니다.


게임 댓글의 커스텀 이모지는 사용자가 입력한 원본 토큰을 웹 기록과 서버 릴레이에 보존합니다. 댓글을 작성한 서버의 게임 출력에는 게임 이모지 플러그인이 처리한 명령 본문을 재사용해 이모지로 표시합니다. 처리된 glyph가 없고 `emoji.game-link.mode`가 `preserve`라면 인식된 토큰은 게임 이모지 플러그인이 처리할 수 있도록 일반 채팅 줄로 출력되며, 이 호환 출력에서는 KWC의 클릭·hover 정보가 붙지 않습니다.

`local-game-chat: true`는 로컬 일반 게임 채팅도 같은 클릭 가능한 컴포넌트로 교체해 게임 발신 메시지에도 댓글을 달 수 있게 합니다. 다른 채팅 포맷 플러그인이 최종 채팅 출력을 독점해야 한다면 끄세요. 이 값을 꺼도 웹→게임과 원격 릴레이 메시지의 댓글 클릭은 유지됩니다.

같은 서버의 게임 발신자 이름을 클릭하면 `/w <실제이름> `이 자동완성됩니다. 연동된 웹 사용자와 다른 서버의 게임 발신자는 `/kchat dm <실제이름> `이 자동완성됩니다. 본문 댓글 클릭과 분리되어 있으며 기존 실명 hover도 유지됩니다.

`game-preview`는 댓글 원문 미리보기를 실제 메시지 전에 표시하고, `game-prefix`는 실제 댓글 줄의 출처 라벨을 바꿉니다. legacy `&` 색상과 config에 설명된 placeholder를 지원합니다.



`server-relay`는 여러 KOKOTO WebChat 서버의 공개 채팅을 연결합니다. 게임, 연동된 웹 사용자, 게스트 메시지를 상대 서버의 웹 채팅과 Minecraft 채팅으로 전달하며 메시지 ID, 댓글 관계, 발신자 정보와 원본 서버 정보를 유지합니다.

## 서버 릴레이 설정

KOKOTO WebChat 5.1.0은 **Relay Protocol v2**를 사용합니다. relay group 자체가 보안 경계이며, 해당 group의 모든 peer 관계가 하나의 group `shared-secret`을 공통으로 사용합니다. peer 항목에는 `id`, `url`, `enabled`만 있으며 `peers[].secret`은 없습니다.

```yaml
server-relay:
  enabled: true
  server-id: "server-1"
  server-name: "Server 1"
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
  groups:
    - id: "main"
      shared-secret: ""
      forwarding:
        enabled: false
      peers:
        - id: "server-2"
          url: "https://server2.example.com/api"
          enabled: true
```

최초 설정은 한 서버에서 `shared-secret: ""`로 두고 시작/리로드한 뒤 그 서버의 `config.yml`에 생성된 값을 같은 **group**의 다른 서버에 그대로 복사하세요. 서버마다 따로 빈 값에서 생성하면 서로 다른 secret이 생겨 연결되지 않습니다. 기존 non-empty secret은 보존되고, 수동 secret이 32자 미만이면 자동 교체하지 않고 invalid/fail-closed됩니다. 양쪽 서버는 같은 group 안에 서로를 peer로 등록하고 동일한 생성/복사 secret을 사용해야 합니다. 같은 peer ID를 여러 local group에 중복 등록할 수 없으며 중복 등록은 비활성화됩니다. direct relay는 각 `/relay/v2/message` 요청을 독립적으로 인증/암호화합니다. `/relay/v2/handshake`는 상태를 저장하지 않는 진단용 identity/health probe이며 routing을 제어하지 않습니다.

Relay v2는 `/relay/v2/message` 하나로 public chat과 cross-server 1:1 DM/read receipt를 전달합니다. payload는 방향별 HKDF-SHA256 key와 AES-256-GCM으로 hop-by-hop 보호됩니다. direct 1-hop HTTP도 payload 암호화/인증 상태로 허용하지만 명시적 경고가 발생하며 forwarding에는 사용할 수 없습니다. direct relay는 각 요청을 독립적으로 인증하며 handshake endpoint는 routing을 제어하지 않습니다. forwarding은 같은 group 안에서 peer 단위로 판단하며, http:// peer는 그 peer를 통한 forwarding만 제외되고 같은 group의 다른 https:// peer는 계속 사용할 수 있습니다.

5.0.0 → 5.1.0 최초 migration에서는 기존 flat relay 구성을 보고 group을 **추측하지 않습니다**. 기존 relay trust key/peer/forwarding 설정은 폐기하고 `server-relay.enabled`를 `false`로 안전하게 reset한 뒤, 운영자가 v2 group을 직접 정의하고 다시 활성화해야 합니다.

전체 protocol, trust model, migration, forwarding, 진단 내용은 `docs/ko/SERVER_RELAY.md`를 참고하세요.

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

KOKOTO WebChat은 커스텀 이모지를 `plugins/KOKOTO-WebChat/emojis` 아래에 저장합니다. 하위 폴더는 이모지 팩으로 처리됩니다. 5.1.0부터 팩 디렉터리명과 이모지 파일명 stem을 같은 토큰 안전 규칙으로 정규화합니다. 공백/사용 불가능 문자는 제거되고 기존 잘못된 이름은 시작 시 일괄 변경되며, 충돌 시 숫자 suffix가 붙습니다. 최종 경로는 `:팩/이름:` 토큰과 그대로 일치합니다.

기본값에서는 `emoji.game-link.enabled`가 `false`이므로 웹→게임 메시지의 `:pack/name:`, `:emoji:pack/name:` 같은 커스텀 이모지 토큰을 그대로 보존합니다. ImageEmojis나 다른 게임 측 이모지 플러그인이 Minecraft 채팅에서 토큰을 렌더링한다면 이 기본값을 사용하세요.

`emoji.game-link.enabled`가 `true`일 때 `emoji.game-link.mode`는 `preserve`, `link`, `label`을 지원합니다.

- `preserve`: game-link가 켜져 있어도 토큰 보존 동작을 강제합니다.
- `link`: `label-format` 텍스트와 KOKOTO WebChat 짧은 이미지 링크를 같이 보냅니다.
- `label`: `label-format` 텍스트만 보냅니다.

`emoji.game-link.*`는 웹→Minecraft 채팅에만 적용됩니다. Discord 이미지 미리보기 링크는 웹→Discord용 `discordsrv.append-web-emoji-links`와 게임→Discord용 `discordsrv.append-game-emoji-links`로 분리해서 제어합니다. `append-game-emoji-links`는 DiscordSRV의 일반 Minecraft→Discord 릴레이 메시지를 가능한 경우 수정하며, `game-relay-mode: "kwc"`는 KWC가 게임 채팅을 Discord로 직접 보낼 때 사용하고, `discordsrv`는 DiscordSRV가 전송을 담당합니다.

KOKOTO WebChat은 웹 기록과 릴레이 payload에는 정규 이모지 토큰을 보존합니다. ImageEmojis 또는 ImageEmojis-Bero가 활성화되어 있으면 공개된 runtime 이모지 저장소를 reflection으로 읽고, 클릭 가능한 Minecraft 컴포넌트를 만들기 전에 수신 서버의 활성 glyph로 토큰을 변환합니다. hard dependency를 추가하거나 리소스팩을 분석하지 않습니다.

상호작용 채팅에서는 ImageEmojis glyph를 먼저 넣은 뒤 발신자·댓글·URL 클릭 이벤트를 구성하므로 이모지와 클릭 가능한 링크가 동시에 동작합니다. 수신 서버에서 해결하지 못한 인식 토큰만 다른 게임 이모지 렌더러를 위한 한 줄의 plain Bukkit fallback을 사용하며, 이 fallback에는 KWC 클릭·hover metadata를 붙일 수 없습니다.

`default-pack`과 `aliases`는 flat 게임 측 토큰을 KOKOTO WebChat의 pack/name id로 매핑할 때 사용합니다. 예:

```yaml
emoji:
  game-link:
    default-pack: "default"
    aliases:
      wave: "default/wave"
```

GIF/JPG/JPEG/WEBP 이모지 원본은 PNG만 읽는 게임 측 이모지 플러그인과의 호환을 위해 같은 폴더에 PNG sidecar를 자동 생성합니다. 웹 UI는 원본 파일을 사용하므로 GIF 애니메이션은 유지됩니다.

### ImageEmojis-Bero 1.9.x

Bukkit/Paper 계열에서는 [ImageEmojis-Bero](https://github.com/KOKOTO-DEV/ImageEmojis-Bero)가 `plugins/KOKOTO-WebChat/emojis`를 같이 읽도록 구성할 수 있습니다. 핵심 연동값은 `serverIp`, `webServerPort`, `emojisFolder: /KOKOTO-WebChat/emojis`, `templateFormat: ':<emoji>:'`, `replaceInCommands: true`입니다. ImageEmojis 리소스팩 HTTP 주소/포트는 Minecraft 클라이언트에서 접근 가능해야 하며 KWC 웹 포트와 별개입니다. 자세한 내용은 `IMAGEEMOJIS_BERO_1_9_0.md`를 참고하세요. 기본 설치·일반 운영은 [원본 ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis)를 따릅니다.

### SimpleNicks-Bero

Bukkit/Paper 계열에서 [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero)의 닉네임을 KWC에 표시하려면 `player-display.mode: "display-name"`을 사용합니다. KWC의 실제 연결 계정/UUID identity는 별도로 유지됩니다. 자세한 내용은 `SIMPLENICKS_BERO.md`, 기본 설치·운영은 [원본 SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks)를 참고하세요.

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

`ui.image-preview-max-height`는 이미지, GIF, 비디오, iframe 계열 미리보기의 표시 높이를 제한합니다. 권장 범위는 `640-720`이며 기본값은 `720`입니다. `ui.image-preview-max-height`가 `0`이면 명시적인 px 상한만 제거되고 자동 viewport 기반 안전 상한은 계속 적용되므로 완전한 높이 무제한이 아닙니다.

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


## 반복 운영 오류 로그

KWC는 HTTP 상태 코드마다 별도 로그 예외를 추가하는 대신 반복 가능한 운영 HTTP/네트워크 오류에 공통 콘솔 정책을 사용합니다. 같은 작업/대상의 최초 오류는 즉시 기록하고, 동일 상태의 반복은 억제한 뒤 이후 요약 로그에서 생략 횟수를 알립니다. 오류 상태가 달라지면 즉시 새 상태를 기록하고, 반복이 억제된 뒤 정상 복구되면 복구 요약을 한 번 기록합니다. Relay 검증/HTTP 전송, 업데이트 소스 조회, 운영 API의 rate/server 오류 같은 반복 transport 계열에 적용하며, 일반적인 사용자 입력 검증/인증 실패 응답은 서버 콘솔 오류로 승격하지 않습니다. 실제 재시도/backoff 정책은 각 기능이 별도로 결정하며 로그 억제와 독립적입니다.

## HTTP 프록시 / 클라이언트 IP

`http.trusted-proxies`는 `X-Forwarded-For`를 신뢰할 프록시를 지정합니다. 직접 HTTP로 공개할 때는 비워두세요. 같은 서버의 Caddy/Nginx 뒤에서 사용할 때는 `127.0.0.1`, `::1`을 블록형 YAML 목록으로 넣으세요. `http.log-client-ip-resolution: true`는 소켓 IP, forwarded 헤더, 최종 클라이언트 IP를 서버 콘솔과 `logs/latest.log`에 찍어 확인할 때만 임시로 사용하세요. 자세한 확인 방법은 `docs/ko/OPERATIONS_SECURITY.md`를 참고하세요.

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

저장된 기록을 사용할 때 채팅 패널 우측 상단 플로팅 영역의 돋보기 버튼과 `/history/search` API로 메시지 내용과 작성자를 검색할 수 있습니다. 검색 옵션에서 날짜/시간 범위, 작성자, 출처, 시스템/이벤트 포함 여부를 지정할 수 있습니다. 검색 결과는 스크롤 가능한 목록으로 표시되며, 채팅 테마와 폰트 설정을 따릅니다. 검색 결과를 클릭하면 기존 주변 기록 로드 방식으로 해당 메시지로 이동합니다. i18n 키가 있는 시스템/이벤트 메시지는 가능한 경우 요청된 웹 UI 언어 기준으로 검색되고 표시됩니다. 검색은 `search.enabled`로 끄거나 켤 수 있고, `search.result-limit` 하나가 웹 UI 결과 수와 `/history/search` API 제한을 모두 제어합니다. 별도 내부 최대치는 없어서 2000으로 설정하면 최대 2000개, 10으로 설정하면 최대 10개가 반환됩니다. 10000이나 100000처럼 매우 큰 값도 허용되지만, 검색 속도 저하, 응답 크기 증가, CPU/메모리/DB 부하 증가를 일으킬 수 있습니다. 기본값은 50이며 일반 사용은 50~200을 권장합니다. `config-version: "5.1.0_auto_migration"` 상태에서는 누락된 검색 설정이 startup/reload 때 자동 삽입됩니다. 정확한 `config-version: "5.1.0"`으로 같은 버전 자동 migration을 끈 경우에만 누락 키를 직접 추가하거나 `_auto_migration`을 다시 활성화해야 합니다.

## 그룹 채팅

`group-chat.enabled`는 웹 그룹 채팅 기능을 켭니다. 공개/비공개 방, 해시 저장되는 선택 비밀번호, 초대, 방 나가기, 방 숨김/다시 표시, 방 설정, 안 읽음 추적, 사용자별 메시지 숨김, 멤버 강퇴/차단/차단 해제, 방장 이전을 지원합니다. 그룹 메시지는 `group-chat.sqlite-file`(기본 `group-messages.db`)에 저장됩니다. `group-chat.retention-days: 0`은 기간 정리 없음이고, 양수 값은 오래된 그룹 메시지를 물리 삭제합니다.

방 입장/퇴장 알림은 전역 `config.yml` 스위치가 아니라 **방별 DB 설정**입니다. 방 설정에서 켜거나 끌 수 있고 `group_rooms.membership_events_enabled`에 저장됩니다. 기존 DB에 컬럼을 추가할 때는 기본 ON으로 마이그레이션됩니다. 실제 멤버십이 변할 때만 이벤트가 저장되며 그룹채팅 창을 닫는 것은 방 나가기가 아닙니다.


## 비공개 채팅 메타데이터 최고관리자

`private-chat-super-admins: []`에는 DM/그룹채팅 메타데이터를 관리/용량 확인용으로 볼 수 있는 정확한 UUID 또는 마인크래프트 이름을 지정합니다. 기본 메타데이터 화면은 참여자/제목, 메시지 수, 대략적인 저장 용량, 보관 상태와 관리 동작을 제공합니다. DM 본문은 `direct-message.admin-audit.enabled: true`, 그룹채팅 본문은 `group-chat.admin-audit.enabled: true`일 때만 열 수 있으며 둘 다 `private-chat-super-admins`에 지정된 계정이어야 합니다. 두 감사 화면은 읽기 전용이고 페이지 열람은 감사 로그에 기록됩니다.


`frontend.standalone.app-name`과 `frontend.standalone.app-short-name`은 standalone 페이지/PWA 이름을 제어합니다. 모바일 홈 화면 웹앱으로 설치한 뒤 값을 바꿨다면 다시 설치해야 반영됩니다. `web-push.notification-title`은 테스트/시스템/백그라운드 푸시의 기본 제목을 제어하며, 비워두면 `frontend.standalone.app-name`을 사용합니다.


기존 config에 `BlueMapWebChat` 또는 `BM WebChat` 같은 레거시 생성 이름이 남아 있으면 레거시 기본값으로 보고 현재 fallback을 사용합니다.

### Dynmap 어댑터

`adapters.dynmap`은 Dynmap 자체 웹채팅 전송 기능을 사용하지 않고 Dynmap 화면에 KWC 프론트엔드를 삽입합니다. `enabled: true`이면 일반적인 `configuration.txt`에서 Dynmap의 `webpath`를 읽고 `kokoto-web-chat/` 에셋을 설치한 뒤 `index.html`의 KWC 마커 블록만 관리합니다. Dynmap은 `update-webpath-files: true`일 때 웹 파일을 다시 생성할 수 있으므로 Dynmap 작업 뒤 페이지가 원복되면 `/kchat reload`로 다시 적용할 수 있습니다. Dynmap 웹 디렉터리를 다른 웹 서버로 복사해서 쓰는 경우에는 실제 공유/마운트된 웹 루트를 `web-root`로 지정해야 하며, 서버 파일시스템에서 보이지 않는 원격 복사본은 KWC가 직접 수정할 수 없습니다. `api-base-url: ""`이면 다른 지도 어댑터와 동일하게 IP 직접 HTTP와 HTTPS 경로를 자동 판별합니다.


### LiveAtlas 어댑터

`adapters.liveatlas`는 기존 LiveAtlas 정적 프론트엔드에 KWC를 삽입합니다. LiveAtlas가 Dynmap, squaremap, Pl3xMap, Overviewer 또는 여러 서버를 표시해도 동일하게 동작하며 Bukkit/Fabric/NeoForge/Forge에서 사용할 수 있습니다. `web-root: ""`이면 일반적인 로컬 지도 웹 디렉터리를 확인하되 `window.liveAtlasConfig` 같은 LiveAtlas 표식이 있는 `index.html`만 대상으로 인정합니다. Caddy/nginx가 별도 디렉터리의 LiveAtlas를 서비스한다면 서버에서 접근 가능한 실제 공유/마운트 경로를 `web-root`에 지정합니다. KWC는 `addon-path` 디렉터리와 LiveAtlas `index.html`의 표시된 KWC 블록만 관리합니다. LiveAtlas 업데이트가 `index.html`을 교체한 뒤에는 `/kchat reload`를 실행하면 됩니다. 같은 실제 웹루트에 LiveAtlas adapter와 Dynmap/squaremap/Pl3xMap KWC adapter를 동시에 켜지 않습니다.

### uNmINeD 어댑터

`adapters.unmined`는 기존 uNmINeD 정적 웹 내보내기에 KWC를 삽입합니다. uNmINeD는 Minecraft 서버 플러그인이 아니라 외부 지도 생성기이므로 Bukkit/Fabric/NeoForge/Forge에서 별도 uNmINeD 런타임 의존성 없이 같은 파일시스템 어댑터를 사용합니다. 현재 내보내기의 `index.html`과 구형 `unmined.index.html`을 지원하며, 자동 탐지는 `unmined.map.properties.js`와 uNmINeD 런타임 같은 표식을 확인한 경우에만 적용합니다. 임의의 내보내기 위치나 Caddy/nginx 문서 루트는 서버에서 볼 수 있는 실제 공유/마운트 경로를 `web-root`로 지정합니다. 지도를 다시 내보내면 HTML 또는 KWC 전용 파일이 교체될 수 있으므로 이후 `/kchat reload`를 실행합니다.

### Overviewer 어댑터

`adapters.overviewer`는 기존 Minecraft Overviewer 정적 웹 지도 출력에 KWC를 삽입합니다. Overviewer는 서버 플러그인이 아니라 외부 렌더러이므로 Bukkit/Fabric/NeoForge/Forge에서 Overviewer 런타임 의존성 없이 같은 파일시스템 어댑터를 사용합니다. KWC는 `Minecraft-Overviewer` generator 메타데이터, `overviewerConfig.js`, `overviewer.js`, `overviewer.css` 같은 Overviewer 전용 표식/파일을 확인한 기존 `index.html`만 대상으로 하며 일반 Leaflet 페이지는 수정하지 않습니다. 임의의 출력 위치나 Caddy/nginx 문서 루트는 서버에서 볼 수 있는 실제 공유/마운트 Overviewer `outputdir`을 `web-root`로 지정합니다. Overviewer 렌더 또는 `--update-web-assets`가 `index.html`을 다시 만들 수 있으므로 이후 `/kchat reload`를 실행합니다. 자체 템플릿을 유지하는 운영자는 Overviewer의 `customwebassets` 기능을 그대로 사용할 수 있으며 KWC는 Overviewer Python 설정 파일을 수정하지 않습니다.

> **IP / 공유기 포트포워딩:** 지도 어댑터의 direct HTTP 자동 판별은 외부에서 접근하는 KWC 포트가 `http.port`(기본 8899)와 같다고 가정합니다. 공유기에서 외부 `8900` → 서버 `8899`처럼 포트를 변환하면 브라우저가 NAT 변환을 알 수 없으므로 해당 지도 어댑터의 `api-base-url`을 `http://공인IP:8900/api`처럼 명시하세요. standalone을 포워딩된 포트로 직접 열 때는 현재 origin을 사용하므로 `api-base-url: ""`를 유지할 수 있습니다.
