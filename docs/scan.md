# 사진 스캔·이력 계약

> 사진 스캔·이력은 2026-11-11 P4 로컬 구현·실제 HTTP 검증을 통과했다. 내 약은 P5 구현 대상이고 dev 배포·대표 이미지 비교는 아직 완료되지 않았다. 기준: Backend `origin/develop` `9195a767`, 2026-11-11 구현 계획, AI-server `4c3a044`의 `docs/SPRING_INTEGRATION.md` 공개 계약.

## 의미와 전환

- 한 장의 사진을 로그인한 회원이 Spring에 업로드한다. Spring이 서버 측 인증으로 AI `POST /predict`를 호출하고 사전 적재된 품목과 승인된 병용금기 4쌍을 조회한다. 식약처 API는 스캔 요청 중 호출하지 않는다.
- 기존 `POST /api/v1/scan`은 JSON `{ "itemSeqList": [...] }`를 저장하는 API다. 조사한 Frontend develop에는 `scanApi.save` 선언만 있고 호출처는 없다. `ScanPage`는 서버 요청 없이 결과 화면으로 이동한다. 저장소 코드 기준으로 같은 POST를 사진 계약으로 전환한다. 외부 호출자는 확인하지 않았으므로 전환 전 접근 로그·배포 소비자를 확인한다. 구 JSON으로 분석 이력을 임의 생성하는 경로는 최종 계약에서 제거한다.
- `GET` 목록·상세의 기존 `sessionId`, `scannedAt`, `drugCount`, `drugs`와 배열 응답은 유지한다. 새 필드를 추가하더라도 이 필드의 타입과 의미를 바꾸지 않는다.
- 스캔은 자동 이력이고, 내 약은 명시적으로 선택해 저장하는 별도 관계다. 스캔으로 내 약·복약 일정·복약 기록을 만들지 않는다.

## 요청과 응답

모든 업무 경로에는 유효한 **access** 토큰이 필요하다. 공통 응답은 `{status,success,message,data?}`이며 HTTP 상태와 `status`는 일치한다. 아래 필드는 `data`의 목표 구조다. 필드 이름·예시는 구현 OpenAPI와 실제 HTTP 검사로 확정한다.

| 경로 | 목표 계약 |
|---|---|
| `POST /api/v1/scan` | `multipart/form-data`의 `file` JPEG/PNG 한 장과 `requestId`(클라이언트가 생성·재시도 때 보존). 최초 정상 분석·저장 201; 같은 회원·requestId·같은 사진의 완료 결과는 200. 다른 사진으로 같은 ID 재사용은 409. |
| `GET /api/v1/scan` | 본인 이력 배열. 원소는 기존 `sessionId`, `scannedAt`, `drugCount` 유지. `drugCount`는 검출 행 수이며 고유 품목 수와 다를 수 있다. |
| `GET /api/v1/scan/{sessionId}` | 본인 이력의 당시 검출과 당시 병용 결과. 기존 `sessionId`, `scannedAt`, `drugs` 유지. 사진 원본·crop URL은 없다. |
| `GET /api/v1/scan/{sessionId}/drugs/{drugId}` | 본인 세션에 포함된 품목의 **현재** 상세. 타인/없는 세션·품목은 동일하게 404. 동일 품목의 여러 검출은 이 상세에 함께 연결될 수 있다. |

새 세션 결과에는 `sessionId`, `scannedAt`(분석 시각), `imageSize`(EXIF 방향 보정 후 너비·높이), `modelSha256`, `mappingSha256`, `referenceVersion`, `drugs`, `combination`을 제공한다. `drugs`의 **각 검출**에는 영구 `detectionId`, 응답 내 `detectionIndex`, `drugId`, 문자열 `code`(item_seq), 당시 품목명, `confidence`, 정규화된 `bboxXyxy`, `recognitionMayBeDegraded`, 자료·허가 상태와 대표 이미지 정보가 필요하다. AI의 `detection_id`는 응답 내 순번이므로 DB의 영구 `detectionId`와 혼동하지 않는다. 기존 `name`, `code`, `imageUrl`을 사용하던 조회자는 호환 필드를 계속 받는다. 이미지 주소가 만료될 수 있으면 조회 시 새로 발급하며 이력에 URL을 저장하지 않는다.

`combination`은 **고유 품목** 기준 `uniqueDrugCount`, `pairCount`, `confirmedContraindicationCount`, `matches`, `coverageStatus`와 자료 기준일을 구분한다. `matches`의 각 항목은 두 품목, 근거 유형(직접 품목/공식 성분코드 연결), 공식 사유·조건, 검수된 짧은 설명, 출처·기준일을 담는다. 미일치는 승인된 참조 목록에서 금기를 확인하지 못했다는 뜻이며 안전 판정이 아니다. 참조자료가 부족하면 해당 상태를 명시한다. AI의 빈 `predictions`는 정상 무검출 세션으로 저장하고 `drugs=[]`, 조합 0개로 반환한다.

