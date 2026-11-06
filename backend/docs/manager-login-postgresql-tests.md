# MANAGER 생성·로그인 PostgreSQL 검증

`AdminManagerLoginPostgresqlTest`는 `secrets.properties`의 `local` 프로필 DB에 연결합니다.
`MANAGER_DB_VERIFY=true`를 명시한 경우에만 실행하며, 연결 실패는 테스트 실패로 처리합니다.

```powershell
$env:MANAGER_DB_VERIFY = 'true'
try {
    .\gradlew.bat test --tests 'com.tikit.admin.controller.AdminManagerLoginPostgresqlTest'
} finally {
    Remove-Item Env:MANAGER_DB_VERIFY -ErrorAction SilentlyContinue
}
```

## 검증 범위

- 실제 생성 API, JPA 저장·조회, BCrypt, Spring Security 인증, JWT 서명·검증을 사용합니다.
- 생성 후 영속성 컨텍스트를 비워 로그인 시 PostgreSQL에서 계정을 다시 읽습니다.
- 이메일 대소문자·공백에 관계없이 로그인하고 MANAGER 역할의 토큰을 발급하는지 확인합니다.
- 생성한 계정의 내 정보 조회는 200, 관리자 전용 API 접근은 403인지 확인합니다.
- 잘못된 비밀번호는 401, 정지·탈퇴된 계정의 로그인은 403인지 확인합니다.
- 실제 감사 로그 저장과 처리 시각 기록을 확인합니다.
- 생성한 계정을 기존 관리자 목록·상세·MANAGER 필터 API로 조회하고, 정지·해제 API 처리와 로그인 차단·복구를 확인합니다.
- 감사 로그 저장을 임시 테이블의 CHECK 제약으로 실패시켜, 신규 계정 INSERT가 수행된 뒤 서비스 트랜잭션에서 함께 롤백되는지 별도 트랜잭션으로 조회합니다.
- 실패 제약을 제거한 뒤 같은 요청이 계정·감사 로그를 모두 커밋하는지 확인합니다.
- Redis의 RefreshTokenRepository만 모킹합니다. 실제 Redis 저장·TTL은 검증하지 않습니다.

## 데이터 격리

- Flyway는 비활성화하고 Hibernate는 스키마 검증만 수행합니다.
- public.members와 public.audit_logs 구조를 복제한 PostgreSQL 임시 테이블을 사용합니다.
- 쓰기 전 테이블과 ID 시퀀스가 임시 스키마에 속하는지 확인합니다.
- JPA SQL은 StatementInspector로 pg_temp.members와 pg_temp.audit_logs를 사용하도록 고정합니다.
- 로그인·관리 흐름 테스트는 테스트 트랜잭션에서 실행하고 롤백 후 임시 테이블 제거를 확인합니다.
- 실패 롤백 테스트는 테스트의 외부 트랜잭션을 비활성화하고, 단일 연결 풀의 ON COMMIT PRESERVE ROWS 임시 테이블을 사용합니다. 실제 서비스가 트랜잭션을 시작·종료하며 그 뒤 별도 조회로 결과를 확인합니다.
- 실패 테스트의 임시 테이블은 성공·실패와 관계없이 finally에서 삭제하고 제거 여부를 확인합니다. 이 테스트의 커밋 역시 세션 임시 테이블에만 영향을 줍니다.
- 원본 테이블 데이터와 ID 시퀀스를 변경하지 않으며, 실제 DB에 사용할 계정을 남기지 않습니다.
