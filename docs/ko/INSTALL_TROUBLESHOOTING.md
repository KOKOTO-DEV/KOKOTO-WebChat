# KOKOTO WebChat 설치 및 문제 해결

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
kwc-platform-bukkit/target/KOKOTO-WebChat-5.3.0-Bukkit-1.18-26.2.jar
```

Windows에서는 루트 validator를 플랫폼 빌드 도우미로도 사용할 수 있습니다.

```bat

> `validate-release-windows.bat`와 이 파일이 필요로 하는 PowerShell helper는 source archive에 포함되어 있습니다. 개발용 회귀검증 하네스는 source archive의 `validation/`에 포함되며, 일반/릴리스 빌드에 별도 validation-tools archive가 필요하지 않습니다.

validate-release-windows.bat --bukkit
validate-release-windows.bat --bukkit --fast
validate-release-windows.bat --parallel
```

`--fast`는 `clean`을 생략하고 기존 빌드 산출물/캐시를 재사용하는 반복 개발용 모드입니다. `--parallel`은 clean/fast 의미를 바꾸지 않습니다. Bukkit이 선택되어 있으면 Bukkit을 먼저 빌드하고, 통과한 뒤 나머지 선택 loader를 병렬 실행하므로 `validate-release-windows.bat --parallel`은 clean 전체 릴리즈 검증으로 사용할 수 있습니다. 메인 콘솔에는 전체/플랫폼별 target 진행률이 실시간 표시되고, `--parallel`에서는 각 활성 플랫폼이 별도 실시간 빌드 창을 사용하며 상세 로그는 `validation-logs/`에 남습니다.

## 설치 또는 업그레이드

1. 마인크래프트 서버를 중지합니다.
2. `plugins/`의 기존 KOKOTO WebChat jar를 새 jar로 교체합니다.
3. 서버를 시작합니다.
4. `plugins/KOKOTO-WebChat/config.yml`을 확인합니다.
5. 주요 경로를 바꿨다면 `/kchat reload` 또는 재시작을 수행합니다.
6. `/kchat reload`가 BlueMap webapp 변경 뒤 `bluemap reload light`를 자동 요청합니다. 자동 실행에 실패하면 `/bluemap reload light`를 수동으로 실행합니다.
7. 브라우저를 강력 새로고침합니다.

## 웹 애드온 등록 확인

```bash
grep -R "bluemap-web-chat" -n /opt/minecraft/server/plugins/BlueMap/webapp.conf
```

현재 버전 쿼리가 포함되어야 합니다.

```text
addons/kokoto-web-chat/config.js?v=5.3.0-<cache-token>
addons/kokoto-web-chat/chat.js?v=5.3.0-<cache-token>
addons/kokoto-web-chat/chat.css?v=5.3.0-<cache-token>
```

실제 웹 파일 갱신도 확인합니다.

```bash
find /opt/minecraft/server -path "*addons/kokoto-web-chat/chat.js" -printf "%p  %TY-%Tm-%Td %TH:%TM\n"
```

## BlueMap webroot 불일치

`/api/config`는 동작하지만 채팅 패널이 보이지 않으면 BlueMap이 다른 webroot를 서비스하고 있을 수 있습니다. 실제 경로와 다음 설정을 맞추세요.

```yaml
adapters:
  bluemap:
    bluemap-web-root: ""
    bluemap-webapp-conf: ""
    addon-path: "addons/kokoto-web-chat"
```

빈 경로는 자동 탐색을 사용합니다. 자동 탐색이 실제 BlueMap 인스턴스와 다르면 절대 경로를 명시하세요.

## 브라우저 캐시

웹 UI 변경을 테스트할 때는 DevTools를 열고 **Network -> Disable cache**를 체크한 뒤 강력 새로고침하세요. 콘솔에서 로드된 버전도 확인할 수 있습니다.

```js
[...document.scripts]
  .filter(s => s.src.includes("bluemap-web-chat"))
  .map(s => s.src)
```

## BlueMap이 이전 addon 버전을 계속 불러오는 경우

업데이트 후 BlueMap이 이전 KOKOTO WebChat addon 버전을 계속 불러오면 `/kchat reload`를 한 번 더 실행하거나 서버를 재시작한 뒤, 브라우저를 강력 새로고침하세요.

## HTTPS 리버스 프록시

공개 서버에서는 Caddy 또는 nginx를 통한 HTTPS 사용을 권장합니다. Caddy는 `docs/ko/CADDY_HTTPS.md`와 `examples/caddy/Caddyfile`, nginx는 `docs/ko/NGINX_HTTPS.md`와 `examples/nginx/kchat.conf`를 참고하세요.
