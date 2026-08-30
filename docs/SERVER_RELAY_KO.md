# 서버 릴레이 — Protocol v2


![Relay Protocol v2 요청별 인증 및 암호화 메시지 흐름](assets/relay-v2-flow.gif)

> **보안 경계:** Relay v2는 종단간 암호화가 아니라 **hop-by-hop authenticated encryption**입니다. 전달에 참여하는 KWC 서버는 신뢰 경계 안의 참가자입니다.

KOKOTO WebChat 5.1.0은 5.0.0의 flat relay trust 구조를 **Relay Protocol v2**로 교체합니다. 공개 채팅과 타 서버 1:1 DM/읽음 확인은 같은 group 기반 인증 전송을 사용합니다. 그룹 채팅 방 자체는 로컬 기능이며 relay하지 않습니다.

## 신뢰 모델

Relay **group이 보안 경계**입니다. 각 group에는 다음만 둡니다.

- group `id`
- 해당 group의 모든 관계가 공통으로 사용하는 `shared-secret`
- group별 `forwarding.enabled`
- `id`, `url`, `enabled`만 갖는 peer 목록

v2에는 `peers[].secret`이 없습니다. 따라서 peer에 다른 group의 secret을 잘못 연결하는 설정 자체를 만들지 못합니다.

같은 peer ID를 로컬의 여러 group에 중복 등록할 수 없습니다. 중복이 발견되면 그 peer ID의 모든 등록을 비활성 처리하고 진단 로그를 남깁니다.

`shared-secret`은 최종적으로 최소 **32자**여야 하지만 운영자가 직접 긴 값을 만들 필요는 없습니다. 최초 설정에서는 **한 서버에서만** `shared-secret: ""`로 두고 KWC를 시작하거나 `/kchat reload`를 실행하세요. KWC가 암호학적으로 안전한 32바이트 URL-safe 난수값을 생성해 그 서버의 `config.yml`에 직접 저장하며, secret 원문은 로그에 출력하지 않습니다. 그 생성값을 같은 group의 다른 모든 서버에 그대로 복사합니다. 각 서버에서 따로 빈 값으로 시작하면 서로 다른 secret이 생성되어 요청 인증이 실패하므로 그렇게 하면 안 됩니다. 이미 비어 있지 않은 secret은 자동 재생성하지 않으며, 수동으로 넣은 값이 32자 미만이면 그대로 invalid/fail-closed 처리됩니다. secret이 유출되면 해당 group의 모든 서버에서 함께 교체하세요.

## 설정 예시

```yaml
server-relay:
  enabled: true
  server-id: "server-1"
  server-name: "Server 1"
  connect-timeout-seconds: 5
  request-timeout-seconds: 10
  max-clock-skew-seconds: 60
  dedupe-seconds: 300
  max-hops: 8

  sources:
    game: true
    web: true
    guest: true
    discord: false
    system: false

  delivery:
    web: true
    game: true

  game-format: "&8[&b{server}&8] &f{sender}&7: &f{message}"

  groups:
    - id: "main"
      shared-secret: ""
      forwarding:
        enabled: false
      peers:
        - id: "server-2"
          url: "https://server2.example.com/api"
          enabled: true
```

먼저 `server-1`을 빈 값으로 한 번 시작/리로드하고 `config.yml`을 다시 열어 생성된 secret을 확인합니다. 그 값을 `server-2`에 그대로 복사해 같은 `main` group에서 `server-1`을 역방향 peer로 등록합니다.

```yaml
server-relay:
  enabled: true
  server-id: "server-2"
  server-name: "Server 2"
  groups:
    - id: "main"
      shared-secret: "<copy-the-generated-secret-from-server-1>"
      forwarding:
        enabled: false
      peers:
        - id: "server-1"
          url: "https://server1.example.com/api"
          enabled: true
```

## 요청별 인증과 선택적 identity/health probe

direct relay는 5.0.0과 같은 동작 방식으로 각 `/relay/v2/message` 요청을 독립적으로 인증합니다. 수신 서버는 송신 서버를 같은 group에 같은 shared secret으로 등록해야 하며, 이 정보로 요청을 인증/복호화합니다. 반대 방향은 독립적입니다. `/relay/v2/handshake`는 상태를 저장하지 않는 진단용 identity/health probe일 뿐이며 direct route를 생성·유지·활성화·비활성화하지 않습니다. 선택적 probe 요청에는 다음이 포함됩니다.

- protocol `2`, 제품 버전 `5.1.0`
- `group-id`
- 송신 server ID
- 대상 server ID
- timestamp
- nonce
- 송신자가 상대에게 실제로 사용하는 transport (`http` 또는 `https`)

수신측은 group 존재 여부, 해당 group의 peer membership, 대상 ID, 시간 오차, nonce 재사용, group shared-secret HMAC을 모두 검증합니다. 따라서 한쪽에만 peer를 등록한 구성은 어느 방향에서도 정상 관계가 되지 않습니다.

Endpoint는 다음 두 개입니다.

```text
/relay/v2/handshake
/relay/v2/message
```

구형 v1 endpoint (`/relay/handshake`, `/relay/receive`, `/relay/dm/receive`, `/relay/dm/read`)는 **HTTP 426**과 protocol `2` / version `5.1.0` 요구를 반환합니다.

