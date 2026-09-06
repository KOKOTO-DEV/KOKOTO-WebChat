# KOKOTO WebChat 5.2.0 릴리스 체크리스트

## 소스/설정/다국어
- [ ] Root/Bukkit/Fabric/NeoForge/Forge 메타데이터와 산출물 버전이 모두 `5.2.0`이다.
- [ ] `config.yml`, `config-baselines/config-5.2.0.yml`, `distribution/config-reference-5.2.0.yml`이 byte-identical이다.
- [ ] 5.1.0 → 5.2.0 migration이 `5.2.0_auto_migration`을 기록하고 기존 Relay v2 group/secret/peer를 포함한 지원되는 운영자 값은 보존하며 retired 설정은 제거하고, 정확한 `5.2.0`은 같은 버전 자동 재구성을 멈춘다. pre-5.1.0 migration에서만 기존 Relay v1 trust/topology를 재사용하지 않고 명시적 Relay v2 재설정을 위해 초기화한다.
- [ ] en-US/ko-KR/ja-JP/zh-CN의 key set과 placeholder가 완전히 같다.
- [ ] `inner.js`와 8개 frontend wrapper가 syntax 및 embedded JS/CSS 일치 검사를 통과한다.

## 기능 smoke test
- [ ] 게임↔웹 공개채팅, 답글, URL, 커스텀 이모지, 고정, 검색, message token, content filter가 정상이다.
- [ ] 기존 잘못된 이모지 팩/파일명이 canonical 이름으로 migration되고 신규 팩/업로드도 같은 규칙을 사용하며 같은 팩 내 충돌은 숫자 suffix로 해소된다.
- [ ] 이모지 picker가 자동 공백 없이 정확한 토큰만 삽입하고 `message-tokens.newline.aliases`의 이모지 전용 줄은 촘촘하게 표시되며 blank-line alias는 실제 빈 줄을 유지한다.
- [ ] 웹 답글이 원문 전체를 보존하고 URL/커스텀 이모지를 정상 표시한다.
- [ ] 게임 내 DM/그룹 이름 클릭은 기존 명령을 입력창에 올리고 본문 클릭은 답글을 준비하며 URL은 URL 열기를 우선한다.
- [ ] `dm-...`/`group-...` 내부 reply ID를 변조해도 실제 DM 참여자/현재 그룹 멤버가 아니면 전송되지 않는다.

- [ ] 공개 메시지 반응이 영속화되고 Relay 2.1 전파가 정상이며 반응 전용 SSE 갱신으로 재생 중 미디어가 재시작되지 않는다. 32 × 16px 빈 상태 `+` 버튼은 본문 아래/다음 메시지 앞에 각각 1px의 시각적 여유를 두어 글을 덮지 않고, 반응 OFF에서는 기존 8px 간격을 그대로 사용하며, 실제 반응이 있을 때만 정상 in-flow 행을 사용한다. 카테고리/검색 재렌더 후에도 picker 위치와 바깥 클릭 닫기가 유지되고 reactor 표시이름 목록은 약 4줄 이후 스크롤되며 **관리자 > 이모지 > 반응 아이콘**은 다른 설정과 동일한 둥근 테마 행과 `이모지 = 검색어` 별칭 편집을 제공하고 별칭은 `reaction-search-aliases.txt`에 저장된다.
- [ ] 공개/DM/그룹 `입력 중...`이 5초 event-driven window로 동작하고 본인/audit viewer를 제외하며 긴 다중 사용자 이름은 인원수로 축약되고 polling/영속 typing 상태를 만들지 않는다. Web Admin Settings/config.yml에서 서버 전체 기본값 오픈채팅 OFF / DM ON / 그룹 ON을 각각 제어한다. `chat.typing-indicator.user-display-control`은 기본 OFF이며, 관리자가 켠 경우 계정별 `입력 중 표시 보기` OFF가 본인 화면의 수신 표시만 숨기고 본인의 typing 송신은 유지해야 한다.
- [ ] 대화 저장은 서버에서 범위를 재검증하고 archive quota와 비공개 방 lock 정책을 적용하며 일반 retention 이후에도 snapshot을 유지하되 관리자 원본 삭제는 cascade한다.
- [ ] 대화 저장 PDF/인쇄 화면은 표시이름+실제이름을 함께 표시하고 현재 KWC appearance를 사용하며 원본이 남아 있는 이미지만 포함하고 영상/음성/기타 파일은 링크로 남기며 사라진 원본은 유실로 표시한다.
- [ ] `chat.conversation-archive.enabled: false`에서 archive API를 등록하지 않고 `conversation-archives.db`를 열거나 만들지 않으며 Web UI에 대화 저장 관련 DOM을 생성하지 않는다.
- [ ] `max-archives-per-user`, `max-messages-per-archive`, `max-messages-per-user`를 기본값이 아닌 값으로 설정했을 때 서버가 실제 한도를 적용하고 `/archive/list`가 적용값을 반환한다.
- [ ] 열린 DM에서 뒤로가기/제목 hover 배경이 안쪽으로 들어온 설정 버튼 영역까지 제목창 전체를 끊김 없이 덮고, 설정 버튼 자체 hover는 별도로 유지된다.
- [ ] 이모지 반응 알림 체크박스 하나가 실시간 브라우저 알림과 백그라운드/모바일 Web Push 반응 알림을 함께 제어하고, 지원되지 않는 전달 경로는 동작하지 않는다.
- [ ] 공개/DM/그룹 bottom-follow는 32px를 사용하고 compose panel의 layout 변화는 viewport를 보존하며 그 변화만으로 즉시 최하단 강제 스크롤하지 않는다.

