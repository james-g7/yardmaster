package yardmaster.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import yardmaster.TestKeys;

class KeysConfigTest {

  private final KeysConfig config = new KeysConfig();

  @Test
  void readsTheMasterKeyFromTheEnvironment() {
    var env = new MockEnvironment().withProperty(MasterKey.ENV_VAR, TestKeys.MASTER_KEY);

    var key = config.masterKey(env).secretKey();

    assertThat(key.getEncoded()).isEqualTo(Base64.getDecoder().decode(TestKeys.MASTER_KEY));
  }

  @Test
  void missingMasterKeyFails() {
    assertThatThrownBy(() -> config.masterKey(new MockEnvironment()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(MasterKey.ENV_VAR + " is not set");
  }

  @Test
  void cipherUsesTheMasterKey() {
    var env = new MockEnvironment().withProperty(MasterKey.ENV_VAR, TestKeys.MASTER_KEY);
    var masterKey = config.masterKey(env);

    String text = config.keyCipher(masterKey).encrypt("sk-test");

    assertThat(new KeyCipher(masterKey).decrypt(text)).isEqualTo("sk-test");
  }
}
