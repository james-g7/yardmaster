package yardmaster.providers;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import yardmaster.TestKeys;
import yardmaster.fake.FakeOpenAi;
import yardmaster.keys.KeyCipher;
import yardmaster.keys.MasterKey;

/**
 * Runs the OpenAiClient tests using {@link FakeOpenAi}; no real API calls are made.
 */
public class OpenAiClientTest {
  @TempDir
  Path dir;
  private FakeOpenAi fake;
  private OpenAiClient client;
  private JdbcTemplate jdbc;
  private KeyCipher cipher;

  @BeforeEach
  void setup() {
    var dataSource =
        new DriverManagerDataSource(
            "jdbc:sqlite:" + dir.resolve("test.db") + "?foreign_keys=on");
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    jdbc = new JdbcTemplate(dataSource);
    cipher = new KeyCipher(MasterKey.from(Map.of(MasterKey.ENV_VAR, TestKeys.MASTER_KEY)));
    // Random per test, so the client can only send it by reading it from the database.
    String apiKey = "sk-test-" + UUID.randomUUID();
    fake = new FakeOpenAi(apiKey);
    Timestamp now = Timestamp.from(Instant.now());
    String providerId = UUID.randomUUID().toString();

    jdbc.update(
        "INSERT INTO providers (id, owner_id, type, base_url, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        providerId, "test", ProviderProperties.OpenAi.TYPE, fake.baseUrl() + "/v1", now, now);

    jdbc.update(
        "INSERT INTO provider_keys (id, owner_id, provider_id, encrypted_key, display_name,"
            + " enabled, abilities, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        UUID.randomUUID().toString(), "test", providerId, cipher.encrypt(apiKey),
        "test-key", true, "{}", now, now);

    ProviderProperties properties = new ProviderProperties(
        new ProviderProperties.OpenAi(
            fake.baseUrl() + "/v1", Duration.ofSeconds(2), Duration.ofSeconds(5)
        )
    );
    client = new OpenAiClient(properties, jdbc, cipher);
  }

  @AfterEach
  void close() {
    fake.close();
  }

  @Test
  void streamsReplyLineByLine() {
    List<String> lines;
    try (Stream<String> stream = client.stream("test".getBytes(UTF_8))) {
      lines = stream.filter(line -> line.startsWith("data:")).toList();
    }
    assertThat(lines).hasSize(FakeOpenAi.CONTENT_PARTS.size() + 2);
    assertThat(lines).last().isEqualTo("data: [DONE]");
  }

  @Test
  void non200BecomesProviderException() {
    fake.setMode(FakeOpenAi.Mode.FAIL_BEFORE_FIRST_BYTE);
    byte[] body = "test".getBytes(UTF_8);

    assertThatThrownBy(() -> client.stream(body))
        .isInstanceOfSatisfying(ProviderException.class, e -> {
          assertThat(e.statusCode()).isEqualTo(500);
          assertThat(e.provider()).isEqualTo(ProviderProperties.OpenAi.TYPE);
        });
    assertThatThrownBy(() -> client.complete(body))
        .isInstanceOfSatisfying(ProviderException.class, e -> {
          assertThat(e.statusCode()).isEqualTo(500);
          assertThat(e.provider()).isEqualTo(ProviderProperties.OpenAi.TYPE);
        });
  }

  @Test
  void wrongKeyIsRejected() {
    jdbc.update("UPDATE provider_keys SET encrypted_key = ?", cipher.encrypt("sk-wrong"));

    assertThatThrownBy(() -> client.complete("test".getBytes(UTF_8)))
        .isInstanceOfSatisfying(
            ProviderException.class, e -> assertThat(e.statusCode()).isEqualTo(401));
  }

  @Test
  void returnsWholeReply() {
    fake.setMode(FakeOpenAi.Mode.OK_JSON);
    String response = client.complete("test".getBytes(UTF_8));
    assertThat(response).contains("\"chat.completion\"");
    assertThat(response).contains(String.join("", FakeOpenAi.CONTENT_PARTS));
  }
}
