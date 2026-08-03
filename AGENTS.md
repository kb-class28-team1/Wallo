AI 챗봇 및 코드 생성기를 위한 전역 프로젝트 규칙
1. 기술 스택 (Technology Stack)
   Frontend: Vue 3 (Composition API), Bootstrap

API & State: Axios, Pinia

Backend: Java 17, Spring Framework 5.3.x (Legacy)

ORM / DB Access: MyBatis

2. 백엔드 코드 생성 절대 규칙 (CRITICAL)
   Spring Boot 사용 금지: 이 프로젝트는 Spring Legacy(5.3.x)입니다. @SpringBootApplication, application.yml 등 Spring Boot 전용 기능이나 어노테이션을 절대 사용하지 마세요.

JPA/Hibernate 사용 금지: 데이터베이스 접근은 오직 MyBatis만 사용합니다. 엔티티(Entity) 클래스에 @Entity, @Id 등의 JPA 어노테이션을 절대 붙이지 마세요.

설정 방식: Spring 설정은 XML이 아닌 Java Config(@Configuration) 기반 설정을 따릅니다.

MyBatis 매퍼: 쿼리는 MyBatis XML 파일에 작성하고, 인터페이스(Mapper)와 연결하여 사용하세요.

3. 프론트엔드 코드 생성 규칙 (CRITICAL)
   Vue 3 문법 강제: 모든 컴포넌트는 Vue 3의 Composition API 및 <script setup> 문법을 사용하여 작성하세요. (Vue 2의 Options API 사용 금지)

UI 스타일링: 화면 레이아웃 및 스타일링은 Bootstrap 프레임워크를 우선적으로 사용하여 빠르고 일관되게 구성하세요.

전역 상태 관리: 로그인 상태, 사용자 자산 정보 등의 전역 상태는 Vuex가 아닌 Pinia를 사용하여 관리하세요.

API 통신 및 에러 핸들링: 백엔드 API와의 통신은 Axios를 사용합니다. 모든 API 호출 시에는 try-catch문을 사용하여 성공 시의 로직뿐만 아니라, 통신 실패(4xx, 5xx) 시 사용자에게 보여줄 에러 처리(alert 등)를 반드시 포함하여 작성하세요.

4. Vite 버전 고정 규칙 (CRITICAL)
   frontend/package.json의 Vite 관련 의존성 버전은 아래 값으로 고정하며, 어떠한 작업에서도 변경하지 마세요.

   - @vitejs/plugin-vue: ^6.0.8
   - vite: ^6.4.3
   - vitest: ^4.1.10
