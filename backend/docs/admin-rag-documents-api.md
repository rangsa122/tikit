# 관리자 RAG 공통 정책 문서 API (#13)

선행 PR #50의 엔티티·Repository를 사용한다. 이번 변경은 문서 등록·조회와 색인 대기 작업 생성까지다.

모든 요청에 ADMIN Access Token을 `Authorization: Bearer <accessToken>`으로 전달한다.

| 메서드 | 경로 | 결과 |
| --- | --- | --- |
| POST | `/api/v1/admin/rag/documents` | 201, 상세 데이터와 Location 헤더 |
| GET | `/api/v1/admin/rag/documents?page=0&size=10` | 200, ID 내림차순 목록 |
| GET | `/api/v1/admin/rag/documents/{id}` | 200, 최신 버전 상세 |

등록 본문:

```json
{"title":"환불 규정","content":"예매 취소 및 환불 정책 본문"}
```

제목은 공백이 아닌 200자 이하 문자열, 본문은 공백이 아닌 문자열이어야 한다.
페이지는 0 이상, 크기는 1~100, 문서 ID는 양수다.

등록 시 POLICY 문서(performanceId=null, latestVersion=1, activeVersion=null),
제목·본문·SHA-256 본문 해시를 가진 최초 버전(QUEUED), 색인 작업(QUEUED)을 한 트랜잭션으로 저장한다.
중간에 저장이 실패하면 모두 롤백한다. 목록·상세는 삭제되지 않은 POLICY 문서의 최신 버전을 반환한다.
응답은 공통 `ResponseDTO` 형식을 사용한다.

잘못된 입력은 400, 미인증은 401, MEMBER·MANAGER는 403, 조회 대상이 없는 상세 요청은 404다.
문서 분할·임베딩·벡터 검색·답변 생성·수정·삭제는 후속 작업이다.

## 검증

```powershell
# 입력 검증·실제 JWT 인증과 관리자 권한 (DB 불필요)
.\gradlew.bat test --tests 'com.tikit.rag.*'

# 실제 PostgreSQL 저장·조회·롤백 (별도 임시 스키마 사용)
.\gradlew.bat localDbTest --tests 'com.tikit.rag.controller.AdminRagDocumentIntegrationTest'

# 개인 DB의 실제 Flyway 테이블·외래키 검증 (생성 데이터는 트랜잭션 롤백)
.\gradlew.bat localDbTest --tests 'com.tikit.rag.controller.AdminRagDocumentSchemaTest'
```

PostgreSQL 테스트는 `secrets.properties`의 개인 DB 설정(`DB_HOST`, `DB_PORT`, `LOCAL_DB_NAME`,
`LOCAL_DB_USERNAME`, `LOCAL_DB_PASSWORD`, `DB_CA_CERT`)을 사용한다.
`TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`, `TEST_DB_SSLMODE`, `TEST_DB_SSLROOTCERT`로 덮어쓸 수 있다.
로컬 비 TLS 테스트 DB는 `TEST_DB_SSLMODE=disable`로 실행한다. 접속 정보는 커밋하지 않는다.

`AdminRagDocumentIntegrationTest`는 컨텍스트마다 고유한 `rag_api_test_<UUID>` 스키마를 생성하고 종료 시 정리한다.
기존 public 테이블과 격리하며 JPA 매핑으로 테이블을 생성한다.
이 테스트는 실제 트랜잭션과 CHECK 제약 위반 시 롤백을 검증하지만, 전체 Flyway·FK·pgvector 검증은 포함하지 않는다.
`AdminRagDocumentSchemaTest`는 local 프로필로 개인 DB의 실제 Flyway 테이블에 저장하며,
문서 최신 버전의 지연 외래키도 즉시 검사한다. 검증용 회원·문서·버전·작업은 모두 롤백한다.
pgvector 인덱싱은 이번 테스트 범위에 포함하지 않는다.

PR #50을 먼저 dev에 병합한 뒤, 이 브랜치에 최신 dev를 반영하고 dev 대상으로 API PR을 올린다.
#50 병합 전에는 dev 비교에 선행 엔티티·Repository 10개 파일도 포함된다.
오래된 `be/feat/13-rag-entities`를 base로 지정하면 최신 dev의 다른 변경까지 비교에 나타나므로 사용하지 않는다.
