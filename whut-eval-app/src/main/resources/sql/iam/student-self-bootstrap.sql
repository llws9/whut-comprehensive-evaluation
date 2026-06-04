-- 一次性初始化 STUDENT 角色的 self 权限与 SELF 范围规则。
-- 该脚本设计为幂等脚本：重复执行不会重复插入已有 permission、role_permission、scope_rule。
--
-- 执行目标：
-- 1. 创建 application.view.self / score.view.self 权限；
-- 2. 将上述权限绑定到 STUDENT 角色；
-- 3. 为 STUDENT 角色下的有效 assignment 补齐 SELF 范围规则。

-- 1. 初始化 permission 定义
INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status, created_at)
SELECT 5004, 'application.view.self', '查看本人申请', 'application', 'ACTIVE', '2026-05-01 09:30:03'
WHERE NOT EXISTS (
    SELECT 1
    FROM iam_permission
    WHERE permission_code = 'application.view.self'
);

INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status, created_at)
SELECT 5009, 'score.view.self', '查看本人成绩', 'score', 'ACTIVE', '2026-05-01 09:30:08'
WHERE NOT EXISTS (
    SELECT 1
    FROM iam_permission
    WHERE permission_code = 'score.view.self'
);

-- 2. 绑定到 STUDENT 角色
INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT 6004, r.id, p.id, '2026-05-01 09:40:03'
FROM iam_role r
JOIN iam_permission p ON p.permission_code = 'application.view.self'
WHERE r.role_code = 'STUDENT'
  AND NOT EXISTS (
      SELECT 1
      FROM iam_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT 6005, r.id, p.id, '2026-05-01 09:40:04'
FROM iam_role r
JOIN iam_permission p ON p.permission_code = 'score.view.self'
WHERE r.role_code = 'STUDENT'
  AND NOT EXISTS (
      SELECT 1
      FROM iam_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

-- 3. 为 application.view.self 补齐 SELF 规则
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

-- 4. 为 score.view.self 补齐 SELF 规则
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

-- 5. 可选核验 SQL：查看 STUDENT 角色已有权限
-- SELECT r.role_code, p.permission_code, p.permission_name
-- FROM iam_role_permission rp
-- JOIN iam_role r ON r.id = rp.role_id
-- JOIN iam_permission p ON p.id = rp.permission_id
-- WHERE r.role_code = 'STUDENT'
--   AND p.permission_code IN ('application.view.self', 'score.view.self');

-- 6. 可选核验 SQL：查看 STUDENT assignment 已有 SELF 规则
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
