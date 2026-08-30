# KOKOTO WebChat 5.1.0 업그레이드


![ui.language 설정 재구성 흐름](assets/config-language-migration.gif)

> **중요:** `ui.language`는 config/reference/migration의 표시 언어만 바꾸며, 실제로 파싱된 운영자 설정값은 그대로 유지됩니다.

KOKOTO WebChat 5.1.0은 **5.0.0** 계열에서 업그레이드합니다. 릴리스 변경사항은 **5.0.0** 릴리스에서 5.1.0까지의 최종 변경사항을 기준으로 정리합니다.

## 업그레이드 전

1. `config.yml`, 채팅/비공개 채팅 DB 또는 JSONL, 업로드, 이모지 파일, 감사 로그를 포함한 KWC 데이터 디렉터리 전체를 백업합니다.
2. 여러 서버가 Relay로 연결돼 있다면 관련 서버를 함께 업그레이드하고 재설정할 계획을 세웁니다. Relay Protocol v1은 5.1.0 Relay v2와 상호 운용되지 않습니다.
3. Caddy/Nginx 뒤에서 사용 중이면 업그레이드 후 client IP 해석을 확인할 수 있도록 기존 공개 URL과 proxy 구성을 유지합니다.

## 설정 migration과 언어

5.1.0은 기존의 **파싱된 설정값을 보존**하면서 현재 template을 기준으로 표시 형식을 재구성합니다. 실제 `ui.language`가 재구성된 `config.yml`, 생성되는 `config-reference-5.1.0.yml`, migration/Difference 안내문의 주석·레이아웃 언어를 선택합니다. 내장 표시 언어는 `en-US`, `ko-KR`, `ja-JP`, `zh-CN`입니다.

Difference 판정은 semantic 방식입니다. 파싱된 YAML setting path와 value를 비교하며 주석, 빈 줄, 들여쓰기, 따옴표 스타일, 줄 번호, 키 순서는 비교 대상이 아닙니다. 따라서 표시 언어만 바꿔서는 설정 Difference가 생기면 안 됩니다.

첫 기동 후 `config.yml`과 `config-reference-5.1.0.yml`을 모두 검토하세요. 5.1.0에서 이미 만든 Relay group을 포함한 운영자 설정값은 same-version 자동 migration 표시 재구성에서도 보존됩니다.

## Relay Protocol v2는 수동 trust migration이 필요함

5.1.0 이전 설정의 첫 migration에서는 기존 flat Relay v1 trust를 새 구조로 추측 변환하지 않습니다. 구형 global shared-secret, flat peer, forwarding 설정을 폐기하고 `server-relay.enabled`를 `false`로 안전하게 재설정합니다.

명시적인 `server-relay.groups`를 만듭니다. 새 group은 한 서버에서 `shared-secret: ""`로 두고 시작/리로드하여 안전한 random 값을 생성·저장한 뒤, 그 값을 같은 group의 다른 서버에 그대로 복사하세요. 기존 비어 있지 않은 secret은 보존되며 수동값이 32자 미만이면 invalid 상태로 남습니다. 같은 group 안에서 양쪽 서버가 서로를 reciprocal peer로 등록해야 합니다. peer 항목은 `id`, `url`, `enabled`만 가지며 peer별 secret은 없습니다. 의도한 모든 peer의 상호 설정을 확인한 뒤에만 `server-relay.enabled`를 다시 켜세요.

순차 업그레이드를 시작하기 전에 아직 5.0.0인 서버에서는 `server-relay.enabled: false`로 바꾸고 reload하십시오. 5.0.0 Relay를 켠 채 상대 서버만 5.1.0으로 올리면 구형 v1 요청을 계속 재시도하면서 정상적인 HTTP 426 응답이 반복 WARN으로 쌓일 수 있습니다. 모든 서버를 5.1.0으로 올리고 v2 group/peer 설정을 맞춘 뒤 Relay를 다시 활성화합니다.

Relay v1/BMWC endpoint는 HTTP 426을 반환합니다. direct 1-hop HTTP peer는 경고와 함께 암호화/인증된 Relay v2 payload를 전달할 수 있지만 forwarding은 같은 group의 **HTTPS→HTTPS** 경로만 허용됩니다. Relay v2는 E2EE가 아니라 hop-by-hop authenticated encryption입니다.

## 비공개 Reply 저장소 업그레이드

5.1.0은 DM/그룹 메시지에 지속되는 reply metadata를 추가합니다. 기존 비공개 메시지는 그대로 유효합니다. SQLite 그룹 저장소는 schema upgrade에서 필요한 optional reply column을 추가하고, 기존 DM/JSONL record는 reply metadata가 없으면 그대로 생략하는 하위 호환 형식을 사용합니다. 수동 DB 변환은 필요하지 않습니다.

5.1.0은 방별 그룹 멤버십 이벤트 상태도 추가합니다. 기존 `group-messages.db`에는 `group_rooms.membership_events_enabled`(기본 ON)와 `group_messages.event_type`(기본 일반 메시지) 컬럼을 자동 추가하므로 수동 DB 변환은 필요 없습니다. 그룹채팅 UI를 닫는 동작은 퇴장 이벤트를 만들지 않습니다.

