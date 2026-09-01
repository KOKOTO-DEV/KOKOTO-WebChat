# BlueMap 의존성 / standalone 모드 점검

## 요약

Java 플러그인 코드는 BlueMap API에 의존하지 않습니다. `plugin.yml`에도 BlueMap `depend`/`softdepend`가 없고, Java 소스에도 BlueMap API import가 없습니다. 런타임 의존성은 Bukkit/Spigot 계열 서버 API입니다. DiscordSRV 연동은 선택 사항입니다.

따라서 채팅 기능 자체는 BlueMap 없이도 동작할 수 있습니다. BlueMap에 특화된 부분은 `kwc-adapter-bluemap`가 담당하며 BlueMap 웹 폴더에 내장 애드온 자산을 복사하고 `webapp.conf`를 패치합니다. standalone 자산은 별도 `kwc-standalone-frontend` 모듈에 들어가며 더 이상 BlueMap adapter에서 불러오지 않습니다.

## 지원 모드

KOKOTO WebChat은 현재 두 형태를 모두 지원합니다.

```text
BlueMap 애드온 패널
standalone 페이지
```

standalone 모드는 기본 비활성화입니다. 필요할 때 명시적으로 켭니다.

```yaml
  api-base-url: ""
```

`frontend.standalone.api-base-url`은 보통 비워둡니다. 직접 HTTP에서는 내부 `http.path-prefix`를 사용하고, 리버스 프록시에서는 `http.public-prefix + http.path-prefix`를 사용하므로 기본 공개 API는 `/chat/api`입니다. standalone만 별도 API URL을 사용해야 할 때만 이 값을 설정합니다.

직접 HTTP URL:

```text
http://<server-host>:8899/
```

HTTPS 리버스 프록시 URL 예시:

```text
https://<domain>/chat
```

## standalone 전용 배포

BlueMap 지도 안에 채팅 UI를 넣지 않을 때는 아래처럼 둡니다.

```yaml
adapters:
  bluemap:
    auto-install: false
    auto-patch-webapp-conf: false

frontend:
  standalone:
    enabled: true
    path: "/"
```



## 투명 창 제한

standalone 브라우저 창과 Document Picture-in-Picture 창은 일반 웹 API만으로 OS 레벨의 진짜 투명 창으로 만들 수 없습니다. CSS로 채팅 패널 자체를 반투명하게 만들 수는 있지만, 브라우저/PIP 창 배경과 데스크탑 투과는 브라우저 또는 운영체제가 제어합니다.

### URL 설정 해석 규칙

`frontend.standalone.api-base-url`은 standalone 페이지 전용 API override이며 일반적으로 비워둡니다. `adapters.bluemap.api-base-url`을 상속하지 않습니다. upload/emoji를 비워두면 공통 공개 API base에 각각 `/uploads`, `/emojis`를 붙입니다. `/chat/api` 같은 절대 브라우저 경로는 그대로 사용합니다. 선행 `/`가 없는 상대값은 `http.cors-origin`이 실제 origin일 때 그 origin을 앞에 붙입니다. `https://...` 전체 URL은 그대로 사용합니다.


