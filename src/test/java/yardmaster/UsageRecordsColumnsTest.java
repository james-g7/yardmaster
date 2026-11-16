package yardmaster;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards the rule that prompts and replies are never stored, on SQLite, without Docker.
 * PostgresStartupIT runs the same check against PostgreSQL.
 */
class UsageRecordsColumnsTest {

    @TempDir
    Path dataDir;

    @Test
    void usageRecordsHasNoPromptOrReplyColumns() throws Exception {
        String url = "jdbc:sqlite:" + dataDir.resolve("columns.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();

        try (Connection conn = DriverManager.getConnection(url)) {
            UsageRecordsColumns.assertNoContentColumns(conn);
        }
    }
}
