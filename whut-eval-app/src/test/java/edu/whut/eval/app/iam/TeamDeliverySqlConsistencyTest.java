package edu.whut.eval.app.iam;

import org.junit.jupiter.api.Test;

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
    void shouldKeepEGroupFileAssetSchemaAlignedWithUploadMapper() throws Exception {
        String sql = Files.readString(TEAM_DELIVERY.resolve("group-e-platform-governance-attachment-ai.sql"));

        assertThat(sql).contains("`sha256` VARCHAR(128) DEFAULT NULL");
        assertThat(extractInsertColumns(sql, "file_asset")).contains("sha256");
    }

    @Test
    void shouldOnlyReferenceApprovedApplicationsInDGroupApplicationScores() throws Exception {
        String bSql = Files.readString(TEAM_DELIVERY.resolve("group-b-student-application.sql"));
        String dSql = Files.readString(TEAM_DELIVERY.resolve("group-d-score-finalization-import-export.sql"));

        Set<String> approvedApplicationIds = extractApplicationIdsByStatus(bSql, "APPROVED");
        Set<String> applicationScoreRefs = extractFinalComponentApplicationRefs(dSql);

        assertThat(applicationScoreRefs).isSubsetOf(approvedApplicationIds);
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
        Matcher matcher = Pattern.compile("INSERT INTO `" + tableName + "` \\(([^)]*)\\) VALUES", Pattern.DOTALL)
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
}
