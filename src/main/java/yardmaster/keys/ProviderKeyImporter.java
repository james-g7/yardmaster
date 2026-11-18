package yardmaster.keys;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import yardmaster.providers.ProviderProperties;

/**
 * Stores the OpenAI key from {@value #ENV_VAR} at startup, encrypted, so a fresh install works
 * before the admin panel exists. Safe to run on every start: it keeps one row and only rewrites
 * it when the key changes. The key itself is never logged.
 */
@Component
class ProviderKeyImporter implements ApplicationRunner {

  static final String ENV_VAR = "YARDMASTER_PROVIDER_KEY_OPENAI";
  static final String DISPLAY_NAME = "openai-env";

  /** Rows made from the environment have no admin behind them; admins arrive in build step 9. */
  static final String OWNER = "system";

  private static final String TYPE = "openai";
  private static final Logger log = LoggerFactory.getLogger(ProviderKeyImporter.class);

  /** What a run did to the stored key. */
  enum Outcome {
    ADDED,
    UPDATED,
    UNCHANGED
  }

  private record Provider(String id, String baseUrl) {}

  private record StoredKey(String id, String encryptedKey) {}

  private final Environment env;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final KeyCipher cipher;
  private final ProviderProperties properties;

  ProviderKeyImporter(
      Environment env,
      JdbcTemplate jdbc,
      PlatformTransactionManager transactions,
      KeyCipher cipher,
      ProviderProperties properties) {
    this.env = env;
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(transactions);
    this.cipher = cipher;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    String plain = env.getProperty(ENV_VAR);
    if (plain == null || plain.isBlank()) {
      return;
    }
    Outcome outcome = tx.execute(status -> importKey(plain.strip()));
    log.info("OpenAI key from {}: {} as '{}'", ENV_VAR, outcome, DISPLAY_NAME);
  }

  Outcome importKey(String plain) {
    Timestamp now = Timestamp.from(Instant.now());
    String providerId = ensureProvider(now);
    List<StoredKey> keys =
        jdbc.query(
            "SELECT id, encrypted_key FROM provider_keys"
                + " WHERE provider_id = ? AND display_name = ?",
            (rs, i) -> new StoredKey(rs.getString(1), rs.getString(2)),
            providerId,
            DISPLAY_NAME);
    if (keys.isEmpty()) {
      jdbc.update(
          "INSERT INTO provider_keys (id, owner_id, provider_id, encrypted_key, display_name,"
              + " enabled, abilities, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
          UUID.randomUUID().toString(),
          OWNER,
          providerId,
          cipher.encrypt(plain),
          DISPLAY_NAME,
          true,
          "{}",
          now,
          now);
      return Outcome.ADDED;
    }
    StoredKey stored = keys.get(0);
    if (holds(stored, plain)) {
      return Outcome.UNCHANGED;
    }
    jdbc.update(
        "UPDATE provider_keys SET encrypted_key = ?, updated_at = ? WHERE id = ?",
        cipher.encrypt(plain),
        now,
        stored.id());
    return Outcome.UPDATED;
  }

  /** Finds the OpenAI provider row, or adds one, with the base URL from the provider config. */
  private String ensureProvider(Timestamp now) {
    String baseUrl = properties.openai().baseUrl();
    List<Provider> providers =
        jdbc.query(
            "SELECT id, base_url FROM providers WHERE type = ? ORDER BY created_at, id",
            (rs, i) -> new Provider(rs.getString(1), rs.getString(2)),
            TYPE);
    if (providers.isEmpty()) {
      String id = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO providers (id, owner_id, type, base_url, created_at, updated_at)"
              + " VALUES (?, ?, ?, ?, ?, ?)",
          id,
          OWNER,
          TYPE,
          baseUrl,
          now,
          now);
      return id;
    }
    Provider provider = providers.get(0);
    if (!provider.baseUrl().equals(baseUrl)) {
      jdbc.update(
          "UPDATE providers SET base_url = ?, updated_at = ? WHERE id = ?",
          baseUrl,
          now,
          provider.id());
    }
    return provider.id();
  }

  /** True if the stored row already holds this key. */
  private boolean holds(StoredKey stored, String plain) {
    try {
      return cipher.decrypt(stored.encryptedKey()).equals(plain);
    } catch (IllegalArgumentException e) {
      // Encrypted under a different master key, so it can't be used: replace it.
      return false;
    }
  }
}
