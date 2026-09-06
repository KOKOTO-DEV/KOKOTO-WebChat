# KOKOTO WebChat 업그레이드 가이드

이 문서는 4.5.5부터 5.2.0까지의 업그레이드 안내를 하나로 통합합니다. 여러 버전을 건너뛰는 경우 버전 순서대로 각 절을 확인하세요.

## 4.5.5에서 4.6.0으로 업그레이드

### 먼저 백업

서버를 중지하고 `plugins/KOKOTO-WebChat`을 백업하세요. 특히 `config.yml`, 각 SQLite DB와 `-wal`/`-shm`, DM/그룹 DB, 업로드, 이모지, 사용자 언어 파일, audit 로그, Web Push 키/구독 파일을 포함해야 합니다.

### 자동 설정 마이그레이션 조각

KOKOTO WebChat은 기존 `config.yml`을 자동으로 덮어쓰거나 병합하지 않습니다. 서버 시작과 `/kchat reload` 때 실제 디스크의 `config.yml`에서 `config-version`을 확인합니다.

- `config-version`이 실행 중인 플러그인 버전과 같으면 이미 검토한 설정으로 간주하고 비교를 생략합니다. 남아 있는 같은 버전의 마이그레이션 설정 조각은 삭제합니다.
- 버전이 없거나 다르면 JAR의 현재 기본 설정과 비교하여 다음 파일을 새로 생성하거나 갱신합니다.

정확한 판정은 다음과 같습니다.

| 실제 설정 상태 | 마이그레이션 파일 |
|---|---|
| 버전 표식 없음 | 차이가 없어도 생성 |
| 버전 표식이 현재 플러그인과 다름 | 생성 또는 갱신 |
| 버전 표식이 현재 플러그인과 같음 | 생성하지 않음. 남은 동일 버전 파일도 제거 |

```text
plugins/KOKOTO-WebChat/config-migration-4.6.0.yml
```

생성 파일은 구조화된 보고서가 아니라 그대로 참고·복사할 수 있는 YAML 설정 조각입니다. 다음 항목을 표시합니다.

- 실제 `config.yml`에 없는 설정과 현재 권장 기본값
- 번들 기본값이 바뀌었고 실제 설정값이 이전 기본값 그대로인 설정
- 최종 검토 표식인 대상 `config-version`

버전 정보, 개수, 이전·새 기본값 설명은 모두 `#` 주석으로만 기록합니다. `migration:`, `summary:`, `settings-to-add:`, `preserved-custom-values:`, `obsolete-settings-to-review:`, `finalize-after-review:` 같은 정보용 YAML 섹션은 만들지 않습니다. 사용자 지정값과 폐기 후보 참고 목록도 출력하지 않습니다.

필요한 설정만 실제 `config.yml`의 동일 위치에 병합하세요. 실제 설정 파일은 자동으로 수정되지 않습니다.

누락 설정이나 변경된 번들 기본값이 하나도 없어도 마이그레이션 파일을 생성하며, 대상 `config-version` 항목을 포함합니다. 이를 통해 버전 표식이 없는 설정도 반드시 명시적으로 검토 완료 처리할 수 있습니다.

4.6.0은 4.5.5 기본 설정을 비교 기준으로 포함합니다. 버전 표식이 없는 설정은 4.5.5 이하로 간주하여 4.6.0 신규 설정과 변경 기본값을 안내합니다. 알 수 없는 명시적 버전은 잘못된 추정을 피하기 위해 누락 설정만 비교하고 이전 기본값 변경 판정은 생략합니다.

검토가 끝난 뒤 실제 config 상단에 다음을 설정합니다.

```yaml
config-version: "4.6.0"
```

같은 버전 표식이 있으면 이후 시작과 reload에서는 비교를 생략합니다.

### 4.6.0에서 추가된 주요 설정

- 새 최상위 `server-relay:` 섹션
- `direct-message.capture-game-whispers`
- `reply.game-click.enabled`
- `reply.game-click.local-game-chat`
- `reply.game-command-format`
- Discord 형식의 `{server}`, `{server_id}` placeholder

버전이 일치하지 않고 비공개 메시지 캡처 또는 로컬 채팅 렌더링 설정이 실제 파일에 없으면, 검토 전 의도치 않은 활성화를 막기 위해 해당 동작은 런타임에서 안전하게 비활성화됩니다.

### DB 마이그레이션

공개 SQLite 기록에는 릴레이 메타데이터 열이 추가형 `ALTER TABLE` 방식으로 추가됩니다. 기존 행은 보존되지만 과거 행의 원본 서버 정보는 소급 생성할 수 없습니다. 첫 4.6.0 실행 전에 DB를 백업하세요.

### 권장 테스트

1. 기존 설정으로 시작해 `config-migration-4.6.0.yml`이 생성되고 `config.yml`은 바뀌지 않는지 확인합니다.
2. 설정 조각의 누락 설정과 변경 기본값을 검토하여 실제 config에 병합합니다.
3. `config-version: "4.6.0"`을 넣고 `/kchat reload` 후 비교 생략 로그가 나오는지 확인합니다.
4. 릴레이, 게임 댓글, DM 복제, Discord 서버 표기, URL·ImageEmojis 처리를 시험합니다.

