package yardmaster.providers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import yardmaster.keys.KeyCipher;

/**
 * Sends chat requests to OpenAI, injects OpenAI properties from
 * {@code ProviderProperties.openai()}.
 *
 * @see ProviderProperties.OpenAi
 */
@Component
public class OpenAiClient {
  private final HttpClient httpClient;
  private final ProviderProperties properties;
  private final JdbcTemplate jdbc;
  private final KeyCipher keyCipher;


  /**
   * Builds an OpenAI client with the given properties.
   *
   * @param properties OpenAI properties auto-injected from {@link ProviderProperties.OpenAi}.
   */
  public OpenAiClient(ProviderProperties properties, JdbcTemplate jdbc, KeyCipher keyCipher) {
    this.properties = properties;
    this.jdbc = jdbc;
    this.keyCipher = keyCipher;
    httpClient = HttpClient.newBuilder()
      .connectTimeout(properties.openai().connectTimeout())
      .build();
  }

  /**
   * Retrieves the OpenAI API key from the database and decrypts it. If several keys are enabled,
   * the oldest one is used, so the choice is the same on every call and on SQLite and PostgreSQL.
   *
   * @return the decrypted OpenAI API key.
   * @throws IllegalStateException if no API key is found.
   *
   * @see KeyCipher
   * @see ProviderProperties.OpenAi
   */
  private String apiKey() {
    List<String> encryptedKey = jdbc.query("SELECT encrypted_key FROM provider_keys "
            + "JOIN providers ON providers.id = provider_keys.provider_id "
            + "WHERE providers.type = ? AND provider_keys.enabled = true "
            + "ORDER BY provider_keys.created_at, provider_keys.id",
            (rs, i) -> rs.getString("encrypted_key"),
             ProviderProperties.OpenAi.TYPE
    );

    if (encryptedKey.isEmpty()) {
      throw new IllegalStateException("No OpenAI API key found");
    }
    return keyCipher.decrypt(encryptedKey.getFirst());
  }

  private HttpRequest buildRequest(byte[] body) {
    return HttpRequest.newBuilder()
        .uri(URI.create(properties.openai().baseUrl() + "/chat/completions"))
        .header("Authorization", "Bearer " + apiKey())
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
        .timeout(properties.openai().readTimeout())
        .build();
  }

  private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
    HttpResponse<T> response;
    try {
      response = httpClient.send(request, bodyHandler);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while calling OpenAI", e);
    }
    return response;
  }

  /**
   * Sends a streaming chat request and returns the reply one line at a time. The caller must
   * close the stream, which also frees the connection.
   *
   * @param body the request body.
   * @return the reply.
   * @throws ProviderException if OpenAI answers with a status other than 200.
   */
  public Stream<String> stream(byte[] body) {
    HttpResponse<Stream<String>> response = send(buildRequest(body),
        HttpResponse.BodyHandlers.ofLines());

    if (response.statusCode() != 200) {
      response.body().close();
      throw new ProviderException(ProviderProperties.OpenAi.TYPE, response.statusCode());
    }
    return response.body();
  }

  /**
   * Sends a non-streaming chat request and returns the whole body.
   *
   * @param body the request body.
   * @return the reply.
   * @throws ProviderException if OpenAI answers with a status other than 200.
   */
  public String complete(byte[] body) {
    HttpResponse<String> response = send(buildRequest(body),
        HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new ProviderException(ProviderProperties.OpenAi.TYPE, response.statusCode());
    }
    return response.body();
  }
}
