package yardmaster.health;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plain health checks on the public port.
 * /healthz: the process is up. /readyz: the database and Valkey both answer.
 * No calls are accepted while Valkey is down, so readiness depends on it.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate valkey;

    public HealthController(JdbcTemplate jdbc, StringRedisTemplate valkey) {
        this.jdbc = jdbc;
        this.valkey = valkey;
    }

    @GetMapping(value = "/healthz", produces = "text/plain")
    public String healthz() {
        return "ok";
    }

    @GetMapping(value = "/readyz", produces = "text/plain")
    public ResponseEntity<String> readyz() {
        if (!databaseUp()) {
            return ResponseEntity.status(503).body("database unavailable");
        }
        if (!valkeyUp()) {
            return ResponseEntity.status(503).body("valkey unavailable");
        }
        return ResponseEntity.ok("ok");
    }

    private boolean databaseUp() {
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean valkeyUp() {
        try {
            var connectionFactory = valkey.getConnectionFactory();
            if (connectionFactory == null) {
                return false;
            }
            try (var connection = connectionFactory.getConnection()) {
                return "PONG".equalsIgnoreCase(connection.ping());
            }
        } catch (RuntimeException e) {
            return false;
        }
    }
}
