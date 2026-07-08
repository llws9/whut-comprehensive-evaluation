package edu.whut.eval.app.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class GroupAIdentitySqlSeedConsistencyTest {

    private static final Path SQL_PATH = Path.of(System.getProperty("user.dir"))
            .getParent()
            .resolve("docs/team-delivery/group-a-identity-user-admin.sql");
    private static final Path TEAM_DELIVERY = SQL_PATH.getParent();

    @Test
    void shouldKeepPermissionSeedInSyncWithApplicationPermissionConstants() throws Exception {
        String sql = Files.readString(SQL_PATH);

        Set<String> permissionSeedCodes = extractFirstStringColumnValues(sql, "iam_permission");
        Set<String> codeConstants = groupAOwnedPermissionCodeConstants();

        assertThat(permissionSeedCodes).containsExactlyInAnyOrderElementsOf(codeConstants);
    }

    @Test
    void shouldReferenceOnlySeededPermissionsInScopeRules() throws Exception {
        String sql = Files.readString(SQL_PATH);

        Set<String> permissionSeedCodes = extractFirstStringColumnValues(sql, "iam_permission");
        Set<String> scopePermissionCodes = extractScopeRulePermissionCodes(sql);

        assertThat(permissionSeedCodes).containsAll(scopePermissionCodes);
    }

    @Test
    void shouldKeepScoreConfirmAssignedOutOfAGroupIdentitySeed() throws Exception {
        String groupASql = Files.readString(SQL_PATH);

        assertThat(AuthorizationPermissionCodes.SCORE_CONFIRM_ASSIGNED).isEqualTo("score.confirm.assigned");
        assertThat(groupASql).doesNotContain("score.confirm.assigned");
    }

    @Test
    void shouldKeepFinalSelfPermissionsOwnedByAGroupIdentitySeed() throws Exception {
        String groupASql = Files.readString(SQL_PATH);
        Path dGroupSafeInitSql = TEAM_DELIVERY.resolve("group-d-score-finalization-import-export.safe-init.sql");
        String dSafeInitSql = Files.exists(dGroupSafeInitSql) ? Files.readString(dGroupSafeInitSql) : "";

        assertThat(AuthorizationPermissionCodes.FINAL_SUBMIT_SELF).isEqualTo("final.submit.self");
        assertThat(AuthorizationPermissionCodes.FINAL_VIEW_SELF).isEqualTo("final.view.self");
        assertThat(groupASql).contains("final.submit.self").contains("final.view.self");
        assertThat(dSafeInitSql).doesNotContain("final.submit.self").doesNotContain("final.view.self");
    }

    @Test
    void shouldUseCurrentIamSessionColumnsInSeed() throws Exception {
        String sql = Files.readString(SQL_PATH);

        List<String> columns = extractInsertColumns(sql, "iam_session");

        assertThat(columns).containsExactly(
                "id",
                "session_no",
                "user_id",
                "access_token_id",
                "refresh_token_id",
                "device_type",
                "client_ip",
                "user_agent",
                "expired_at",
                "revoked_at",
                "status",
                "created_at",
                "updated_at"
        );
        assertThat(columns).doesNotContain("token_id", "login_ip");
    }


    @Test
    void shouldExecuteGroupASqlSeedOnH2CompatibilityMode() throws Exception {
        String sql = toH2CompatibleSql(Files.readString(SQL_PATH));
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:group_a_sql_seed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) {
                    try (Statement jdbcStatement = connection.createStatement()) {
                        jdbcStatement.execute(statement);
                    }
                }
            }
            try (Statement statement = connection.createStatement()) {
                assertThat(queryLong(statement, "SELECT COUNT(*) FROM iam_permission"))
                        .isEqualTo(groupAOwnedPermissionCodeConstants().size());
                assertThat(queryLong(statement, "SELECT COUNT(*) FROM iam_scope_rule sr LEFT JOIN iam_permission p ON p.permission_code = sr.permission_code WHERE p.id IS NULL"))
                        .isZero();
                assertThat(queryLong(statement, "SELECT COUNT(*) FROM iam_session WHERE session_no IS NULL OR access_token_id IS NULL OR refresh_token_id IS NULL OR client_ip IS NULL OR updated_at IS NULL"))
                        .isZero();
            }
        }
    }


    @Test
    void shouldKeepActiveSessionSeedRowsUnexpired() throws Exception {
        String sql = toH2CompatibleSql(Files.readString(SQL_PATH));
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:group_a_active_session_seed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) {
                    try (Statement jdbcStatement = connection.createStatement()) {
                        jdbcStatement.execute(statement);
                    }
                }
            }
            try (Statement statement = connection.createStatement()) {
                assertThat(queryLong(statement, "SELECT COUNT(*) FROM iam_session WHERE status = 'ACTIVE' AND expired_at <= CURRENT_TIMESTAMP"))
                        .isZero();
            }
        }
    }

    private static Set<String> permissionCodeConstants() {
        Set<String> codes = new LinkedHashSet<>();
        for (Field field : AuthorizationPermissionCodes.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && field.getType().equals(String.class)) {
                try {
                    codes.add((String) field.get(null));
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException("Cannot read permission constant: " + field.getName(), exception);
                }
            }
        }
        return codes;
    }

    private static Set<String> groupAOwnedPermissionCodeConstants() {
        Set<String> codes = permissionCodeConstants();
        codes.remove(AuthorizationPermissionCodes.SCORE_CONFIRM_ASSIGNED);
        return codes;
    }

    private static Set<String> extractFirstStringColumnValues(String sql, String tableName) {
        String block = extractInsertBlock(sql, tableName);
        Matcher matcher = Pattern.compile("\\(\\s*\\d+\\s*,\\s*'([^']+)'").matcher(block);
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static Set<String> extractScopeRulePermissionCodes(String sql) {
        String block = extractInsertBlock(sql, "iam_scope_rule");
        Matcher matcher = Pattern.compile("\\(\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*'([^']+)'").matcher(block);
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static List<String> extractInsertColumns(String sql, String tableName) {
        Matcher matcher = Pattern.compile("INSERT INTO `" + tableName + "` \\(([^)]*)\\) VALUES", Pattern.DOTALL)
                .matcher(sql);
        assertThat(matcher.find()).as("insert block for %s", tableName).isTrue();
        return Arrays.stream(matcher.group(1).split(","))
                .map(column -> column.replace("`", "").trim())
                .toList();
    }

    private static String extractInsertBlock(String sql, String tableName) {
        Matcher matcher = Pattern.compile("INSERT INTO `" + tableName + "`[\\s\\S]*?;", Pattern.DOTALL)
                .matcher(sql);
        assertThat(matcher.find()).as("insert block for %s", tableName).isTrue();
        return matcher.group();
    }
    private static long queryLong(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private static String toH2CompatibleSql(String sql) {
        String normalized = sql
                .replaceAll("(?m)^--.*$", "")
                .replace("`", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主表'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织树'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户组织归属'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色模板'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限字典'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关系'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色分配'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据范围规则'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录会话'", "")
                .replace("JSON DEFAULT NULL", "VARCHAR(2048) DEFAULT NULL")
                .replace("TINYINT(1)", "BOOLEAN")
                .replace("SET NAMES utf8mb4;", "");
        normalized = normalized.lines()
                .filter(line -> !line.trim().startsWith("CONSTRAINT fk_"))
                .reduce("", (left, right) -> left + right + System.lineSeparator());
        normalized = normalized.replaceAll(",\\s*\\)", ")");
        return normalized;
    }

}
