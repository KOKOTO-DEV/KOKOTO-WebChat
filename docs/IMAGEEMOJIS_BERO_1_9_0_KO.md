# ImageEmojis-Bero 연동 (1.9.x)

KOKOTO WebChat 5.1.0은 [ImageEmojis-Bero](https://github.com/KOKOTO-DEV/ImageEmojis-Bero)와 선택적으로 연동할 수 있습니다. 서버측 runtime glyph 연동은 **Bukkit/Paper 계열**에서 동작하며, 현재 1.9.x Bero 계열(1.9.2 포함)을 기준으로 확인되어 있습니다. reflection 기반이라 ImageEmojis-Bero가 없어도 KWC 자체는 정상 시작합니다.

기본 설치, 명령어, 권한, 리소스팩 생성 및 일반 운영은 [원본 ImageEmojis 문서](https://github.com/MrQuackDuck/ImageEmojis)를 참고하세요. 이 문서는 KWC와 같이 사용할 때 필요한 차이와 운영 설정만 설명합니다.

## KWC와 함께 쓸 때의 대표 설정

```yaml
# plugins/ImageEmojis-Bero/config.yml
# Minecraft 클라이언트에서 접근 가능한 공인 도메인 또는 공인 IP
serverIp: yourdomain

# ImageEmojis-Bero 리소스팩 HTTP 서버 포트
webServerPort: 5000

# KWC와 같은 이모지 파일 트리를 사용
emojisFolder: /KOKOTO-WebChat/emojis

# 서버 정책에 따라 NONE / OPTIONAL / REQUIRED 선택
enforcementPolicy: REQUIRED

# /msg, /tell, /kchat reply, /kchat dm 등 명령어 내부 토큰 변환
replaceInCommands: true

# KWC와 동일한 정규 토큰 형식 권장
templateFormat: ':<emoji>:'
```

`replaceInAnvils`, `replaceOnSigns`, `replaceInCommandBlocks`, `replaceInBooks`, `suggestionMode`, `mergeWithServerResourcePack`, `extendedUnicodeRange` 등은 ImageEmojis-Bero 자체 운영 선택값이지 KWC 필수값은 아닙니다. 특히 `extendedUnicodeRange` 변경은 기존 이모지 코드 배치에 영향을 줄 수 있으므로 ImageEmojis-Bero 자체 안내를 따르세요.

## 공용 이모지 폴더

```yaml
emojisFolder: /KOKOTO-WebChat/emojis
```

은 서버의 `plugins` 폴더 기준으로 다음 위치를 사용합니다.

```text
plugins/KOKOTO-WebChat/emojis/<팩>/<이름>.png
```

따라서 KWC 웹 이모지와 ImageEmojis-Bero가 생성하는 Minecraft 리소스팩을 같은 팩/이름 구조로 관리할 수 있습니다. ImageEmojis-Bero 게임 리소스팩은 PNG를 사용하고, KWC는 웹 표시를 위해 GIF/JPG/JPEG/WEBP 원본을 함께 보관하거나 필요한 경우 같은 위치의 PNG sidecar를 사용할 수 있습니다.

## 리소스팩 포트와 방화벽

`serverIp`와 `webServerPort`는 **KWC 웹서버가 아니라 ImageEmojis-Bero의 리소스팩 HTTP 서버 설정**입니다. Minecraft 클라이언트가 해당 주소로 직접 리소스팩을 받아야 하므로 설정한 호스트와 포트가 실제 외부에서 접근 가능해야 합니다.

예를 들어:

```text
serverIp: yourdomain
webServerPort: 5000
```

이면 클라이언트에서 `yourdomain:5000` TCP 접근이 가능해야 합니다. 환경에 따라 다음이 필요합니다.

- 서버 OS/방화벽에서 TCP 5000 허용
- 공유기/NAT 환경이면 공인 5000 → Minecraft 서버 PC의 5000 포트포워딩
- `yourdomain` DNS를 실제 접근 가능한 공인 주소로 연결

KWC의 `/chat` 웹 포트를 공개했다고 해서 ImageEmojis-Bero의 5000 포트가 자동으로 공개되는 것은 아닙니다. 두 HTTP 서비스는 별개입니다.

## KWC 연동 동작

- KWC는 웹/히스토리/릴레이 데이터에 다른 서버의 private-use glyph 대신 정규 이모지 토큰을 보존합니다.
- Bukkit/Paper 계열에서 게임으로 상호작용 채팅을 만들기 전에 ImageEmojis-Bero의 현재 runtime repository를 읽어 인식 토큰을 수신 서버의 현재 리소스팩 glyph로 변환할 수 있습니다.
- `:pack/name:`과 기존 `:emoji:pack/name:` 형식을 인식합니다. `templateFormat: ':<emoji>:'`과 팩 경로가 포함된 이름을 쓰면 일반적으로 `:pack/name:` 형태가 됩니다.
- 발신자/댓글 클릭, URL 클릭, ImageEmojis glyph를 같은 메시지에서 같이 사용할 수 있습니다.
- runtime symbol을 얻지 못해도 hard dependency로 서버가 중지되지 않고 토큰/plain broadcast fallback을 사용할 수 있습니다.

서버측 runtime 연동은 Bukkit/Paper 계열 범위입니다. KWC의 Fabric/NeoForge/Forge 서버 빌드가 Bukkit ImageEmojis 플러그인 API까지 지원한다고 의미하지 않습니다. 선택형 ImageEmojis client picker는 별개의 클라이언트 기능입니다.

## 권한과 명령어

일반적으로 `imageemojis.use` 권한이 필요합니다. `/msg`, `/tell`, `/kchat reply`, `/kchat dm` 등 명령어 안에서 이모지를 사용할 때는 `replaceInCommands: true`를 유지하세요.

## 이모지 갱신 순서

1. `/emojis reload`로 ImageEmojis-Bero 리소스팩을 다시 생성합니다.
2. 접속 중인 사용자는 `/emojis update`를 실행하거나 재접속합니다.
3. KWC의 짧은 runtime 이모지 캐시가 갱신될 때까지 잠시 기다립니다. 이모지 파일만 바꾼 경우 보통 `/kchat reload`는 필요하지 않습니다.

## 멀티서버 릴레이

릴레이는 정규 토큰만 전달하며 PNG나 리소스팩을 다른 서버로 복사하지 않습니다. 이모지를 표시할 모든 수신 서버에 같은 팩/이름의 파일과 호환되는 ImageEmojis-Bero 구성이 있어야 합니다.

## 문제 해결

- **웹에서는 이모지인데 게임에서는 토큰:** 같은 PNG, `imageemojis.use`, 리소스팩 수락 여부, `/emojis reload`, `/emojis update`/재접속을 확인합니다.
- **게임에서는 이모지인데 웹에서는 토큰:** `plugins/KOKOTO-WebChat/emojis` 아래 같은 팩/이름이 있는지 확인합니다.
- **리소스팩 다운로드 실패:** 클라이언트 기준 `serverIp:webServerPort` 접근을 확인하고, KWC 웹 포트와 별개로 TCP 방화벽/NAT 설정을 확인합니다.
- **`/kchat reply`, `/kchat dm`, `/msg`, `/tell`에서 변환 안 됨:** `replaceInCommands: true`를 확인합니다.
- **타 서버에서 토큰으로 남음:** 릴레이는 리소스팩 파일을 동기화하지 않으므로 해당 서버에도 이모지 파일을 동기화합니다.

## 프로젝트 링크

- KWC 연동 확인 포크: [ImageEmojis-Bero](https://github.com/KOKOTO-DEV/ImageEmojis-Bero)
- 원본 플러그인 / 일반 설치·운영: [ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis)

## Upstream 참조 문서

- [ImageEmojis upstream on Modrinth](https://modrinth.com/plugin/image-emojis)
- [ImageEmojis upstream source](https://github.com/MrQuackDuck/ImageEmojis)

위 링크는 upstream 프로젝트 문서입니다. 이 문서의 KWC token 변환, 공유 디렉터리 처리, Bero 전용 연동 동작은 실제 설치된 Bero/KWC 버전을 기준으로 확인해야 합니다.
