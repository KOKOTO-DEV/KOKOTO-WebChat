# BlueMapWebChat 릴리스 체크리스트

릴리스 전 확인 항목:

- `pom.xml` 버전을 갱신합니다.
- `src/main/resources/plugin.yml` 버전을 갱신합니다.
- `src/main/resources/config.yml`의 버전 주석을 갱신합니다.
- README와 문서의 빌드 출력 예시 버전을 갱신합니다.
- CHANGELOG 항목을 추가합니다.
- JavaScript 문법 검사를 실행합니다.

```bash
node --check inner.js
node --check src/main/resources/web/chat.js
```

- YAML 파일을 검증합니다.

```bash
python3 - <<'PY'
import yaml, glob
for path in ['src/main/resources/config.yml'] + glob.glob('src/main/resources/lang/*.yml'):
    with open(path, encoding='utf-8') as f:
        yaml.safe_load(f)
    print('OK', path)
PY
```

- `en-US.yml` 기준으로 언어 키 개수가 일치하는지 확인합니다.
- Maven으로 빌드합니다.

```bash
mvn clean package
```

- `webapp.conf`가 새 버전 query를 가리키는지 확인합니다.
- DevTools 캐시 비활성화 상태로 브라우저 로딩을 테스트합니다.

- [ ] `USER_MANUAL_EN.md`, `USER_MANUAL_KO.md`, `USER_MANUAL_JA.md`, `USER_MANUAL_ZH_CN.md`의 주요 목차가 일치하고 현재 명령어·권한·기본값·기능 동작을 반영한다.

## 4.6.2 전송 상태 릴리스 점검

- [ ] `pom.xml`, `plugin.yml`, 기본 `config-version`, 산출물 예제와 현재 매뉴얼이 `4.6.2`이다.
- [ ] 검토 완료된 4.6.1 설정에 다른 실제 차이가 없으면 `config-migration-4.6.2.yml`에는 `config-version: "4.6.2"`만 나온다.
- [ ] 동일 이름이 로컬/원격에 모두 있을 때 서버 미지정 DM은 현재 서버 사용자만 선택하고, 원격 사용자는 서버가 명시된 경우에만 선택한다.
- [ ] 타 서버 DM은 `pending`으로 시작하며 수신 저장 확인 후에만 `delivered`, HTTP 502·타임아웃·라우팅·수신 거부 시 `failed`가 된다.
- [ ] 실패 DM 재시도는 같은 relay ID를 사용하며 수신 메시지를 중복 저장하지 않는다.
- [ ] `pending` 상태에서 재시작하면 `failed` / 재시도 가능 상태로 복구한다.
- [ ] 웹 DM 응답 유실 시 같은 client message ID로 재시도해 중복 DM을 만들지 않는다.
- [ ] 그룹채팅 웹 재시도도 같은 client message ID로 기존 저장 메시지를 반환하고 중복 삽입하지 않는다.
- [ ] 허브/체인 사설 DM 릴레이는 최종 수신 서버 저장 확인 후에만 성공을 반환한다.
- [ ] DM과 그룹채팅의 모든 메시지에 읽음 상태를 표시한다. 1:1 DM은 상대가 읽기 전 `미확인`, 읽은 뒤 `✓`가 되며, 그룹채팅은 미확인 수신자 수를 숫자로 표시하고 0명이 되면 `✓`가 된다. DM과 그룹채팅의 모든 메시지를 읽음 상태 계산에 포함한다.

## 4.6.0 릴레이·DM·게임 댓글 점검

- [ ] `config-version`이 없거나 다른 config에서는 다른 차이가 없어도 실제 `config.yml`을 덮어쓰지 않고 `config-version`이 포함된 `config-migration-4.6.0.yml`이 생성된다.
- [ ] `config-version: "4.6.0"`이 일치하면 비교를 생략하고 같은 버전의 오래된 안내 파일을 제거한다.
- [ ] 마이그레이션 설정 조각이 4.6.0 누락 키와 Discord 형식 기본값 변경 2개만 실제 YAML 설정으로 표시하고, 사용자 지정값과 정보용 섹션은 출력하지 않는다.
- [ ] `activePeers=<유효>/<설정>` 수가 맞고 잘못된 피어는 제외 이유를 로그로 남긴다.
- [ ] HTTPS 경로, HMAC 오류, unknown peer, 시간 차이, reload를 시험했다.
- [ ] 현재 서버의 웹 배지는 숨겨지고, 다른 서버 배지 색상은 서버별로 고정되어 구별된다.
- [ ] 게임/Discord 출력에서 원본 서버가 보인다.
- [ ] 여러 서버가 같은 Discord 채널을 공유해도 DiscordSRV 게임 메시지에는 원본 서버명이 한 번만 붙고 이모지 링크도 중복되지 않는다.
- [ ] 같은 서버 게임 이름 클릭은 `/w`, 웹/다른 서버 게임 이름 클릭은 `/bmchat dm`, URL 아닌 본문은 `/bmchat reply`, URL은 링크 열기다.
- [ ] `capture-game-whispers`가 DM 활성화 상태에서 양쪽 사용자에게 복제된다.
- [ ] SQLite 릴레이 열 마이그레이션 후 기존 기록이 유지된다.
- [ ] en-US, ko-KR, ja-JP, zh-CN 언어 키가 일치한다.
- [ ] ImageEmojis-Bero 1.9.0: 공용 폴더 PNG, 일반 채팅, `/bmchat reply`, `/bmchat dm`, URL+이모지 클릭 공존, 원격 릴레이 표시를 확인한다.

