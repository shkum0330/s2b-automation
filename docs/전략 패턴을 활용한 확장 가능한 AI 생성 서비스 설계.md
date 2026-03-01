## 1. 개요

프로젝트 내에서 LLM을 활용한 텍스트 생성 아키텍처 설계 및 구현 내용을 작성한다. 비즈니스 요구사항의 변화에 유연하게 대응하고, 코드의 유지보수성을 극대화하기 위해 객체 지향 설계 원칙(SOLID)과 디자인 패턴(전략 패턴, 템플릿 메서드 패턴)을 적용한 구조를 소개한다.

## 2. 배경 및 설계 목표

### 2.1. 문제 인식

초기 AI 텍스트 생성 서비스는 특정 AI Provider(예: Gemini)의 API 호출 로직과 비즈니스 로직이 강하게 결합되는 경향이 있었다. 이로 인해 다음과 같은 문제점이 발생할 수 있다.

* **확장성 한계:** 생성 도메인이 추가될 때마다 분기문(`if-else`)이 증가한다.
* **벤더 종속성:** 타 AI 모델(OpenAI, Claude 등)로의 교체 혹은 병행 사용 요구사항 발생 시, 비즈니스 로직 전반을 수정해야 하는 side effect가 존재한다.

### 2.2. 설계 목표

* **관심사 분리:** AI 통신을 담당하는 기반 로직과 프롬프트를 구성하는 도메인 비즈니스 로직을 완벽히 분리한다.
* **개방-폐쇄 원칙 (OCP) 준수:** 기존 코드의 수정 없이 새로운 생성 양식이나 새로운 AI Provider를 추가할 수 있는 유연한 구조를 확립한다.

## 3. 아키텍처 설계

설계 시 "무엇을 생성할 것인가"와 "어떤 도구로 생성할 것인가"를 인터페이스와 추상 클래스를 통해 격리하는 데 중점을 두었다.

### 3.1. AI 통신 계층: 전략 패턴(Strategy Pattern) 적용

특정 AI API 벤더에 종속되지 않도록 통신 규격을 인터페이스로 정의한다.

* **`AiProviderService` (전략 인터페이스):** 텍스트 생성이라는 단일 책임(SRP)을 가지며, 시스템 내 모든 AI 서비스 구현체의 표준 규격이 된다.
* **`GeminiService` (구체화된 전략):** `AiProviderService`를 구현(implements)하며, 실제 WebClient 등을 이용해 Gemini API와 통신하고 응답 데이터를 파싱하는 역할만 수행한다.

### 3.2. 도메인 생성 계층: 템플릿 메서드 패턴 (Template Method) 적용

물품 종류에 따라 프롬프트 작성 방식은 다르나, 전체적인 생성 절차는 동일하다. 이를 추상화하여 중복 코드를 제거한다.

* **`AbstractGenerationService` (추상 클래스):** 생성 작업의 표준 절차(요청 수신 -> 프롬프트 빌드 -> AI API 호출 -> 결과 파싱)을 템플릿 메서드로 정의한다. AI 통신은 주입받은 `AiProviderService` 인터페이스에 위임한다.
* **`GenerationServiceImpl` (구체 클래스):** 도메인에 특화된 프롬프트 생성 로직(`buildPrompt`)과 결과 처리 로직(`parseResponse`)만을 오버라이딩하여 구현한다.

## 4. 핵심 코드 구조 (Implementation)

의존성 주입(DI)을 통해 런타임에 유연하게 의존관계를 맺는 구조로 구현되었다.

```java
// 1. AI 통신 전략 인터페이스 (Strategy)
public interface AiProviderService {
    String generateText(String prompt);
}

// 2. 구체적인 AI 서비스 구현체 (Concrete Strategy)
@Service
public class GeminiService implements AiProviderService {
    @Override
    public String generateText(String prompt) {
        // HTTP 통신 및 Gemini API 응답 파싱 로직 캡슐화
        return responseText;
    }
}

// 3. 비즈니스 로직의 파이프라인 추상화 (Template Method)
public abstract class AbstractGenerationService<T, R> {
    
    // 강한 결합 방지: 인터페이스에 의존
    private final AiProviderService aiProviderService;

    protected AbstractGenerationService(AiProviderService aiProviderService) {
        this.aiProviderService = aiProviderService;
    }

    // Template Method
    public R processGeneration(T request) {
        // 1. 구체 클래스에 위임된 프롬프트 생성 로직
        String prompt = buildPrompt(request);
        
        // 2. 인터페이스를 통한 다형성 호출
        String rawResult = aiProviderService.generateText(prompt);
        
        // 3. 구체 클래스에 위임된 응답 파싱 로직
        return parseResponse(rawResult);
    }

    protected abstract String buildPrompt(T request);
    protected abstract R parseResponse(String rawResult);
}

```

## 5. 기대 효과

1. **유지보수성 및 확장성 극대화:** 새로운 AI 모델(예: `ChatGptService`)을 연동해야 할 경우, `AiProviderService`를 구현하는 클래스를 새로 작성하여 Bean으로 등록하기만 하면 된다. 핵심 비즈니스 로직인 `AbstractGenerationService`나 하위 구현체는 일절 수정할 필요가 없다.
2. **테스트 용이성 향상:**
비즈니스 로직을 단위 테스트할 때, 외부 API인 실제 AI 서버를 호출할 필요 없이 `AiProviderService`를 Mock 객체로 대체하여 빠르고 멱등성 있는 테스트 환경을 구축할 수 있다.
3. **코드 가독성 및 책임 명확화:**
각 클래스가 단일 책임을 가지게 되어, 장애 발생 시 원인 추적(AI API의 응답 문제인지, 내부 프롬프트 빌드 문제인지 등등)이 매우 용이해진다.

## 6. 향후 과제

* **동적 전략 선택 도입:** 다수의 AI Provider Bean이 등록될 경우, 클라이언트의 요청 파라미터나 시스템 부하 상태에 따라 런타임에 동적으로 최적의 AI 구현체를 선택하는 팩토리 패턴 추가 적용을 고려할 수 있다.