package yardmaster.keys;

import java.util.Arrays;
import java.util.Base64;
import java.util.function.UnaryOperator;

/**
 * The AES-256 master key, read from the YARDMASTER_MASTER_KEY environment variable.
 *
 * <p>The variable holds base64 that decodes to exactly 32 bytes. Error messages name the variable
 * but never include its value, and the value is not chained as a cause.
 */
public final class MasterKey {

  /** Name of the environment variable that holds the key. */
  public static final String ENV_VAR = "YARDMASTER_MASTER_KEY";

  /** Required key length in bytes (AES-256). */
  public static final int KEY_BYTES = 32;

  private final byte[] bytes;

  private MasterKey(byte[] bytes) {
    this.bytes = bytes;
  }

  /** Loads the key from the process environment. */
  public static MasterKey fromEnvironment() {
    return load(System::getenv);
  }

  /** Loads the key using the given variable lookup. Fails with a message naming the variable. */
  public static MasterKey load(UnaryOperator<String> env) {
    String value = env.apply(ENV_VAR);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          ENV_VAR + " is not set. Set it to base64 of 32 random bytes.");
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(value.strip());
    } catch (IllegalArgumentException e) {
      // Deliberately not chained: the decoder's message can quote part of the value.
      throw new IllegalStateException(ENV_VAR + " is not valid base64.");
    }
    if (decoded.length != KEY_BYTES) {
      throw new IllegalStateException(
          ENV_VAR + " must decode to exactly " + KEY_BYTES + " bytes (AES-256).");
    }
    return new MasterKey(decoded);
  }

  /** Returns a copy of the raw key bytes. */
  public byte[] bytes() {
    return Arrays.copyOf(bytes, bytes.length);
  }

  @Override
  public String toString() {
    return "MasterKey[redacted]";
  }
}
