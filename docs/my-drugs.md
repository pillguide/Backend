# 선택한 내 약 API

모든 경로에 access token이 필요하다. 응답은 기존 `{status, success, message, data}` 형식을 따른다. 내 약은 회원과 품목의 관계이며 스캔 이력 및 복약 일정과 별개다.

| 요청 | 결과 |
|---|---|
| `POST /api/v1/my-drugs` | `{ "scanSessionId": 123, "detectionIds": [456, 457] }`. 본인 스캔에 속한 영구 검출 ID 1~300개만 허용한다. 새 관계가 하나 이상이면 201, 모두 기존 관계이면 200. `data`는 선택된 고유 품목의 내 약 목록이다. |
| `GET /api/v1/my-drugs?page=0&size=20` | 최신 등록순, 동일 시각은 관계 ID 역순. size 1~100. `data={items,page,size,totalElements,totalPages,hasNext}`. |
| `GET /api/v1/my-drugs/{memberDrugId}` | `data={memberDrugId,firstRegisteredAt,drug}`. drug는 현재 약 상세이며 과거 스캔 당시의 설명이 아니다. |
| `DELETE /api/v1/my-drugs/{memberDrugId}` | 본인 관계만 제거하고 200 및 제거한 관계 ID 반환. 없는 관계와 타인 관계는 동일한 404. |

목록 항목은 `memberDrugId`, `firstRegisteredAt`, `drugId`, `name`, `code`(item_seq), `imageUrl`, `infoStatus`, `authorizationStatus`, `sourceAsOf`, `representativeImageStatus`를 제공한다. 현재 대표 이미지는 미준비(`NOT_READY`)다. 공식 자료의 부재나 허가 상태를 복용 가능 여부로 해석하지 않는다.

같은 품목의 여러 검출을 선택해도 회원·품목 관계는 하나다. 기존 관계의 ID와 최초 등록 시각을 보존하며, 제거 후 재등록하면 새로운 관계와 시각을 만든다. 최초 시각은 DB의 마이크로초 정밀도로 생성하여 최초 응답과 재조회 값이 같다. 잘못된 검출이 하나라도 포함되면 전체 선택을 저장하지 않는다. 타인 스캔/검출 및 해당 세션에 없는 검출은 404, 비어 있거나 잘못된 요청 형식은 400이다.

쓰기 작업은 회원 잠금 아래 인증 버전·만료를 다시 확인하고 같은 회원의 동시 추가·제거를 직렬화한다. 관계 삭제로 약 마스터, 스캔, 복약 일정이나 복용 기록을 삭제하지 않는다. 내 약 등록은 인식 결과에 대한 정답 확인이 아니며, 사용자는 재촬영하거나 저장하지 않을 수 있다.

기존 검색 상세 및 스캔 약 상세에는 `reference`가 추가된다. 문자열 품목 코드, 모델명, 분류 코드/명, 성분 표시, 자료·허가 상태, 자료 기준일/수집일과 이미지 준비 상태를 담는다. 과거 스캔의 검출명/병용 결과는 당시 snapshot을 유지한다.
