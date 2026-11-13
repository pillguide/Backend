# 대표 이미지 조회 — DEV 우선

Backend #23 / AI-server #3. 기존의 이미지 key/hash에 검증된 앞면150장의 식별자와 SHA-256을 연결한다. DB 스키마·회원·스캔·검출 ID는 변경하지 않는다. 사용자 사진은 저장하지 않는다.

## 설정과 게시

기본 비활성이다. DEV manifest #4에서만 `REPRESENTATIVE_IMAGES_ENABLED=true`를 지정한다. 기존 `AWS_REGION`, `PILLGUIDE_S3_BUCKET`, `PILLGUIDE_S3_PREFIX=dev/` 및 SDK 기본 인증 경로(현재 EC2 역할)를 사용한다. 인증 정보를 새로 배포하지 않는다. 운영 설정은 전체 DEV 완료·재검증 이후 별도 적용한다.

자산 검증 목록의 정확한150개 품목에 대해 다음 순서로 게시한다.

1. 원본 검증: AI-server `provenance/representative-front-150.json` + `scripts/validate_representative_images.py`.
2. private S3 `dev/reference/front/{itemSeq}/{sha256}.png`에 원본 게시. 같은 키를 다른 바이트로 덮어쓰지 않는다.
3. 객체의 실제 바이트 해시·형식 확인 후 하나의 DEV DB 트랜잭션에서 기존 Drug의 `representative_image_key/hash`만 연결한다. itemSeq/productCode 정확히150개 일치하지 않으면 취소한다.
4. 조회 기능 활성화 후 실제 DEV 인증 API와 이미지 읽기를 검증한다.

## 응답 계약

| 경로 | 이미지 정보 |
|---|---|
| 스캔 생성·재시도·이력 상세 | 검출별 `imageUrl`, `representativeImageStatus` |
| 이력의 단건 약 상세 | `imageUrl`, `representativeImageStatus`, `reference.representativeImageStatus` |
| 약 상세·내 약 상세 | `imageUrl`, `reference.representativeImageStatus` |
| 내 약 저장·목록 | `imageUrl`, `representativeImageStatus` |
| 검색 | 기존 `imageUrl` 필드에 동일한 대표 주소 또는 null |

READY는 검증 후 게시한 key/hash가 현재 환경·품목과 일치하고 주소 발급에 성공한 상태다. 매 조회마다 원격 객체를 검사한다는 뜻이 아니며, 실제 다운로드 성공과 구분한다. 미게시·비활성·잘못된 키·주소 발급 실패는 null/NOT_READY로 처리하여 스캔·저장을 막지 않는다. 발급 오류의 비밀값이나 URL 쿼리는 로그에 남기지 않는다.

주소는 최대10분 동안 사용한다(발급 자격 만료가 더 빠르면 먼저 만료될 수 있음). 클라이언트는 이미지 읽기 실패 시 같은 인증된 상세/이력 API를 다시 조회해 주소를 갱신하고, 다시 실패하면 이미지를 읽을 수 없다고 안내한다. 실패한 이미지를 정답이나 안전 판단으로 해석하지 않는다. 단순 img 표시에는 CORS 변경이 필요 없다.

URL은 DB나 분석 snapshot에 저장하지 않는다. 이력 재조회는 당시 품목명·점수·bbox·병용 정보와 현재 대표 이미지를 함께 반환하므로 URL까지 바이트 동일한 응답을 보장하지 않는다. 세션 `imageComparisonStatus`는 비어 있지 않은 검출 전체의 이미지 준비 상태이고, `userImageAvailable=false`는 서버에 사용자 원본이 없다는 기존 의미를 유지한다. 과거 legacy 세션에는 기존 메타데이터 부재 의미를 유지한다.

## 검증·중단

150개 객체/DB 매핑과 URL 다운로드, 인증 없는 직접 읽기 거부, URL 재조회, 미준비·발급 실패, 이력 소유권·선택 저장·스냅샷 보존을 확인한다. 정기 참조 갱신은 기존 key/hash를 보존한다.

문제가 생기면 DEV 활성 설정을 false로 되돌리고 DEV만 재시작한다. 이 동작은 이미 발급된 URL을 즉시 폐기하지 않으므로 최대10분의 유효 기간을 고려한다. 잘못된 매핑은 기존 품목 ID를 유지해 해당 key/hash만 정정한다. 기존 회원이나 이력을 삭제하거나 전체 DB를 복원하지 않는다.
