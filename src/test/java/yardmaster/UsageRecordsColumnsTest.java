package yardmaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Guards the rule that prompts and replies are never stored: usage_records must have no column for them. */
class UsageRecordsColumnsTest {

    private static final List<String> FORBIDDEN = List.of("prompt", "reply", "content", "body");

    @TempDir
    Path dataDir;

    @Test
    void usageRecordsHasNoPromptOrReplyColumns() throws Exception {
        String url = "jdbc:sqlite:" + dataDir.resolve("columns.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();

        List<String> columns = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
                ResultSet rs = conn.getMetaData().getColumns(null, null, "usage_records", null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }

        assertThat(columns).as("usage_records columns").isNotEmpty();
        assertThat(columns)
                .as("usage_records must not have columns that could hold prompts or replies")
                .noneMatch(name -> FORBIDDEN.stream()
                        .anyMatch(word -> name.toLowerCase(Locale.ROOT).contains(word)));
    }
}
