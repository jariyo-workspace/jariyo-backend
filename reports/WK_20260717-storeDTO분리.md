# 개요
`store` 도메인에서 `StoreQueryService` 내부에 있던 응답 `record`들을 별도 `dto` 패키지로 분리했다.

컨트롤러와 테스트는 새 DTO 타입을 참조하도록 변경했고, 동작에는 변화가 없도록 유지했다.

# 변경 파일
- `src/main/java/com/example/jariyo_backend/domain/store/dto/StoreSummary.java`
- `src/main/java/com/example/jariyo_backend/domain/store/dto/StoreDetail.java`
- `src/main/java/com/example/jariyo_backend/domain/store/dto/ServiceSummary.java`
- `src/main/java/com/example/jariyo_backend/domain/store/dto/ServiceStaffSummary.java`
- `src/main/java/com/example/jariyo_backend/domain/store/dto/StoreMemberSummary.java`
- `src/main/java/com/example/jariyo_backend/domain/store/dto/StoreMemberDetail.java`
- `src/main/java/com/example/jariyo_backend/domain/store/dto/StaffScheduleSummary.java`
- `src/main/java/com/example/jariyo_backend/domain/store/dto/StaffScheduleExceptionSummary.java`
- `src/main/java/com/example/jariyo_backend/domain/store/dto/BusinessHourSummary.java`
- `src/main/java/com/example/jariyo_backend/domain/store/dto/ScheduleExceptionSummary.java`
- `src/main/java/com/example/jariyo_backend/domain/store/dto/StorePolicySummary.java`
- `src/main/java/com/example/jariyo_backend/domain/store/service/StoreQueryService.java`
- `src/main/java/com/example/jariyo_backend/domain/store/controller/StoreController.java`
- `src/test/java/com/example/jariyo_backend/domain/store/controller/StoreControllerTests.java`

# 실행 명령
- `./gradlew test`

# 테스트 결과
- `./gradlew test` 성공

# 이슈
- 기능 변경은 없지만 DTO 파일 수가 늘어났으므로 후속 수정 시 import 정리 비용이 생길 수 있다.

# 후속 작업
- 필요하면 `store` 도메인 응답 DTO 네이밍 규칙을 추가로 정리한다.

# 셀프리뷰
- inner record를 제거해 패키지 구조를 단순하게 만들었다.
- 컴파일 확인과 테스트를 모두 통과했다.
- 이번 변경은 구조 개선용 리팩터링이라 API 계약 자체는 유지했다.
