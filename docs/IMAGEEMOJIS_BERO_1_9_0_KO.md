# ImageEmojis-Bero 1.9.0 호환

BlueMapWebChat 4.7.0은 [ImageEmojis-Bero 1.9.0](https://github.com/KOKOTO-DEV/ImageEmojis-Bero)과 연동할 수 있는 선택형 호환 경로를 포함합니다. reflection 기반이라 hard dependency를 추가하지 않으며, ImageEmojis-Bero가 없어도 BlueMapWebChat 자체는 정상 시작합니다.

## 지원 동작

- 웹, 게임 댓글, DM, 서버 릴레이 메시지는 BMChat 기록과 릴레이 payload에 정규 이모지 토큰을 보존합니다.
- BMChat이 클릭 가능한 Minecraft 컴포넌트를 만들기 전에 수신 서버의 ImageEmojis-Bero runtime 이모지 저장소를 읽고, 인식한 토큰을 그 서버의 현재 리소스팩 glyph로 변환합니다.
- 발신자 이름 클릭, `/bmchat reply`, URL 클릭과 ImageEmojis glyph를 한 채팅 줄에서 같이 사용할 수 있습니다.
- BMChat의 `:pack/name:`과 기존 `:emoji:pack/name:` 형식을 모두 인식합니다. ImageEmojis-Bero의 일반 설정인 `templateFormat: ":<emoji>:"`은 이모지 이름에 팩 경로가 포함되므로 `:pack/name:`을 생성합니다.
- runtime 저장소에서 해결하지 못한 인식 토큰은 ImageEmojis-Bero의 `BroadcastMessageEvent` 리스너가 처리할 수 있도록 plain Bukkit broadcast fallback을 사용합니다. 이 fallback 줄에는 BMChat 클릭·hover 정보를 붙일 수 없습니다.

## 공용 이모지 폴더 권장 설정

웹 UI와 Minecraft 리소스팩이 같은 파일을 사용하게 하려면 ImageEmojis-Bero가 BlueMapWebChat 이모지 폴더를 읽도록 설정합니다.

```yaml
# plugins/ImageEmojis-Bero/config.yml
emojisFolder: "/BlueMapWebChat/emojis"
templateFormat: ":<emoji>:"
replaceInCommands: true
```

ImageEmojis-Bero 1.9.0의 경로 처리 기준으로 실제 위치는 서버의 `plugins` 폴더 아래가 됩니다.

```text
plugins/BlueMapWebChat/emojis/<팩>/<이름>.png
```

ImageEmojis-Bero는 한 단계의 팩 폴더와 PNG 파일을 읽습니다. BlueMapWebChat은 웹용 GIF/JPG/JPEG/WEBP 원본을 유지하면서 게임 플러그인용 PNG sidecar를 같은 폴더에 생성할 수 있습니다.

## BlueMapWebChat 권장 설정

게임으로 보낼 때 토큰을 그대로 유지하는 구성을 권장합니다.

```yaml
emoji:
  game-link:
    enabled: false
    default-pack: ""
    aliases: {}

reply:
  game-click:
    enabled: true
    local-game-chat: true
```

`emoji.game-link.enabled: false`는 Minecraft 채팅에 BMChat 이미지 링크를 덧붙이지 않고 정규 토큰을 유지합니다. `:wave:` 같은 flat 토큰을 `default/wave` 같은 BMChat 팩 ID에 연결해야 할 때만 `default-pack` 또는 `aliases`를 사용하세요.

## 권한과 명령어 변환

플레이어가 ImageEmojis-Bero 이모지를 사용하려면 `imageemojis.use` 권한이 필요합니다. `/bmchat reply`, `/bmchat dm`, `/w`, `/msg` 같은 명령어에 이모지 토큰을 입력하게 하려면 `replaceInCommands: true`를 유지해야 합니다. BMChat은 웹·기록·릴레이에는 원본 토큰을 남기고, 작성 서버의 즉시 게임 출력에는 ImageEmojis-Bero가 변환한 명령 본문을 사용합니다.

## 이모지 변경 후 적용 순서

이모지 파일을 추가·교체·이름 변경한 뒤에는 다음 순서로 적용합니다.

1. `/emojis reload`로 ImageEmojis-Bero 리소스팩을 다시 생성합니다.
2. 접속 중인 플레이어는 `/emojis update`를 실행하거나 재접속합니다.
3. BMChat의 runtime token→glyph 캐시는 약 5초 이내에 다시 읽습니다. 이모지 파일만 바꾼 경우 보통 `/bmchat reload`는 필요하지 않습니다.

## 서버간 릴레이

BMChat은 다른 서버의 private-use glyph가 아니라 정규 토큰 텍스트를 전달합니다. 이모지를 표시해야 하는 모든 수신 서버에 ImageEmojis-Bero 1.9.0이 설치되어 있고 같은 팩/이름의 이모지가 있어야 합니다. 서버마다 리소스팩의 glyph 배치가 달라도 수신 서버가 자기 runtime 매핑으로 다시 변환하므로 안전합니다.

서버별 이모지 구성이 다르면 수신 서버에서 찾지 못한 원격 토큰은 텍스트로 남거나 plain broadcast fallback을 사용합니다.

## DiscordSRV

ImageEmojis-Bero는 같은 이름의 Discord 이모지로 템플릿을 변환할 수 있고, BMChat은 `discordsrv.append-web-emoji-links`와 `discordsrv.append-game-emoji-links`로 공개 이미지 미리보기 링크를 붙일 수 있습니다. 같은 미리보기를 두 방식으로 중복 생성하지 않도록 원하는 표시 방식만 활성화하세요.

여러 Minecraft 서버가 같은 Discord 채널을 공유할 때 BMChat은 원본 서버만 DiscordSRV의 기본 게임 메시지를 보강합니다. 릴레이 수신 서버는 Discord로 메시지를 다시 보내지 않으며 서버명이나 이모지 링크를 다시 붙이지 않습니다.

## 문제 해결

- **게임에서 토큰 글자가 그대로 보임:** 같은 `<팩>/<이름>.png`가 있는지 확인하고 `/emojis reload`를 실행한 뒤 `imageemojis.use` 권한과 짧은 BMChat 캐시 갱신 시간을 확인합니다.
- **웹에서는 보이지만 게임에서 안 보임:** 플레이어가 ImageEmojis-Bero 리소스팩을 수락하고 최신 버전으로 갱신했는지 확인합니다.
- **게임에서는 보이지만 웹에서 토큰으로 보임:** 같은 팩/이름의 파일이 `plugins/BlueMapWebChat/emojis` 아래에도 있는지 확인합니다.
- **`/bmchat reply`나 `/bmchat dm`의 이모지가 변환되지 않음:** `replaceInCommands: true`를 유지합니다.
- **다른 서버에서 온 이모지가 안 보임:** 모든 수신 서버에 같은 이모지 PNG와 팩/이름을 동기화해야 합니다. 채팅 릴레이는 리소스팩 파일을 복사하지 않습니다.
- **링크 클릭 대신 댓글 명령만 나옴:** URL 조각에는 `OPEN_URL`을 우선 적용하고 URL이 아닌 본문에만 댓글 동작을 넣는 현재 4.7.0 소스를 사용합니다.

## 호환 경계

4.7.0 연동은 ImageEmojis-Bero 1.9.0에서 제공하는 `getEmojiRepository().getEmojis()` runtime 저장소와 각 이모지 모델의 `getName()`, `getTemplate()`, `getAsUtf8Symbol()` 접근자를 기준으로 합니다. 이후 ImageEmojis-Bero 버전에서 이 runtime API가 바뀌더라도 BMChat이 hard failure로 중지되지는 않지만, 호환 코드가 갱신되기 전까지 토큰 또는 plain broadcast fallback으로 동작할 수 있습니다.
