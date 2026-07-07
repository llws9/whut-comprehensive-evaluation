package edu.whut.eval.app.iam;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TeamDeliverySqlConsistencyTest {

    private static final Path ROOT = Path.of(System.getProperty("user.dir")).getParent();
    private static final Path TEAM_DELIVERY = ROOT.resolve("docs/team-delivery");
    private static final Path IAM_RESOURCE_SQL = ROOT.resolve("whut-eval-app/src/main/resources/sql/iam");
    private static final Path B_GROUP_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-b-student-application.safe-init.sql");
    private static final Path C_GROUP_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-c-review-workflow.safe-init.sql");
    private static final Path E_GROUP_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-e-platform-governance-attachment-ai.safe-init.sql");

    @Test
    void shouldKeepStudentSelfResourceSqlAlignedWithGroupASchema() throws Exception {
        for (Path path : Files.list(IAM_RESOURCE_SQL).filter(file -> file.getFileName().toString().endsWith(".sql")).toList()) {
            String sql = Files.readString(path);

            assertThat(sql).doesNotContain("role_code = 'student'");
            assertThat(sql).doesNotContain("INSERT INTO iam_permission (permission_code");
            assertThat(sql).doesNotContain("INSERT INTO iam_role_permission (role_id");
            assertThat(sql).doesNotContain("INSERT INTO iam_scope_rule (\n    assignment_id");
        }
    }

    @Test
    void shouldKeepBGroupApplicationTableAlignedWithRuntimeMapper() throws Exception {
        String sql = Files.readString(TEAM_DELIVERY.resolve("group-b-student-application.sql"));

        assertThat(sql).contains("CREATE TABLE `application_submission` (\n  `application_id` BIGINT NOT NULL");
        assertThat(sql).contains("PRIMARY KEY (`application_id`)");
        assertThat(sql).doesNotContain("CREATE TABLE `application_submission` (\n  `id` BIGINT NOT NULL");
    }

    @Test
    void shouldKeepBGroupPublicPoolAttachmentSnapshotsAlignedWithEGroupFileSeed() throws Exception {
        String sql = Files.readString(TEAM_DELIVERY.resolve("group-b-student-application.sql"));

        assertThat(sql).doesNotContain("(23004, 21002, 'FILE-0005', 'PUBLIC_POOL'");
        assertThat(sql).contains("(23004, 21002, 'FILE-0008', 'PUBLIC_POOL', 2, '综测申请模板.pdf', 'application/pdf', 142000, 'attachments/2026/05/guide-template.pdf'");
    }

    @Test
    void shouldProvideNonDestructiveBGroupSafeInitSql() throws Exception {
        String sql = Files.readString(B_GROUP_SAFE_INIT_SQL);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `application_submission`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `application_attachment`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `application_fact`");
        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(sql).doesNotContain("ENGINE=");
        assertThat(sql).doesNotContain("CHARSET");
        assertThat(sql).doesNotContain("COLLATE");
        assertThat(sql).doesNotContain("COMMENT=");
        assertThat(extractCreateTableBlock(sql, "application_submission"))
                .contains("`application_id` BIGINT NOT NULL AUTO_INCREMENT")
                .contains("KEY `idx_application_submission_active_claim` (`applicant_user_id`, `item_code`, `academic_year`, `term`, `status`)");
        assertThat(extractCreateTableBlock(sql, "application_attachment"))
                .contains("`id` BIGINT NOT NULL AUTO_INCREMENT")
                .contains("`storage_key` VARCHAR(512) NOT NULL")
                .contains("`uploaded_by` BIGINT NOT NULL")
                .contains("KEY `idx_application_attachment_application_id` (`application_id`)")
                .contains("KEY `idx_application_attachment_uploaded_by` (`uploaded_by`)")
                .doesNotContain("selected_source")
                .doesNotContain("snapshot_");
        assertThat(extractCreateTableBlock(sql, "application_fact"))
                .contains("`id` BIGINT NOT NULL AUTO_INCREMENT")
                .contains("`application_id` BIGINT NOT NULL")
                .contains("`score_value` DECIMAL(10,2) DEFAULT NULL")
                .contains("`display_text` VARCHAR(1000) DEFAULT NULL")
                .contains("`evidence_count` INT NOT NULL")
                .contains("`extra_json` VARCHAR(2000) DEFAULT NULL")
                .contains("UNIQUE KEY `uk_application_fact_application_id` (`application_id`)");
    }

    @Test
    void shouldRerunBGroupSafeInitSqlWithoutOverwritingRuntimeApplicationRows() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:b_group_safe_init;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
            executeStatements(connection, Files.readString(B_GROUP_SAFE_INIT_SQL));

            connection.createStatement().executeUpdate("""
                    INSERT INTO application_submission (applicant_user_id, org_unit_id, category_code, item_code, academic_year, term, title, description, status, submitted_at, created_at, updated_at, version)
                    VALUES (1001, 2010, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', '2025-2026', '上学期', 'runtime title', 'runtime desc', 'DRAFT', NULL, '2026-07-06 10:00:00', '2026-07-06 10:00:00', 0)
                    """);
            long applicationId = singleLong(connection, "SELECT application_id FROM application_submission WHERE title = 'runtime title'");
            connection.createStatement().executeUpdate("""
                    INSERT INTO application_attachment (application_id, file_id, storage_key, original_filename, content_type, size, uploaded_by, sort_no)
                    VALUES (%d, 'file_runtime_001', 'runtime/a.pdf', 'a.pdf', 'application/pdf', 128, 1001, 0)
                    """.formatted(applicationId));
            connection.createStatement().executeUpdate("""
                    INSERT INTO application_fact (application_id, score_value, display_text, evidence_count, extra_json, created_at, updated_at)
                    VALUES (%d, 2.00, 'runtime warning', 1, '{"optionCode":"OPTION_A"}', '2026-07-06 10:00:00', '2026-07-06 10:00:00')
                    """.formatted(applicationId));

            executeStatements(connection, Files.readString(B_GROUP_SAFE_INIT_SQL));

            assertThat(countRows(connection, "application_submission", "application_id = " + applicationId)).isEqualTo(1);
            assertThat(singleString(connection, "SELECT title FROM application_submission WHERE application_id = " + applicationId))
                    .isEqualTo("runtime title");
            assertThat(countRows(connection, "application_attachment", "application_id = " + applicationId + " AND file_id = 'file_runtime_001'")).isEqualTo(1);
            assertThat(singleString(connection, "SELECT storage_key FROM application_attachment WHERE application_id = " + applicationId))
                    .isEqualTo("runtime/a.pdf");
            assertThat(countRows(connection, "application_fact", "application_id = " + applicationId)).isEqualTo(1);
            assertThat(singleString(connection, "SELECT display_text FROM application_fact WHERE application_id = " + applicationId))
                    .isEqualTo("runtime warning");
        }
    }

    @Test
    void shouldKeepEGroupFileAssetSchemaAlignedWithUploadMapper() throws Exception {
        String sql = Files.readString(TEAM_DELIVERY.resolve("group-e-platform-governance-attachment-ai.sql"));

        assertThat(sql).contains("`sha256` VARCHAR(128) DEFAULT NULL");
        assertThat(extractInsertColumns(sql, "file_asset")).contains("sha256");
    }

    @Test
    void shouldProvideNonDestructiveEGroupSafeInitSql() throws Exception {
        String sql = Files.readString(E_GROUP_SAFE_INIT_SQL);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `evaluation_category`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `evaluation_item`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `file_asset`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `public_attachment_entry`");
        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(sql).doesNotContain("ENGINE=");
        assertThat(sql).doesNotContain("CHARSET");
        assertThat(sql).doesNotContain("COLLATE");
        assertThat(sql).doesNotContain("COMMENT=");
        assertThat(sql).contains("`id` BIGINT NOT NULL AUTO_INCREMENT");
        assertThat(extractCreateTableBlock(sql, "public_attachment_entry")).contains("`updated_at` DATETIME NOT NULL");
        assertThat(extractInsertColumns(sql, "public_attachment_entry")).contains("updated_at");
    }

    @Test
    void shouldRerunEGroupSafeInitSqlWithoutOverwritingRuntimeFileRows() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:e_group_safe_init;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
            executeStatements(connection, Files.readString(E_GROUP_SAFE_INIT_SQL));

            connection.createStatement().executeUpdate("""
                    INSERT INTO file_asset (file_id, storage_key, bucket, original_filename, content_type, size, sha256,
                        uploader_user_id, uploader_type, upload_channel, status, created_at, updated_at)
                    VALUES ('file_runtime_001', 'runtime/original.pdf', 'runtime-bucket', 'runtime.pdf', 'application/pdf',
                        4096, 'runtime-sha256', 9001, 'USER', 'SELF_UPLOAD', 'ACTIVE',
                        '2026-07-06 10:00:00', '2026-07-06 10:00:00')
                    """);

            executeStatements(connection, Files.readString(E_GROUP_SAFE_INIT_SQL));

            assertThat(countRows(connection, "file_asset", "file_id = 'file_runtime_001'")).isEqualTo(1);
            assertThat(singleString(connection, "SELECT storage_key FROM file_asset WHERE file_id = 'file_runtime_001'"))
                    .isEqualTo("runtime/original.pdf");
            assertThat(singleLong(connection, "SELECT uploader_user_id FROM file_asset WHERE file_id = 'file_runtime_001'"))
                    .isEqualTo(9001L);
            assertThat(singleString(connection, "SELECT status FROM file_asset WHERE file_id = 'file_runtime_001'"))
                    .isEqualTo("ACTIVE");
            assertThat(countRows(connection, "file_asset", "file_id = 'FILE-0008'")).isEqualTo(1);
            assertThat(countRows(connection, "public_attachment_entry", "id = 14001")).isEqualTo(1);
        }
    }

    @Test
    void shouldProvideNonDestructiveCGroupSafeInitSql() throws Exception {
        String sql = Files.readString(C_GROUP_SAFE_INIT_SQL);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `application_review_log`");
        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(sql).doesNotContain("ENGINE=");
        assertThat(sql).doesNotContain("CHARSET");
        assertThat(sql).doesNotContain("COLLATE");
        assertThat(sql).doesNotContain("COMMENT=");
        assertThat(extractCreateTableBlock(sql, "application_review_log"))
                .contains("`id` BIGINT NOT NULL AUTO_INCREMENT")
                .contains("`application_id` BIGINT NOT NULL")
                .contains("`action` VARCHAR(32) NOT NULL")
                .contains("`reviewer_id` BIGINT NOT NULL")
                .contains("`review_role` VARCHAR(64) NOT NULL")
                .contains("`reason` VARCHAR(1000) DEFAULT NULL")
                .contains("`reviewed_at` DATETIME NOT NULL")
                .contains("KEY `idx_application_review_log_application_id` (`application_id`)")
                .contains("KEY `idx_application_review_log_reviewer_id` (`reviewer_id`)");
    }

    @Test
    void shouldRerunCGroupSafeInitSqlWithoutOverwritingRuntimeReviewLogs() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:c_group_safe_init;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
            executeStatements(connection, Files.readString(C_GROUP_SAFE_INIT_SQL));

            connection.createStatement().executeUpdate("""
                    INSERT INTO application_review_log (application_id, action, reviewer_id, review_role, reason, reviewed_at)
                    VALUES (21013, 'APPROVE', 1010, 'COUNSELOR', 'runtime approve', '2026-07-07 10:00:00')
                    """);
            long reviewLogId = singleLong(connection, "SELECT id FROM application_review_log WHERE reason = 'runtime approve'");

            executeStatements(connection, Files.readString(C_GROUP_SAFE_INIT_SQL));

            assertThat(countRows(connection, "application_review_log", "id = " + reviewLogId)).isEqualTo(1);
            assertThat(singleString(connection, "SELECT action FROM application_review_log WHERE id = " + reviewLogId))
                    .isEqualTo("APPROVE");
            assertThat(singleString(connection, "SELECT reason FROM application_review_log WHERE id = " + reviewLogId))
                    .isEqualTo("runtime approve");
        }
    }

    @Test
    void shouldOnlyReferenceApprovedApplicationsInDGroupApplicationScores() throws Exception {
        String bSql = Files.readString(TEAM_DELIVERY.resolve("group-b-student-application.sql"));
        String dSql = Files.readString(TEAM_DELIVERY.resolve("group-d-score-finalization-import-export.sql"));

        Set<String> approvedApplicationIds = extractApplicationIdsByStatus(bSql, "APPROVED");
        Set<String> applicationScoreRefs = extractFinalComponentApplicationRefs(dSql);

        assertThat(applicationScoreRefs).isSubsetOf(approvedApplicationIds);
    }

    private static void executeStatements(Connection connection, String sql) throws Exception {
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isBlank()) {
                connection.createStatement().execute(trimmed);
            }
        }
    }

    private static int countRows(Connection connection, String tableName, String condition) throws Exception {
        try (var resultSet = connection.createStatement()
                .executeQuery("SELECT COUNT(*) FROM " + tableName + " WHERE " + condition)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private static String singleString(Connection connection, String sql) throws Exception {
        try (var resultSet = connection.createStatement().executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private static long singleLong(Connection connection, String sql) throws Exception {
        try (var resultSet = connection.createStatement().executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private static Set<String> extractApplicationIdsByStatus(String sql, String status) {
        String block = extractInsertBlock(sql, "application_submission");
        Matcher matcher = Pattern.compile("(?m)^\\((\\d+),.*'" + status + "'").matcher(block);
        Set<String> ids = new LinkedHashSet<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private static Set<String> extractFinalComponentApplicationRefs(String sql) {
        String block = extractInsertBlock(sql, "final_component_score");
        Matcher matcher = Pattern.compile("'APPLICATION'\\s*,\\s*'(\\d+)'").matcher(block);
        Set<String> refs = new LinkedHashSet<>();
        while (matcher.find()) {
            refs.add(matcher.group(1));
        }
        return refs;
    }

    private static Set<String> extractInsertColumns(String sql, String tableName) {
        Matcher matcher = Pattern.compile("INSERT INTO `" + tableName + "` \\(([^)]*)\\)", Pattern.DOTALL)
                .matcher(sql);
        assertThat(matcher.find()).as("insert block for %s", tableName).isTrue();
        Set<String> columns = new LinkedHashSet<>();
        for (String column : matcher.group(1).split(",")) {
            columns.add(column.replace("`", "").trim());
        }
        return columns;
    }

    private static String extractInsertBlock(String sql, String tableName) {
        Matcher matcher = Pattern.compile("INSERT INTO `" + tableName + "`[\\s\\S]*?;", Pattern.DOTALL)
                .matcher(sql);
        assertThat(matcher.find()).as("insert block for %s", tableName).isTrue();
        return matcher.group();
    }

    private static String extractCreateTableBlock(String sql, String tableName) {
        Matcher matcher = Pattern.compile("CREATE TABLE IF NOT EXISTS `" + tableName + "`[\\s\\S]*?;", Pattern.DOTALL)
                .matcher(sql);
        assertThat(matcher.find()).as("create table block for %s", tableName).isTrue();
        return matcher.group();
    }
}
