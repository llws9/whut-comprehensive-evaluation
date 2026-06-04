-- 为 STUDENT 角色的有效 assignment 补齐 SELF 范围规则。
-- 该脚本设计为幂等脚本：重复执行不会重复插入相同 assignment + permission_code + scope_type 的规则。
-- 前置条件：
-- 1. STUDENT 角色已存在；
-- 2. STUDENT 角色已具备 application.view.self / score.view.self 权限；
-- 3. 目标 assignment 已处于有效状态。

-- 1. 为 application.view.self 补齐 SELF 规则
INSERT INTO iam_scope_rule (
    id,
    assignment_id,
    permission_code,
    scope_type,
    org_unit_id,
    category_code,
    item_code,
    expression_json,
    priority,
    status,
    created_at
)
SELECT
    81000 + ura.id,
    ura.id,
    'application.view.self',
    'SELF',
    NULL,
    NULL,
    NULL,
    JSON_OBJECT('owner', 'self'),
    10,
    'ACTIVE',
    '2026-06-05 00:00:00'
FROM iam_user_role_assignment ura
JOIN iam_role r ON r.id = ura.role_id
WHERE r.role_code = 'STUDENT'
  AND ura.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM iam_scope_rule sr
      WHERE sr.assignment_id = ura.id
        AND sr.permission_code = 'application.view.self'
        AND sr.scope_type = 'SELF'
        AND sr.status = 'ACTIVE'
  );

-- 2. 为 score.view.self 补齐 SELF 规则
INSERT INTO iam_scope_rule (
    id,
    assignment_id,
    permission_code,
    scope_type,
    org_unit_id,
    category_code,
    item_code,
    expression_json,
    priority,
    status,
    created_at
)
SELECT
    82000 + ura.id,
    ura.id,
    'score.view.self',
    'SELF',
    NULL,
    NULL,
    NULL,
    JSON_OBJECT('owner', 'self'),
    10,
    'ACTIVE',
    '2026-06-05 00:00:00'
FROM iam_user_role_assignment ura
JOIN iam_role r ON r.id = ura.role_id
WHERE r.role_code = 'STUDENT'
  AND ura.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM iam_scope_rule sr
      WHERE sr.assignment_id = ura.id
        AND sr.permission_code = 'score.view.self'
        AND sr.scope_type = 'SELF'
        AND sr.status = 'ACTIVE'
  );

-- 3. 可选核验 SQL
-- SELECT
--     ura.id AS assignment_id,
--     r.role_code,
--     sr.permission_code,
--     sr.scope_type,
--     sr.priority,
--     sr.status
-- FROM iam_scope_rule sr
-- JOIN iam_user_role_assignment ura ON ura.id = sr.assignment_id
-- JOIN iam_role r ON r.id = ura.role_id
-- WHERE r.role_code = 'STUDENT'
--   AND sr.permission_code IN ('application.view.self', 'score.view.self')
-- ORDER BY ura.id, sr.permission_code;
