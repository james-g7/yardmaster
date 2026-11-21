package yardmaster.keys;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationContextInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import yardmaster.TestKeys;
import yardmaster.YardmasterApplication;
import yardmaster.fake.FakeOpenAi;

/**
 * Fails if the provider key from the environment is ever stored in plain text, logged, or returned
 * in a response. Runs the whole app with a fake key and searches every place it could end up.
 */
@Testcontainers
class ProviderKeyLeakIT {

  /** Distinctive, so a match can only be the key itself. */
  private static final String FAKE_KEY = "test-key-123-leak-check";

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @TempDir Path dataDir;

  @Container
  static GenericContainer<?> valkey =
      new GenericContainer<>("valkey/valkey:8-alpine").withExposedPorts(6379);

  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final FakeOpenAi openAi = new FakeOpenAi(FAKE_KEY);
  private ConfigurableApplicationContext app;

  @AfterEach
  void stop() {
    if (app != null) {
      app.close();
    }
    openAi.close();
    root().detachAppender(logs);
  }

  @Test
  void keyIsNeverStoredInPlainTextLoggedOrReturned() throws Exception {
    // Check after the first start too: a restart could re-encrypt a row that was stored in plain.
    app = start();
    assertNotInDatabase();
    app.close();

    // Start again, so the "unchanged" path on restart is covered too.
    app = start();
    assertNotInDatabase();
    assertNotInResponses();
    assertNotInLogs();
  }

  private void assertNotInDatabase() {
    var jdbc = app.getBean(JdbcTemplate.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_keys", Integer.class))
        .as("the importer stored the key")
        .isOne();
    // Every column of every table, not just provider_keys.
    List<String> tables =
        jdbc.queryForList(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
            String.class);
    for (String table : tables) {
      for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM " + table)) {
        row.forEach(
            (column, value) ->
                assertThat(String.valueOf(value))
                    .as("%s.%s", table, column)
                    .doesNotContain(FAKE_KEY));
      }
    }
  }

  private void assertNotInResponses() throws IOException, InterruptedException {
    int publicPort = port("local.server.port");
    int adminPort = port("local.management.port");
    var responses = new ArrayList<HttpResponse<String>>();
    for (String path : List.of("/healthz", "/readyz", "/v1/models", "/does-not-exist")) {
      responses.add(send(get(publicPort, path)));
    }
    // Answers 404 until the chat endpoint lands (#26); from then on it reaches the fake OpenAI.
    responses.add(
        send(
            HttpRequest.newBuilder(uri(publicPort, "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"model\":\"gpt-4o-mini\",\"stream\":true,"
                            + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .build()));
    for (String path :
        List.of(
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus",
            "/actuator/env",
            "/actuator/configprops")) {
      responses.add(send(get(adminPort, path)));
    }

    assertThat(responses.get(0).body()).as("the public port answers").isEqualTo("ok");
    for (var response : responses) {
      String where = response.request().method() + " " + response.uri();
      assertThat(response.body()).as(where + " body").doesNotContain(FAKE_KEY);
      assertThat(response.headers().map().toString())
          .as(where + " headers")
          .doesNotContain(FAKE_KEY);
    }
  }

  private void assertNotInLogs() {
    assertThat(logs.list)
        .as("log capture works")
        .anyMatch(e -> e.getFormattedMessage().contains("OpenAI key from"));
    for (ILoggingEvent event : logs.list) {
      assertThat(event.getFormattedMessage()).as(event.getLoggerName()).doesNotContain(FAKE_KEY);
      assertThat(String.valueOf(event.getMDCPropertyMap())).doesNotContain(FAKE_KEY);
      for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
        assertThat(String.valueOf(t.getMessage()))
            .as(event.getLoggerName())
            .doesNotContain(FAKE_KEY);
      }
    }
  }

  /**
   * Settings go in as command-line arguments so they override application.yml. Logging for the app
   * and for JDBC (which logs SQL parameters at TRACE) is turned all the way up.
   */
  private ConfigurableApplicationContext start() {
    return new SpringApplicationBuilder(YardmasterApplication.class)
        .listeners(captureLogsOnceLoggingIsSetUp())
        .run(
            "--spring.datasource.url=jdbc:sqlite:" + dataDir.resolve("yardmaster.db"),
            "--spring.data.redis.host=" + valkey.getHost(),
            "--spring.data.redis.port=" + valkey.getMappedPort(6379),
            "--server.port=0",
            "--management.server.port=0",
            "--yardmaster.providers.openai.base-url=" + openAi.baseUrl(),
            "--logging.level.yardmaster=TRACE",
            "--logging.level.org.springframework.jdbc=TRACE",
            "--" + MasterKey.ENV_VAR + "=" + TestKeys.MASTER_KEY,
            "--" + ProviderKeyImporter.ENV_VAR + "=" + FAKE_KEY);
  }

  /** Spring Boot resets Logback while starting, so the appender is attached after that. */
  private ApplicationListener<ApplicationContextInitializedEvent> captureLogsOnceLoggingIsSetUp() {
    return event -> {
      if (!logs.isStarted()) {
        logs.start();
      }
      root().addAppender(logs);
    };
  }

  private int port(String property) {
    return Integer.parseInt(app.getEnvironment().getRequiredProperty(property));
  }

  private static HttpResponse<String> send(HttpRequest request)
      throws IOException, InterruptedException {
    return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static HttpRequest get(int port, String path) {
    return HttpRequest.newBuilder(uri(port, path)).GET().build();
  }

  private static URI uri(int port, String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private static Logger root() {
    return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
  }
}
