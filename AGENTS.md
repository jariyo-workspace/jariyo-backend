# Repository Guidelines

## Project Structure & Module Organization
`src/main/java/com/example/jariyo_backend` contains the Spring Boot application code. `src/main/resources` holds runtime configuration such as [`application.properties`](/home/noobth/github/jariyo-backend/src/main/resources/application.properties). Tests live under `src/test/java/com/example/jariyo_backend`. Build files are at the root: `build.gradle`, `settings.gradle`, and the Gradle wrapper scripts `gradlew` and `gradlew.bat`. Product and process documentation is kept in `jariyo-docs/`, with contributor rules in `jariyo-docs/CONTRIBUTING.md`.

## Documentation First
Before implementing or changing behavior, review the relevant documents in `jariyo-docs/docs/`. Use `01-plan.md` for scope, `02-data-model.md` for entities and relationships, `04-api-spec.md` and `05-frontend-api-guide.md` for request and response contracts, and `06-backend-implementation-guide.md` for backend direction. Treat these files as the source of truth for feature intent. If code behavior, interfaces, or data structures change, update the related documents in the same task.

## Work Reports
At the end of each completed task, add a report file under `./reports`. If the directory does not exist, create it at the project root first. Use `WK_yyyymmdd-title.md` for general work and `TB_yyyymmdd-title.md` for bug fixes or troubleshooting. The `title` must be Korean without spaces, for example `WK_20260715-예약조회API.md` or `TB_20260715-로그인타임아웃.md`.

Write report filenames and report bodies in Korean unless a fixed technical term must stay in English. Every report must include these sections:

- `개요`
- `변경 파일`
- `실행 명령`
- `테스트 결과`
- `이슈`
- `후속 작업`
- `셀프리뷰`

## Final Self-Review
Before considering a task complete, review your own work against these guidelines. Confirm that you checked and updated the relevant `jariyo-docs` documents, followed the branch and commit rules, ran the required tests, created the required report in `./reports`, and recorded remaining risks or follow-up actions in the report's `셀프리뷰` section.

## Build, Test, and Development Commands
Use the Gradle wrapper so the project runs with the pinned toolchain.

- `./gradlew bootRun` starts the Spring Boot app locally.
- `./gradlew test` runs the JUnit 5 test suite.
- `./gradlew build` compiles, tests, and packages the application.
- `./gradlew clean` removes prior build outputs when you need a fresh build.

The project targets Java 17 and resolves dependencies from Maven Central.

## Coding Style & Naming Conventions
Follow the existing Java style in this repository: tabs for indentation, one top-level class per file, and standard Spring Boot annotations. Keep package names lowercase (`com.example.jariyo_backend`), class names in `PascalCase`, and methods and fields in `camelCase`. Name Spring test classes after the class or feature they cover, for example `ReservationServiceTests`.

## Testing Guidelines
Testing is currently based on `spring-boot-starter-test` with JUnit Platform enabled. Place tests under the mirrored package path in `src/test/java`. Prefer focused unit or slice tests for new behavior, and keep `@SpringBootTest` usage for application-level wiring checks. Run `./gradlew test` for every task, including documentation or configuration changes, and record the result in the task report.

## Commit & Pull Request Guidelines
This repository follows the conventions documented in `jariyo-docs/CONTRIBUTING.md`. Do not commit directly to `main`, and never push directly to `master`. Also avoid force pushes, direct merges to protected branches, and history-rewriting Git actions that bypass review unless explicitly requested and approved. Create an issue first, then work on a scoped branch such as `feature/reservation-status-policy` or `fix/api-response-typo`. Use prefixed commit messages like `feat: 예약 엔드포인트 추가`, `fix: 응답 매핑 수정`, or `docs: API 가이드 업데이트`.

PR titles should use `[FEAT]`, `[FIX]`, `[DOCS]`, `[REFACTOR]`, or `[CHORE]`, and each PR should cover one topic, link its issue (`Closes #12`), and include the `jariyo-docs` updates or document references that justify the change.

## Language Rules
Write repository reports, explanations, and documentation updates in Korean by default. Keep fixed technical identifiers such as package names, class names, commands, and commit prefixes in English where needed.
