package com.tikit.rag.support;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** PostgreSQL tests use a unique schema, never the application's existing tables. */
@TestConfiguration(proxyBeanMethods = false)
@PropertySource(value = "classpath:secrets.properties", ignoreResourceNotFound = true)
@PropertySource("classpath:application-rag-test.properties")
public class PostgresRagTestConfiguration {

    @Bean(destroyMethod = "close")
    IsolatedPostgresDataSource dataSource(Environment environment) throws SQLException {
        String prefix = "rag.test.datasource.";
        return new IsolatedPostgresDataSource(
                environment.getRequiredProperty(prefix + "url"),
                environment.getRequiredProperty(prefix + "username"),
                environment.getRequiredProperty(prefix + "password"),
                environment.getRequiredProperty(prefix + "sslmode"),
                environment.getRequiredProperty(prefix + "sslrootcert"));
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.tikit.rag.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "create-drop",
                "hibernate.hbm2ddl.halt_on_error", "true"));
        return factory;
    }

    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource source) {
        return new JdbcTemplate(source);
    }

    public static final class IsolatedPostgresDataSource extends DriverManagerDataSource implements AutoCloseable {
        private final String schemaName = "rag_test_" + UUID.randomUUID().toString().replace("-", "");

        IsolatedPostgresDataSource(String url, String username, String password,
                                  String sslmode, String sslrootcert) throws SQLException {
            super(requirePostgresUrl(url), username, password);
            var properties = new Properties();
            properties.setProperty("connectTimeout", "10");
            properties.setProperty("socketTimeout", "30");
            properties.setProperty("sslmode", sslmode);
            if (!sslrootcert.isBlank()) {
                properties.setProperty("sslrootcert", sslrootcert);
            }
            setConnectionProperties(properties);
            try (var connection = super.getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schemaName);
            }
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
                // A single explicit search_path prevents fallback to public for missing tables.
                connection.setSchema(schemaName);
                return connection;
            } catch (SQLException exception) {
                connection.close();
                throw exception;
            }
        }

        @Override
        public void close() throws SQLException {
            // The name is generated internally; only this test context's schema is removed.
            try (var connection = super.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
            }
        }

        private static String requirePostgresUrl(String url) {
            if (!url.startsWith("jdbc:postgresql:")) {
                throw new IllegalArgumentException("RAG 통합 테스트는 PostgreSQL 연결이 필요합니다.");
            }
            return url;
        }
    }
}
