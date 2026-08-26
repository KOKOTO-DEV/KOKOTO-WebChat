# KOKOTO WebChat 5.0.0 릴리스 체크리스트

## 소스/설정/다국어
- [ ] Root/Bukkit/Fabric/NeoForge/Forge 메타데이터와 산출물 버전이 모두 `5.0.0`이다.
- [ ] `config.yml`, `config-baselines/config-5.0.0.yml`, `distribution/config-reference-5.0.0.yml`이 byte-identical이다.
- [ ] 4.7.0 → 5.0.0 migration이 `5.0.0_auto_migration`을 기록하고 기존 값을 보존하며 중첩 누락 설정/주석을 중복 없이 삽입한다. 정확한 `5.0.0`은 같은 버전 backfill을 중지한다.
- [ ] en-US/ko-KR/ja-JP/zh-CN 언어 key set이 동일하고 빈 번역이 없다.
- [ ] `inner.js`와 모든 wrapper가 JS syntax 및 embedded frontend 일치 검사를 통과한다.

## 기능 smoke test
- [ ] 로그인/게임연동/logout/guest 및 세션 만료가 정상이다.
- [ ] 게임↔웹 공개채팅, 댓글, 고정, 검색, message token, 컨텐츠 필터가 정상이다.
- [ ] DM/그룹 생성·전송·읽음·재시도·history가 정상이고 타 서버 DM은 server+UUID를 정확히 사용한다.
- [ ] 일반 파일 선택 업로드와 클립보드 붙여넣기가 `random`/`original` 파일명 모드에서 모두 정상이다. Windows 긴 파일명은 유지되고 8.3 별칭으로 깨진 링크를 만들지 않는다.
- [ ] 계정별 UI 프로필과 계정 공통 키워드/알림 설정이 정상이며 guest 설정은 브라우저 로컬이다.
- [ ] Web Push가 정상이고 같은 기기의 live-page OS 알림과 중복되지 않는다.
- [ ] Web Admin Filter/Settings와 관리자 Discord 키워드 알림이 정상이며 DiscordSRV 논리 채널명만 기본 선택지로 보인다.
- [ ] 활성화한 지도 adapter가 자기 asset/marker만 설치·제거·reload한다.
- [ ] server relay 서명, unknown-peer/backoff, dedupe, forwarding 설정이 정상이다.
- [ ] ImageEmojis-Bero를 사용하는 경우 공용 `plugins/KOKOTO-WebChat/emojis` 경로, `serverIp:webServerPort` 클라이언트 접근, 리소스팩 reload/update, 게임↔웹 토큰 표시를 확인한다.
- [ ] SimpleNicks-Bero를 사용하는 경우 `player-display.mode: "display-name"`에서 닉네임이 표시되며 KWC 계정/UUID identity는 실제 사용자로 유지되는지 확인한다.

## 보안 판정
- [ ] frontend API는 Bearer 인증, SSE는 짧은 1회용 stream ticket을 사용한다.
- [ ] 관리자 IP 제한이 login/link 완료/이후 ADMIN 요청에 모두 적용된다.
- [ ] request body 상한과 bounded HTTP worker가 적용된다.
- [ ] Web Push는 사설/위험 endpoint를 거부하고 redirect를 따라가지 않는다.
- [ ] Discord 사용자 입력이 임의 mention을 만들 수 없고 CDN redirect는 승인된 HTTPS host만 허용한다.
- [ ] profile import가 unknown/nested/duplicate/wrong-type/injection 형태를 거부한다.
- [ ] 공개 운영 HTTPS 권고가 문서에 명시되어 있다.

## 플랫폼/빌드 판정
- [ ] `update-check.enabled`가 Bukkit/Fabric/NeoForge/Forge 모두 실제 연결되어 있고 KWC 우선/BMWC fallback 조회와 `kwc.update.notify` 접속 알림이 정상이다.
- [ ] Bukkit JAR이 JDK 17로 빌드된다.
- [ ] Fabric 16개 exact-target이 대상별 JDK 17/21/25로 모두 빌드된다.
- [ ] NeoForge 12개 exact-target이 대상별 JDK 17/21/25로 모두 빌드된다.
- [ ] Forge 16 exact-target이 대상별 JDK 17/21/25로 모두 빌드된다.
- [ ] `validate-release-windows.bat`가 `FINAL RELEASE BUILD PASS`를 출력하고 정확히 45개 배포 JAR 및 `release-5.0.0/SHA256SUMS.txt`를 만든다.

## 문서/배포
- [ ] README/Upgrade/Configuration/User Manual/Wiki가 최종 5.0.0 동작과 일치하며 “나중에 리브랜딩” 같은 개발 중간 문구가 없다.
- [ ] Modrinth/CurseForge 설명·Summary·5.0.0 릴리스 노트가 최종 기능/플랫폼 범위와 일치한다.
- [ ] README/배포 설명/`AI_USAGE.md`에 AI 보조 사용 안내가 있고 기능 CHANGELOG에는 넣지 않는다.
- [ ] 4.7.0 업데이트 체커가 발견할 수 있도록 5.0.0을 기존 BMWC 배포 페이지에 먼저 게시한다.
- [ ] BMWC 페이지 상단에 “BlueMapWebChat is now KOKOTO WebChat” 전환 안내를 넣고 새 주소가 실제 활성화된 뒤 정식 새 주소를 명시한다.
- [ ] 5.0.0 업데이트 알림의 CurseForge 링크는 새 KWC CurseForge 페이지가 실제 활성화되기 전까지 기존 BMWC bridge 페이지를 사용한다.
- [ ] 같은 프로젝트 rename/연결이 불가능하면 BMWC는 종료/이전 안내 페이지로 유지하고 이후 신규 버전은 KWC 페이지에 게시한다.
- [ ] Modrinth는 한 프로젝트에서 각 파일의 loader를 정확히 지정한다. CurseForge는 기존 BMWC가 Bukkit Plugins class이므로 Bukkit bridge를 먼저 올리고 Fabric/NeoForge/Forge 파일은 project class 호환을 확인한 뒤 게시한다.
- [ ] GitHub 저장소는 `BlueMapWebChat` → `KOKOTO-WebChat` rename을 우선 사용하고 rename 뒤 local remote를 새 주소로 갱신하며 예전 저장소 이름을 재사용하지 않는다.
