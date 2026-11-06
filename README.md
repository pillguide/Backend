# 약학다식 · PillGuide Backend

사진 기반 알약 인식과 복약 정보 서비스를 연결하는 **약학다식**의 Spring 비즈니스 서버입니다. 사용자 인증, 약 정보와 스캔 기록을 관리하고, **식약처 OpenAPI 조회와 DB 연계는 이 서버가 담당**합니다.

## 주요 기능과 기술

- OAuth2·JWT 기반 인증 및 회원 정보 처리.
- `item_seq`를 이용한 식약처 약 정보 조회·등록, 스캔 세션 저장·목록·상세 조회.
- Java 21 · Spring Boot 4.0.3 · Spring Security · Spring Data JPA · PostgreSQL.
- WebClient로 외부 API 호출, springdoc으로 API 문서화, Actuator로 상태 확인.

## 아키텍처

```text
Frontend (Vercel) → Cloudflare → Spring Pod (EC2 / K3s)
                                    ├─ PostgreSQL (RDS)
                                    ├─ 식약처 OpenAPI
                                    └─ Tailscale → 로컬 FastAPI / Model H
                                       (Spring 클라이언트 연동 예정)
```

AI 서버는 알약별 Top-1·confidence·item_seq·위치·인식 저하 가능성을 반환하고, Spring은 이를 품목 정보 및 사용자 데이터와 연결합니다. 식약처 키와 추론 API 키는 서버 측에서만 관리합니다.

**현재 상태:** `POST /api/v1/scan`은 사진 업로드가 아닌 JSON `itemSeqList`를 받아 이미 식별된 품목을 저장합니다. 사진 수신과 AI `/predict` 호출은 후속 연동 작업입니다. Pod에서 추론 서버로 통신한 검증은 Spring 애플리케이션 연동 완료와 다릅니다.

## 로컬 개발

JDK 21, 저장소의 Gradle Wrapper, `config` 서브모듈 접근 권한이 필요합니다.

```sh
git submodule update --init --recursive
./gradlew check bootJar
./gradlew bootRun
```

Windows PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용합니다. `bootRun` 전에 승인된 로컬 프로필과 DB·OAuth·JWT·식약처 설정을 공급해야 합니다. 운영 DB나 운영 비밀정보를 개발용 기본값으로 사용하지 않습니다.

`config`는 이 저장소가 기록한 커밋을 사용합니다. 임의로 최신 브랜치를 따라가거나 `--remote`로 갱신하지 않습니다. 키·토큰·비밀번호는 Git에 넣지 않습니다.

## 코드 구조

| 경로 | 역할 |
|---|---|
| `src/main/java/kr/co/pillguide/backend/api/` | 회원·약 도메인의 Controller, Service, Repository, DTO |
| `src/main/java/kr/co/pillguide/backend/common/` | 보안, OAuth2/JWT, 공통 응답·예외·설정 |
| `src/test/` | Spring 컨텍스트·인증·예외 처리 테스트 |
| `config/` | 환경 설정 서브모듈 |
| `gradle/`, `scripts/` | 의존성 구성과 배포 설정 검증 |

## 배포

GitHub Actions가 테스트와 JAR 빌드 후 Linux ARM64 이미지를 배포하고, `manifest/main`의 이미지 digest를 갱신합니다. Argo CD가 EC2의 K3s에 반영합니다.

| Backend 브랜치 | manifest 경로 / 네임스페이스 |
|---|---|
| `develop` | `dev/` / `dev` |
| `main` | `production/` / `production` |

현재 두 환경은 각각 1 replica와 `Recreate` 전략으로 배포 중 짧은 중단을 허용합니다. CI가 replica 수를 변경하지는 않습니다. 상세 운영 제약과 스키마 설정 주의사항은 배포 문서를 확인합니다.

## 연결 문서

- [개발·기여 규칙](CONTRIBUTING.md) · [GitOps 배포 가이드](docs/gitops.md)
- [Swagger 설정과 API 문서 구성](src/main/java/kr/co/pillguide/backend/common/config/swagger/SwaggerConfig.java)
- [AI-server — 요청·응답 및 Spring 연동 명세](https://github.com/pillguide/AI-server/blob/main/docs/SPRING_INTEGRATION.md)
- [Frontend](https://github.com/pillguide/Frontend) · [manifest](https://github.com/pillguide/manifest)
