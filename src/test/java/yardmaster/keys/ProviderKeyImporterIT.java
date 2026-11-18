package yardmaster.keys;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import yardmaster.TestKeys;
import yardmaster.YardmasterApplication;

/** Starts the whole app more than once on one SQLite file and checks the imported key. */
@Testcontainers
class ProviderKeyImporterIT {

  private static final String FAKE_KEY = "test-key-123";

  @TempDir Path dataDir;

  @Container
  static GenericContainer<?> valkey =
      new GenericContainer<>("valkey/valkey:8-alpine").withExposedPorts(6379);

  @Test
  void startingTwiceWithTheSameKeyKeepsOneEncryptedRow() {
    start(FAKE_KEY).close();

    try (var app = start(FAKE_KEY)) {
      var jdbc = app.getBean(JdbcTemplate.class);
      assertThat(count(jdbc, "providers")).isOne();
      assertThat(count(jdbc, "provider_keys")).isOne();
      String stored = storedKey(jdbc);
      assertThat(stored).isNotEqualTo(FAKE_KEY).doesNotContain(FAKE_KEY);
      assertThat(app.getBean(KeyCipher.class).decrypt(stored)).isEqualTo(FAKE_KEY);
    }
  }

  @Test
  void restartingWithAChangedKeyUpdatesTheRow() {
    start(FAKE_KEY).close();

    try (var app = start("test-key-456")) {
      var jdbc = app.getBean(JdbcTemplate.class);
      assertThat(count(jdbc, "provider_keys")).isOne();
      assertThat(app.getBean(KeyCipher.class).decrypt(storedKey(jdbc))).isEqualTo("test-key-456");
    }
  }

  private ConfigurableApplicationContext start(String providerKey) {
    return new SpringApplicationBuilder(YardmasterApplication.class)
        .properties(
            Map.of(
                "spring.datasource.url", "jdbc:sqlite:" + dataDir.resolve("yardmaster.db"),
                "spring.data.redis.host", valkey.getHost(),
                "spring.data.redis.port", String.valueOf(valkey.getMappedPort(6379)),
                "server.port", "0",
                "management.server.port", "0",
                MasterKey.ENV_VAR, TestKeys.MASTER_KEY,
                ProviderKeyImporter.ENV_VAR, providerKey))
        .run();
  }

  private static String storedKey(JdbcTemplate jdbc) {
    return jdbc.queryForObject("SELECT encrypted_key FROM provider_keys", String.class);
  }

  private static int count(JdbcTemplate jdbc, String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }
}
