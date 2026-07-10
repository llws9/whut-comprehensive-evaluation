package edu.whut.eval.app.iam;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamDeliverySqlConsistencyTest {

    private static final Path ROOT = Path.of(System.getProperty("user.dir")).getParent();
    private static final Path TEAM_DELIVERY = ROOT.resolve("docs/team-delivery");
    private static final Path IAM_RESOURCE_SQL = ROOT.resolve("whut-eval-app/src/main/resources/sql/iam");
    private static final Path A_GROUP_SQL = TEAM_DELIVERY.resolve("group-a-identity-user-admin.sql");
    private static final Path B_GROUP_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-b-student-application.safe-init.sql");
    private static final Path C_GROUP_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-c-review-workflow.safe-init.sql");
    private static final Path D_GROUP_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-d-score-finalization-import-export.safe-init.sql");
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
    void shouldInitializeFullSeedChainOnAGroupSchemaAndKeepSafeInitRerunnable() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:full_seed_init_smoke;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
            executeStatements(connection, toH2CompatibleGroupASql(Files.readString(A_GROUP_SQL)));

            executeRuntimeSafeInitChain(connection);
            connection.createStatement().executeUpdate("""
                    INSERT INTO application_submission (applicant_user_id, org_unit_id, category_code, item_code, academic_year, term, title, description, status, submitted_at, created_at, updated_at, version)
                    VALUES (1001, 2010, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', '2025-2026', '上学期', 'runtime full-chain application', 'runtime desc', 'APPROVED', '2026-07-08 09:00:00', '2026-07-08 08:00:00', '2026-07-08 09:00:00', 1)
                    """);
            long applicationId = singleLong(connection, "SELECT application_id FROM application_submission WHERE title = 'runtime full-chain application'");
            connection.createStatement().executeUpdate("""
                    INSERT INTO application_fact (application_id, score_value, display_text, evidence_count, extra_json, created_at, updated_at)
                    VALUES (%d, 4.50, 'runtime approved score', 1, '{"optionCode":"PAPER_CORE"}', '2026-07-08 09:00:00', '2026-07-08 09:00:00')
                    """.formatted(applicationId));
            connection.createStatement().executeUpdate("""
                    INSERT INTO final_record (student_user_id, academic_year, status, moral_total, intellectual_total, physical_total,
                        labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at)
                    VALUES (1001, '2025-2026', 'SUBMITTED', 0.00, 4.50, 0.00, 0.00, 4.50,
                        '2026-07-08 09:10:00', NULL, NULL, 1, '2026-07-08 09:10:00', '2026-07-08 09:10:00')
                    """);
            long finalRecordId = singleLong(connection, "SELECT id FROM final_record WHERE student_user_id = 1001 AND academic_year = '2025-2026'");
            connection.createStatement().executeUpdate("""
                    INSERT INTO final_component_score (final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at)
                    VALUES (%d, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', 4.50, 'runtime approved score', 'APPLICATION', '%d', '2026-07-08 09:10:00')
                    """.formatted(finalRecordId, applicationId));

            executeRuntimeSafeInitChain(connection);

            assertThat(singleString(connection, "SELECT path FROM org_unit WHERE id = 2010"))
                    .isEqualTo("/WHUT/CS/CS2022/CS2201");
            assertThat(countRows(connection, "iam_permission", "permission_code = 'score.confirm.assigned'")).isEqualTo(1);
            assertThat(countRows(connection, "iam_role_permission", "role_id = 4003 AND permission_id = (SELECT id FROM iam_permission WHERE permission_code = 'score.confirm.assigned')")).isEqualTo(1);
            assertThat(countRows(connection, "iam_role_permission", "role_id = 4004 AND permission_id = (SELECT id FROM iam_permission WHERE permission_code = 'score.confirm.assigned')")).isEqualTo(1);
            assertThat(countRows(connection, "iam_scope_rule", "assignment_id = 7010 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002")).isEqualTo(1);
            assertThat(countRows(connection, "iam_scope_rule", "assignment_id = 7011 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002")).isEqualTo(1);
            assertThat(countRows(connection, "application_submission", "application_id = " + applicationId + " AND title = 'runtime full-chain application'")).isEqualTo(1);
            assertThat(countRows(connection, "application_fact", "application_id = " + applicationId + " AND display_text = 'runtime approved score'")).isEqualTo(1);
            assertThat(countRows(connection, "final_record", "id = " + finalRecordId + " AND status = 'SUBMITTED'")).isEqualTo(1);
            assertThat(countRows(connection, "final_component_score", "final_record_id = " + finalRecordId + " AND source_ref_id = '" + applicationId + "'")).isEqualTo(1);
            assertThat(countRows(connection, "file_asset", "file_id = 'FILE-0008'")).isEqualTo(1);
            assertThat(countRows(connection, "public_attachment_entry", "id = 14001")).isEqualTo(1);
        }
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

    @Test
    void shouldProvideNonDestructiveDGroupSafeInitSql() throws Exception {
        String sql = Files.readString(D_GROUP_SAFE_INIT_SQL);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `final_record`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `final_component_score`");
        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(sql).doesNotContain("ENGINE=");
        assertThat(sql).doesNotContain("CHARSET");
        assertThat(sql).doesNotContain("COLLATE");
        assertThat(sql).doesNotContain("COMMENT=");
        assertThat(sql).doesNotContain("RAND() * 0");
        assertThat(sql).doesNotContain("1 /");
        assertThat(sql).contains("CREATE TEMPORARY TABLE IF NOT EXISTS d_seed_collision_guard");
        assertThat(sql).contains("INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status, created_at)");
        assertThat(sql).contains("INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)");
        assertThat(sql).contains("INSERT INTO iam_scope_rule (id, assignment_id, permission_code, scope_type, org_unit_id, category_code, item_code, expression_json, priority, status, created_at)");
        assertThat(extractCreateTableBlock(sql, "final_record"))
                .contains("`id` BIGINT NOT NULL AUTO_INCREMENT")
                .contains("`student_user_id` BIGINT NOT NULL")
                .contains("`academic_year` VARCHAR(32) NOT NULL")
                .contains("`status` VARCHAR(32) NOT NULL")
                .contains("`moral_total` DECIMAL(10,2) NOT NULL")
                .contains("`intellectual_total` DECIMAL(10,2) NOT NULL")
                .contains("`physical_total` DECIMAL(10,2) NOT NULL")
                .contains("`labor_total` DECIMAL(10,2) NOT NULL")
                .contains("`grand_total` DECIMAL(10,2) NOT NULL")
                .contains("`submitted_at` DATETIME DEFAULT NULL")
                .contains("`confirmed_at` DATETIME DEFAULT NULL")
                .contains("`confirm_comment` VARCHAR(1000) DEFAULT NULL")
                .contains("`version` BIGINT NOT NULL")
                .contains("`created_at` DATETIME NOT NULL")
                .contains("`updated_at` DATETIME NOT NULL")
                .contains("UNIQUE KEY `uk_final_record_student_year` (`student_user_id`, `academic_year`)")
                .contains("KEY `idx_final_record_student_user_id` (`student_user_id`)")
                .contains("KEY `idx_final_record_academic_year` (`academic_year`)")
                .contains("KEY `idx_final_record_status` (`status`)");
        assertThat(extractCreateTableBlock(sql, "final_component_score"))
                .contains("`id` BIGINT NOT NULL AUTO_INCREMENT")
                .contains("`final_record_id` BIGINT NOT NULL")
                .contains("`category_code` VARCHAR(64) NOT NULL")
                .contains("`item_code` VARCHAR(64) NOT NULL")
                .contains("`score_value` DECIMAL(10,2) NOT NULL")
                .contains("`display_text` VARCHAR(1000) DEFAULT NULL")
                .contains("`source_type` VARCHAR(32) NOT NULL")
                .contains("`source_ref_id` VARCHAR(64) DEFAULT NULL")
                .contains("`created_at` DATETIME NOT NULL")
                .contains("KEY `idx_final_component_score_record_id` (`final_record_id`)")
                .contains("KEY `idx_final_component_score_category_code` (`category_code`)")
                .contains("KEY `idx_final_component_score_item_code` (`item_code`)");
    }

    @Test
    void shouldRerunDGroupSafeInitSqlWithoutOverwritingRuntimeFinalRecords() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:d_group_safe_init;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
            createMinimalIamTables(connection);
            executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL));
            connection.createStatement().executeUpdate("""
                    INSERT INTO final_record (student_user_id, academic_year, status, moral_total, intellectual_total, physical_total,
                        labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at)
                    VALUES (1001, '2025-2026', 'SUBMITTED', 0.80, 5.00, 0.60, 1.20, 7.60,
                        '2026-07-07 12:00:00', NULL, NULL, 1, '2026-07-07 12:00:00', '2026-07-07 12:00:00')
                    """);
            long recordId = singleLong(connection, "SELECT id FROM final_record WHERE student_user_id = 1001 AND academic_year = '2025-2026'");
            connection.createStatement().executeUpdate("""
                    INSERT INTO final_component_score (final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at)
                    VALUES (%d, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', 2.00, 'runtime component', 'APPLICATION', '21013', '2026-07-07 12:00:00')
                    """.formatted(recordId));

            executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL));

            assertThat(countRows(connection, "final_record", "id = " + recordId)).isEqualTo(1);
            assertThat(singleString(connection, "SELECT status FROM final_record WHERE id = " + recordId)).isEqualTo("SUBMITTED");
            assertThat(singleString(connection, "SELECT display_text FROM final_component_score WHERE final_record_id = " + recordId))
                    .isEqualTo("runtime component");
        }
    }

    @Test
    void shouldSeedScoreConfirmPermissionUsingExistingNaturalKeyWhenIdDiffers() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:d_confirm_permission_existing_code;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
            createMinimalIamTables(connection);
            connection.createStatement().executeUpdate("""
                    INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status, created_at)
                    VALUES (9099, 'score.confirm.assigned', '已有确认权限', 'score', 'ACTIVE', CURRENT_TIMESTAMP())
                    """);

            executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL));

            assertThat(singleLong(connection, "SELECT id FROM iam_permission WHERE permission_code = 'score.confirm.assigned'"))
                    .isEqualTo(9099L);
            assertThat(countRows(connection, "iam_role_permission", "role_id = 4003 AND permission_id = 9099")).isEqualTo(1);
            assertThat(countRows(connection, "iam_role_permission", "role_id = 4004 AND permission_id = 9099")).isEqualTo(1);
            assertThat(countRows(connection, "iam_role_permission", "permission_id = 5023")).isEqualTo(0);
        }
    }

    @Test
    void shouldRerunDPermissionSeedWithoutDuplicateBindings() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:d_confirm_permission_rerun;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
            createMinimalIamTables(connection);

            executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL));
            executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL));

            long permissionId = singleLong(connection, "SELECT id FROM iam_permission WHERE permission_code = 'score.confirm.assigned'");
            assertThat(permissionId).isEqualTo(5023L);
            assertThat(countRows(connection, "iam_permission", "permission_code = 'score.confirm.assigned'")).isEqualTo(1);
            assertThat(countRows(connection, "iam_role_permission", "role_id = 4003 AND permission_id = " + permissionId)).isEqualTo(1);
            assertThat(countRows(connection, "iam_role_permission", "role_id = 4004 AND permission_id = " + permissionId)).isEqualTo(1);
            assertThat(countRows(connection, "iam_scope_rule", "assignment_id = 7010 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002")).isEqualTo(1);
            assertThat(countRows(connection, "iam_scope_rule", "assignment_id = 7011 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002")).isEqualTo(1);
        }
    }

    @Test
    void shouldFailDPermissionSeedWhenReservedIdsAreOccupiedByUnrelatedRows() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:d_confirm_permission_collision;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
            createMinimalIamTables(connection);
            connection.createStatement().executeUpdate("""
                    INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status, created_at)
                    VALUES (5023, 'unrelated.permission', '占用固定编号', 'test', 'ACTIVE', CURRENT_TIMESTAMP())
                    """);

            assertThatThrownBy(() -> executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL)))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static void executeStatements(Connection connection, String sql) throws Exception {
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isBlank()) {
                connection.createStatement().execute(trimmed);
            }
        }
    }

    private static void executeRuntimeSafeInitChain(Connection connection) throws Exception {
        executeStatements(connection, Files.readString(E_GROUP_SAFE_INIT_SQL));
        executeStatements(connection, Files.readString(B_GROUP_SAFE_INIT_SQL));
        executeStatements(connection, Files.readString(C_GROUP_SAFE_INIT_SQL));
        executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL));
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

    private static void createMinimalIamTables(Connection connection) throws Exception {
        executeStatements(connection, """
                CREATE TABLE iam_permission (
                  id BIGINT NOT NULL,
                  permission_code VARCHAR(128) NOT NULL,
                  permission_name VARCHAR(128) NOT NULL,
                  permission_group VARCHAR(64) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  created_at DATETIME NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_iam_permission_code (permission_code)
                );
                CREATE TABLE iam_role_permission (
                  id BIGINT NOT NULL,
                  role_id BIGINT NOT NULL,
                  permission_id BIGINT NOT NULL,
                  created_at DATETIME NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_iam_role_permission_role_permission (role_id, permission_id)
                );
                CREATE TABLE iam_scope_rule (
                  id BIGINT NOT NULL,
                  assignment_id BIGINT NOT NULL,
                  permission_code VARCHAR(128) NOT NULL,
                  scope_type VARCHAR(64) NOT NULL,
                  org_unit_id BIGINT DEFAULT NULL,
                  category_code VARCHAR(64) DEFAULT NULL,
                  item_code VARCHAR(64) DEFAULT NULL,
                  expression_json VARCHAR(2000) DEFAULT NULL,
                  priority INT NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  created_at DATETIME NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_iam_scope_rule_natural (assignment_id, permission_code, scope_type, org_unit_id)
                );
                """);
    }

    private static String toH2CompatibleGroupASql(String sql) {
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

    private static String extractCreateTableBlock(String sql, String tableName) {
        Matcher matcher = Pattern.compile("CREATE TABLE IF NOT EXISTS `" + tableName + "`[\\s\\S]*?;", Pattern.DOTALL)
                .matcher(sql);
        assertThat(matcher.find()).as("create table block for %s", tableName).isTrue();
        return matcher.group();
    }
}