---

## 4.6.0에서 4.6.1로 업그레이드

**5.1.0 참고:** 이 문서의 관리자 DM 본문 감사 동작은 5.1.0에서도 유지됩니다. `direct-message.admin-audit.enabled`와 `private-chat-super-admins`를 함께 사용하며 감사 화면은 읽기 전용입니다.


### 주요 변경점

- 서버 릴레이 메시지에서 UUID가 확인된 타 서버 플레이어를 기존 웹 DM 대상 검색에서 표시합니다. 게임 메시지와 계정 연동 웹 메시지의 표시 이름, 실제 이름, UUID를 검색할 수 있습니다.
- 기존 비공개 채팅 메타데이터 목록에서 선택적으로 DM 본문을 읽기 전용으로 감사 열람할 수 있습니다.
- Modrinth 기반 간단한 업데이트 확인과 관리자 접속 알림을 추가했습니다.
- 플러그인과 설정 버전이 `4.6.1`로 변경되었습니다.

### 설정 마이그레이션

기존 설정에 `config-version: "4.6.0"`이 있으면 4.6.1 실행 시 다음 파일을 생성합니다.

```text
plugins/KOKOTO-WebChat/config-migration-4.6.1.yml
```

생성 조각에는 신규 설정들과 대상 버전 표식만 들어갑니다.

```yaml
update-check:
  enabled: true

direct-message:
  admin-audit:
    enabled: false

config-version: "4.6.1"
```

실제 `config.yml`은 자동 수정하지 않습니다. DM 본문 확인이 명확히 필요한 경우가 아니라면 감사 기능은 비활성화 상태로 유지하세요.

### DM 본문 감사 기능 활성화

다음 두 조건을 모두 설정해야 합니다.

```yaml
private-chat-super-admins:
  - "정확한마인크래프트이름또는UUID"

direct-message:
  admin-audit:
    enabled: true
```

- 일반 ADMIN 또는 MODERATOR 역할만으로는 본문을 볼 수 없습니다.
- 감사 화면은 읽기 전용입니다.
- 페이지를 열람할 때마다 대화 ID, 페이지 위치, 조회 건수가 감사 로그에 기록되며 메시지 본문은 감사 로그에 복사하지 않습니다.
- 설정 변경 후 `/kchat reload` 또는 재시작을 실행합니다. JAR 교체는 서버 재시작이 필요합니다.

### 서버 간 DM 버전 요구사항

서버 간 DM을 주고받는 모든 서버는 KOKOTO WebChat 4.6.1 이상을 사용해야 합니다. `서버명 · 종류` 클릭 시 해당 글의 UUID와 원본 서버 ID를 직접 전달하며, 타 서버 검색 결과와 기존 원격 대화에서도 대상 서버 ID와 플레이어 UUID를 함께 유지합니다.

---

## 4.6.1에서 4.6.2로 업그레이드

KOKOTO WebChat 4.6.2는 DM 전달 신뢰성을 개선하고 동일 이름 DM 라우팅 문제를 수정하며 메시지별 읽음 상태를 추가합니다. 새로 설정해야 하는 관리자 옵션은 없습니다.

### 변경 사항

- 서버가 지정되지 않은 DM 이름은 현재 서버 사용자만 대상으로 합니다. 타 서버 대상은 `server-id + UUID`로 명시적으로 구분합니다.
- 타 서버 DM은 수신 서버가 실제 저장을 확인한 뒤에만 전달 완료로 처리합니다. 라우팅, HTTP, 타임아웃, 수신 실패는 재시도 가능한 실패 상태로 남습니다.
- DM 재시도는 영구 relay ID를 재사용하고 웹 전송은 client message ID를 사용하므로 요청 또는 응답이 불확실해도 같은 메시지가 중복 저장되지 않습니다. 서버 재시작으로 중단된 pending 전송은 재시도 가능한 실패 상태로 복구합니다.
- 그룹채팅 웹 전송도 client message ID로 중복을 방지합니다. 정상 전송 완료 문구는 표시하지 않으며, 시간 옆에는 처리 중 `전송중`, 실패 시 `실패 · 재시도`만 짧게 표시합니다.
- DM과 그룹채팅의 **모든 메시지**에 읽음 상태를 계산하며 시간 표시 옆에 표시합니다. 1:1 DM은 상대가 읽기 전 짧은 `미확인` 문구를 표시하고, 읽으면 `✓`로 바뀝니다. 그룹채팅은 기존처럼 미확인 수신자 수를 숫자로 표시하고 0명이 되면 `✓`를 표시합니다.
- 타 서버 DM의 읽음 상태는 인증된 사설 릴레이를 통해 전달되며 허브/체인 구성에서도 원본 메시지 쪽에 반영됩니다. 대화방을 다시 열면 최신 읽음 ACK를 안전하게 재전송하므로 일시적인 릴레이 또는 HTTP 실패 때문에 체크표시가 영구적으로 누락되지 않습니다.
- 다단계 DM 릴레이는 최종 수신 서버의 저장 확인 뒤에만 상위 서버에 성공을 반환합니다.
- 업데이트 알림을 수정했습니다. 알림 대상 관리자가 로그인하면 이전 예약 조회 결과에만 의존하지 않고 제한된 주기로 Modrinth를 다시 확인하며, OP를 명시적으로 알림 대상으로 인정합니다. 조회 실패는 경고 로그로 남고 reload 시 이전 업데이트 리스너도 해제됩니다.

