package yardmaster.keys;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts provider keys at rest with AES-256-GCM under the {@link MasterKey}. The output is
 * base64 of a random 12-byte IV followed by the ciphertext and its 128-bit tag, so changing any
 * byte makes {@link #decrypt} fail.
 */
public final class KeyCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int TAG_BYTES = TAG_BITS / 8;
  private static final String CORRUPT =
      "Encrypted value is corrupt, was changed, or was made with a different master key.";

  private static final SecureRandom RANDOM = new SecureRandom();

  private final SecretKey key;

  /** Creates a cipher that uses the given master key. */
  public KeyCipher(MasterKey masterKey) {
    this.key = masterKey.secretKey();
  }

  /** Encrypts with a fresh random IV, so the same text never gives the same output twice. */
  public String encrypt(String plain) {
    byte[] iv = new byte[IV_BYTES];
    RANDOM.nextBytes(iv);
    byte[] sealed = run(Cipher.ENCRYPT_MODE, iv, plain.getBytes(UTF_8));
    byte[] out = ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array();
    return Base64.getEncoder().encodeToString(out);
  }

  /**
   * Decrypts text made by {@link #encrypt} and checks its tag.
   *
   * @throws IllegalArgumentException if the text is not valid, was changed, or was encrypted with
   *     a different master key. The message never contains the text.
   */
  public String decrypt(String text) {
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(text);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(CORRUPT);
    }
    if (bytes.length < IV_BYTES + TAG_BYTES) {
      throw new IllegalArgumentException(CORRUPT);
    }
    byte[] iv = Arrays.copyOfRange(bytes, 0, IV_BYTES);
    byte[] sealed = Arrays.copyOfRange(bytes, IV_BYTES, bytes.length);
    return new String(run(Cipher.DECRYPT_MODE, iv, sealed), UTF_8);
  }

  private byte[] run(int mode, byte[] iv, byte[] input) {
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
      return cipher.doFinal(input);
    } catch (GeneralSecurityException e) {
      // In practice only decrypting fails here: every JDK ships AES-GCM.
      throw new IllegalArgumentException(CORRUPT, e);
    }
  }
}
