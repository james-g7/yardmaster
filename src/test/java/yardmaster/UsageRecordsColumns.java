package yardmaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared check for the rule that prompts and replies are never stored: usage_records must have no
 * column for them.
 */
final class UsageRecordsColumns {

  private static final List<String> FORBIDDEN = List.of("prompt", "reply", "content", "body");

  private UsageRecordsColumns() {}

  static void assertNoContentColumns(Connection conn) throws SQLException {
    List<String> columns = new ArrayList<>();
    try (ResultSet rs = conn.getMetaData().getColumns(null, null, "usage_records", null)) {
      while (rs.next()) {
        columns.add(rs.getString("COLUMN_NAME"));
      }
    }

    assertThat(columns).as("usage_records columns").isNotEmpty();
    assertThat(columns)
        .as("usage_records must not have columns that could hold prompts or replies")
        .noneMatch(
            name ->
                FORBIDDEN.stream().anyMatch(word -> name.toLowerCase(Locale.ROOT).contains(word)));
  }
}
