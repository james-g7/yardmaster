package yardmaster.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeyCipherTest {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String CORRUPT =
      "Encrypted value is corrupt, was changed, or was made with a different master key.";

  private final KeyCipher cipher = new KeyCipher(randomMasterKey());

  @Test
  void roundTripWorks() {
    String plain = "sk-test-key-123 ünïcødé";

    assertThat(cipher.decrypt(cipher.encrypt(plain))).isEqualTo(plain);
  }

  @Test
  void roundTripWorksForEmptyText() {
    assertThat(cipher.decrypt(cipher.encrypt(""))).isEmpty();
  }

  @Test
  void encryptingTheSameTextTwiceGivesDifferentOutput() {
    String plain = "sk-test-key-123";

    String first = cipher.encrypt(plain);
    String second = cipher.encrypt(plain);

    assertThat(first).isNotEqualTo(second);
    // 12-byte IV, 15 bytes of ciphertext, 16-byte tag
    assertThat(Base64.getDecoder().decode(first)).hasSize(12 + 15 + 16);
    assertThat(first).doesNotContain(plain);
  }

  @Test
  void flippingAnyByteMakesDecryptFail() {
    byte[] sealed = Base64.getDecoder().decode(cipher.encrypt("sk-test-key-123"));

    // IV, ciphertext and tag are all covered.
    for (int i = 0; i < sealed.length; i++) {
      byte[] changed = sealed.clone();
      changed[i] ^= 1;
      String text = Base64.getEncoder().encodeToString(changed);

      assertThatThrownBy(() -> cipher.decrypt(text))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(CORRUPT);
    }
  }

  @Test
  void differentMasterKeyCannotDecrypt() {
    String text = cipher.encrypt("sk-test-key-123");
    var other = new KeyCipher(randomMasterKey());

    assertThatThrownBy(() -> other.decrypt(text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(CORRUPT);
  }

  @Test
  void textShorterThanIvAndTagFails() {
    String justTooShort = Base64.getEncoder().encodeToString(new byte[12 + 16 - 1]);
    String tiny = Base64.getEncoder().encodeToString(new byte[5]);

    assertThatThrownBy(() -> cipher.decrypt(justTooShort)).hasMessage(CORRUPT).hasNoCause();
    assertThatThrownBy(() -> cipher.decrypt(tiny)).hasMessage(CORRUPT).hasNoCause();
  }

  @Test
  void textThatIsNotBase64Fails() {
    String text = "not-base64-secret!";

    assertThatThrownBy(() -> cipher.decrypt(text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(CORRUPT)
        .hasNoCause();
  }

  private static MasterKey randomMasterKey() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return MasterKey.from(Map.of(MasterKey.ENV_VAR, Base64.getEncoder().encodeToString(bytes)));
  }
}