AI 응답의 같은 `item_seq`가 두 번 나오면 두 검출을 그대로 저장한다. 예를 들어 A,A,B는 검출 3, 고유 품목 2, 비교 조합 A–B 한 쌍이다. 선택 저장 여부는 이 전체 사진의 조합 결과를 바꾸지 않는다. `confidence`는 정답 확률이 아니며 저하 경고가 없다고 정확성을 보장하지 않는다. 좌표는 AI가 보낸 `[x1,y1,x2,y2]` 정규화 좌표이고, 표시 사진과 EXIF 방향을 일치시킨다.

## 선택한 내 약

| 경로 | 목표 계약 |
|---|---|
| `POST /api/v1/my-drugs` | `{ "scanSessionId": 123, "detectionIds": [1,2] }`. 본인 세션의 검출에서만 품목을 결정한다. 빈 선택은 400. 중복 검출/기존 등록은 회원·품목당 한 관계만 유지하고 최초 등록 시각을 보존한다. |
| `GET /api/v1/my-drugs`, `GET /api/v1/my-drugs/{memberDrugId}` | 본인 관계와 현재 공식 자료·자료 상태를 반환. 목록 크기는 제한한다. |
| `DELETE /api/v1/my-drugs/{memberDrugId}` | 본인 관계만 삭제. 타인/없는 관계는 동일하게 404. 과거 스캔·복약 일정·기록은 보존한다. 재등록은 새 등록 시각이다. |

사용자는 인식 결과를 정답 확인할 의무가 없다. 현재 브라우저의 사진과 검출 좌표를 대표 이미지 옆에서 참고용으로 비교하고 재촬영하거나 저장을 건너뛸 수 있다. 원본 사진과 crop은 영구 저장하지 않으므로 과거 이력에서 다시 표시할 수 없다.

## 오류·재시도·저장

| HTTP | 상황 |
|---:|---|
| 400 | 필수 필드 누락, 잘못된 요청 형식, 빈 검출 선택 |
| 401 | access 부재·만료·용도 불일치·로그아웃 후 폐기 |
| 404 | 타인/없는 세션·검출·품목·내 약 관계 |
| 409 | 같은 requestId에 다른 사진 |
| 413 | 파일 10 MiB, 전체 요청 11 MiB, 2천만 픽셀 중 한도 초과 |
| 415 | JPEG/PNG 이외·손상·애니메이션 이미지 |
| 502 | AI 계약 오류, 모델/매핑 불일치, 서버 간 인증 실패 |
| 503 | AI busy/미준비·일시 연결 불가, 최초 정상 참조자료 부재. 가능한 `Retry-After` 전달 |
| 504 | AI 응답 제한시간 초과 |
| 500 | 내부 오류·DB 쓰기 실패; 부분 이력 없는 rollback |

인증·입력 확인 후 **회원 잠금이나 DB 쓰기 트랜잭션 없이** AI를 호출한다. 현재 참조자료를 한 읽기 snapshot으로 결합하고, 짧은 쓰기 트랜잭션에서 회원 상태 재검증·requestId 재확인·세션/검출/병용 snapshot을 원자적으로 저장한다. 성공 응답은 커밋 뒤에 보낸다. 같은 ID의 동시 요청으로 AI가 중복 호출될 수 있으나 성공 이력은 최대 하나다. timeout 후 자동 AI 재전송은 하지 않는다. 응답 유실 후 재요청은 같은 requestId, 재촬영은 새 requestId다.

과거 JSON 스캔 행은 실제로 수집하지 않은 사진·점수·좌표·병용 결과를 채워 넣지 않는다. legacy 상태와 nullable 새 필드로 구분하고 기존 조회를 유지한다. 내부 AI 오류 원문·키·사진·전체 URL은 응답과 로그에 노출하지 않는다.

## 완료 검증

실제 Spring→FastAPI→DB의 단일·다중·중복 품목·미검출을 대조한다. 4쌍, 경고, 자료 부재와 오류는 통제된 fixture로 별도 확인한다. requestId 경쟁, 저장 rollback, logout 중 분석, 타계정 소유권, 과거 snapshot, 업로드 한도와 사진 비보관을 검증한다. dev 환경의 실제 경로·프록시·OAuth·참조자료 버전을 확인해야 완료라고 표시한다.
