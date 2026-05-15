-- 为 student 角色补齐“查看自己的数据”权限。
-- 该脚本设计为幂等脚本：重复执行不会重复插入 permission 或 role_permission。
-- 注意：本脚本只初始化 permission 与 role_permission，不会自动创建 iam_scope_rule。
-- 如果希望学生侧最终严格只看本人数据，还需要后续为对应 assignment 补充 SELF 范围规则。

-- 1. 初始化 permission 定义
INSERT INTO iam_permission (permission_code, permission_name, status)
SELECT 'application.view.self', '查看本人申请', 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM iam_permission
    WHERE permission_code = 'application.view.self'
);

INSERT INTO iam_permission (permission_code, permission_name, status)
SELECT 'score.view.self', '查看本人成绩', 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM iam_permission
    WHERE permission_code = 'score.view.self'
);

-- 2. 绑定到 student 角色
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM iam_role r
JOIN iam_permission p ON p.permission_code = 'application.view.self'
WHERE r.role_code = 'student'
  AND NOT EXISTS (
      SELECT 1
      FROM iam_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

INSERT INTO iam_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM iam_role r
JOIN iam_permission p ON p.permission_code = 'score.view.self'
WHERE r.role_code = 'student'
  AND NOT EXISTS (
      SELECT 1
      FROM iam_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

-- 3. 可选核验 SQL
-- SELECT r.role_code, p.permission_code, p.permission_name
-- FROM iam_role_permission rp
-- JOIN iam_role r ON r.id = rp.role_id
-- JOIN iam_permission p ON p.id = rp.permission_id
-- WHERE r.role_code = 'student'
--   AND p.permission_code IN ('application.view.self', 'score.view.self');
