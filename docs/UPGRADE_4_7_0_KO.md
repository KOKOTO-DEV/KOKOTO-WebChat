# BlueMapWebChat 4.7.0 업그레이드

4.7.0은 Bukkit/Spigot 호환 기준을 Minecraft 1.18까지 낮추고, 관리자 커스텀 이모지 다중 업로드와 설정 가능한 메시지 토큰 치환을 추가합니다.

## 호환성

- 보수적으로 지원하는 Minecraft 범위: **1.18 ~ 26.2**
- Java 요구 버전: **Java 17**
- `plugin.yml`: `api-version: '1.18'`
- Maven 빌드 기준 API: `spigot-api:1.18.2-R0.1-SNAPSHOT`
- Paper `AsyncChatEvent`는 reflection으로 감지하며 Bukkit `AsyncPlayerChatEvent`를 실제 링크된 fallback으로 유지합니다.
- 1.17 이하는 이번 릴리스의 공식 호환 범위로 잡지 않습니다.

## 커스텀 이모지 다중 업로드

이모지 업로드도 일반 채팅 파일 업로드와 같은 파일 선택 흐름을 사용합니다. 화면의 업로드 버튼은 숨겨진 다중 파일 입력창을 열고, 파일 선택창에서 파일을 고르면 선택된 `FileList`를 즉시 일반 배열로 복사한 뒤 native input을 비우고 바로 순차 업로드를 시작합니다. 별도의 선택 확인용 업로드 버튼은 없고, 파일 선택창 focus/visibility 우회 로직도 사용하지 않습니다. 진행률과 실제 전송 중 취소는 유지됩니다. 서버 쪽 기존 이모지 업로드 endpoint가 파일별 검증, 전체 용량 계산, 중복 파일명 처리, 감사 로그, PNG sidecar 생성을 그대로 담당합니다.

## 메시지 토큰

기본 alias는 영어만 제공하며 관리자가 어떤 언어로든 교체하거나 추가할 수 있습니다. `:enter:`, `:newline:`, `:nextline:`, `:linebreak:`, `:br:`은 다음 줄, `:blankline:`, `:emptyline:`, `:paragraphbreak:`은 빈 줄, `:tab:`, `:indent:`는 설정된 수의 공백으로 치환됩니다. `:separator:` 같은 일반 문자 치환도 custom 항목으로 추가할 수 있습니다. 알 수 없는 토큰은 그대로 두므로 기존 커스텀/이미지 이모지 토큰과 충돌하지 않습니다.

## 설정

4.7.0은 `message-tokens` 설정을 추가합니다. 그 외 기존 기본값은 변경하지 않았고 검토 표식은 다음과 같이 변경됩니다.

```yaml
config-version: "4.7.0"
```

startup/reload 시 알려진 최상위 `config.yml` 블록도 4.7.0 bundled 기본 순서로 재정렬하며, 각 블록의 현재 내용·설정값·사용자 지정 주석은 보존하고 기본에 없는 최상위 블록은 마지막에 기존 순서대로 유지합니다.

검토가 끝난 4.6.3 설정에는 `config-migration-4.7.0.yml`을 통해 새 `message-tokens` 섹션과 4.7.0 검토 표식이 추가됩니다. 비교 대상은 4.6.3으로 제한되지 않으며 더 오래된 설정이나 `config-version`이 없는 설정도 현재 4.7.0 기준으로 누락 항목을 검사합니다. 또한 `config-reference-4.7.0.yml`을 항상 생성해 현재 JAR의 완전한 4.7.0 기본 설정과 모든 주석을 그대로 제공합니다. 오래된 설정은 이 파일을 기준으로 전체 구조를 비교하면 됩니다. `message-tokens.custom: {}` 같은 빈 map도 누락된 경우 migration에 유지됩니다. migration 파일 하단에는 전체 reference와의 텍스트 diff가 주석으로 추가됩니다. 동일한 줄은 출력하지 않고, 각 차이는 파일명 다음 별도 줄에 `Line` 또는 `Lines`를 표시한 뒤 실제로 다른 내용만 보여줍니다. 실제 차이 줄은 원본 YAML 들여쓰기를 그대로 유지하도록 줄 앞에 `#`만 직접 붙이며 reference 전용 블록은 삽입 위치도 표시합니다.

### 게임 줄바꿈 동작

설정된 `newline` / `blank-line` 토큰으로 만든 줄바꿈만 Minecraft의 기존 한 줄 평탄화 처리를 보호 상태로 통과한 뒤 최종 전송 시 별도의 게임 채팅 줄로 출력됩니다. 일반 CR/LF 입력은 기존과 동일하게 평탄화됩니다. 서버간 릴레이에서 이 의도적인 줄바꿈을 게임에 표시하려면 수신 BlueMapWebChat 서버도 같은 4.7.0 토큰 줄 전송 지원이 적용되어 있어야 하며, 구버전 수신 서버는 전달된 일반 LF를 기존 평탄화 단계에서 공백으로 바꿉니다.
