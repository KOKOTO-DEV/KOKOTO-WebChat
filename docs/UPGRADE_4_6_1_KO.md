# 4.6.0에서 4.6.1로 업그레이드

## 주요 변경점

- 서버 릴레이 메시지에서 UUID가 확인된 타 서버 플레이어를 기존 웹 DM 대상 검색에서 표시합니다. 게임 메시지와 계정 연동 웹 메시지의 표시 이름, 실제 이름, UUID를 검색할 수 있습니다.
- 기존 비공개 채팅 메타데이터 목록에서 선택적으로 DM 본문을 읽기 전용으로 감사 열람할 수 있습니다.
- Modrinth 기반 간단한 업데이트 확인과 관리자 접속 알림을 추가했습니다.
- 플러그인과 설정 버전이 `4.6.1`로 변경되었습니다.

## 설정 마이그레이션

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

## DM 본문 감사 기능 활성화

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

## 서버 간 DM 버전 요구사항

서버 간 DM을 주고받는 모든 서버는 KOKOTO WebChat 4.6.1 이상을 사용해야 합니다. `서버명 · 종류` 클릭 시 해당 글의 UUID와 원본 서버 ID를 직접 전달하며, 타 서버 검색 결과와 기존 원격 대화에서도 대상 서버 ID와 플레이어 UUID를 함께 유지합니다.
