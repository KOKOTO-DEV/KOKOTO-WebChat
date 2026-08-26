# KOKOTO WebChat


## 프로젝트 이름 및 배포 주소 전환

**BlueMapWebChat(BMWC)는 5.0.0부터 KOKOTO WebChat으로 이름이 변경됩니다.** 4.7.0 사용자의 기존 업데이트 확인 경로를 끊지 않기 위해 5.0.0 전환 릴리스는 우선 기존 BlueMapWebChat 프로젝트 페이지를 통해 배포할 수 있습니다. BMWC 4.x 데이터는 5.0.0에서 마이그레이션 입력으로만 사용하며, 실제 5.0.0 런타임 이름은 KOKOTO WebChat입니다.

전환 기간에는 다음 기존 주소를 배포 진입점으로 계속 사용할 수 있습니다.

- Modrinth: `https://modrinth.com/plugin/bluemapwebchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/bluemapwebchat`
- GitHub: `https://github.com/KOKOTO-DEV/BlueMapWebChat`

목표 KOKOTO WebChat 주소는 `https://modrinth.com/plugin/kokoto-webchat`, `https://www.curseforge.com/minecraft/bukkit-plugins/kokoto-webchat`, `https://github.com/KOKOTO-DEV/KOKOTO-WebChat`이며 **각 플랫폼에서 실제 이름/주소 전환이 완료된 뒤** 정식 주소로 사용합니다. 아직 생성·변경되지 않은 목표 주소를 현재 다운로드 주소처럼 안내하지 않습니다. 기존 프로젝트를 같은 항목으로 연결할 수 없는 플랫폼에서는 BMWC 페이지를 종료/이전 안내 페이지로 남기고 새 KOKOTO WebChat 페이지로 이동하도록 안내합니다.

Bukkit/Paper/Spigot, Fabric 1.18.2~26.2 exact-target, NeoForge 1.20.2~26.2 exact-target 및 Forge 1.18.2~26.2 exact-target 서버에서 동작하는 서버측 웹 채팅입니다. standalone 페이지뿐 아니라 BlueMap, squaremap, Dynmap, Pl3xMap, LiveAtlas, uNmINeD, Minecraft Overviewer 지도 화면에도 KWC 채팅을 삽입할 수 있습니다.

## 주요 기능

