# SimpleNicks-Bero 연동

KOKOTO WebChat 5.0.0은 **Bukkit/Paper 계열**에서 [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero)가 Bukkit player display name에 적용한 닉네임을 사용할 수 있습니다. KWC는 SimpleNicks를 hard dependency로 요구하거나 해당 플러그인의 DB를 직접 읽지 않습니다.

기본 설치, `/nick` 명령어, 권한, SQLite/MySQL, 저장 닉네임, 보호 기능, PlaceholderAPI/MiniPlaceholders 등 일반 운영은 [원본 SimpleNicks 문서](https://github.com/Simplexity-Development/SimpleNicks)를 참고하세요. 이 문서는 KWC 연동에 필요한 부분만 설명합니다.

## KWC 설정

```yaml
player-display:
  mode: "display-name"
```

`name`은 실제 Minecraft 사용자명을 사용하고, `display-name`은 SimpleNicks-Bero가 닉네임을 적용하는 Bukkit display name을 사용합니다. KWC는 연결 계정/UUID를 별도로 유지하므로 화면에 표시되는 닉네임이 바뀌어도 인증된 실제 사용자 identity는 바뀌지 않습니다.

## SimpleNicks-Bero 대표 설정 예

다음은 다국어 서버에서 사용할 수 있는 예시이며 **KWC 필수 설정은 아닙니다**.

```yaml
mysql:
  enabled: false
  ip: localhost:3306
  name: simplenicks
  username: username1
  password: badpassword!

max-nickname-length: 30
nickname-regex: '[A-Za-z0-9_가-힣 ぁ-ゔァ-ヴー々〆〤一-龥?!]+'

require-permission:
  nick: false
  color: false
  format: false
  who: false

tablist-nick: true
nickname-prefix: ''
```

위 `nickname-regex`는 영문/숫자/밑줄, 한글, 일본어, CJK, 공백과 지정 기호를 허용합니다. 실제 허용 범위는 서버 정책과 다른 플러그인 호환성을 고려해 조정하세요. MySQL, 닉네임 보호, 저장 개수, tablist 설정은 SimpleNicks 자체 기능이며 KWC 동작을 제어하지 않습니다.

## 색상/포맷

SimpleNicks-Bero는 MiniMessage 닉네임을 Bukkit display name에 적용합니다. KWC 웹에서 이름의 포맷을 어떻게 표시할지는 KWC 설정이 결정합니다.

```yaml
player-display:
  mode: "display-name"
  strip-colors: true
```

`strip-colors`는 원하는 표시 정책에 맞게 선택하세요. 인증, guest 사칭 방지, relay identity와 실제 계정 매칭은 포맷된 닉네임 자체를 신뢰하지 않고 실제 플레이어 identity를 기준으로 합니다.

## 범위

이 문서의 연동은 Bukkit player display name을 사용하는 Bukkit/Paper 계열 KWC 대상입니다. Fabric/NeoForge/Forge KWC 빌드가 Bukkit SimpleNicks 플러그인 API를 지원한다는 의미는 아닙니다.

## 프로젝트 링크

- KWC에서 사용하는 포크: [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero)
- 원본 플러그인 / 일반 설치·운영: [SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks)
