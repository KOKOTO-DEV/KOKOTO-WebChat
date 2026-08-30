# KOKOTO WebChat 5.1.0 — 참조 표준 및 공식 문서

이 페이지는 KOKOTO WebChat 5.1.0의 기술·운영 문서가 참조하는 **1차 표준과 공식 제품/프로젝트 문서**를 정리합니다. KWC 고유 동작은 이 저장소의 5.1.0 소스와 현재 릴리스 문서가 기준이며, 아래 링크는 기반 프로토콜·API·저장 방식·서드파티 연동을 설명하기 위한 참조입니다.

## 암호화 및 인증

- **RFC 2104 — HMAC: Keyed-Hashing for Message Authentication**  
  https://www.rfc-editor.org/rfc/rfc2104.html  
  Relay Protocol v2의 handshake/응답 인증 설명에 사용합니다.
- **RFC 5869 — HMAC-based Extract-and-Expand Key Derivation Function (HKDF)**  
  https://www.rfc-editor.org/info/rfc5869/  
  Relay v2 방향별 키 파생과 Web Push 키 파생 설명에 사용합니다.
- **NIST SP 800-38D — Galois/Counter Mode (GCM) and GMAC**  
  https://csrc.nist.gov/pubs/sp/800/38/d/final  
  AES-GCM 인증 암호화 설명의 기준입니다.
- **RFC 8018 — PKCS #5: Password-Based Cryptography Specification Version 2.1**  
  https://www.rfc-editor.org/info/rfc8018/  
  PBKDF2 비밀번호 해싱 설명에 사용합니다.
- **RFC 8291 — Message Encryption for Web Push**  
  https://www.rfc-editor.org/info/rfc8291/  
  Web Push payload 암호화 설명에 사용합니다.
- **RFC 8292 — VAPID for Web Push**  
  https://www.rfc-editor.org/info/rfc8292/  
  KWC의 VAPID application-server 식별 설명에 사용합니다.

## HTTP, SSE, JDK API

- **RFC 9110 — HTTP Semantics**  
  https://www.rfc-editor.org/rfc/rfc9110.html
- **WHATWG HTML Standard — Server-sent events / EventSource**  
  https://html.spec.whatwg.org/multipage/server-sent-events.html
- **Oracle Java SE 17 — `com.sun.net.httpserver.HttpServer`**  
  https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html

## 저장소

- **SQLite — Write-Ahead Logging**  
  https://sqlite.org/wal.html  
  KWC의 SQLite WAL 저장 및 백업 설명에 사용합니다.

## Reverse proxy 및 지도 호스팅

- **Caddy — `reverse_proxy` directive**  
  https://caddyserver.com/docs/caddyfile/directives/reverse_proxy
- **NGINX — `ngx_http_proxy_module`**  
  https://nginx.org/en/docs/http/ngx_http_proxy_module.html
- **BlueMap Wiki**  
  https://bluemap.bluecolored.de/wiki/
- **BlueMap Reverse Proxy 가이드**  
  https://bluemap.bluecolored.de/wiki/webserver/ReverseProxy.html

## Discord 연동

- **DiscordSRV Documentation**  
  https://docs.discordsrv.com/
- **DiscordSRV Initial Setup**  
  https://docs.discordsrv.com/installation/initial-setup/

## 선택 연동 프로젝트

- **ImageEmojis upstream**  
  https://github.com/MrQuackDuck/ImageEmojis
- **SimpleNicks upstream**  
  https://github.com/Simplexity-Development/SimpleNicks

upstream 문서는 원 프로젝트 동작을 설명합니다. KWC-Bero 연동이나 KWC 고유 호환성은 해당 KWC 문서와 5.1.0 소스가 기준입니다.

## Android 파일 선택

- **Android Developers — OpenableColumns / DISPLAY_NAME**  
  https://developer.android.com/reference/android/provider/OpenableColumns.html
- **Chromium Issue 387440285 — Android picker에서 Browse를 거치지 않고 직접 선택할 때 큰 숫자형 파일명이 노출될 수 있는 문제**  
  https://issues.chromium.org/issues/387440285
- **Chromium Issue 40912007 — Android Chrome이 비밀번호와 무관한 일반 HTML 입력창에도 비밀번호/결제 관리 UI를 표시할 수 있는 문제**  
  https://issues.chromium.org/issues/40912007

이 참조는 Android/브라우저의 파일명 전달 동작을 설명합니다. 일반 웹 JavaScript에서 KWC가 볼 수 있는 것은 브라우저가 노출한 `File.name`뿐이며 Android MediaStore를 직접 조회할 수 없습니다.

## 문서 렌더링

- **GitHub Docs — Mermaid 다이어그램**  
  https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-diagrams
- **GitHub Docs — 비코드 파일 렌더링**  
  https://docs.github.com/en/repositories/working-with-files/using-files/working-with-non-code-files
- **Mermaid — Flowchart syntax**  
  https://mermaid.js.org/syntax/flowchart.html
- **Mermaid — Sequence diagram syntax**  
  https://mermaid.js.org/syntax/sequenceDiagram.html

## 참조 정책

- 가능한 경우 1차 표준과 공식 vendor/project 문서를 우선합니다.
- 참조 링크가 있다고 해서 KWC가 해당 표준이나 제품의 모든 기능을 구현한다는 의미는 아닙니다.
- KWC의 실제 동작, 기본값, 호환 규칙, 보안 경계는 반드시 5.1.0 소스와 현재 KWC 문서로 확인합니다.
- 과거 버전 문서는 역사 자료로 유지하며 현재 5.1.0 동작의 정의로 사용하지 않습니다.
