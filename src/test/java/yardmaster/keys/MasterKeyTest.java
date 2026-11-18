package yardmaster.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MasterKeyTest {

  private static final SecureRandom RANDOM = new SecureRandom();

  @Test
  void validKeyLoads() {
    byte[] bytes = randomBytes(32);

    var key = MasterKey.from(env(Base64.getEncoder().encodeToString(bytes))).secretKey();

    assertThat(key.getAlgorithm()).isEqualTo("AES");
    assertThat(key.getEncoded()).isEqualTo(bytes);
  }

  @Test
  void ignoresSurroundingWhitespace() {
    byte[] bytes = randomBytes(32);

    var key = MasterKey.from(env(" " + Base64.getEncoder().encodeToString(bytes) + "\n"));

    assertThat(key.secretKey().getEncoded()).isEqualTo(bytes);
  }

  @Test
  void shortKeyFails() {
    String value = Base64.getEncoder().encodeToString(randomBytes(16));

    assertFailsWithoutShowing(value, "must decode to 32 bytes", "decodes to 16");
  }

  @Test
  void longKeyFails() {
    String value = Base64.getEncoder().encodeToString(randomBytes(33));

    assertFailsWithoutShowing(value, "must decode to 32 bytes", "decodes to 33");
  }

  @Test
  void notBase64Fails() {
    assertFailsWithoutShowing("not-base64-secret!", "is not valid base64");
  }

  @Test
  void missingKeyFails() {
    assertThatThrownBy(() -> MasterKey.from(Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(MasterKey.ENV_VAR + " is not set");
  }

  @Test
  void blankKeyFails() {
    assertThatThrownBy(() -> MasterKey.from(env("   ")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(MasterKey.ENV_VAR + " is not set");
  }

  @Test
  void toStringHidesTheKey() {
    String value = Base64.getEncoder().encodeToString(randomBytes(32));

    assertThat(MasterKey.from(env(value)).toString())
        .isEqualTo("MasterKey[hidden]")
        .doesNotContain(value);
  }

  private static void assertFailsWithoutShowing(String value, String... expected) {
    assertThatThrownBy(() -> MasterKey.from(env(value)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(MasterKey.ENV_VAR)
        .hasMessageContainingAll(expected)
        .hasNoCause()
        .message()
        .doesNotContain(value);
  }

  private static Map<String, String> env(String value) {
    var env = new HashMap<String, String>();
    env.put(MasterKey.ENV_VAR, value);
    return env;
  }

  private static byte[] randomBytes(int count) {
    byte[] bytes = new byte[count];
    RANDOM.nextBytes(bytes);
    return bytes;
  }
}