- 로그인 사용자별 시각 UI 프로필을 계정에 여러 개 저장(기본 5개, 관리자 조절)하고, 서버 간 이동용 strict JSON 프로필 가져오기/내보내기 지원
- 로그인 사용자의 키워드/알림 종류를 계정 공통으로 동기화하고 창 상태·Web Push endpoint는 기기 로컬 유지, 동일 기기의 Web Push 활성 시 페이지 OS 알림 중복 억제
- KWC가 감지·멘션·중복 제거를 담당하고 DiscordSRV 논리 채널을 Web Admin에서 선택하는 관리자 전용 Discord 키워드 알림
- 공개/그룹 및 선택형 DM에 공통 적용되는 Unicode 금지어 필터: 차단/마스킹/순화어, N:1·1:N·N:N 치환, compact/interleave 우회 탐지
- UTF-8 `filter-lists/*.txt` 대량 필터 단어 목록 + 목록별 차단/필터링 선택 + 커스텀 차단/마스킹/치환 규칙, Web Admin에서 TXT 가져오기·편집·활성/비활성·삭제 지원
- Web Admin **Filter/Settings** 관리와 게임 `/kchat filter`, `/kchat settings` 운영 명령
- 커스텀 규칙 작성법과 Block/Mask/Replace 실제 예: [`docs/CONFIGURATION_KO.md`](docs/CONFIGURATION_KO.md#커스텀-필터-빠른-사용법)
- 세션 기간 변경 시 기존 USER/MODERATOR 또는 ADMIN 세션을 최초 생성 시각 기준으로 재계산하며 `0`은 무제한
- `upload.filename-mode: original` 선택 시 새 업로드의 안전한 Unicode 원본 파일명을 보존하고 중복 이름은 덮어쓰지 않음
- BlueMap/squaremap/Dynmap/Pl3xMap/LiveAtlas/uNmINeD/Overviewer 지도 안 채팅 패널 또는 standalone 페이지 제공
- 게임 ↔ 웹 채팅 양방향 전달
- HMAC 서명 기반 서버 간 공개 채팅 릴레이와 서버별 웹 색상 배지, 게임/Discord 서버명 표시
- Minecraft 메시지 클릭 댓글(`/kchat reply`)과 웹 발신자 클릭 KWC DM(`/kchat dm`)
- 게임 `/w`/`/msg`/`/tell`류 귓속말을 양쪽 사용자의 웹 DM으로 선택적 복제
- 게스트 채팅, 수학 캡차, 쿨다운/분당 제한
- `/kchat auth <code>` 계정 연동, 웹 비밀번호 로그인, 로컬 관리자 계정
- 관리자/모더레이터 웹 패널, 메시지 숨김, 고정/삭제 표시 토글, 게스트/IP 뮤트, 세션 revoke
- 관리자 커스텀 이모지 관리: 이모지 폴더/파일 생성, 다중 업로드, 이름 변경, 이동, 삭제
- **ImageEmojis-Bero 1.9.x 연동**: Bukkit/Paper 계열에서 KWC 이모지 폴더 공유, 게임 glyph 변환, 토큰 기반 웹/릴레이 처리
- **SimpleNicks-Bero 연동**: Bukkit display name 기반 닉네임 표시와 실제 연결 계정 identity 분리
- 파일/클립보드 업로드, 이미지/영상/오디오/YouTube/Shorts 미리보기, 선택형 TikTok 및 X/Twitter 임베드
- DiscordSRV 연동, Discord CDN 미디어 캐시
- 답글 및 원본 메시지 점프, 게임 채팅 원문 미리보기, 고정 메시지, 가상 스크롤, 창 이동/크기조절, PIP
- 연동/저장된 플레이어 대상 1:1 대화 스레드형 메시지함, 안 읽은 배지, 스레드별 보관 설정
- 타 서버 릴레이 발신자를 기존 웹 DM 대상 검색에서 검색
- 정확한 계정 허용 목록과 감사 로그를 사용하는 선택형 읽기 전용 관리자 DM 본문 감사
- en-US, ko-KR, ja-JP, zh-CN 다국어 UI

## 연동 플러그인

KWC 5.0.0은 Bukkit/Paper 계열에서 다음 포크 플러그인 연동을 공식 문서화합니다.

- [**ImageEmojis-Bero**](https://github.com/KOKOTO-DEV/ImageEmojis-Bero) — `plugins/KOKOTO-WebChat/emojis`를 공용 폴더로 사용하고 웹/히스토리/릴레이에는 정규 `:pack/name:` 토큰을 유지하면서 게임에서는 ImageEmojis glyph로 표시할 수 있습니다. `serverIp` + `webServerPort`(대표적으로 TCP 5000)의 리소스팩 HTTP 서버는 Minecraft 클라이언트에서 접근 가능해야 합니다. [`docs/IMAGEEMOJIS_BERO_1_9_0_KO.md`](docs/IMAGEEMOJIS_BERO_1_9_0_KO.md) 참고. 일반 설치·운영은 [원본 ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis)를 따릅니다.
- [**SimpleNicks-Bero**](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) — `player-display.mode: "display-name"`으로 Bukkit display name에 적용된 닉네임을 KWC에 표시하면서 실제 연결 username/UUID는 별도 identity로 유지합니다. [`docs/SIMPLENICKS_BERO_KO.md`](docs/SIMPLENICKS_BERO_KO.md) 참고. 일반 설치·운영은 [원본 SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks)를 따릅니다.

둘 다 KWC hard dependency가 아니며, 이 Bukkit/Paper 연동 설명은 Fabric/NeoForge/Forge 빌드에서 Bukkit 플러그인 API를 지원한다는 의미가 아닙니다.

## 빌드

```bash
mvn clean package
```

```text
kwc-platform-bukkit/target/KOKOTO-WebChat-5.0.0-Bukkit-1.18-26.2.jar
```

### Fabric exact-target

Fabric은 16개 Minecraft 버전별 exact-target JAR로 빌드합니다. 스크립트가 대상에 따라 JDK 17/21/25를 선택합니다.

```bat
kwc-platform-fabric\build-all.bat
```

대상: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`. 산출물은 `kwc-platform-fabric/targets/<Minecraft>/build/libs/KOKOTO-WebChat-5.0.0-Fabric-<Minecraft>.jar`입니다.

### NeoForge exact-target

NeoForge는 12개 Minecraft 버전별 exact-target JAR로 빌드합니다. 1.20.2~1.20.6은 NeoGradle userdev, 1.21.1 이후 대상은 ModDevGradle을 사용하며 JDK 17/21/25를 대상별로 선택합니다.

```bat
kwc-platform-neoforge\build-all.bat
```

대상: `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`. 산출물은 `kwc-platform-neoforge/targets/<Minecraft>/build/libs/KOKOTO-WebChat-5.0.0-NeoForge-<Minecraft>.jar`입니다.

### Forge exact-target

Forge는 범용 JAR 하나가 아니라 16개 Minecraft 버전별 exact-target JAR을 빌드합니다.

```bat
kwc-platform-forge\build-all.bat
```

스크립트가 각 대상에 맞춰 JDK 17/21/25를 선택하고 `KOKOTO-WebChat-5.0.0-Forge-<Minecraft>.jar`을 각 target의 `build/libs/` 아래에 생성합니다.

### Windows 최종 릴리스 검증

소스 루트에서 `validate-release-windows.bat`를 실행하면 Bukkit, Fabric 16개 target, NeoForge 12개 target, Forge 16개 target을 연속 빌드합니다. `FINAL RELEASE BUILD PASS`가 출력되고 `release-5.0.0/`에 배포용 JAR이 정확히 45개 모이며 `SHA256SUMS.txt`가 생성되어야 실제 빌드까지 최종 검증된 것으로 판정합니다.

## 기본 설치

1. Bukkit/Paper/Spigot JAR은 `plugins/`에, Fabric/NeoForge/Forge JAR은 `mods/`에 넣습니다.
2. 서버를 한 번 실행해서 `<KWC data dir>/config.yml`을 생성합니다. `<KWC data dir>`는 Bukkit 계열에서는 `plugins/KOKOTO-WebChat`, Fabric/NeoForge/Forge에서는 `config/KOKOTO-WebChat`입니다.
3. 새로 생성된 config는 최상단 `enabled: false` 상태입니다. 설정 검토 전 웹 서버, 채팅 기능, 정리 작업이 실행되지 않게 하기 위한 안전 기본값이며, `/kchat reload`는 계속 사용할 수 있습니다.
4. 저장 방식, 보관 기간, 업로드, 미리보기, 인증, 외부 공개 설정을 확인한 뒤 `enabled: true`로 바꿉니다.
5. BlueMap 안에 띄울 경우 `adapters.bluemap.enabled: true`로 켭니다. Bukkit 계열에서는 BlueMap 웹 파일을 직접 관리하는 경우가 아니면 `auto-install`과 `auto-patch-webapp-conf`를 `true`로 둡니다. Fabric/NeoForge 26.1.2/26.2와 Forge 26.1.2/26.2는 BlueMap 모드가 있을 때 BlueMapAPI로 등록하며, 이전 exact target에는 직접 BlueMapAPI bridge가 포함되지 않습니다.
6. squaremap은 `adapters.squaremap.enabled: true`, Dynmap은 `adapters.dynmap.enabled: true`로 켭니다. Dynmap은 `configuration.txt`의 `webpath`를 찾아 KWC 전용 에셋과 `index.html` 마커 블록을 관리합니다.
7. Pl3xMap은 Bukkit/Paper 계열 또는 Fabric에서 `adapters.pl3xmap.enabled: true`로 켭니다. KWC는 Pl3xMap `config.yml`의 `settings.web-directory.path`를 읽어 KWC 전용 에셋과 `index.html` 마커 블록을 관리합니다. 현재 Pl3xMap 26.2는 NeoForge 빌드를 제공하지 않습니다.
8. LiveAtlas를 쓸 경우 Bukkit/Fabric/NeoForge/Forge에서 `adapters.liveatlas.enabled: true`로 켭니다. 기존 LiveAtlas `index.html`이 있는 웹루트만 수정하며, 외부 Caddy/nginx 디렉터리를 쓰면 `web-root`에 서버에서 접근 가능한 공유/마운트 경로를 지정합니다. 같은 실제 웹루트에 LiveAtlas adapter와 Dynmap/squaremap/Pl3xMap adapter를 동시에 켜지 않습니다.
9. uNmINeD 정적 웹 내보내기를 쓸 경우 `adapters.unmined.enabled: true`로 켭니다. uNmINeD는 서버 플러그인이 아니므로 보통 `web-root`에 내보낸 사이트 디렉터리를 지정합니다. 현재 `index.html`과 구형 `unmined.index.html`은 uNmINeD 표식을 확인한 뒤에만 수정합니다.
10. Minecraft Overviewer 정적 웹 지도를 쓸 경우 `adapters.overviewer.enabled: true`로 켜고 보통 `web-root`에 Overviewer의 생성 `outputdir`을 지정합니다. KWC는 Overviewer 전용 generator/asset 표식을 확인한 `index.html`만 수정하며 일반 Leaflet 페이지는 건드리지 않습니다.
11. standalone만 쓸 경우 `frontend.standalone.enabled: true`로 두고 지도 adapter를 모두 `false`로 둡니다.
12. 서버 재시작 또는 `/kchat reload`를 실행합니다. BlueMap은 `bluemap reload light`를 자동 요청하고, squaremap/Dynmap/Pl3xMap/LiveAtlas/uNmINeD/Overviewer는 KWC가 웹 파일을 직접 다시 확인합니다. 지도/사이트 생성기가 웹 파일을 다시 만들었다면 `/kchat reload`를 다시 실행합니다.


기존 설정값은 보존하지만 migration 중에는 기존 config의 주석/레이아웃을 이어 붙이지 않습니다. 실행 중인 JAR의 기본 `config.yml`이 유일한 migration 템플릿이며, KWC가 최신 기본 config를 새로 만든 뒤 기존 사용자 설정값만 그 위에 덮어씁니다. 따라서 구버전 주석·순서·공백·들여쓰기는 버리고 최신 번들 주석/레이아웃으로 통일됩니다. `<KWC data dir>/config-reference-5.0.0.yml`은 관리자가 전체 기본 설정을 확인하기 위한 번들 기본 config의 정확한 복사본일 뿐 migration 입력으로 사용하지 않습니다. 이전 marker에 `_auto_migration`이 없었다면 고정된 설정으로 보고 실제 버전 업그레이드 전에 기존 `config.yml`을 백업합니다. migration 결과는 `config-version: "5.0.0_auto_migration"`이며 이 marker가 남아 있는 동안 startup/reload마다 같은 재구성을 반복합니다. 정확한 `config-version: "5.0.0"`은 같은 버전 설정을 고정하여 `config.yml`을 수정하지 않습니다. 기본값 변경은 `config-migration-5.0.0.yml` 검토 항목으로 남기고, 이전 버전의 생성된 reference/migration/upgrade 파일은 자동 삭제합니다. 번들 UTF-8 기본 차단 목록 `filter-lists/ko-KR.txt`, `en-US.txt`, `ja-JP.txt`, `zh-CN.txt`는 기본 목록 초기화 마커가 없을 때 한 번 초기화되므로 이 기능이 추가되기 전부터 사용하던 데이터 폴더에서도 생성됩니다. 이미 존재하거나 비활성화된 목록 파일은 절대 덮어쓰지 않으며, 초기화가 끝난 뒤 관리자가 삭제한 기본 목록은 재시작해도 다시 만들지 않습니다.

## 5.0.0 KOKOTO WebChat 구조 및 이름 전환

5.0.0부터 정식 프로젝트 식별자를 KOKOTO WebChat으로 전환합니다. Maven 모듈은 `kwc-core`, `kwc-standalone-frontend`, `kwc-adapter-bluemap`, `kwc-adapter-squaremap`, `kwc-adapter-dynmap`, `kwc-adapter-pl3xmap`, `kwc-adapter-liveatlas`, `kwc-adapter-unmined`, `kwc-adapter-overviewer`, `kwc-platform-bukkit`이며 Java package는 `dev.kokoto.webchat`입니다. 모든 플랫폼의 정식 게임 명령은 `/kchat`(짧은 alias `/kc`), 권한은 `kwc.*`, 데이터 폴더는 `<KWC data dir>`, 리버스 프록시 예시는 `/chat`을 사용합니다.

기존 BlueMapWebChat 4.x 설치는 마이그레이션 입력으로만 사용합니다. `plugins/KOKOTO-WebChat`에 기존 데이터 파일이 하나도 없을 때만 `plugins/BlueMapWebChat`의 운영 데이터를 가져오고 `web-addon.*` → `adapters.bluemap.*`, `standalone-web.*` → `frontend.standalone.*`로 변환합니다. KWC 데이터가 이미 있으면 `.legacy-import-complete`를 수동 삭제해도 BMWC 데이터를 다시 병합하지 않습니다. 원본 `plugins/BlueMapWebChat` 폴더를 제거하면 임시 `.legacy-import-complete` 마커도 자동 삭제됩니다. `/bmchat`, `/bluemapchat`, `/bmc`, `/kwc` 명령 alias는 더 이상 제공하지 않습니다. 기존 `bluemapwebchat.*` 권한은 권한 호환 계층에서 처리될 수 있지만 새 설정과 문서는 `kwc.*`를 사용합니다.

> **BMWC HTTPS 마이그레이션:** BMWC의 표준 `/bmwc/api` 및 `/bmwc/chat` 공개 구조는 KWC의 새 기본 `/chat` 구조로 바뀝니다. 표준 BMWC API URL 설정은 빈 자동값으로 변환되지만 Caddy/nginx 설정 파일은 자동 변경되지 않으므로 `/chat` prefix 제거 방식으로 직접 수정해야 합니다.


릴레이 protocol v1은 기존 BMWC 서버와 연결하기 위해 wire header `X-BMWC-Relay-*`를 그대로 유지합니다. 이것은 제품 식별자가 아니라 레거시 프로토콜 namespace이며, `server-id`, peer URL, shared secret이 맞으면 KWC와 BMWC 4.x 사이에서도 릴레이할 수 있습니다.

## 4.7.0 이모지 다중 업로드 및 호환 범위 확대

4.7.0은 설정 가능한 `:token:` 메시지 치환도 추가합니다. 기본 alias는 영어만 제공하고, 관리자는 어떤 언어든 추가하거나 바꿀 수 있습니다. 줄바꿈/빈 줄/들여쓰기 action과 일반 문자 custom 치환을 지원하며, 알 수 없는 토큰은 기존 이모지 호환을 위해 그대로 둡니다.

4.7.0부터 관리자 이모지 업로드도 일반 채팅 파일 업로드와 같은 파일 선택 흐름을 사용합니다. 화면의 업로드 버튼이 숨겨진 다중 파일 입력창을 열고, 파일을 선택하면 선택 목록을 즉시 복사한 뒤 native input을 비우고 바로 순차 업로드를 시작합니다. 별도의 선택 확인용 업로드 단계는 없습니다. 진행률과 실제 전송 중 취소는 유지되며, 파일당 제한, 전체 이모지 용량 제한, 파일명 중복 처리, 감사 로그, PNG sidecar 생성은 기존 서버 업로드 경로를 그대로 사용합니다.

Bukkit/Spigot API 기준을 1.21에서 1.18로 낮추고 Java 17은 그대로 유지합니다. 이번 릴리스의 보수적인 Minecraft 지원 범위는 **1.18 ~ 26.2**입니다. Paper 전용 `AsyncChatEvent`는 계속 reflection으로 감지하고 Bukkit 구형 채팅 이벤트를 fallback으로 사용합니다.

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

서버 간 DM을 주고받는 모든 서버는 KOKOTO WebChat 4.6.2 이상을 사용하는 것을 권장합니다.

자세한 내용은 `docs/UPGRADE_4_6_2_KO.md`를 참고하세요.

## 4.6.1 타 서버 DM 대상·전송 경로 분리

타 서버 DM 대상은 이제 `서버 ID + 플레이어 UUID` 조합으로 식별합니다. 로컬 서버에 같은 UUID의 사용자가 있어도 `서버명 · 종류`를 누르면 해당 타 서버 사용자의 대화가 열리고, 서명된 전용 DM 릴레이를 통해 대상 서버로 전달됩니다.

서버 간 DM을 사용하는 모든 연결 서버는 KOKOTO WebChat 4.6.1 이상을 사용해야 합니다.

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
adapters:
  bluemap:
    auto-install: true
    auto-patch-webapp-conf: true

frontend:
  standalone:
    enabled: true
    path: "/"
```

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

standalone URL:

```text
http://<server-host>:8899/
```

## HTTPS / Caddy 권장 구성

공개 운영에서는 HTTP API를 직접 외부에 열지 말고, BlueMap과 KOKOTO WebChat을 내부 HTTP로 두고 HTTPS 리버스 프록시 뒤에 두는 구성을 권장합니다.

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
  # 권장값은 빈 값입니다. 업로드 URL은 자동으로 /chat/api를 따라갑니다.
  # 기존 명시 방식도 동작합니다: "/chat/api" 또는 "/chat/api/uploads"
  public-base-url: ""
  # 0 = 무제한. 양수는 upload.directory 전체 파일 용량을 제한합니다.
  max-total-size-mb: 0

emoji:
  # 권장값은 빈 값입니다. 이모지 URL은 자동으로 /chat/api를 따라갑니다.
  # 기존 명시 방식도 동작합니다: "/chat/api" 또는 "/chat/api/emojis"
  public-base-url: ""
  max-total-size-mb: 64
  show-storage-usage: true
  show-storage-limit: true
```

예시 경로:

```text
https://map.example.com/          # BlueMap
https://map.example.com/chat/api  # KOKOTO WebChat API
https://map.example.com/chat # standalone 채팅
```


URL 설정 참고: `http.path-prefix`는 KWC 내부 API 경로이고 `http.public-prefix`는 외부 리버스 프록시 prefix입니다. 기본값에서는 Caddy/Nginx가 `/chat`을 제거하므로 외부 `/chat`은 내부 `/`, 외부 `/chat/api`는 내부 `/api`로 전달됩니다. adapter와 standalone의 `api-base-url`은 별도 공개 API URL이 필요한 경우가 아니면 비워둡니다.

자세한 내용은 `docs/CADDY_HTTPS_KO.md`를 참고하세요.

## 자주 쓰는 설정

- `ui.language`: 기본 UI 언어. `en-US`, `ko-KR`, `ja-JP`, `zh-CN`
- `ui.theme`: `system`, `dark`, `light`, `high-contrast`
- `player-display.mode`: `name`, `display-name`, `custom-name`
- `player-display.strip-colors`: `false`면 실제 채팅 작성자 이름에 Minecraft legacy 색상 코드를 렌더링합니다. 시스템/event 메시지는 항상 색상 코드를 제거합니다.
- `guest.block-player-name-spoofing`: 실제 계정명과 알려진 display/custom 이름을 보호하며, `player-display.strip-colors: false`여도 사칭 판정에서는 Minecraft 색상/포맷 코드를 제거한 이름까지 항상 비교합니다.
- `commands.enabled`: 웹 명령어 패널 사용 여부
- `commands.allow-all`: 프리셋 외 임의 콘솔 명령어 허용 여부
- `commands.run-from-chat-input`: 채팅 입력창의 `/command` 실행 허용 여부
- `ui.picture-in-picture.enabled`: PIP 버튼과 PIP 실행을 함께 제어합니다.

## 채팅 기록 보관기간

새로 생성된 config는 최상단 `enabled: false` 상태이므로, 보관 기간과 정리 관련 값을 검토하고 `enabled: true`로 바꾸기 전까지 자동 정리 작업이 실행되지 않습니다. 서버 정책에 맞게 채팅 기록, 업로드, 외부 미디어 캐시 보관 기간을 확인한 뒤 활성화하세요.

## 그룹 채팅방

`group-chat.enabled`를 켜면 웹 그룹 채팅방 기능을 사용할 수 있습니다. 사용자는 방을 만들고, 공개/비공개를 선택하고, 선택적으로 방 비밀번호를 설정하고, 저장된 플레이어를 초대하고, 초대 수락/거절, 방 나가기, 내 목록에서 방 숨김/다시 표시, 방 설정 변경, 멤버 강퇴/차단/차단 해제, 방장 이전, 웹 메시지 전송을 할 수 있습니다. 공개방은 방 목록에 보이고, 비공개방은 초대받은 사용자만 들어갈 수 있습니다. 방 비밀번호는 평문이 아니라 PBKDF2 해시로 저장됩니다.

그룹 채팅은 전용 SQLite 저장소를 사용합니다(`group-chat.sqlite-file`, 기본 `group-messages.db`). `group-chat.retention-days: 0`은 보관 기한 없음이고, 양수 값은 그룹 채팅 제목 옆에 보관 기간으로 표시되며 해당 기간이 지난 그룹 메시지는 물리 삭제됩니다. `group-chat.max-messages-per-room: 0`은 방별 개수 정리 없음입니다. 이번 릴리스는 웹 중심이며, 게임 명령어 `/kchat group`, 방 음소거, 소유자/멤버 동작을 넘어선 세부 역할 관리 UI, 그룹 JSONL 저장은 아직 포함하지 않았습니다.

## 1:1 메시지함 / DM 스레드

`direct-message.enabled`를 켜면 1:1 대화 스레드형 메시지함을 사용할 수 있습니다. 대상은 UUID/이름이 저장된 연동·접속 기록 플레이어와, 서버 릴레이 메시지에서 UUID가 확인된 타 서버 플레이어입니다. 릴레이로 받은 표시 이름과 실제 Minecraft 이름도 웹 DM의 새 대화 대상 검색에 반영되므로 별도 DM 버튼 없이 이름을 검색해 대화를 시작할 수 있습니다. UUID가 없는 게스트·Discord 발신자는 대상에 포함되지 않습니다. A→B와 B→A는 같은 스레드를 사용하며, 저장은 UUID 기준으로 하고 UI는 가능하면 `표시명 (실제 계정명)` 형태로 표시합니다.

DM은 공개 채팅 기록과 분리된 전용 저장소를 사용합니다. `direct-message.storage: auto`는 공개 채팅이 `jsonl` 저장방식일 때 DM도 JSONL을 사용하고, 그 외에는 SQLite를 사용합니다. 필요하면 `direct-message.storage`를 `sqlite` 또는 `jsonl`로 직접 지정하고 `direct-message.sqlite-file` 또는 `direct-message.jsonl-file`을 사용할 수 있습니다. `direct-message.retention-days: 0`은 보관 기한 없음이며, 그 외 값은 DM 메시지함 제목 옆에 보관 기간으로 표시되고 해당 일수가 지난 DM 원문은 물리 삭제됩니다. `direct-message.max-messages-per-thread: 0`은 스레드별 개수 정리 없음입니다. `direct-message.confirm-hide`는 웹 UI에서 DM을 내 화면에서 숨길 때 확인창을 띄울지 정합니다. 개인 메시지가 서버에 저장되는 기능이므로 기본값은 비활성화이며, 서버 정책에 맞게 보관 주기를 정한 뒤 켜는 것을 권장합니다.


`direct-message.capture-game-whispers`를 켜면 게임의 `/w`, `/msg`, `/tell`류 명령을 같은 웹 DM 스레드에 복제할 수 있습니다. 같은 서버의 게임 발신자 이름을 클릭하면 `/w <실제이름> `, 웹 발신자는 `/kchat dm <실제이름> `, 다른 서버의 게임 발신자는 `/kchat dm <실제이름>@<server-id> `가 자동완성됩니다. 또한 `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, `/t`에서 대상에 `이름@server-id`를 사용하면 같은 타 서버 KWC DM 릴레이로 전송됩니다. 서버를 붙이지 않은 `/kchat dm <이름>`은 항상 현재 서버 사용자만 찾으며, 타 서버 대상은 `@server-id`를 명시하거나 웹 UI에서 해당 서버 사용자를 직접 선택해야 합니다.

## 커스텀 이모지와 게임 측 이모지 플러그인

KOKOTO WebChat은 커스텀 이모지를 `<KWC data dir>/emojis` 아래에 저장합니다. 하위 폴더는 이모지 팩으로 처리됩니다.

기본값에서는 웹→게임 채팅이 `:default/wave:`, `:emoji:default/wave:` 같은 커스텀 이모지 토큰을 그대로 보존합니다. ImageEmojis나 다른 게임 측 이모지 플러그인이 Minecraft 채팅에서 같은 토큰 텍스트를 렌더링한다면 이 기본값을 사용하세요.

`emoji.game-link.enabled`를 켠 경우 `emoji.game-link.mode`는 `preserve`, `link`, `label`을 지원합니다.

- `preserve`: 원래 토큰 텍스트를 변경하지 않습니다.
- `link`: 설정된 토큰 텍스트와 BM Web Chat 짧은 이미지 링크를 같이 보냅니다.
- `label`: 설정된 토큰 텍스트만 보냅니다.

`emoji.game-link.*`는 웹→Minecraft 채팅에만 적용됩니다. Discord 이미지 미리보기 링크는 별도 설정으로 분리됩니다. `discordsrv.append-web-emoji-links`는 웹→Discord 메시지용이고, `discordsrv.append-game-emoji-links`는 DiscordSRV가 실제 Discord에 올린 게임 메시지의 `:emoji:` 토큰을 웹→Discord와 동일한 등록 이모지 처리 경로로 넘겨 URL을 붙입니다. 여러 서버가 같은 Discord 채널을 공유할 때 LOWEST 단계에서 잡은 게임 채팅 정보는 원본 서버 판별에만 쓰며, Minecraft용 glyph나 게임 렌더링 결과는 Discord 변환 원본으로 쓰지 않습니다. 수신 릴레이 서버는 해당 메시지를 Discord로 다시 보내지 않습니다. DiscordSRV가 일반 Minecraft 채팅을 릴레이한다면 `discordsrv.game-relay-mode`를 `discordsrv`로 두세요. KWC가 직접 전송하게 하려면 `kwc`를 사용하고 DiscordSRV의 일반 Minecraft 채팅 릴레이는 꺼서 중복 게시를 막습니다.

BM Web Chat은 웹 기록과 서버 릴레이 payload에는 원본 이모지 토큰을 그대로 보존합니다. ImageEmojis 또는 ImageEmojis-Bero가 활성화되어 있으면 공개된 runtime 이모지 저장소를 reflection으로 읽어, 클릭 가능한 Minecraft 컴포넌트를 만들 때 수신 서버의 현재 token→glyph 매핑을 사용합니다. 플러그인 hard dependency나 리소스팩 분석은 필요하지 않으며, 매핑하지 못한 토큰은 기존 게임 측 렌더링 경로로 fallback합니다.

GIF/JPG/JPEG/WEBP 이모지를 업로드하면, PNG만 읽는 게임 측 이모지 플러그인과의 호환을 위해 같은 폴더에 PNG sidecar도 생성합니다.

```text
<KWC data dir>/emojis/default/wave.gif
<KWC data dir>/emojis/default/wave.png
```

웹 UI는 원본 파일을 사용하므로 GIF 애니메이션은 유지됩니다. 게임 측 이모지 플러그인이 같은 이모지 디렉터리를 감시한다면 PNG sidecar를 사용할 수 있습니다. 이모지 추가/변경 후에는 해당 플러그인의 reload 명령을 실행하세요.

ImageEmojis-Bero 1.9.x 공용 폴더, 권한, 명령어 변환, 서버 릴레이 및 문제 해결은 [`docs/IMAGEEMOJIS_BERO_1_9_0_KO.md`](docs/IMAGEEMOJIS_BERO_1_9_0_KO.md)를 참고하세요.

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
/kchat dm <플레이어> <메시지>
/kchat reply <메시지ID> <메시지>
/kchat auth <code>
/kchat password <newPassword>
/kchat reload
/kchat admin create <id>
/kchat admin password <id> <password>
/kchat admin role <id> <user|moderator|admin>
/kchat guest mute <guest|ip> <value> [minutes] [reason]
/kchat guest unmute <guest|ip> <value>
/kchat guest list
/kchat sessions
/kchat revoke <username>
```

## 권한

```text
kwc.auth
kwc.webchat
kwc.dm
kwc.reply
kwc.group
kwc.admin
kwc.update.notify
```

## 문서

- `docs/USER_MANUAL_KO.md` - 전체 기능 사용자·운영자 통합 매뉴얼
- `docs/CONFIGURATION_KO.md` - 설정 참고
- `docs/SERVER_RELAY_KO.md` - 서버 간 공개 채팅 릴레이
- `docs/UPGRADE_5_0_0_KO.md` - 4.7.0→5.0.0 메이저 업그레이드와 migration
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

관리적으로 영향을 주는 행동은 기본적으로 `<KWC data dir>/audit` 아래 날짜별 텍스트 로그에 append 됩니다. audit 로그는 서버 운영자 확인용이며 웹 UI에는 표시하지 않습니다.


참고: `frontend.standalone.app-name`/`frontend.standalone.app-short-name`으로 모바일 홈 화면 웹앱 이름을 바꿀 수 있고, `web-push.notification-title`로 기본 푸시 제목을 바꿀 수 있습니다. `web-push.notification-title`을 비워두면 `frontend.standalone.app-name`을 사용합니다. Android/데스크톱 브라우저는 HTTPS와 Push API 지원이 맞으면 BlueMap addon 또는 standalone 페이지 어디서든 푸시를 켤 수 있습니다. iOS/iPadOS 푸시는 일반 탭이 아니라 홈 화면에 추가한 웹앱에서만 시도하세요.


기존 config에 `KOKOTO WebChat` 또는 `KOKOTO WebChat` 같은 예전 기본 이름이 남아 있으면 새 기본 이름처럼 처리해서 푸시 제목에 그대로 노출되지 않게 했습니다.


- Pl3xMap integration: `docs/PL3XMAP_INTEGRATION.md`

- uNmINeD integration: `docs/UNMINED_INTEGRATION.md`

## Overviewer

- Overviewer 연동: `docs/OVERVIEWER_INTEGRATION.md`

## Forge Stage 1

Forge는 Minecraft 1.18.2~26.2를 하나의 범용 JAR이 아니라 버전별 exact-target 서버 JAR로 제공합니다. 소스는 `src/common`과 `compat118`, `compatClassic`, `compatModern`, `compat26`으로 분리되어 있으며 자세한 대상은 `kwc-platform-forge/README.md`를 참고하세요. BlueMapAPI 직접 연동은 Forge 26.1.2/26.2에만 포함하고, 이전 Forge 대상은 loader-neutral 파일시스템/정적 지도 어댑터를 사용합니다. 전체 빌드는 `kwc-platform-forge/build-all.bat`(Windows) 또는 `build-all.sh`를 사용하며, 스크립트가 exact target에 맞춰 JDK 17/21/25를 자동 선택합니다. 시스템 Java 25 하나로 구형 ForgeGradle을 직접 실행하면 안 됩니다.

## 생성형 AI 사용 안내

이 프로젝트의 개발 과정에서 코드 리뷰, 구현 및 패치 작성 보조, 문서 작성, 다국어 번역에 생성형 AI를 보조 도구로 사용했습니다. 프로젝트 요구사항, 아키텍처 및 설계 결정, 소스 통합, 테스트, 호환성 검증, 릴리스 검증과 최종 승인은 사람이 직접 주도하고 검토합니다. AI 보조 결과물은 검토와 검증 후에만 프로젝트에 반영합니다. 자세한 내용은 `AI_USAGE.md`를 참고하세요.
