# KOKOTO WebChat 5.1.0 릴리스 체크리스트

## 소스/설정/다국어
- [ ] Root/Bukkit/Fabric/NeoForge/Forge 메타데이터와 산출물 버전이 모두 `5.1.0`이다.
- [ ] `config.yml`, `config-baselines/config-5.1.0.yml`, `distribution/config-reference-5.1.0.yml`이 byte-identical이다.
- [ ] 5.0.0 → 5.1.0 migration이 `5.1.0_auto_migration`을 기록하고 지원되는 운영자 값은 보존하되 retired 설정은 제거하며, Relay v1 trust/topology는 추정하지 않고 명시적 재설정을 위해 비활성 Relay v2로 재구성하고, 정확한 `5.1.0`은 같은 버전 자동 재구성을 멈춘다.
- [ ] en-US/ko-KR/ja-JP/zh-CN의 key set과 placeholder가 완전히 같다.
- [ ] `inner.js`와 8개 frontend wrapper가 syntax 및 embedded JS/CSS 일치 검사를 통과한다.

## 기능 smoke test
- [ ] 게임↔웹 공개채팅, 답글, URL, 커스텀 이모지, 고정, 검색, message token, content filter가 정상이다.
- [ ] 기존 잘못된 이모지 팩/파일명이 canonical 이름으로 migration되고 신규 팩/업로드도 같은 규칙을 사용하며 같은 팩 내 충돌은 숫자 suffix로 해소된다.
- [ ] 이모지 picker가 자동 공백 없이 정확한 토큰만 삽입하고 `message-tokens.newline.aliases`의 이모지 전용 줄은 촘촘하게 표시되며 blank-line alias는 실제 빈 줄을 유지한다.
- [ ] 웹 답글이 원문 전체를 보존하고 URL/커스텀 이모지를 정상 표시한다.
- [ ] 게임 내 DM/그룹 이름 클릭은 기존 명령을 입력창에 올리고 본문 클릭은 답글을 준비하며 URL은 URL 열기를 우선한다.
- [ ] `dm-...`/`group-...` 내부 reply ID를 변조해도 실제 DM 참여자/현재 그룹 멤버가 아니면 전송되지 않는다.

## Relay/보안
- [ ] 선택적 signed `/relay/v2/handshake` identity/health probe는 양쪽이 같은 group ID와 group shared-secret으로 서로를 등록했을 때 성공하며, probe는 route 상태를 만들지 않고 direct relay는 각 요청을 독립 인증한다.
- [ ] 한쪽만 peer를 등록한 경우 양방향 모두 relay할 수 없다.
- [ ] `server-relay.forwarding.enabled` 기본값이 `false`다.
- [ ] forwarding을 켜도 inbound/outbound forwarding hop이 모두 HTTPS일 때만 허용되며 HTTP forwarding은 차단된다.
- [ ] 직접 HTTP peer는 1-hop 호환을 유지하되 명시적인 다국어 WARNING/경고/警告를 출력한다.
- [ ] KWC 내장 HTTP listener가 loopback이 아닌 주소에 노출되면 HTTP 노출 경고를 출력한다.
- [ ] 공개 relay와 서버 간 1:1 DM/읽음 확인은 정상이고 그룹 채팅은 로컬 기능으로 유지된다.

## 배포/빌드
- [ ] Modrinth updater가 주소 전환 중 `kokoto-webchat`을 우선 조회하고 `bluemapwebchat`으로 fallback하며 두 소스가 모두 실패한 경우에만 경고하는지 확인한다.
- [ ] CurseForge 링크가 `bukkit-plugins/kokoto-webchat`이다.
- [ ] Windows 경로 preflight가 전체 validator와 Fabric/NeoForge/Forge의 build-all/build-target에 모두 적용된다.
- [ ] Bukkit JDK17, Fabric 16 / NeoForge 12 / Forge 16 exact-target이 대상별 JDK 17/21/25로 빌드된다.
- [ ] `validate-release-windows.bat`가 `FINAL RELEASE BUILD PASS`, 45개 deployable JAR, SHA256SUMS를 생성한다.
- [ ] 최종 acceptance는 `--fast` 없이 실행한다. sequential 또는 `--parallel` clean 실행은 허용하지만 cached/부분 빌드는 `FINAL RELEASE BUILD PASS`로 취급하지 않는다.