### 설정 마이그레이션

4.6.1과 비교해 4.6.2 기본 설정에는 새 관리자 설정 키가 없고 기존 설정 기본값도 변경되지 않습니다. 검토용 버전 표식만 변경됩니다.

```yaml
config-version: "4.6.2"
```

검토 완료된 4.6.1 설정으로 실행하면 `plugins/KOKOTO-WebChat/config-migration-4.6.2.yml`을 생성합니다. 관련 없는 누락 설정이나 로컬/기본값 차이가 없다면 이 파일에는 새 `config-version` 표식만 들어갑니다. 실제 `config.yml`은 자동으로 덮어쓰지 않습니다.

### 서버 간 배포

서버 간 DM을 주고받는 모든 서버는 KOKOTO WebChat 4.6.2 이상을 사용해야 합니다. JAR 교체 후 각 서버를 재시작하면 새 릴레이 처리와 추가 방식 DB 마이그레이션이 적용됩니다. 기존 DM 및 그룹채팅 메시지는 유지됩니다.

---

## KOKOTO WebChat 4.6.3 업그레이드

4.6.3은 그룹채팅 메시지 본문을 확인할 수 있는 선택적 읽기 전용 관리자 감사 기능을 추가합니다. 기존 DM 감사 동작은 변경하지 않습니다.

### 새 설정

```yaml
group-chat:
  admin-audit:
    enabled: false

config-version: "4.6.3"
```

이미 `4.6.2`로 표시된 설정이라면 다른 실제 누락이 없는 경우 migration fragment에는 이 새 스위치와 4.6.3 검토 마커만 들어갑니다. 기존 `config.yml`은 덮어쓰지 않습니다.

### 접근 조건

다음 두 조건이 모두 필요합니다.

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

group-chat:
  admin-audit:
    enabled: true
