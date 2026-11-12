package com.tikit.rag.support;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/** 실제 PostgreSQL에 테스트 실행 전용 스키마를 만들어 기존 테이블과 격리한다. */
public final class IsolatedPostgresDataSource extends DelegatingDataSource implements AutoCloseable {
    private final String schema = "rag_api_test_" + UUID.randomUUID().toString().replace("-", "");

    public IsolatedPostgresDataSource(DataSource target) throws SQLException {
        super(target);
        try (var connection = target.getConnection(); var statement = connection.createStatement()) {
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                throw new SQLException("RAG 통합 테스트에는 PostgreSQL이 필요합니다.");
            }
            // schema는 외부 입력이 아닌 영문 접두사와 UUID로만 구성한다.
            statement.execute("CREATE SCHEMA " + schema);
        }
    }

    public String getSchema() {
        return schema;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return useTestSchema(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return useTestSchema(super.getConnection(username, password));
    }

    private Connection useTestSchema(Connection connection) throws SQLException {
        try {
            // public을 검색 경로에 포함하지 않아 기존 테이블로 접근하지 않는다.
            connection.setSchema(schema);
            return connection;
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    @Override
    public void close() throws SQLException {
        // 이 실행에서 직접 생성한 스키마만 정리한다. 로컬/운영 테이블은 건드리지 않는다.
        try (var connection = obtainTargetDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