서버간 DM Reply는 상대 서버의 local DB row ID가 아니라 stable relay message ID를 사용합니다. 서버 사이에서 비공개 메시지 ID를 복사하거나 강제로 맞추지 마세요.

## Emoji와 SSE 변경

SSE 기본 제한은 **resolved client IP당 10개**, **전체 500개**로 변경되었습니다. 각 값의 `0`은 해당 제한 비활성화입니다. reverse proxy 뒤에서는 `http.trusted-proxies`를 확인하세요. 잘못 설정하면 여러 사용자가 proxy IP 하나로 보이면서 같은 per-IP 제한을 공유할 수 있습니다.

Emoji catalog reload도 복구성이 강화되었습니다. `/emojis` 일시 실패 시 마지막 정상 catalog를 유지하고 bounded exponential backoff로 재시도하며, SSE 재연결 뒤 강제 동기화하고 서버의 `emoji-catalog` invalidation event를 받아 갱신합니다. `emoji.message-token-limit: 0`은 계속 무제한입니다.

5.1.0은 custom emoji pack/item 이름도 canonicalize합니다. 지원하지 않는 문자와 공백을 제거하고 같은 pack 안의 충돌은 숫자 suffix로 구분합니다. 실제 디스크 파일명이나 token 문자열을 고정값으로 가정하는 외부 연동이 있다면 확인하세요.

- Android 관리자 이모지 업로드는 이미지 전용 Photo Picker 대신 일반 시스템 파일/DocumentsUI 선택기를 사용합니다. 데스크톱의 이미지 필터와 서버측 이미지 검증은 그대로 유지됩니다.

## 함께 확인할 다른 변경사항

- DM/그룹 Reply는 원문 관계를 저장하고 현재 DM thread/그룹 membership을 서버에서 다시 검증합니다.
- 게임 `/kchat group` 명령은 공백이 포함된 방 이름, 따옴표 이름, 반복 공백을 올바르게 해석합니다.
- 방별 멤버십 알림은 실제 입장/초대 수락과 퇴장/강퇴/차단에만 발생하며, 방 창을 닫거나 숨기는 것은 퇴장으로 처리하지 않습니다.
- 채팅 설정 프로필은 명시적으로 저장한 값과 미설정 상태를 정확히 보존하고, 불러온 글자 크기는 이미 열린 DM/그룹 창에도 적용됩니다.
- 좁은 화면/모바일에서 DM/그룹의 시간·Reply·읽음 상태가 큰 고정 영역을 차지하지 않고 자연스럽게 줄바꿈됩니다.
- `/kchat reload`는 외부 노출 HTTP listener와 direct HTTP Relay peer를 다시 검사하고, 해당 다국어 경고를 서버 로그뿐 아니라 명령 실행자에게도 출력합니다.
- `commands.broadcast-result-to-web-chat: true`이면 Web 명령 실행 안내가 온라인 Minecraft 플레이어에게도 전달됩니다.
- 공개/DM/그룹 메시지 입력창만 일반 text `<input>` 대신 1줄 `<textarea>`를 사용하고 `autocomplete=off`, Enter 전송, 커서 위치/이모지 삽입 동작은 그대로 유지합니다. Android Chrome이 일반 입력창에도 비밀번호·주소·결제수단 Autofill accessory를 띄우는 경로를 피하기 위한 조치이며 로그인/비밀번호 입력창은 변경하지 않습니다.
- 프로젝트 주소 전환 중에는 updater가 canonical Modrinth `kokoto-webchat`을 먼저 확인하고 `bluemapwebchat`으로 fallback합니다. BMWC는 전환 완료 전까지 실제 업데이트 소스로 유지하며 두 소스가 모두 실패한 경우에만 경고합니다.
- 외부에 노출된 plain HTTP listener와 direct HTTP relay peer는 다국어 보안 경고를 출력합니다.

## 업그레이드 후 확인

1. `config-version`과 생성된 5.1.0 reference/migration 파일을 확인합니다.
2. `ui.language`를 바꿨을 때 표시 형식만 바뀌고 운영 설정값이 유지되는지 확인합니다.
3. Relay를 사용한다면 v2 reciprocal peer를 먼저 확인하고 다시 활성화하며, 다른 서버가 더 이상 v1 endpoint를 기대하지 않는지 확인합니다.
4. proxy 뒤에서는 resolved client IP와 SSE 동작을 확인하고, 문제 분석 중에만 `http.log-client-ip-resolution`을 임시 활성화합니다.
5. 실제 사용하는 배포 방식에서 공개 채팅, DM/그룹 Reply, custom emoji, 업로드, Web Push/알림, map/standalone frontend를 테스트합니다.
6. 정상 운영과 retention cleanup을 충분히 확인할 때까지 업그레이드 전 백업을 보관합니다.