## Relay/보안
- [ ] 선택적 signed `/relay/v2/handshake` identity/health probe는 양쪽이 같은 group ID와 group shared-secret으로 서로를 등록했을 때 성공하며, probe는 route 상태를 만들지 않고 direct relay는 각 요청을 독립 인증한다.
- [ ] 한쪽만 peer를 등록한 경우 양방향 모두 relay할 수 없다.
- [ ] `server-relay.forwarding.enabled` 기본값이 `false`다.
- [ ] forwarding을 켜도 inbound/outbound forwarding hop이 모두 HTTPS일 때만 허용되며 HTTP forwarding은 차단된다.
- [ ] 직접 HTTP peer는 1-hop 호환을 유지하되 명시적인 다국어 WARNING/경고/警告를 출력한다.
- [ ] KWC 내장 HTTP listener가 loopback이 아닌 주소에 노출되면 HTTP 노출 경고를 출력한다.
- [ ] 공개 relay와 서버 간 1:1 DM/읽음 확인은 정상이고 그룹 채팅은 로컬 기능으로 유지된다.

## 배포/빌드
- [ ] Modrinth updater가 canonical `kokoto-webchat` 프로젝트만 조회하고 기존 BMWC 프로젝트 주소를 조회하지 않는지 확인한다.
- [ ] CurseForge 링크가 `bukkit-plugins/kokoto-webchat`이다.
- [ ] Windows 경로 preflight가 전체 validator와 Fabric/NeoForge/Forge의 build-all/build-target에 모두 적용된다.
- [ ] Bukkit JDK17, Fabric 16 / NeoForge 12 / Forge 16 exact-target이 대상별 JDK 17/21/25로 빌드된다.
- [ ] release validator가 모든 NeoForge JAR을 열어 target이 선택한 `META-INF/mods.toml` 또는 `META-INF/neoforge.mods.toml` 경로와 `modLoader`, `loaderVersion`, KWC ID/version, 정확한 Minecraft dependency, 미치환 template placeholder 부재를 검증한다.
- [ ] release validator가 security + Relay/reaction/typing 회귀 harness를 실행하고 완성된 shaded Bukkit JAR을 대상으로 대화저장 SQLite runtime harness를 실행한다.

> `validate-release-windows.bat`와 이 파일이 필요로 하는 PowerShell helper는 source archive에 포함되어 있습니다. 별도의 `KWC-5.2.0-validation-tools.zip`에는 개발용 브라우저 회귀검증 도구만 들어 있으며 릴리스 빌드 실행에는 필요하지 않습니다.

- [ ] `validate-release-windows.bat`가 `FINAL RELEASE BUILD PASS`, 45개 deployable JAR, SHA256SUMS를 생성한다.
- [ ] 최종 acceptance는 `--fast` 없이 실행한다. sequential 또는 `--parallel` clean 실행은 허용하지만 cached/부분 빌드는 `FINAL RELEASE BUILD PASS`로 취급하지 않는다.
