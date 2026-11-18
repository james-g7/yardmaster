package yardmaster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import yardmaster.keys.MasterKey;

/** Application entry point. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class YardmasterApplication {

  /** Checks the master key, creates the data folder, then starts Spring. */
  public static void main(String[] args) throws IOException {
    try {
      MasterKey.from(System.getenv());
    } catch (IllegalStateException e) {
      // The message names the variable and never contains its value.
      System.err.println("Yardmaster cannot start: " + e.getMessage());
      System.exit(1);
    }
    // SQLite won't create the folder its file lives in.
    String dataDir = System.getenv().getOrDefault("YARDMASTER_DATA_DIR", "./data");
    Files.createDirectories(Path.of(dataDir));
    SpringApplication.run(YardmasterApplication.class, args);
  }
}