## 4.6.1 타 서버 DM 검색·관리자 감사 점검

- [ ] `pom.xml`, `plugin.yml`, 기본 `config-version`, 빌드 산출물 예제와 캐시 관련 문서가 `4.6.1`이다.
- [ ] `config-version: "4.6.0"` 설정에서 다른 실제 누락이 없다면 `config-migration-4.6.1.yml`에는 `direct-message.admin-audit.enabled: false`와 `config-version: "4.6.1"`만 나온다.
- [ ] UUID가 포함된 타 서버 게임/연동 웹 발신자를 기존 DM 검색에서 표시 이름, 실제 이름, UUID로 찾을 수 있고 UUID 없는 게스트·Discord 발신자는 제외된다.
- [ ] 재시작 후 보존된 공개 기록에서 타 서버 플레이어 이름이 DM 검색 대상으로 복원된다.
- [ ] 일반 ADMIN/MODERATOR 계정은 다른 사용자의 DM 본문을 볼 수 없다.
- [ ] `private-chat-super-admins`에 등록되어도 `direct-message.admin-audit.enabled: false`이면 메타데이터만 보인다.
- [ ] 두 조건을 모두 켜면 관리자 DM 행이 읽기 전용 감사 화면으로 열리고 전송·숨김·읽음 처리는 제공하지 않으며 전역 숨김 메시지는 제외된다.
- [ ] 감사 페이지를 읽을 때마다 actor, thread ID, pagination, limit, returned count가 `admin.dm-audit-read`로 기록되고 본문은 감사 로그에 복사되지 않는다.

## 4.7.0 다중 업로드 및 호환성 확인

- [ ] 기존 설정 버전과 관계없이 `config-reference-4.7.0.yml`이 기본 `config.yml`과 byte-for-byte 동일하게 주석까지 포함해 생성된다.
- [ ] startup/reload 시 알려진 최상위 `config.yml` 블록이 4.7.0 기본 순서로 재정렬되지만 YAML 값과 블록 주석은 유지되고, 기본에 없는 최상위 블록은 마지막에 기존 순서대로 남는다.
- [ ] bundled `config.yml`의 message-token 기본 alias/예시는 영어만 사용하며, 언어별 alias는 관리자가 설정으로 추가하는 방식으로 문서화되어 있다.
- [ ] `en-US.yml`, `ko-KR.yml`, `ja-JP.yml`, `zh-CN.yml`의 key 집합이 동일하고 빈/null 번역이 없다.
- [ ] 생성된 4.7.0 migration 보고서 하단에는 현재 설정과 reference의 텍스트 diff가 주석으로 나오며, 동일한 줄은 제외하고 파일명과 `Line`/`Lines`를 실제 차이 내용과 별도 줄로 표시하며 reference 전용 블록은 삽입 위치도 표시한다.
- [ ] `pom.xml`과 `plugin.yml` 버전이 4.7.0이다.
- [ ] `plugin.yml`은 `api-version: '1.18'`, POM은 Java 17 + Spigot API 1.18.2를 사용한다.
- [ ] 관리자 이모지 Upload 버튼이 일반 업로드와 같은 숨겨진 다중 파일 picker를 열고, 파일 선택 직후 별도 확인 단계 없이 순차 업로드를 시작하며 각 선택 파일을 정확히 한 번만 전송한다.
- [ ] 중간 파일이 실패해도 뒤의 선택 파일 업로드가 계속된다.
- [ ] game-link sidecar가 켜진 경우 GIF/JPG/WEBP의 PNG sidecar 생성이 유지된다.
- [ ] 검토 완료된 4.6.3 config는 새 `message-tokens` 설정(`custom: {}` 포함)과 `config-version: "4.7.0"`이 들어간 migration을 만들고, 더 오래되거나 버전 표식이 없는 config도 현재 4.7.0 전체 키와 비교된다.

## 4.6.3 관리자 그룹채팅 감사 확인

- [ ] 이전 기본 주석을 그대로 쓰는 config는 해당 주석 블록만 4.6.3 설명으로 갱신되고 YAML 설정값이나 사용자 지정 주석은 변경되지 않는다.
- [ ] `config-version: "4.6.2"` 설정은 다른 누락이 없을 때 `group-chat.admin-audit.enabled: false`와 `config-version: "4.6.3"`이 포함된 `config-migration-4.6.3.yml`을 생성한다.
- [ ] `private-chat-super-admins`에 등록되어 있어도 `group-chat.admin-audit.enabled: false`이면 그룹은 메타데이터만 보인다.
- [ ] 두 조건을 모두 켜면 등록된 최고관리자는 방 멤버가 아니어도 그룹 본문을 읽기 전용으로 열 수 있고 읽음/미확인 수는 변하지 않는다.
- [ ] 각 감사 페이지 열람은 actor/room/pagination/count를 포함한 `admin.group-audit-read`를 남기며 본문은 감사 로그에 복사하지 않는다.

