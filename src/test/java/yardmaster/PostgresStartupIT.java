package yardmaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Several-replica setup: PostgreSQL plus a real Valkey. The same migrations must apply. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0")
@ActiveProfiles("postgres")
@Testcontainers
class PostgresStartupIT {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Container
  @ServiceConnection(name = "redis")
  static GenericContainer<?> valkey =
      new GenericContainer<>("valkey/valkey:8-alpine").withExposedPorts(6379);

  @Value("${local.server.port}")
  int port;

  @Autowired JdbcTemplate jdbc;

  @Autowired DataSource dataSource;

  @Test
  void startsMigratesAndIsReady() throws Exception {
    assertThat(HttpChecks.get(port, "/readyz").statusCode()).isEqualTo(200);
    Integer tables =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'usage_records'",
            Integer.class);
    assertThat(tables).isEqualTo(1);
  }

  @Test
  void usageRecordsHasNoPromptOrReplyColumns() throws Exception {
    try (Connection conn = dataSource.getConnection()) {
      UsageRecordsColumns.assertNoContentColumns(conn);
    }
  }
}
