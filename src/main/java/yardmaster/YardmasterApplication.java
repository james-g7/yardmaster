package yardmaster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Application entry point. */
@SpringBootApplication
public class YardmasterApplication {

  /** Creates the data folder, then starts Spring. */
  public static void main(String[] args) throws IOException {
    // SQLite won't create the folder its file lives in.
    String dataDir = System.getenv().getOrDefault("YARDMASTER_DATA_DIR", "./data");
    Files.createDirectories(Path.of(dataDir));
    SpringApplication.run(YardmasterApplication.class, args);
  }
}
