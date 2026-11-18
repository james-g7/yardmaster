package yardmaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Default setup: SQLite file plus a real Valkey. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"management.server.port=0", "YARDMASTER_MASTER_KEY=" + TestKeys.MASTER_KEY})
@Testcontainers
@DirtiesContext // close the app so Windows can delete the SQLite temp file
class SqliteStartupIT {

  @TempDir static Path dataDir;

  @Container
  @ServiceConnection(name = "redis")
  static GenericContainer<?> valkey =
      new GenericContainer<>("valkey/valkey:8-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void sqlite(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dataDir.resolve("test.db"));
  }

  @Value("${local.server.port}")
  int port;

  @Autowired JdbcTemplate jdbc;

  @Test
  void startsMigratesAndIsReady() throws Exception {
    assertThat(HttpChecks.get(port, "/healthz").body()).isEqualTo("ok");
    assertThat(HttpChecks.get(port, "/readyz").statusCode()).isEqualTo(200);
    Integer tables =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'usage_records'",
            Integer.class);
    assertThat(tables).isEqualTo(1);
  }
}
