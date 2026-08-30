# KOKOTO WebChat 5.1.0 — 사용자 가이드

이 문서는 일반 사용자를 위한 핵심 사용법입니다. 서버 설치·운영은 `INSTALLATION_OPERATIONS_KO.md`, 구현 세부사항은 `TECHNICAL_REFERENCE_KO.md`, 전체 장문 참고서는 `COMPLETE_REFERENCE_KO.md`를 보세요.

## 공개 채팅
일반 채팅 화면에서 게임 ↔ 웹 대화를 사용합니다. 답글, 고정, 검색, 업로드, 커스텀 이모지, 미리보기, 최신 메시지 이동이 같은 타임라인에서 동작합니다. 이름 클릭 동작과 메시지 본문 reply 동작은 분리되어 URL은 기존 링크 동작을 유지합니다.

## 계정과 게스트
연동된 Minecraft 계정은 서버 사용자 신원을 유지하며 지원되는 채팅/알림 설정을 계정 단위로 동기화할 수 있습니다. 게스트는 관리자가 허용한 경우에만 사용할 수 있고 이름, CAPTCHA, 세션, moderation 정책을 적용받습니다.

## DM과 그룹 채팅

![비공개 Reply 검증 및 서버간 식별 흐름](assets/private-reply-flow.svg)
1:1 DM은 수신자 UI 또는 `/kchat dm`, 그룹 room은 그룹 UI 또는 `/kchat group`으로 사용합니다. 게임에서는 DM/그룹 대화 이름을 클릭하면 해당 명령이 준비되고, 비공개 메시지 본문을 클릭하면 reply가 준비됩니다. 실제 전송 시 서버가 DM 참여 여부/현재 그룹 멤버십을 다시 확인합니다.

## 답글·이모지·미디어
답글은 원문 전체를 보존합니다. 등록된 커스텀 이모지는 reply preview에서도 표시되고 중첩 링크는 만들지 않습니다. 이모지 선택기는 caret 위치에 정확한 token만 삽입하며 자동 공백을 붙이지 않습니다. newline alias로 만든 연속 이모지 전용 줄은 간격을 줄이고 의도적인 빈 줄은 그대로 유지합니다.

## 알림과 화면 설정
데스크톱 알림, Web Push, 키워드 설정, 계정 UI profile, 테마/폰트/text-shadow, PIP는 서버가 제공하는 웹 설정에서 조정합니다. 창 위치/크기와 Push endpoint 같은 device-local 상태는 해당 기기에만 남습니다.

## 문제가 있을 때
서버 업데이트 후 먼저 웹 페이지를 새로고침하고 현재 접속 URL과 로그인 상태를 확인하세요. 관리자는 데이터 파일을 직접 수정하기 전에 설치·운영 가이드와 Wiki의 `Troubleshooting`을 확인하는 것을 권장합니다.

## 참조 문서

구현·보안 세부사항과 외부 표준은 [TECHNICAL_REFERENCE_KO.md](TECHNICAL_REFERENCE_KO.md)와 [REFERENCES_KO.md](REFERENCES_KO.md)를 참고하세요.
