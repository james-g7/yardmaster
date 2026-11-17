package yardmaster.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final StringRedisTemplate valkey = mock(StringRedisTemplate.class);
  private final RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
  private final RedisConnection connection = mock(RedisConnection.class);
  private final HealthController controller = new HealthController(jdbc, valkey);

  @Test
  void healthzIsAlwaysOk() {
    assertThat(controller.healthz()).isEqualTo("ok");
  }

  @Test
  void readyWhenDatabaseAndValkeyAnswer() {
    databaseUp();
    when(valkey.getConnectionFactory()).thenReturn(factory);
    when(factory.getConnection()).thenReturn(connection);
    when(connection.ping()).thenReturn("PONG");

    assertThat(controller.readyz().getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void notReadyWhenDatabaseDown() {
    when(jdbc.queryForObject("SELECT 1", Integer.class))
        .thenThrow(new DataAccessResourceFailureException("down"));

    var response = controller.readyz();
    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getBody()).contains("database");
  }

  @Test
  void notReadyWhenValkeyDown() {
    databaseUp();
    when(valkey.getConnectionFactory()).thenReturn(factory);
    when(factory.getConnection()).thenThrow(new DataAccessResourceFailureException("down"));

    var response = controller.readyz();
    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getBody()).contains("valkey");
  }

  private void databaseUp() {
    when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
  }
}
