# AGENTS.md

## 1. 프로젝트 개요
이 프로젝트는 **견적 작성 업무 자동화 시스템**입니다.

- Backend: Java 17, Spring Boot 3.x, Spring Data JPA, MySQL 기반 REST API
- Frontend: Python 3.10+, **PyQt5** 기반 데스크톱 GUI 클라이언트
- 핵심 기능: 견적 정보 자동 생성(AI/Scraping), 회원 관리(Kakao), 결제(TossPayments/Brandpay)

## 2. 공통 작업 원칙
모든 에이전트는 아래 원칙을 기본값으로 준수합니다.

- 코드 스타일: Java는 Google Java Style, Python은 PEP8 준수
- 문서화 및 주석 (Documentation):
  - 언어: 모든 주석과 Javadoc/Docstring은 한국어로 작성한다.
  - 원칙: 코드를 그대로 읽는 주석(e.g., "ID로 멤버 조회")은 금지한다. 대신 "왜 이 로직이 필요한지", "어떤 비즈니스 규칙 때문인지" 의도와 맥락을 설명한다.
  - 형식:
    - Java: Public Class/Method에는 Javadoc(`/** ... */`)을 필수적으로 작성한다.
    - Python: 모듈 상단과 함수 내부에 Docstring(`""" ... """`)을 작성한다.
  - TODO: 구현되지 않은 기능이나 개선이 필요한 부분은 `// TODO: [내용]` 형태로 남긴다.
- 보안: API 키/DB 비밀번호/JWT 시크릿 등 민감정보 하드코딩 금지
- 설정 관리: 환경값은 `application.yml`(백엔드), `config.ini`(프론트엔드)로 관리
- 변경 최소화: 요청 범위 외 파일/로직은 수정하지 않음
- 예외 처리:
  - 백엔드는 `GlobalExceptionHandler`를 통한 일관된 에러 응답 유지
  - 프론트엔드는 예외 시 앱 종료 대신 사용자 알림(QMessageBox) 우선
- 로깅: 장애 재현 가능하도록 핵심 입력/분기/실패 원인을 로그에 남김(민감정보 제외)

## 3. 저장소 구조
- `/backend`
  - `src/main/java/com/backend/domain`: 도메인 비즈니스 로직(Generation, Member, Payment 등)
  - `src/main/java/com/backend/global`: 공통 설정(Auth, Config, Exception)
  - `src/test/java/com/backend`: 통합/단위 테스트
- `/frontend`
  - `main.py`: 앱 진입점
  - `login_window.py`, `main_window.py`, `payment_window.py`: UI
  - `api_worker.py`: 비동기 API 호출(QThread)
  - `auto_input_manager.py`: 자동 입력 로직
  - `config.ini`: 사용자/환경 설정
- `/docs`
  - `DTO 필드에 final 키워드를 사용하는 이유.md`
  - `Jenkins와 GitHub Actions.md`

## 4. 실행 및 검증 명령
작업 후 아래 검증을 기본으로 수행합니다.

- 백엔드 테스트: `cd backend && ./gradlew test`
- 백엔드 실행: `cd backend && ./gradlew bootRun`
- 프론트엔드 실행: `cd frontend && python main.py`

## 5. 커밋 및 PR 규칙
- 커밋 메시지 형식: `type(scope): 한국어 요약`
- 권장 type: `feat`, `fix`, `refactor`, `chore`, `test`, `docs`
- 권장 scope: `backend`, `frontend`, `infra`, `docs`
- 예시:
  - `feat(frontend): 인증정보 자동 입력 로직 추가`
  - `fix(backend): 관리자 권한 필터 예외 처리 보강`

PR 전 체크리스트:
- 백엔드 변경 시 `./gradlew test` 통과 확인
- 보안 영향(JWT/Auth/결제/시크릿) 검토
- 회귀 위험 지점(예외 처리, 동시성, 외부 API 스펙) 점검
- 환경 파일(`application.yml`, `config.ini`)에 민감정보 커밋 여부 확인

## 6. 영역별 구현 가이드

### Backend 가이드
- 도메인 간 의존성 최소화(`domain` 내부 결합도 관리)
- `GlobalExceptionHandler` + `ErrorResult` 형태의 응답 일관성 유지
- DB 접근 시 N+1, 인덱스, 불필요한 I/O를 항상 점검
- 결제 수정 시 `TossConfirmResponseDto` 포함 외부 API 필드 스펙을 우선 기준으로 개발
- 생성 요청 DTO(`GenerateElectronicRequest` 등)는 필수값 검증(`@Valid`, `@NotBlank`) 유지

DTO 작성 정책:
- 신규 DTO는 가능하면 불변 구조(예: final 필드 + 생성자 기반) 우선
- 기존 mutable DTO를 무조건 교체하지 않고, 영향 범위를 검토해 단계적으로 개선
- 참고 문서: `docs/DTO 필드에 final 키워드를 사용하는 이유.md`

### Frontend 가이드
- API 호출은 반드시 `api_worker.py` 기반 비동기 처리로 UI 프리징 방지
- `login_window.py` -> `main_window.py` 전환에서 상태(access token) 전달을 명확히 유지
- 자동 입력 로직은 중단 가능성(ESC 등), 포커스 이탈, 클립보드 실패를 고려해 방어적으로 구현
- 사용자 설정은 `config.ini`로 관리하고 기본값/예외값 처리 로직 포함

### Automation/AI 가이드
- `ScrapingService`는 대상 DOM 변경을 고려한 방어 코드(선택자 실패 대비) 작성
- `GeminiService` 호출은 타임아웃/재시도/실패 로그 정책을 명시적으로 유지
- 생성/스크래핑 실패는 관리자 로그 흐름(`AdminLogController`)으로 추적 가능해야 함

### QA/Security 가이드
- 최소 확인 테스트: `BackendApplicationTests` + 변경 도메인 테스트
- 보안 우선 검토: `JwtAuthenticationFilter`, `SecurityConfig`, 인증/인가 경계
- 배포 파이프라인 참고: `docs/Jenkins와 GitHub Actions.md`
- `.gitignore` 대상(환경/비밀 파일) 커밋 여부 상시 확인

## 7. 에이전트 역할 정의

### Spring_Architect
- 역할: 안정적이고 확장 가능한 백엔드 API 구현
- 집중: Security/JPA/예외 처리/쿼리 성능

### Python_GUI_Dev
- 역할: 사용자 친화적이고 끊김 없는 데스크톱 앱 구현
- 집중: PyQt5 UI/스레드 처리/설정 관리/실사용 안정성

### Automation_Engineer
- 역할: G2B 자동화 및 생성형 AI 연계 품질 확보
- 집중: 스크래핑 내구성/AI 실패 대응/운영 로그 추적성

### QA_Sec_Ops
- 역할: 테스트/보안/배포 파이프라인 품질 게이트 유지
- 집중: 회귀 방지, 취약점 점검, 시크릿/환경 파일 관리
