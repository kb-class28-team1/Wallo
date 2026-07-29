# Wallo 전역 프로젝트 규칙

이 파일의 규칙은 저장소 루트와 모든 하위 디렉터리에 적용한다.
AI 챗봇과 코드 생성기는 코드 작성, 수정, 리뷰, 예제 제시 시 아래 규칙을 반드시 준수한다.

## 기술 스택

### Frontend

- Vue 3
- Composition API
- `<script setup>`
- Bootstrap
- Axios
- Pinia

### Backend

- Java 17
- Spring Framework 5.3.x 기반 Spring Legacy
- Apache Tomcat 9
- Java Config
- MyBatis

## 규칙 우선순위 및 사전 승인

- 이 문서의 규칙과 사용자가 별도로 전달한 프로젝트 조건을 모든 구현 판단보다 최우선으로 적용한다.
- 규칙을 위반하거나 기존 기술 스택 및 조건을 변경해야 하는 상황에서는 작업을 진행하지 말고 먼저 사용자에게 이유와 영향을 설명한 후 승인을 요청한다.
- 새로운 프레임워크, 라이브러리, 인프라, 개발 패턴 또는 설정 방식을 추가해야 하는 경우에도 구현하거나 의존성을 추가하기 전에 반드시 사용자에게 확인한다.
- 사용자 승인 없이 이 규칙의 예외를 임의로 만들거나 대체 기술을 도입하지 않는다.

## 백엔드 필수 규칙

1. Spring Boot를 사용하지 않는다.
   - `@SpringBootApplication`을 사용하지 않는다.
   - `application.yml` 및 Spring Boot 자동 설정 방식은 사용하지 않는다.
   - `application.properties`는 Spring Legacy의 일반 속성 파일로만 사용할 수 있으며, Java Config에서 명시적으로 불러온다.
   - Spring Boot 전용 기능, 자동 설정, 스타터 의존성 및 어노테이션을 추가하지 않는다.

2. JPA와 Hibernate를 사용하지 않는다.
   - 데이터베이스 접근은 MyBatis만 사용한다.
   - 모델, DTO, VO 등에 `@Entity`, `@Id`, `@GeneratedValue` 등 JPA 어노테이션을 붙이지 않는다.
   - JPA Repository를 생성하지 않는다.

3. Spring 설정은 Java Config를 사용한다.
   - 설정 클래스는 `@Configuration` 기반으로 작성한다.
   - 새로운 Spring XML 설정 파일을 만들지 않는다.

4. MyBatis 사용 방식을 준수한다.
   - SQL 쿼리는 MyBatis Mapper XML 파일에 작성한다.
   - Java Mapper 인터페이스와 Mapper XML을 연결하여 사용한다.
   - Java 어노테이션이나 서비스/DAO 클래스에 SQL을 직접 작성하지 않는다.

5. 서버와 배포 환경은 Apache Tomcat 9을 기준으로 한다.
   - 백엔드는 외부 Tomcat 9에 배포 가능한 Spring Legacy 애플리케이션으로 구성한다.
   - Tomcat 10 이상 또는 Jakarta EE 전용 API를 사용하지 않는다.
   - Servlet 관련 코드는 Tomcat 9과 호환되는 `javax.servlet` API를 사용한다.
   - 임베디드 서버나 Spring Boot 실행 방식을 도입하지 않는다.

## 프론트엔드 필수 규칙

1. 모든 Vue 컴포넌트는 Vue 3 Composition API와 `<script setup>` 문법으로 작성한다.
   - Vue 2 문법을 사용하지 않는다.
   - Options API의 `data`, `methods`, `computed`, `mounted` 옵션을 사용하지 않는다.

2. UI 레이아웃과 스타일링은 Bootstrap을 우선 사용한다.
   - Bootstrap의 그리드, 유틸리티 및 컴포넌트를 먼저 활용하여 일관된 UI를 구성한다.
   - Bootstrap으로 충분히 구현 가능한 스타일을 불필요하게 중복 작성하지 않는다.

3. 전역 상태는 Pinia로 관리한다.
   - 로그인 상태, 사용자 정보, 사용자 자산 정보 등 여러 화면에서 공유되는 상태는 Pinia Store에 둔다.
   - Vuex를 사용하지 않는다.

