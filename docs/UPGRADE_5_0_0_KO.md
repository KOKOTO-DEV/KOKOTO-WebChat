# KOKOTO WebChat 5.0.0 업그레이드

5.0.0은 직전 정식 버전 4.7.0 이후의 개발 내용을 하나로 정리하면서 **BlueMapWebChat(BMWC) → KOKOTO WebChat(KWC)** 이름 전환까지 완료하는 메이저 릴리스입니다.

## 배포 주소 전환

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

## 4.7.0 → 5.0.0 주요 변경사항

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

## 플랫폼 지원

- Bukkit/Paper/Spigot: `KOKOTO-WebChat-5.0.0-Bukkit-1.18-26.2.jar`, Java 17 bytecode, 선언 지원 범위 1.18–26.2.
- Fabric exact-target: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`. 대상별 JDK 17/21/25를 사용합니다.
- NeoForge exact-target: `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`. 대상별 JDK 17/21/25를 사용합니다.
- Forge exact-target: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`. 대상별 JDK 17/21/25를 사용합니다.

## BlueMapWebChat 데이터 마이그레이션

Bukkit에서 기존 `plugins/BlueMapWebChat`이 있으면 첫 KWC 시작 시 1회 마이그레이션 입력으로 사용합니다. 운영 데이터는 `plugins/KOKOTO-WebChat`으로 가져오되 원본 BMWC 디렉터리는 수정하지 않아 롤백/마이그레이션 원본으로 남깁니다.

기존 `web-addon.*` / `standalone-web.*`는 `adapters.bluemap.*` / `frontend.standalone.*`로 변환합니다. 5.0.0에서는 `/bmchat`, `/bluemapchat`, `/bmc`, `/kwc` 명령 alias를 등록하지 않습니다. 기존 `bluemapwebchat.*` 권한은 런타임 호환 fallback으로만 처리합니다. Relay Protocol v1의 `X-BMWC-Relay-*` wire header는 기존 BMWC peer와의 호환을 위해 유지합니다.

## BMWC → KWC 웹 경로 마이그레이션

기존 BMWC 표준 `/bmwc/api`, `/bmwc/chat` reverse-proxy 구조는 KWC 기본값으로 유지하지 않습니다. 새 공개 기본 prefix는 `/chat`이고 standalone `/chat`, API `/chat/api`가 됩니다. KWC는 마이그레이션 시 표준 BMWC URL 설정을 정규화할 수 있지만 외부 Caddy/nginx 파일은 수정할 수 없으므로 직접 변경해야 합니다. `CADDY_HTTPS_KO.md`, `NGINX_HTTPS_KO.md`를 참고하세요.

## 설정 변경

4.7.0과 5.0.0 bundled reference를 구조적으로 비교하면 **79개 설정 경로 추가, 14개 제거, 기존 2개 값 변경**입니다. 주요 전환은 `web-addon.* → adapters.bluemap.*`, `standalone-web.* → frontend.standalone.*`, `discordsrv.game-to-discord* → discordsrv.game-relay-*`, `ui.show-login-only-when-hidden` 제거입니다. 추가 그룹에는 다른 지도 어댑터, `content-filter.*`, `ui.user-profiles.*`, `admin-alerts.discord.*`, `http.public-prefix`, relay forwarding 제어, `upload.filename-mode`가 포함됩니다.

실제 버전 migration 시 고정된 구버전 config를 먼저 백업하고 최신 번들 `config.yml`을 새 뼈대로 만든 뒤 기존 사용자 설정값만 덮어씁니다. 구버전 주석/레이아웃은 가져오지 않고 실제 config를 다음 상태로 만듭니다.

```yaml
config-version: "5.0.0_auto_migration"
```

`_auto_migration`을 유지하면 startup/reload마다 최신 같은 버전 번들 config를 다시 뼈대로 만들고 현재 값을 덮어씁니다. `config-reference-5.0.0.yml`은 관리자 확인용 기본설정 복사본일 뿐 migration 입력이 아닙니다. 같은 버전 config를 고정할 때 정확한 `config-version: "5.0.0"`으로 바꿉니다.

## 보안 수정과 사용자 체감

정상 사용자의 UI/사용법은 바뀌지 않습니다. 로그인, 채팅, DM/그룹, 업로드, 프로필, Push, 관리자 화면은 기존 방식 그대로 사용합니다. 차단되는 것은 허용되지 않은 관리자 IP, 비정상 Push endpoint, 과도하게 큰/잘못된 요청, 의도하지 않은 Discord 멘션, 경로 규칙을 어기는 업로드처럼 원래 허용하면 안 되는 경우입니다.

공개 운영은 HTTPS를 권장합니다. 공인 IP 직접 운영 자체는 지원하지만 브라우저가 secure context를 요구하는 기능은 브라우저 보안 정책을 따릅니다.

## 최종 릴리스 판정

최종 후보는 `validate-release-windows.bat`가 `FINAL RELEASE BUILD PASS`로 끝나고 배포 JAR이 정확히 45개 수집되며, static/config/i18n/document 검증과 로그인·공개채팅·업로드/클립보드·DM/그룹·relay·사용 중인 지도/Discord 연동 smoke test가 통과해야 배포 확정합니다.
