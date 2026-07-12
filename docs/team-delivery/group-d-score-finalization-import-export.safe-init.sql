CREATE TABLE IF NOT EXISTS `final_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_user_id` BIGINT NOT NULL,
  `academic_year` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `moral_total` DECIMAL(10,2) NOT NULL,
  `intellectual_total` DECIMAL(10,2) NOT NULL,
  `physical_total` DECIMAL(10,2) NOT NULL,
  `labor_total` DECIMAL(10,2) NOT NULL,
  `grand_total` DECIMAL(10,2) NOT NULL,
  `submitted_at` DATETIME DEFAULT NULL,
  `confirmed_at` DATETIME DEFAULT NULL,
  `confirm_comment` VARCHAR(1000) DEFAULT NULL,
  `version` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_final_record_student_year` (`student_user_id`, `academic_year`),
  KEY `idx_final_record_student_user_id` (`student_user_id`),
  KEY `idx_final_record_academic_year` (`academic_year`),
  KEY `idx_final_record_status` (`status`)
);

CREATE TABLE IF NOT EXISTS `final_component_score` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `final_record_id` BIGINT NOT NULL,
  `category_code` VARCHAR(64) NOT NULL,
  `item_code` VARCHAR(64) NOT NULL,
  `score_value` DECIMAL(10,2) NOT NULL,
  `display_text` VARCHAR(1000) DEFAULT NULL,
  `source_type` VARCHAR(32) NOT NULL,
  `source_ref_id` VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_final_component_score_record_id` (`final_record_id`),
  KEY `idx_final_component_score_category_code` (`category_code`),
  KEY `idx_final_component_score_item_code` (`item_code`)
);

CREATE TEMPORARY TABLE IF NOT EXISTS d_seed_collision_guard (
  id BIGINT NOT NULL PRIMARY KEY
);
DELETE FROM d_seed_collision_guard;
INSERT INTO d_seed_collision_guard (id) VALUES (1);
INSERT INTO d_seed_collision_guard (id)
SELECT 1
WHERE EXISTS (SELECT 1 FROM iam_permission WHERE id = 5023 AND permission_code <> 'score.confirm.assigned')
   OR EXISTS (SELECT 1 FROM iam_permission WHERE id = 5024 AND permission_code <> 'score.import')
   OR EXISTS (
     SELECT 1 FROM iam_role_permission WHERE id = 6048 AND NOT EXISTS (
       SELECT 1 FROM iam_permission p WHERE p.permission_code = 'score.confirm.assigned' AND p.id = iam_role_permission.permission_id AND iam_role_permission.role_id = 4003
     )
   )
   OR EXISTS (
     SELECT 1 FROM iam_role_permission WHERE id = 6049 AND NOT EXISTS (
       SELECT 1 FROM iam_permission p WHERE p.permission_code = 'score.confirm.assigned' AND p.id = iam_role_permission.permission_id AND iam_role_permission.role_id = 4004
     )
   )
   OR EXISTS (
     SELECT 1 FROM iam_role_permission WHERE id = 6050 AND NOT EXISTS (
       SELECT 1 FROM iam_permission p WHERE p.permission_code = 'score.import' AND p.id = iam_role_permission.permission_id AND iam_role_permission.role_id = 4003
     )
   )
   OR EXISTS (
     SELECT 1 FROM iam_role_permission WHERE id = 6051 AND NOT EXISTS (
       SELECT 1 FROM iam_permission p WHERE p.permission_code = 'score.import' AND p.id = iam_role_permission.permission_id AND iam_role_permission.role_id = 4004
     )
   )
   OR EXISTS (SELECT 1 FROM iam_scope_rule WHERE id = 8019 AND NOT (assignment_id = 7010 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002))
   OR EXISTS (SELECT 1 FROM iam_scope_rule WHERE id = 8020 AND NOT (assignment_id = 7011 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002))
   OR EXISTS (SELECT 1 FROM iam_scope_rule WHERE id = 8021 AND NOT (assignment_id = 7010 AND permission_code = 'score.import' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002))
   OR EXISTS (SELECT 1 FROM iam_scope_rule WHERE id = 8022 AND NOT (assignment_id = 7011 AND permission_code = 'score.import' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002));

INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status, created_at)
SELECT 5023, 'score.confirm.assigned', '确认授权范围最终成绩', 'score', 'ACTIVE', CURRENT_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM iam_permission WHERE permission_code = 'score.confirm.assigned');

INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status, created_at)
SELECT 5024, 'score.import', '导入导师/固定成绩', 'score', 'ACTIVE', CURRENT_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM iam_permission WHERE permission_code = 'score.import');

INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT 6048, 4003, p.id, CURRENT_TIMESTAMP()
FROM iam_permission p
WHERE p.permission_code = 'score.confirm.assigned'
  AND NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
    WHERE rp.role_id = 4003 AND rp.permission_id = p.id
  );

INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT 6049, 4004, p.id, CURRENT_TIMESTAMP()
FROM iam_permission p
WHERE p.permission_code = 'score.confirm.assigned'
  AND NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
    WHERE rp.role_id = 4004 AND rp.permission_id = p.id
  );

INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT 6050, 4003, p.id, CURRENT_TIMESTAMP()
FROM iam_permission p
WHERE p.permission_code = 'score.import'
  AND NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
    WHERE rp.role_id = 4003 AND rp.permission_id = p.id
  );

INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT 6051, 4004, p.id, CURRENT_TIMESTAMP()
FROM iam_permission p
WHERE p.permission_code = 'score.import'
  AND NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
    WHERE rp.role_id = 4004 AND rp.permission_id = p.id
  );

INSERT INTO iam_scope_rule (id, assignment_id, permission_code, scope_type, org_unit_id, category_code, item_code, expression_json, priority, status, created_at)
SELECT 8019, 7010, 'score.confirm.assigned', 'ORG_SUBTREE', 2002, NULL, NULL, '{"scoreRole":"counselor"}', 80, 'ACTIVE', CURRENT_TIMESTAMP()
WHERE NOT EXISTS (
  SELECT 1 FROM iam_scope_rule
  WHERE assignment_id = 7010 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002
);

INSERT INTO iam_scope_rule (id, assignment_id, permission_code, scope_type, org_unit_id, category_code, item_code, expression_json, priority, status, created_at)
SELECT 8020, 7011, 'score.confirm.assigned', 'ORG_SUBTREE', 2002, NULL, NULL, '{"scoreRole":"college_reviewer"}', 70, 'ACTIVE', CURRENT_TIMESTAMP()
WHERE NOT EXISTS (
  SELECT 1 FROM iam_scope_rule
  WHERE assignment_id = 7011 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002
);

INSERT INTO iam_scope_rule (id, assignment_id, permission_code, scope_type, org_unit_id, category_code, item_code, expression_json, priority, status, created_at)
SELECT 8021, 7010, 'score.import', 'ORG_SUBTREE', 2002, NULL, NULL, '{"scoreRole":"counselor"}', 80, 'ACTIVE', CURRENT_TIMESTAMP()
WHERE NOT EXISTS (
  SELECT 1 FROM iam_scope_rule
  WHERE assignment_id = 7010 AND permission_code = 'score.import' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002
);

INSERT INTO iam_scope_rule (id, assignment_id, permission_code, scope_type, org_unit_id, category_code, item_code, expression_json, priority, status, created_at)
SELECT 8022, 7011, 'score.import', 'ORG_SUBTREE', 2002, NULL, NULL, '{"scoreRole":"college_reviewer"}', 70, 'ACTIVE', CURRENT_TIMESTAMP()
WHERE NOT EXISTS (
  SELECT 1 FROM iam_scope_rule
  WHERE assignment_id = 7011 AND permission_code = 'score.import' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002
);