4. API 통신은 Axios를 사용한다.
   - 모든 API 호출은 `try-catch`로 처리한다.
   - 성공 처리뿐 아니라 4xx, 5xx 및 네트워크 오류 처리도 반드시 구현한다.
   - 실패 시 사용자가 원인을 인지할 수 있도록 `alert` 또는 화면 내 오류 메시지를 제공한다.

## 디렉터리 구조 규칙

### 저장소 루트

```text
Wallo/
├─ CLAUDE.md
├─ AGENTS.md
├─ backend/
├─ frontend/
├─ database/mysql/
└─ README.md
```

### Backend

- Java 패키지 루트는 `backend/src/main/java/com/wallo/`를 사용한다.
- 최상위 도메인은 `config`, `common`, `auth`, `user`, `asset`, `ai`, `goal`, `spending`, `product`, `report`, `challenge`, `external`로 구분한다.
- 각 도메인은 필요에 따라 `controller`, `service`, `repository`, `mapper`, `domain`, `dto/request`, `dto/response`, `exception`으로 나눈다.
- 공통 설정 클래스는 `config/` 아래 `RootConfig`, `WebConfig`, `SecurityConfig`, `MyBatisConfig`, `MongoConfig`, `SwaggerConfig`, `CorsConfig`, `SchedulerConfig` 역할에 맞게 배치한다.
- 리소스는 `backend/src/main/resources/` 아래 `mapper`, `prompts`, `logback.xml`, 일반 속성 파일로 구분한다.
- 웹 리소스는 `backend/src/main/webapp/WEB-INF/` 아래에 둔다. 기존 XML 설정이 있더라도 새로운 Spring 설정은 Java Config를 우선한다.
- 테스트 패키지는 실제 메인 패키지인 `com/wallo` 구조를 따른다.

### Frontend

- 프론트엔드 코드는 `frontend/src/` 아래 `api`, `assets`, `components`, `composables`, `layouts`, `router`, `stores`, `utils`, `views`로 구분한다.
- API 모듈은 `api/` 아래 기능별 파일로 분리하고 공통 Axios 인스턴스는 `httpClient.js`에 둔다.
- 화면 컴포넌트는 `views/` 아래 `auth`, `dashboard`, `asset`, `ai`, `product`, `report`, `challenge`, `user` 도메인별로 배치한다.
- 재사용 컴포넌트는 `components/` 아래 `common` 또는 기능별 디렉터리에 둔다. 공통 네비게이션은 `components/navigation/`에 둔다.
- Pinia Store는 `stores/`, 재사용 Composition 로직은 `composables/`, 포맷터와 저장소 및 오류 처리 유틸리티는 `utils/`에 둔다.
- 라우터 설정과 가드는 `router/index.js`, `router/guards.js`로 분리한다.
- 공통 화면 구조는 `layouts/` 아래 `DefaultLayout.vue`, `AuthLayout.vue`, `ChatLayout.vue` 역할에 맞게 배치한다.
- 파일을 편의상 `src/` 루트에 추가하지 말고 역할에 맞는 디렉터리에 배치한다.

### 폴더 생성 및 보고

- 작업에 필요한 표준 폴더가 없다면 해당 작업을 수행할 때 생성한다.
- 아직 사용하지 않는 빈 폴더와 빈 파일을 미리 대량으로 생성하지 않는다.
- 새로 생성한 폴더가 있다면 최종 응답의 맨 마지막에 생성한 폴더와 생성 이유를 알린다.
- `CLAUDE.md`와 `AGENTS.md`의 프로젝트 규칙은 동일하게 유지한다.

## 코드 생성 및 변경 전 확인

- 새 코드를 만들기 전에 위 기술 스택과 금지 사항을 확인한다.
- 기존 코드가 이 규칙과 충돌하더라도, 새 코드와 수정 코드는 이 규칙을 따른다.
- 다른 라이브러리나 패턴을 도입해야 한다면 먼저 기존 기술 스택으로 해결 가능한지 확인하고, 필요할 경우 사용자 승인을 받은 뒤 진행한다.
- 요구사항이 이 규칙과 충돌하면 임의로 우회하거나 작업을 계속하지 말고 충돌 내용을 사용자에게 알린 후 결정을 기다린다.
