# 사진 분석 실행 설정

`scan.ai.base-url`, `scan.ai.api-key`를 서버 비밀 설정으로 주입한다. 미설정이면 앱 기동은 가능하지만 사진 분석은503이다. 브라우저는 AI 서버를 직접 호출하지 않는다.

| 설정 | 기본값 |
|---|---|
| `scan.ai.expected-model-sha256` | `fb11caa45b8f80e077aaa12072915dccca27e704b7f3ecffa266370a3fb500b7` |
| `scan.ai.expected-mapping-sha256` | `71569e7f4d277b5c6e69930086db7d52920a1c12dae3325a9cbc2234829e8bce` |
| `scan.ai.connect-timeout-millis` | 10000 |
| `scan.ai.response-timeout-millis` | 60000 |
| `scan.ai.max-in-memory-bytes` | 1048576 |

모델 또는 150종 매핑을 바꿀 때 health의 실제 해시, 모델 catalog, 이 설정을 함께 검증한다. 성공 JSON의 해시가 맞지 않으면502이며 다른 버전의 결과로 이력을 만들지 않는다.

`application.properties`에서 파일10MiB/요청11MiB, 메모리 threshold11MiB, 지연 multipart 파싱을 설정한다. PhotoValidator의 픽셀 한도는20MP다. 사진 bytes는 요청 처리 중에만 존재하며 파일명은 내부 전송 시 일반 이름으로 바꾼다. 프록시 제한/timeout은 dev 배포에서 같은 계약과 대조한다.

`aiLiveTest`는 별도 opt-in 검증이다. 실제 로컬 AI와 무작위 포트의 Spring HTTP 서버, loopback55432의 격리DB만 허용한다. 기본 test/postgresTest와 분리되어 있고 dev/prod DB에 실행하지 않는다. 실제 OAuth 대신 테스트 회원·토큰을 발급하며 참조자료도 통제된 fixture로 표시한다. 입력 샘플 경로와 서버 키는 환경변수로 전달하고 소스에 포함하지 않는다.

스캔·응답·오류의 제품 계약은 `scan.md`, 최신 실제 필드/타입은 공개 OpenAPI를 따른다. `drugs[].code`는 문자열 `item_seq`이며 기존 조회자와 호환하기 위해 필드명을 유지한다. `detectionId`는 내 약 선택용 영구 ID, `detectionIndex`는 사진 내1부터 시작하는 순번이다.