```

일반 ADMIN/MODERATOR 역할만으로는 본문을 볼 수 없습니다. 감사 화면은 읽기 전용이고 방 멤버십이 없어도 열 수 있지만 실제로 방에 참여하지 않으며, 읽음/미확인 수를 변경하지 않고 메시지 전송·업로드·숨김·멤버 변경도 제공하지 않습니다. 각 페이지 열람은 본문을 감사 로그에 복사하지 않은 채 `admin.group-audit-read`로 기록됩니다.

### 설정 주석

4.6.3은 기본 `config.yml` 주석을 현재 업데이트 체크, 타 서버 DM 전달/읽음 ACK, 그룹 읽음 상태, DM/그룹 관리자 감사 동작에 맞게 갱신합니다. 서버 시작 또는 `/kchat reload` 때 기존 config의 주석이 **이전 KOKOTO WebChat 기본 주석과 정확히 같은 경우에만** 새 기본 주석으로 바뀔 수 있습니다. 이 주석 갱신은 설정값을 변경하지 않으며, 사용자가 수정한 주석은 유지되고 `config-version`도 migration fragment를 검토한 뒤 관리자가 직접 변경하는 방식 그대로입니다.
### 비공개 채팅 미디어 재생

DM 및 그룹채팅 메시지 목록도 일반채팅과 같은 stable key 기반 DOM 갱신 방식을 사용합니다. 같은 대화가 갱신될 때 기존 메시지와 영상/오디오 DOM은 계속 연결된 상태로 유지하고, 새로 추가·삭제된 메시지와 전달/읽음 메타데이터만 갱신합니다. 따라서 메시지를 보내거나 받는 동안 재생 중인 미디어가 처음부터 다시 시작되지 않습니다. 이미 맨 아래를 보고 있던 경우에만 최신 메시지를 따라가고, 중간을 보고 있으면 현재 위치를 유지합니다. 대화방을 나가거나 다른 대화로 전환하면 해당 대화의 메시지/미디어 DOM과 private 미디어 열림 상태를 완전히 삭제합니다. 다시 들어오면 `▶ Video`/`▶ Audio` 같은 미열림 click-to-load 상태부터 새로 생성되며, click-to-load가 꺼져 있어도 이전 플레이어를 재사용하지 않고 새 미재생 미디어 요소를 만듭니다. 재진입만으로 `play()`를 호출하지 않습니다.

---

## KOKOTO WebChat 4.7.0 업그레이드

4.7.0은 Bukkit/Spigot 호환 기준을 Minecraft 1.18까지 낮추고, 관리자 커스텀 이모지 다중 업로드와 설정 가능한 메시지 토큰 치환을 추가합니다.

### 호환성

- 보수적으로 지원하는 Minecraft 범위: **1.18 ~ 26.2**
- Java 요구 버전: **Java 17**
- `plugin.yml`: `api-version: '1.18'`
- Maven 빌드 기준 API: `spigot-api:1.18.2-R0.1-SNAPSHOT`
- Paper `AsyncChatEvent`는 reflection으로 감지하며 Bukkit `AsyncPlayerChatEvent`를 실제 링크된 fallback으로 유지합니다.
- 1.17 이하는 이번 릴리스의 공식 호환 범위로 잡지 않습니다.

### 커스텀 이모지 다중 업로드

이모지 업로드도 일반 채팅 파일 업로드와 같은 파일 선택 흐름을 사용합니다. 화면의 업로드 버튼은 숨겨진 다중 파일 입력창을 열고, 파일 선택창에서 파일을 고르면 선택된 `FileList`를 즉시 일반 배열로 복사한 뒤 native input을 비우고 바로 순차 업로드를 시작합니다. 별도의 선택 확인용 업로드 버튼은 없고, 파일 선택창 focus/visibility 우회 로직도 사용하지 않습니다. 진행률과 실제 전송 중 취소는 유지됩니다. 서버 쪽 기존 이모지 업로드 endpoint가 파일별 검증, 전체 용량 계산, 중복 파일명 처리, 감사 로그, PNG sidecar 생성을 그대로 담당합니다.

### 메시지 토큰

기본 alias는 영어만 제공하며 관리자가 어떤 언어로든 교체하거나 추가할 수 있습니다. `:enter:`, `:newline:`, `:nextline:`, `:linebreak:`, `:br:`은 다음 줄, `:blankline:`, `:emptyline:`, `:paragraphbreak:`은 빈 줄, `:tab:`, `:indent:`는 설정된 수의 공백으로 치환됩니다. `:separator:` 같은 일반 문자 치환도 custom 항목으로 추가할 수 있습니다. 알 수 없는 토큰은 그대로 두므로 기존 커스텀/이미지 이모지 토큰과 충돌하지 않습니다.

### 설정

4.7.0은 `message-tokens` 설정을 추가합니다. 그 외 기존 기본값은 변경하지 않았고 검토 표식은 다음과 같이 변경됩니다.

```yaml
config-version: "4.7.0"
```

startup/reload 시 알려진 최상위 `config.yml` 블록도 4.7.0 bundled 기본 순서로 재정렬하며, 각 블록의 현재 내용·설정값·사용자 지정 주석은 보존하고 기본에 없는 최상위 블록은 마지막에 기존 순서대로 유지합니다.

검토가 끝난 4.6.3 설정에는 `config-migration-4.7.0.yml`을 통해 새 `message-tokens` 섹션과 4.7.0 검토 표식이 추가됩니다. 비교 대상은 4.6.3으로 제한되지 않으며 더 오래된 설정이나 `config-version`이 없는 설정도 현재 4.7.0 기준으로 누락 항목을 검사합니다. 또한 `config-reference-4.7.0.yml`을 항상 생성해 현재 JAR의 완전한 4.7.0 기본 설정과 모든 주석을 그대로 제공합니다. 오래된 설정은 이 파일을 기준으로 전체 구조를 비교하면 됩니다. `message-tokens.custom: {}` 같은 빈 map도 누락된 경우 migration에 유지됩니다. migration 파일 하단에는 전체 reference와의 텍스트 diff가 주석으로 추가됩니다. 동일한 줄은 출력하지 않고, 각 차이는 파일명 다음 별도 줄에 `Line` 또는 `Lines`를 표시한 뒤 실제로 다른 내용만 보여줍니다. 실제 차이 줄은 원본 YAML 들여쓰기를 그대로 유지하도록 줄 앞에 `#`만 직접 붙이며 reference 전용 블록은 삽입 위치도 표시합니다.

#### 게임 줄바꿈 동작

설정된 `newline` / `blank-line` 토큰으로 만든 줄바꿈만 Minecraft의 기존 한 줄 평탄화 처리를 보호 상태로 통과한 뒤 최종 전송 시 별도의 게임 채팅 줄로 출력됩니다. 일반 CR/LF 입력은 기존과 동일하게 평탄화됩니다. 서버간 릴레이에서 이 의도적인 줄바꿈을 게임에 표시하려면 수신 KOKOTO WebChat 서버도 같은 4.7.0 토큰 줄 전송 지원이 적용되어 있어야 하며, 구버전 수신 서버는 전달된 일반 LF를 기존 평탄화 단계에서 공백으로 바꿉니다.

---

## KOKOTO WebChat 5.0.0 업그레이드

5.0.0은 직전 정식 버전 4.7.0 이후의 개발 내용을 하나로 정리하면서 **BlueMapWebChat(BMWC) → KOKOTO WebChat(KWC)** 이름 전환까지 완료하는 메이저 릴리스입니다.

### 배포 주소 전환

가장 안전한 배포 순서는 **5.0.0을 기존 BlueMapWebChat 프로젝트 페이지에 먼저 게시하는 것**입니다. 4.7.0의 업데이트 체커는 기존 Modrinth `bluemapwebchat` 프로젝트를 확인하므로, 기존 페이지에 5.0.0을 먼저 올려야 기존 설치가 정상적으로 5.0.0을 발견할 수 있습니다.

전환 중 기존 주소:

- Modrinth: `https://modrinth.com/plugin/bluemapwebchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/bluemapwebchat`
- GitHub: `https://github.com/KOKOTO-DEV/BlueMapWebChat`

전환 완료 후 목표 KOKOTO WebChat 주소:

