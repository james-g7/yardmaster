package yardmaster.keys;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Makes the master key and the provider key cipher available to the rest of the app. */
@Configuration(proxyBeanMethods = false)
class KeysConfig {

  /** Reads {@value MasterKey#ENV_VAR} through Spring, so tests can supply it as a property. */
  @Bean
  MasterKey masterKey(Environment env) {
    String value = env.getProperty(MasterKey.ENV_VAR);
    return MasterKey.from(value == null ? Map.of() : Map.of(MasterKey.ENV_VAR, value));
  }

  @Bean
  KeyCipher keyCipher(MasterKey masterKey) {
    return new KeyCipher(masterKey);
  }
}