## 암호화와 인증

Relay v2는 group secret, group ID, 송신 ID, 수신 ID를 입력으로 HKDF-SHA256을 사용해 **방향별 256-bit key**를 생성합니다. 각 요청은 12-byte 난수 IV와 128-bit tag의 AES-256-GCM을 사용합니다.

다음 값은 GCM AAD에 포함됩니다.

```text
group-id
from-server-id
to-server-id
timestamp
nonce
IV
```

이 값 중 하나라도 변경되면 인증에 실패합니다. 암호화된 payload 안에는 `public`, `dm`, `read` 종류와 해당 relay envelope가 들어갑니다.

알려진 peer에 대한 성공/오류 응답도 HMAC-SHA256으로 인증합니다. 응답 서명에는 group, 응답 서버, 요청 서버, 응답 timestamp, 원 요청 nonce, HTTP status, response body가 들어가므로 네트워크 중간자가 성공 응답만 위조할 수 없습니다.

## 재전송/루프 방어

- `max-clock-skew-seconds` timestamp 검사
- 요청 nonce 중복 차단
- relay ID / receipt ID 중복 차단
- origin server loop 검사
- forwarding `max-hops`

을 함께 적용합니다.

## HTTP와 HTTPS

직접 1-hop HTTP peer는 허용합니다. **Relay payload 자체는 AES-256-GCM으로 암호화·인증되므로 v1처럼 평문 payload를 보내지 않습니다.** 그래도 HTTPS는 transport metadata 보호, 일반적인 서버 인증, defense-in-depth를 제공하므로 KWC가 명시적인 `[경고]`를 출력합니다.

HTTP는 forwarding hop으로 사용할 수 없습니다.

Forwarding 조건은 모두 만족해야 합니다.

1. 해당 group의 `forwarding.enabled: true`
2. 이 서버에 설정된 incoming peer 항목의 URL이 HTTPS
3. 선택할 다음 peer 항목의 URL도 HTTPS
4. 다음 peer가 같은 group에 속함

HTTP 차단은 **peer 단위**입니다. `http://` peer는 direct relay에는 계속 사용할 수 있지만 그 peer에서 들어온 메시지를 더 forwarding하지 않고, 그 peer 자체도 다음 forwarding hop으로 선택하지 않습니다. 같은 group의 다른 `https://` peer는 계속 forwarding 후보가 됩니다.

## Group 격리

한 group에서 수신한 메시지를 다른 group으로 forwarding하지 않습니다. 다음 hop은 항상 **수신한 동일 group 내부**에서만 선택합니다. 공개 채팅, DM, DM read receipt에 동일하게 적용됩니다.

로컬에서 새로 발생한 메시지를 서버가 명시적으로 가입한 여러 group에 각각 publish하는 것은 가능하지만, 이는 수신 메시지의 cross-group forwarding과는 다릅니다.

## E2EE가 아닌 hop-by-hop 암호화

Relay v2는 **hop-by-hop authenticated encryption**이며 end-to-end encryption이 아닙니다. 중계 서버는 incoming payload를 복호화해 envelope를 검증·처리한 뒤 다음 hop용 방향키로 다시 암호화합니다.

따라서 중계 서버는 trusted participant이며 relay payload를 볼 수 있습니다. Relay v2를 E2EE로 설명하면 안 됩니다.

## 5.0.0 / v1에서 업그레이드

5.1.0은 기존 flat 설정을 보고 v2 group을 **추측해서 만들지 않습니다**. 최초 5.0.0 → 5.1.0 migration에서는:

- `server-relay.shared-secret` 폐기
- flat `server-relay.peers` 폐기
- `server-relay.forward-received-public-chat` 폐기
- 구형 top-level forwarding 설정을 임의의 group에 이식하지 않음
- `server-relay.enabled: false`로 안전하게 reset
- 운영자가 v2 group을 직접 정의한 뒤 다시 활성화

합니다. 잘못된 trust group 자동 생성보다 재설정을 요구하는 쪽을 우선합니다.

## 운영 확인

시작/reload 로그에서 다음을 확인하세요.

- `Server relay protocol v2 enabled`
- direct 요청의 인증/복호화 성공 여부와 선택적으로 실행한 identity/health probe 결과
- peer ID 중복 진단
- group secret 길이 진단
- HTTP peer `[경고]`
- forwarding HTTPS 차단 경고

선택적 identity/health probe가 실패하면 group ID, 양쪽 server ID, 상호 peer 등록, group secret, API base URL, 서버 시간 동기화, 네트워크 접근성을 확인하세요. direct 메시지 전달은 probe 상태와 독립적입니다.

## 참조 표준 및 공식 문서

이 문서에서 사용하는 1차 표준과 공식 서드파티 문서는 [REFERENCES_KO.md](REFERENCES_KO.md)에 정리되어 있습니다.

- [RFC 2104 — HMAC](https://www.rfc-editor.org/rfc/rfc2104.html)
- [RFC 5869 — HKDF](https://www.rfc-editor.org/info/rfc5869/)
- [NIST SP 800-38D — GCM](https://csrc.nist.gov/pubs/sp/800/38/d/final)
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)
