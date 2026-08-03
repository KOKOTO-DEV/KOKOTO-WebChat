# 4.5.5에서 4.6.0으로 업그레이드

## 먼저 백업

서버를 중지하고 `plugins/BlueMapWebChat`을 백업하세요. 특히 `config.yml`, 각 SQLite DB와 `-wal`/`-shm`, DM/그룹 DB, 업로드, 이모지, 사용자 언어 파일, audit 로그, Web Push 키/구독 파일을 포함해야 합니다.

## 자동 설정 마이그레이션 조각

BlueMapWebChat은 기존 `config.yml`을 자동으로 덮어쓰거나 병합하지 않습니다. 서버 시작과 `/bmchat reload` 때 실제 디스크의 `config.yml`에서 `config-version`을 확인합니다.

- `config-version`이 실행 중인 플러그인 버전과 같으면 이미 검토한 설정으로 간주하고 비교를 생략합니다. 남아 있는 같은 버전의 마이그레이션 설정 조각은 삭제합니다.
- 버전이 없거나 다르면 JAR의 현재 기본 설정과 비교하여 다음 파일을 새로 생성하거나 갱신합니다.

정확한 판정은 다음과 같습니다.

| 실제 설정 상태 | 마이그레이션 파일 |
|---|---|
| 버전 표식 없음 | 차이가 없어도 생성 |
| 버전 표식이 현재 플러그인과 다름 | 생성 또는 갱신 |
| 버전 표식이 현재 플러그인과 같음 | 생성하지 않음. 남은 동일 버전 파일도 제거 |

```text
plugins/BlueMapWebChat/config-migration-4.6.0.yml
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

## 4.6.0에서 추가된 주요 설정

- 새 최상위 `server-relay:` 섹션
- `direct-message.capture-game-whispers`
- `reply.game-click.enabled`
- `reply.game-click.local-game-chat`
- `reply.game-command-format`
- Discord 형식의 `{server}`, `{server_id}` placeholder

버전이 일치하지 않고 비공개 메시지 캡처 또는 로컬 채팅 렌더링 설정이 실제 파일에 없으면, 검토 전 의도치 않은 활성화를 막기 위해 해당 동작은 런타임에서 안전하게 비활성화됩니다.

## DB 마이그레이션

공개 SQLite 기록에는 릴레이 메타데이터 열이 추가형 `ALTER TABLE` 방식으로 추가됩니다. 기존 행은 보존되지만 과거 행의 원본 서버 정보는 소급 생성할 수 없습니다. 첫 4.6.0 실행 전에 DB를 백업하세요.

## 권장 테스트

1. 기존 설정으로 시작해 `config-migration-4.6.0.yml`이 생성되고 `config.yml`은 바뀌지 않는지 확인합니다.
2. 설정 조각의 누락 설정과 변경 기본값을 검토하여 실제 config에 병합합니다.
3. `config-version: "4.6.0"`을 넣고 `/bmchat reload` 후 비교 생략 로그가 나오는지 확인합니다.
4. 릴레이, 게임 댓글, DM 복제, Discord 서버 표기, URL·ImageEmojis 처리를 시험합니다.
