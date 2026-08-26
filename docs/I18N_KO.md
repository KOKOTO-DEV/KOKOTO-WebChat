# KOKOTO WebChat 다국어 가이드

## 내장 언어

- `en-US.yml` - 영어, 기본값
- `ko-KR.yml` - 한국어
- `ja-JP.yml` - 일본어
- `zh-CN.yml` - 중국어 간체

언어 파일은 첫 실행 시 `plugins/KOKOTO-WebChat/lang/`로 복사됩니다. 이후 업데이트에서는 누락된 내장 키가 자동 병합됩니다. 이미 수정된 값은 보존되므로, 예전 문구가 남아 있으면 복사된 lang 파일을 직접 수정하거나 삭제 후 재생성하세요.

## UI 언어 설정

```yaml
ui:
  language: "ko-KR"
  language-fallback: "en-US"
```

`language`는 서버 기본값이고, 사용자는 채팅 설정에서 브라우저별 언어를 따로 선택할 수 있습니다. 브라우저별 선택값은 localStorage에 저장됩니다.

지원값:

```text
en-US, ko-KR, ja-JP, zh-CN
```

## fallback 동작

선택 언어에 키가 없으면 `ui.language-fallback`을 사용합니다. fallback에도 없으면 내장 영어 기본값을 사용합니다.

## 번역 범위

언어 파일은 웹 UI 대부분을 포함합니다.

- 창 제목/상태
- 버튼/placeholder
- 로그인/연동/비밀번호 모달
- 계정/사용자 설정 모달
- 고정/삭제 표시 토글을 포함한 관리자/모더레이터 패널
- 업로드 진행/취소/오류
- 미디어 미리보기 및 social embed 라벨
- PIP 미지원/열기 실패 메시지
- 명령어 패널과 명령어 응답
- 고정 메시지 UI

게임으로 보내는 채팅 형식은 언어 파일이 아니라 `config.yml`에서 설정합니다.

```yaml
chat:
  web-user-to-game-format: "[Web] {player}: {message}"
  web-guest-to-game-format: "[Web Guest] {guest}: {message}"
```



## 시스템 메시지

내장 서버 announcement와 웹 명령어 결과 메시지는 i18n 키와 함께 전송됩니다. 웹 UI는 해당 키가 언어 파일에 있으면 보는 사람의 선택 언어로 표시합니다. `config.yml`의 `announcements.*.message` 문구는 사용자 지정/fallback 문구로 유지되므로, 번역 키가 없거나 서버별 문구가 필요한 경우 그대로 사용됩니다.

접혀 있는 고정 메시지도 일반 메시지와 같은 채팅 폰트 및 메시지 글자 크기 설정을 따릅니다.

## 새 언어 추가

1. `plugins/KOKOTO-WebChat/lang/en-US.yml`을 복사합니다.
2. 예: `fr-FR.yml`로 이름을 바꿉니다.
3. `web:` 아래 문자열을 번역합니다.
4. `ui.language`를 `.yml`을 제외한 새 파일명으로 설정합니다.
5. `/kchat reload` 후 페이지를 새로고침합니다.
