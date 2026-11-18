package yardmaster.keys;

import java.util.Base64;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * The AES-256 key that encrypts provider keys at rest. It comes from the {@value #ENV_VAR}
 * environment variable and is never stored, logged or shown in error messages.
 */
public final class MasterKey {

  /** Environment variable holding the key: 32 bytes, base64 encoded. */
  public static final String ENV_VAR = "YARDMASTER_MASTER_KEY";

  private static final int KEY_BYTES = 32;

  private final SecretKey key;

  private MasterKey(byte[] bytes) {
    this.key = new SecretKeySpec(bytes, "AES");
  }

  /**
   * Reads and checks the key from the given environment, usually {@code System.getenv()}.
   *
   * @throws IllegalStateException if the variable is missing, not base64, or not 32 bytes. The
   *     message names the variable and never contains its value.
   */
  public static MasterKey from(Map<String, String> env) {
    String value = env.get(ENV_VAR);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          ENV_VAR + " is not set. Set it to 32 random bytes in base64, such as the output of"
              + " `openssl rand -base64 32`.");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(value.strip());
    } catch (IllegalArgumentException e) {
      // The decoder's own message can quote part of the value, so it is not passed on.
      throw new IllegalStateException(ENV_VAR + " is not valid base64.");
    }
    if (bytes.length != KEY_BYTES) {
      throw new IllegalStateException(
          ENV_VAR
              + " must decode to "
              + KEY_BYTES
              + " bytes for AES-256, but decodes to "
              + bytes.length
              + ".");
    }
    return new MasterKey(bytes);
  }

  /** The key for {@code javax.crypto}. */
  public SecretKey secretKey() {
    return key;
  }

  @Override
  public String toString() {
    return "MasterKey[hidden]";
  }
}
