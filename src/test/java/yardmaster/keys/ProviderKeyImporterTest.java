package yardmaster.keys;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import yardmaster.keys.ProviderKeyImporter.Outcome;
import yardmaster.providers.ProviderProperties;

/** Runs the importer against a real SQLite file with the real migrations. No Docker needed. */
class ProviderKeyImporterTest {

  private static final String BASE_URL = "http://localhost:9999/v1";
  private static final SecureRandom RANDOM = new SecureRandom();

  @TempDir Path dir;

  private JdbcTemplate jdbc;
  private DataSourceTransactionManager transactions;
  private KeyCipher cipher;

  @BeforeEach
  void migrate() {
    var dataSource =
        new DriverManagerDataSource("jdbc:sqlite:" + dir.resolve("test.db") + "?foreign_keys=on");
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    jdbc = new JdbcTemplate(dataSource);
    transactions = new DataSourceTransactionManager(dataSource);
    cipher = new KeyCipher(randomMasterKey());
  }

  @Test
  void doesNothingWhenTheVariableIsMissingOrBlank() {
    run(null);
    run("  ");

    assertThat(count("providers")).isZero();
    assertThat(count("provider_keys")).isZero();
  }

  @Test
  void firstRunAddsTheProviderAndAnEncryptedKey() {
    run("sk-test-key-123");

    assertThat(jdbc.queryForMap("SELECT owner_id, type, base_url FROM providers"))
        .containsEntry("owner_id", "system")
        .containsEntry("type", "openai")
        .containsEntry("base_url", BASE_URL);
    var row =
        jdbc.queryForMap(
            "SELECT owner_id, provider_id, encrypted_key, display_name, enabled, abilities"
                + " FROM provider_keys");
    assertThat(row)
        .containsEntry("owner_id", "system")
        .containsEntry("provider_id", jdbc.queryForObject("SELECT id FROM providers", String.class))
        .containsEntry("display_name", "openai-env")
        .containsEntry("abilities", "{}");
    assertThat(jdbc.queryForObject("SELECT enabled FROM provider_keys", Boolean.class)).isTrue();
    String encrypted = (String) row.get("encrypted_key");
    assertThat(encrypted).doesNotContain("sk-test-key-123");
    assertThat(cipher.decrypt(encrypted)).isEqualTo("sk-test-key-123");
  }

  @Test
  void runningAgainWithTheSameKeyChangesNothing() {
    assertThat(importer(null).importKey("sk-test-key-123")).isEqualTo(Outcome.ADDED);
    String before = storedKey();

    assertThat(importer(null).importKey("sk-test-key-123")).isEqualTo(Outcome.UNCHANGED);

    assertThat(count("providers")).isOne();
    assertThat(count("provider_keys")).isOne();
    assertThat(storedKey()).isEqualTo(before);
  }

  @Test
  void changedKeyUpdatesTheSameRow() {
    importer(null).importKey("sk-old-key");
    String id = jdbc.queryForObject("SELECT id FROM provider_keys", String.class);

    assertThat(importer(null).importKey("sk-new-key")).isEqualTo(Outcome.UPDATED);

    assertThat(count("provider_keys")).isOne();
    assertThat(jdbc.queryForObject("SELECT id FROM provider_keys", String.class)).isEqualTo(id);
    assertThat(cipher.decrypt(storedKey())).isEqualTo("sk-new-key");
  }

  @Test
  void keyStoredUnderAnotherMasterKeyIsReplaced() {
    new ProviderKeyImporter(
            new MockEnvironment(),
            jdbc,
            transactions,
            new KeyCipher(randomMasterKey()),
            properties(BASE_URL))
        .importKey("sk-test-key-123");

    assertThat(importer(null).importKey("sk-test-key-123")).isEqualTo(Outcome.UPDATED);

    assertThat(count("provider_keys")).isOne();
    assertThat(cipher.decrypt(storedKey())).isEqualTo("sk-test-key-123");
  }

  @Test
  void stripsSurroundingWhitespaceFromTheVariable() {
    run(" sk-test-key-123\n");

    assertThat(cipher.decrypt(storedKey())).isEqualTo("sk-test-key-123");
  }

  @Test
  void reusesTheExistingProviderAndUpdatesItsBaseUrl() {
    Timestamp old = yesterday();
    jdbc.update(
        "INSERT INTO providers (id, owner_id, type, base_url, created_at, updated_at)"
            + " VALUES ('p1', 'system', 'openai', 'https://old.example/v1', ?, ?)",
        old,
        old);

    importer(null).importKey("sk-test-key-123");

    assertThat(count("providers")).isOne();
    assertThat(jdbc.queryForMap("SELECT base_url FROM providers WHERE id = 'p1'"))
        .containsEntry("base_url", BASE_URL);
    assertThat(jdbc.queryForObject("SELECT provider_id FROM provider_keys", String.class))
        .isEqualTo("p1");
    assertThat(providerUpdatedAt())
        .isAfter(old);
  }

  @Test
  void leavesAnExistingProviderWithTheSameBaseUrlAlone() {
    Timestamp old = yesterday();
    jdbc.update(
        "INSERT INTO providers (id, owner_id, type, base_url, created_at, updated_at)"
            + " VALUES ('p1', 'system', 'openai', ?, ?, ?)",
        BASE_URL,
        old,
        old);

    importer(null).importKey("sk-test-key-123");

    assertThat(providerUpdatedAt())
        .isEqualTo(old);
  }

  /** SQLite keeps milliseconds, so the test value must not have more. */
  private static Timestamp yesterday() {
    return Timestamp.from(Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS));
  }

  private Timestamp providerUpdatedAt() {
    return jdbc.queryForObject("SELECT updated_at FROM providers WHERE id = 'p1'", Timestamp.class);
  }

  private void run(String value) {
    importer(value).run(new DefaultApplicationArguments());
  }

  private ProviderKeyImporter importer(String value) {
    var env = new MockEnvironment();
    if (value != null) {
      env.setProperty(ProviderKeyImporter.ENV_VAR, value);
    }
    return new ProviderKeyImporter(env, jdbc, transactions, cipher, properties(BASE_URL));
  }

  private String storedKey() {
    return jdbc.queryForObject("SELECT encrypted_key FROM provider_keys", String.class);
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  private static ProviderProperties properties(String baseUrl) {
    return new ProviderProperties(
        new ProviderProperties.OpenAi(baseUrl, Duration.ofSeconds(5), Duration.ofSeconds(60)));
  }

  private static MasterKey randomMasterKey() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return MasterKey.from(Map.of(MasterKey.ENV_VAR, Base64.getEncoder().encodeToString(bytes)));
  }
}