- Modrinth: `https://modrinth.com/plugin/kokoto-webchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/kokoto-webchat`
- GitHub: `https://github.com/KOKOTO-DEV/KOKOTO-WebChat`

KWC 5.0.0 업데이트 체커는 `kokoto-webchat`을 먼저 확인하고 아직 없으면 `bluemapwebchat`으로 fallback하므로 전환 전후를 모두 처리합니다. 플랫폼에서 기존 BMWC 프로젝트를 같은 항목으로 이름/주소 변경할 수 없다면 BMWC 페이지는 종료/이전 안내 페이지로 남기고 새 KWC 페이지로 연결합니다. **기존 4.7.0 사용자가 5.0.0 bridge release를 발견할 수 있기 전에 BMWC 페이지를 먼저 종료하면 안 됩니다.**

GitHub는 저장소를 `KOKOTO-WebChat`으로 rename하는 방식을 우선 사용하고, rename 후 로컬 clone의 origin도 새 URL로 갱신합니다.

### 4.7.0 → 5.0.0 주요 변경사항

- 공식 이름/식별자를 **KOKOTO WebChat**으로 통일: `/kchat`(`/kc`), `kwc.*`, `plugins/KOKOTO-WebChat` 또는 `config/KOKOTO-WebChat`, `dev.kokoto.webchat`, `kwc-*` 모듈.
- Bukkit/Paper/Spigot, Fabric 16개 exact-target, NeoForge 12개 exact-target, Forge 16개 exact-target이 공용 코어를 사용하도록 멀티플랫폼 구조 완성.
- BlueMap, squaremap, Dynmap, Pl3xMap, LiveAtlas, uNmINeD, Minecraft Overviewer 어댑터 구조 추가/정리.
- standalone 기본 활성화, 공개 기본 prefix `/chat`, API `/chat/api` 구조 확정.
- Unicode 컨텐츠 필터, UTF-8 starter filter list, 규칙 편집/테스트, mask/replace, 우회 탐지 추가.
- 로그인 계정별 서버측 시각 UI 프로필, strict JSON import/export, 계정 공통 키워드/알림 설정 추가.
- 같은 기기의 Web Push와 라이브 페이지 OS 알림 중복 억제.
- 관리자 Discord 키워드 알림 추가. 감지/포맷/멘션/중복제거는 KWC가 담당하고 DiscordSRV는 JDA 연결과 채널 매핑만 제공.
- `update-check.enabled`가 Bukkit뿐 아니라 Fabric, NeoForge, Forge에서도 동작하며 KWC 우선/BMWC fallback Modrinth 조회와 `kwc.update.notify` 접속 알림을 사용합니다.
- `upload.filename-mode: random|original` 추가. 원본 파일명 모드에서 안전한 Unicode/공백/`~`/`+`/`%` 파일 제공과 참조 추적을 통일하고 Windows 클립보드 8.3 별칭 문제 수정.
- `discordsrv.game-to-discord`를 `discordsrv.game-relay-mode: discordsrv|kwc`로 대체하고 게임발 등록 이모지의 DiscordSRV native 경로 처리 수정.
- 서버 relay peer/backoff, 타 서버 DM 전달/읽음 확인, 플랫폼 공통 `/kchat` 명령 일관성 강화.
- 정상 UX를 바꾸지 않는 보안 hardening 추가: Bearer 인증, 1회용 SSE stream ticket, request body 상한, bounded HTTP worker, 관리자 IP 제한 재검증, Web Push SSRF 차단, Discord 멘션/CDN redirect 방어, iframe message source 검증.

### 플랫폼 지원

- Bukkit/Paper/Spigot: `KOKOTO-WebChat-5.0.0-Bukkit-1.18-26.2.jar`, Java 17 bytecode, 선언 지원 범위 1.18–26.2.
- Fabric exact-target: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`. 대상별 JDK 17/21/25를 사용합니다.
- NeoForge exact-target: `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`. 대상별 JDK 17/21/25를 사용합니다.
- Forge exact-target: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`. 대상별 JDK 17/21/25를 사용합니다.

### BlueMapWebChat 데이터 마이그레이션

Bukkit에서 기존 `plugins/BlueMapWebChat`이 있으면 첫 KWC 시작 시 1회 마이그레이션 입력으로 사용합니다. 운영 데이터는 `plugins/KOKOTO-WebChat`으로 가져오되 원본 BMWC 디렉터리는 수정하지 않아 롤백/마이그레이션 원본으로 남깁니다.

기존 `web-addon.*` / `standalone-web.*`는 `adapters.bluemap.*` / `frontend.standalone.*`로 변환합니다. 5.0.0에서는 `/bmchat`, `/bluemapchat`, `/bmc`, `/kwc` 명령 alias를 등록하지 않습니다. 기존 `bluemapwebchat.*` 권한은 런타임 호환 fallback으로만 처리합니다. Relay Protocol v1의 `X-BMWC-Relay-*` wire header는 기존 BMWC peer와의 호환을 위해 유지합니다.

### BMWC → KWC 웹 경로 마이그레이션

