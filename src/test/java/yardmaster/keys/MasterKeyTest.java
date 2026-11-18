package yardmaster.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MasterKeyTest {

  private static MasterKey load(String value) {
    return MasterKey.load(Map.of(MasterKey.ENV_VAR, value)::get);
  }

  @Test
  void validKeyLoads() {
    byte[] raw = new byte[32];
    for (int i = 0; i < raw.length; i++) {
      raw[i] = (byte) i;
    }
    MasterKey key = load(Base64.getEncoder().encodeToString(raw));
    assertThat(key.bytes()).isEqualTo(raw);
  }

  @Test
  void shortKeyFails() {
    String secret = Base64.getEncoder().encodeToString(new byte[16]);
    assertThatThrownBy(() -> load(secret))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("YARDMASTER_MASTER_KEY")
        .hasMessageContaining("32 bytes")
        .hasMessageNotContaining(secret);
  }

  @Test
  void longKeyFails() {
    assertThatThrownBy(() -> load(Base64.getEncoder().encodeToString(new byte[33])))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void missingKeyFails() {
    assertThatThrownBy(() -> MasterKey.load(name -> null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("YARDMASTER_MASTER_KEY");
  }

  @Test
  void blankKeyFails() {
    assertThatThrownBy(() -> load("  "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("YARDMASTER_MASTER_KEY");
  }

  @Test
  void invalidBase64FailsWithoutLeakingValue() {
    String secret = "short-not-base64!";
    assertThatThrownBy(() -> load(secret))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("YARDMASTER_MASTER_KEY")
        .hasMessageNotContaining(secret)
        .hasNoCause();
  }

  @Test
  void toStringDoesNotRevealKey() {
    String encoded = Base64.getEncoder().encodeToString(new byte[32]);
    assertThat(load(encoded).toString()).doesNotContain(encoded);
  }
}
