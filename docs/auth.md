# 인증 계약과 전환

2026-11-11 로컬 구현·회귀 검증 완료. 실제 공급자 로그인과 dev 배포 경로 확인은 최종 E2E 단계에서 별도 수행한다. 운영 적용 완료 문서가 아니다.

## 요청 경계

업무 API는 access JWT가 필요하다. 로그인 시작(`/api/v1/oauth2/authorize/{provider}`), 공급자 callback(`/login/oauth2/code/{provider}`), 일회용 code 교환(`/api/v1/auth/exchange`), 공개 Swagger/OpenAPI와 실제 health 프로브만 공개한다. reissue/logout은 `X-Refresh-Token`으로 별도 인증하므로 만료 access가 없어도 호출 가능하다. 허용 Origin의 CORS OPTIONS는 업무 권한을 주지 않는다. 다른 Actuator API는 공개하지 않는다.

access/refresh JWT는 용도 claim과 무작위 jti를 가진다. access의 회원 인증 버전이 현재 Member.authVersion과 다르면 401이다. 구형 토큰은 재로그인해야 한다. 새 로그인이나 재발급만으로 이전 access를 추가 폐기하지 않으며 logout 성공 때만 authVersion을 증가시킨다.

## 회원 잠금과 원자성

로그인·rotation·logout은 Member 행 잠금을 먼저 잡고 현재 토큰 상태를 다시 읽는다. 회원당 refresh는 한 행이고 로그인·rotation은 기존 행을 교체/갱신한다. 로그인 시 이미 존재하는 유효 access의 만료 의미는 바뀌지 않는다. logout은 authVersion 증가·refresh 삭제·FCM 연결 해제를 한 트랜잭션으로 커밋한다. 회전된 이전 refresh 재사용은 401이다. rotation이 먼저 커밋하면 이전 refresh logout은 401일 수 있으므로 프런트는 진행 중인 같은 탭 재발급을 기다리고 최신 refresh를 사용한다.

추론 등 장시간 외부 호출 중에는 회원 잠금을 잡지 않는다. 추론 결과 저장 직전 회원 잠금 아래 요청 access의 인증 버전을 재검증한다. 이미 전송한 응답이나 이미 시작한 FCM 전송 회수를 보장하지 않는다.

## OAuth 브라우저 전달

브라우저에서 일회용 verifier와 SHA-256 challenge를 만든다. 서버는 challenge를 공급자 OAuth authorization request의 서버 측 attributes에 연결하고 공급자 state 검증을 유지한다. 성공 callback에서 JWT를 URL로 전달하지 않는다. 짧게 만료되는 무작위 교환 code를 발급하며 DB에는 code hash, challenge, 회원, 만료 시각만 보관한다. callback URL에는 code만 넣는다.

`POST /api/v1/auth/exchange`는 `{code, verifier}`를 받아 challenge 일치·만료·미소비를 확인한 후 원자적으로 한 번만 소비하고 토큰을 발급한다. 코드 재사용·잘못된 verifier·만료는 401이며 토큰을 발급하지 않는다. 추가정보 입력 완료를 로그인 조건으로 쓰지 않는다. 기존 이메일·성별·생일 데이터는 삭제하지 않는다. frontend는 교환 후 URL을 정리하고 기존 토큰 저장 방식을 유지한다.

## 응답과 문서

실패는 기존 `{status, success, message}` envelope를 따른다. HTTP와 body.status가 같아야 한다. 인증 없음·잘못된 용도·폐기·만료는 401, 권한 부족은 403, 본인 조회 대상 부재/타인 자료는 404다. 알려진 요청 구문/검증 오류는 400, 미규정 내부 오류는 500이다. 내부 IllegalArgumentException 전체를 400으로 바꾸지 않는다. 오류 상세·키·토큰을 응답/로그에 노출하지 않는다.

## 기존 DB 보존

Member.authVersion은 기존 행 0으로 초기화하며 NOT NULL/default 0을 둔다. refresh JWT claim 증가를 위해 token 길이를 충분히 늘린다. 회원별 refresh 중복이 있으면 임의 삭제하지 않고 전환 전에 중단한다. 기존 회원·복약·FCM·스캔 데이터는 유지한다. 배포 전 SQL 반복 적용 및 기존 행 보존을 PostgreSQL에서 검증한다. 운영 DB 변경은 이 문서 작성으로 실행되지 않는다.

## 통과 기준

- 실제 보안 필터: 모든 업무 API의 익명/변조/만료/refresh Bearer/폐기 access 401, 공개 경로 정상.
- 실제 PostgreSQL: 동시 rotation 1회 성공, logout 선행 후 이전 access/refresh 거절, 재로그인 후 종전 access 유지, 회원별 refresh 최대 1행, FCM 해제 원자성.
- OAuth 모의 계약: callback URL JWT 없음, verifier 바인딩, provider state 유지, code 일회성/만료, 추가정보 gate 없음. 실제 공급자 로그인은 dev E2E에서 별도 검증.
- Swagger 인증/오류 정의와 실제 HTTP 응답 대조, 기존 복약·FCM 회귀 통과.

공식 참고: https://docs.spring.io/spring-security/reference/7.0/servlet/authorization/authorize-http-requests.html

## 구현 검증과 적용 순서

일반 JUnit 73건, PostgreSQL 17 JUnit 66건, `check postgresTest bootJar` 통과(2026-11-11). 기존 복약·FCM 검증을 포함한다. 프런트는 로그인 시작/콜백/공통 재발급/로그아웃에 한정해 수정했고 lint·테스트 9건·빌드를 통과했다.

로그아웃에서 refresh 행만 삭제하면 Member의 cascade collection이 삭제한 행을 다시 유지할 수 있음을 실제 PostgreSQL 경합 테스트로 재현했다. 회원의 연관관계에서도 토큰을 제거해 고쳤고 버전 증가·FCM 해제·refresh 삭제를 함께 검증했다.

배포 전 `docs/sql/auth-state.sql`, `docs/sql/login-exchange.sql`을 사전 점검 후 적용하고 새 backend와 frontend 로그인 계약을 함께 반영한다. 로그인 시작 쿼리는 `exchange_challenge`, callback은 `code`이다. 교환 코드는 2분 유효하며 DB에는 hash만 저장한다. 만료 코드는 기본 60초 간격으로 별도 짧은 트랜잭션에서 정리한다(`app.oauth2.exchange-cleanup-enabled`, `app.oauth2.exchange-cleanup-ms`, `app.oauth2.exchange-cleanup-initial-delay-ms`). 사용자에게 새 선택을 요구하는 정책이 아닌 일회용 인증 자료의 내부 수명이다.

외부 `/api/v1/drugs/register`, `/batch-register` 컨트롤러는 제거했다. 약 마스터는 이후 내부 참조자료 수집 단계에서만 갱신한다. 익명 업무 요청은 401이고 인증된 제거 경로 요청은 404이다.
