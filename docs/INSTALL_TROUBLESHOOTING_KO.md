# BlueMapWebChat 설치 및 문제 해결

## 요구 사항

- Bukkit/Spigot/Paper 호환 서버 또는 호환 포크
- 빌드/실행용 Java 17 이상
- BlueMap 플러그인과 동작 중인 BlueMap webroot
- 브라우저가 채팅 API 포트에 접근 가능해야 합니다. 기본값: `8899/tcp`
- Discord 브리지는 선택 사항이며 DiscordSRV 사용 시에만 필요합니다.

## 빌드

```bash
mvn clean package
```

출력:

```text
target/BlueMapWebChat-4.7.0.jar
```

## 설치 또는 업그레이드

1. 마인크래프트 서버를 중지합니다.
2. `plugins/`의 기존 BlueMapWebChat jar를 새 jar로 교체합니다.
3. 서버를 시작합니다.
4. `plugins/BlueMapWebChat/config.yml`을 확인합니다.
5. 주요 경로를 바꿨다면 `/bmchat reload` 또는 재시작을 수행합니다.
6. BlueMap이 웹앱 변경을 자동 반영하지 않으면 `/bluemap reload`를 실행합니다.
7. 브라우저를 강력 새로고침합니다.

## 웹 애드온 등록 확인

```bash
grep -R "bluemap-web-chat" -n /opt/minecraft/server/plugins/BlueMap/webapp.conf
```

현재 버전 쿼리가 포함되어야 합니다.

```text
addons/bluemap-web-chat/config.js?v=4.7.0-<cache-token>
addons/bluemap-web-chat/chat.js?v=4.7.0-<cache-token>
addons/bluemap-web-chat/chat.css?v=4.7.0-<cache-token>
```

실제 웹 파일 갱신도 확인합니다.

```bash
find /opt/minecraft/server -path "*addons/bluemap-web-chat/chat.js" -printf "%p  %TY-%Tm-%Td %TH:%TM\n"
```

## BlueMap webroot 불일치

`/api/config`는 동작하지만 채팅 패널이 보이지 않으면 BlueMap이 다른 webroot를 서비스하고 있을 수 있습니다. `web-addon.bluemap-web-root`, `web-addon.bluemap-webapp-conf`, `web-addon.addon-path`를 실제 경로와 맞추세요.

## 브라우저 캐시

웹 UI 변경을 테스트할 때는 DevTools를 열고 **Network -> Disable cache**를 체크한 뒤 강력 새로고침하세요. 콘솔에서 로드된 버전도 확인할 수 있습니다.

```js
[...document.scripts]
  .filter(s => s.src.includes("bluemap-web-chat"))
  .map(s => s.src)
```

## BlueMap이 이전 addon 버전을 계속 불러오는 경우

업데이트 후 BlueMap이 이전 BlueMapWebChat addon 버전을 계속 불러오면 `/bmchat reload`를 한 번 더 실행하거나 서버를 재시작한 뒤, 브라우저를 강력 새로고침하세요.

## HTTPS 리버스 프록시

공개 서버에서는 Caddy 또는 nginx를 통한 HTTPS 사용을 권장합니다. Caddy는 `docs/CADDY_HTTPS_KO.md`와 `examples/caddy/Caddyfile`, nginx는 `docs/NGINX_HTTPS_KO.md`와 `examples/nginx/bluemapwebchat.conf`를 참고하세요.
