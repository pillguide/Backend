# Contribution Guide

## Rule

- 설명할 수 있는 코드만 커밋할 것
- AI 코드 그대로 복사하지 말것
- 반드시 커밋 전 빌드하여 로컬에서 실행되는지 먼저 확인할 것
- 이슈 발생 시 팀원에게 전파
- PR 생성과 머지 후 반드시 `Actions` 탭에서 CI/CD 성공 여부 확인

## Workflows

1. 이슈 템플릿을 활용해 이슈 생성 (`#35`)
2. `develop` 브랜치에서 새 작업 브랜치 생성 (`feature/#35`)
3. 기능 구현 후 의미 있는 단위로 커밋
4. `develop` 브랜치로 PR 생성
5. `Actions` 탭에서 PR CI 성공 여부 확인 
6. 리뷰를 받고, 승인 후 `develop` 브랜치에 머지
7. 머지 후 `Actions` 탭에서 CI/CD 성공 여부 확인

### CI/CD Flows

- `CI-main.yml`: 운영 환경에 배포되는 CI/CD
- `CI-pr.yml`: PR 머지 시 빌드 테스트 CI/CD

## Git Branches

모든 작업은 `develop` 브랜치에서 시작(파생)

### Branches Naming

**`<이슈타입>/#<이슈번호>-<작업내용 혹은 기능>`**

- **`<이슈타입>`**: 생성한 이슈의 타입
- **`<이슈번호>`**: GitHub 이슈 번호 (ex. `#12`)
- **`<작업내용>`**: 해당 브랜치의 작업내용 혹은 기능, 애매하다면 없어도 무방

### Branch Types

| 타입         | 설명               | 브랜치                        | 
|------------|------------------|----------------------------|
| `main`     | 운영 코드 브랜치        |                            |
| `fix`      | 버그 혹은 기능 수정 브랜치  | `fix/#12-login`            |
| `feature`  | 기능 개발 브랜치        | `feature/#35-oauth2`       |
| `refactor` | 리팩토링 브랜치         | `refactor/#64-vendor-list` |
| `hotfix`   | `main` 브랜치 긴급 수정 | `hotfix/#22-contract-bug`  |

### Issue Type

| 타입         | 설명               | 
|------------|------------------|
| `feat`     | 새로운 기능 추가        |
| `fix`      | 버그 혹은 기능 수정      |
| `docs`     | 문서 작업            |
| `refactor` | 기능 변경 없이 리팩토링    |
| `ci/cd`    | CI/CD 파이프라인 설정   |
| `build`    | 빌드 시스템 또는 의존성 변경 |
| `chore`    | 이외 기타 작업         |

## Commit Messages

- 모든 커밋은 의미있는 단위로 분할하여 커밋

```text
<type>(<scope>): <subject>

<body>
```

- `<type>`: 이슈 타입(`feat`, `fix` 등)
- `<scope>`(선택): 해당 커밋의 영향을 받는 모듈 혹은 영역
- `<subject>`: 50자 내외의 변경 사항 간략하게 서술

```text
# 예시
feat(auth): JWT 리프레쉬 토큰 재발급 로직 추가

- 리프레쉬 토큰을 활용한 재발급 로직과 엔드포인트 추가
```

## Pull Request

- PR 템플릿을 활용하여 작성
- AI 코드 리뷰어의 리뷰를 활용