기존 BMWC 표준 `/bmwc/api`, `/bmwc/chat` reverse-proxy 구조는 KWC 기본값으로 유지하지 않습니다. 새 공개 기본 prefix는 `/chat`이고 standalone `/chat`, API `/chat/api`가 됩니다. KWC는 마이그레이션 시 표준 BMWC URL 설정을 정규화할 수 있지만 외부 Caddy/nginx 파일은 수정할 수 없으므로 직접 변경해야 합니다. `CADDY_HTTPS.md`, `NGINX_HTTPS.md`를 참고하세요.

### 설정 변경

4.7.0과 5.0.0 bundled reference를 구조적으로 비교하면 **79개 설정 경로 추가, 14개 제거, 기존 2개 값 변경**입니다. 주요 전환은 `web-addon.* → adapters.bluemap.*`, `standalone-web.* → frontend.standalone.*`, `discordsrv.game-to-discord* → discordsrv.game-relay-*`, `ui.show-login-only-when-hidden` 제거입니다. 추가 그룹에는 다른 지도 어댑터, `content-filter.*`, `ui.user-profiles.*`, `admin-alerts.discord.*`, `http.public-prefix`, relay forwarding 제어, `upload.filename-mode`가 포함됩니다.

실제 버전 migration 시 고정된 구버전 config를 먼저 백업하고 최신 번들 `config.yml`을 새 뼈대로 만든 뒤 기존 사용자 설정값만 덮어씁니다. 구버전 주석/레이아웃은 가져오지 않고 실제 config를 다음 상태로 만듭니다.

```yaml
config-version: "5.0.0_auto_migration"
```

`_auto_migration`을 유지하면 startup/reload마다 최신 같은 버전 번들 config를 다시 뼈대로 만들고 현재 값을 덮어씁니다. `config-reference-5.0.0.yml`은 관리자 확인용 기본설정 복사본일 뿐 migration 입력이 아닙니다. 같은 버전 config를 고정할 때 정확한 `config-version: "5.0.0"`으로 바꿉니다.

### 보안 수정과 사용자 체감

정상 사용자의 UI/사용법은 바뀌지 않습니다. 로그인, 채팅, DM/그룹, 업로드, 프로필, Push, 관리자 화면은 기존 방식 그대로 사용합니다. 차단되는 것은 허용되지 않은 관리자 IP, 비정상 Push endpoint, 과도하게 큰/잘못된 요청, 의도하지 않은 Discord 멘션, 경로 규칙을 어기는 업로드처럼 원래 허용하면 안 되는 경우입니다.

공개 운영은 HTTPS를 권장합니다. 공인 IP 직접 운영 자체는 지원하지만 브라우저가 secure context를 요구하는 기능은 브라우저 보안 정책을 따릅니다.

### 최종 릴리스 판정


> `validate-release-windows.bat`와 이 파일이 필요로 하는 PowerShell helper는 source archive에 포함되어 있습니다. 별도의 `KWC-5.2.0-validation-tools.zip`에는 개발용 브라우저 회귀검증 도구만 들어 있으며 릴리스 빌드 실행에는 필요하지 않습니다.

최종 후보는 `validate-release-windows.bat`가 `FINAL RELEASE BUILD PASS`로 끝나고 배포 JAR이 정확히 45개 수집되며, static/config/i18n/document 검증과 로그인·공개채팅·업로드/클립보드·DM/그룹·relay·사용 중인 지도/Discord 연동 smoke test가 통과해야 배포 확정합니다.

---

## KOKOTO WebChat 5.1.0 업그레이드


![ui.language 설정 재구성 흐름](../assets/config-language-migration.svg)

[Animated GIF](../assets/config-language-migration.gif) · [PNG](../assets/config-language-migration.png) · [SVG](../assets/config-language-migration.svg)

> **중요:** `ui.language`는 config/reference/migration의 표시 언어만 바꾸며, 실제로 파싱된 운영자 설정값은 그대로 유지됩니다.

KOKOTO WebChat 5.1.0은 **5.0.0** 계열에서 업그레이드합니다. 릴리스 변경사항은 **5.0.0** 릴리스에서 5.1.0까지의 최종 변경사항을 기준으로 정리합니다.

### 업그레이드 전

1. `config.yml`, 채팅/비공개 채팅 DB 또는 JSONL, 업로드, 이모지 파일, 감사 로그를 포함한 KWC 데이터 디렉터리 전체를 백업합니다.
2. 여러 서버가 Relay로 연결돼 있다면 관련 서버를 함께 업그레이드하고 재설정할 계획을 세웁니다. Relay Protocol v1은 5.1.0 Relay v2와 상호 운용되지 않습니다.
3. Caddy/Nginx 뒤에서 사용 중이면 업그레이드 후 client IP 해석을 확인할 수 있도록 기존 공개 URL과 proxy 구성을 유지합니다.

### 설정 migration과 언어

5.1.0은 기존의 **파싱된 설정값을 보존**하면서 현재 template을 기준으로 표시 형식을 재구성합니다. 실제 `ui.language`가 재구성된 `config.yml`, 생성되는 `config-reference-5.1.0.yml`, migration/Difference 안내문의 주석·레이아웃 언어를 선택합니다. 내장 표시 언어는 `en-US`, `ko-KR`, `ja-JP`, `zh-CN`입니다.

