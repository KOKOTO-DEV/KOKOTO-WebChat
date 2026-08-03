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