Difference 판정은 semantic 방식입니다. 파싱된 YAML setting path와 value를 비교하며 주석, 빈 줄, 들여쓰기, 따옴표 스타일, 줄 번호, 키 순서는 비교 대상이 아닙니다. 따라서 표시 언어만 바꿔서는 설정 Difference가 생기면 안 됩니다.

첫 기동 후 `config.yml`과 `config-reference-5.1.0.yml`을 모두 검토하세요. 5.1.0에서 이미 만든 Relay group을 포함한 운영자 설정값은 same-version 자동 migration 표시 재구성에서도 보존됩니다.

### Relay Protocol v2는 수동 trust migration이 필요함

5.1.0 이전 설정의 첫 migration에서는 기존 flat Relay v1 trust를 새 구조로 추측 변환하지 않습니다. 구형 global shared-secret, flat peer, forwarding 설정을 폐기하고 `server-relay.enabled`를 `false`로 안전하게 재설정합니다.

명시적인 `server-relay.groups`를 만듭니다. 새 group은 한 서버에서 `shared-secret: ""`로 두고 시작/리로드하여 안전한 random 값을 생성·저장한 뒤, 그 값을 같은 group의 다른 서버에 그대로 복사하세요. 기존 비어 있지 않은 secret은 보존되며 수동값이 32자 미만이면 invalid 상태로 남습니다. 같은 group 안에서 양쪽 서버가 서로를 reciprocal peer로 등록해야 합니다. peer 항목은 `id`, `url`, `enabled`만 가지며 peer별 secret은 없습니다. 의도한 모든 peer의 상호 설정을 확인한 뒤에만 `server-relay.enabled`를 다시 켜세요.

순차 업그레이드를 시작하기 전에 아직 5.0.0인 서버에서는 `server-relay.enabled: false`로 바꾸고 reload하십시오. 5.0.0 Relay를 켠 채 상대 서버만 5.1.0으로 올리면 구형 v1 요청을 계속 재시도하면서 정상적인 HTTP 426 응답이 반복 WARN으로 쌓일 수 있습니다. 모든 서버를 5.1.0으로 올리고 v2 group/peer 설정을 맞춘 뒤 Relay를 다시 활성화합니다.

Relay v1/BMWC endpoint는 HTTP 426을 반환합니다. direct 1-hop HTTP peer는 경고와 함께 암호화/인증된 Relay v2 payload를 전달할 수 있지만 forwarding은 같은 group의 **HTTPS→HTTPS** 경로만 허용됩니다. Relay v2는 E2EE가 아니라 hop-by-hop authenticated encryption입니다.

### 비공개 Reply 저장소 업그레이드

5.1.0은 DM/그룹 메시지에 지속되는 reply metadata를 추가합니다. 기존 비공개 메시지는 그대로 유효합니다. SQLite 그룹 저장소는 schema upgrade에서 필요한 optional reply column을 추가하고, 기존 DM/JSONL record는 reply metadata가 없으면 그대로 생략하는 하위 호환 형식을 사용합니다. 수동 DB 변환은 필요하지 않습니다.

5.1.0은 방별 그룹 멤버십 이벤트 상태도 추가합니다. 기존 `group-messages.db`에는 `group_rooms.membership_events_enabled`(기본 ON)와 `group_messages.event_type`(기본 일반 메시지) 컬럼을 자동 추가하므로 수동 DB 변환은 필요 없습니다. 그룹채팅 UI를 닫는 동작은 퇴장 이벤트를 만들지 않습니다.

서버간 DM Reply는 상대 서버의 local DB row ID가 아니라 stable relay message ID를 사용합니다. 서버 사이에서 비공개 메시지 ID를 복사하거나 강제로 맞추지 마세요.

### Emoji와 SSE 변경

SSE 기본 제한은 **resolved client IP당 10개**, **전체 500개**로 변경되었습니다. 각 값의 `0`은 해당 제한 비활성화입니다. reverse proxy 뒤에서는 `http.trusted-proxies`를 확인하세요. 잘못 설정하면 여러 사용자가 proxy IP 하나로 보이면서 같은 per-IP 제한을 공유할 수 있습니다.

Emoji catalog reload도 복구성이 강화되었습니다. `/emojis` 일시 실패 시 마지막 정상 catalog를 유지하고 bounded exponential backoff로 재시도하며, SSE 재연결 뒤 강제 동기화하고 서버의 `emoji-catalog` invalidation event를 받아 갱신합니다. `emoji.message-token-limit: 0`은 계속 무제한입니다.

5.1.0은 custom emoji pack/item 이름도 canonicalize합니다. 지원하지 않는 문자와 공백을 제거하고 같은 pack 안의 충돌은 숫자 suffix로 구분합니다. 실제 디스크 파일명이나 token 문자열을 고정값으로 가정하는 외부 연동이 있다면 확인하세요.

- Android 관리자 이모지 업로드는 이미지 전용 Photo Picker 대신 일반 시스템 파일/DocumentsUI 선택기를 사용합니다. 데스크톱의 이미지 필터와 서버측 이미지 검증은 그대로 유지됩니다.

### 함께 확인할 다른 변경사항

- DM/그룹 Reply는 원문 관계를 저장하고 현재 DM thread/그룹 membership을 서버에서 다시 검증합니다.
- 게임 `/kchat group` 명령은 공백이 포함된 방 이름, 따옴표 이름, 반복 공백을 올바르게 해석합니다.
- 방별 멤버십 알림은 실제 입장/초대 수락과 퇴장/강퇴/차단에만 발생하며, 방 창을 닫거나 숨기는 것은 퇴장으로 처리하지 않습니다.
- 채팅 설정 프로필은 명시적으로 저장한 값과 미설정 상태를 정확히 보존하고, 불러온 글자 크기는 이미 열린 DM/그룹 창에도 적용됩니다.
- 좁은 화면/모바일에서 DM/그룹의 시간·Reply·읽음 상태가 큰 고정 영역을 차지하지 않고 자연스럽게 줄바꿈됩니다.
- `/kchat reload`는 외부 노출 HTTP listener와 direct HTTP Relay peer를 다시 검사하고, 해당 다국어 경고를 서버 로그뿐 아니라 명령 실행자에게도 출력합니다.
- `commands.broadcast-result-to-web-chat: true`이면 Web 명령 실행 안내가 온라인 Minecraft 플레이어에게도 전달됩니다.
- 공개/DM/그룹 메시지 입력창만 일반 text `<input>` 대신 1줄 `<textarea>`를 사용하고 `autocomplete=off`, Enter 전송, 커서 위치/이모지 삽입 동작은 그대로 유지합니다. Android Chrome이 일반 입력창에도 비밀번호·주소·결제수단 Autofill accessory를 띄우는 경로를 피하기 위한 조치이며 로그인/비밀번호 입력창은 변경하지 않습니다.
- 5.2.0부터 updater는 canonical Modrinth `kokoto-webchat`만 확인하며 기존 BMWC 프로젝트 주소는 업데이트 소스로 조회하지 않습니다.
- 외부에 노출된 plain HTTP listener와 direct HTTP relay peer는 다국어 보안 경고를 출력합니다.

### 업그레이드 후 확인

1. `config-version`과 생성된 5.1.0 reference/migration 파일을 확인합니다.
2. `ui.language`를 바꿨을 때 표시 형식만 바뀌고 운영 설정값이 유지되는지 확인합니다.
3. Relay를 사용한다면 v2 reciprocal peer를 먼저 확인하고 다시 활성화하며, 다른 서버가 더 이상 v1 endpoint를 기대하지 않는지 확인합니다.
4. proxy 뒤에서는 resolved client IP와 SSE 동작을 확인하고, 문제 분석 중에만 `http.log-client-ip-resolution`을 임시 활성화합니다.
5. 실제 사용하는 배포 방식에서 공개 채팅, DM/그룹 Reply, custom emoji, 업로드, Web Push/알림, map/standalone frontend를 테스트합니다.
6. 정상 운영과 retention cleanup을 충분히 확인할 때까지 업그레이드 전 백업을 보관합니다.

## KOKOTO WebChat 5.1.0에서 5.2.0으로 업그레이드

업그레이드 전에 KWC 데이터 디렉터리를 백업하세요. 일반 5.1.0 → 5.2.0 migration은 지원되는 운영자 값과 기존 Relay v2 group/secret/peer를 보존합니다. relay trust reset은 과거 pre-5.1.0 → 5.1.0 migration에만 해당합니다. 현재 reference는 `config-reference-5.2.0.yml`이며 자동 검토 상태는 관리자가 정확한 `config-version: "5.2.0"`을 선택하기 전까지 `5.2.0_auto_migration`을 사용합니다.

5.2.0은 Relay Protocol 2.1을 기존 2.x와 호환되는 capability revision으로 도입합니다. Protocol major `2`가 호환성 경계이고 KWC 제품 버전은 진단용입니다. 공개 reaction, 참가자 서버로만 전달되는 타 서버 DM reaction, 타 서버 DM typing은 2.1 확장을 사용하며 공통 v2 public/DM/read 동작은 2.x 호환 범위에 남습니다.

새 사용자 데이터로 `chat.conversation-archive.enabled`가 true일 때 개인 대화 snapshot용 `conversation-archives.db`, 반응 상태용 `public-reactions.jsonl`(기존 파일명 유지, 공개/DM/그룹 반응 저장), 관리자 reaction picker 설정용 `reaction-catalog.json`이 추가됩니다. 다른 KWC 데이터와 함께 백업하세요. snapshot은 첨부 바이트를 복제하지 않으며 관리자 원본/방 강제삭제와 비공개 방 잠금 정책이 개인 보관함보다 우선합니다. `reaction-catalog.json`에는 관리자 반응 전체 ON/OFF 상태와 커스텀 이모지 허용 여부도 저장되며, 기능을 꺼도 기존 `public-reactions.jsonl` 데이터는 삭제하지 않습니다.

업그레이드 후 공개/DM/그룹 reaction, DM/그룹 `입력 중...`, 대화 저장과 PDF/인쇄, 비공개 방 Settings/Invite/Leave 권한, 공개/DM/그룹 32px bottom-follow를 확인하세요. 여러 relay 서버가 있으면 reaction/typing 확장을 일관되게 사용하려면 모두 5.2.0/Relay 2.1로 올리는 것을 권장합니다.

