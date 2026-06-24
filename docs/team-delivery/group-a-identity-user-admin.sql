-- A 组：身份、组织、角色、权限、范围规则、会话
-- 负责表：
--   iam_user
--   org_unit
--   org_membership
--   iam_role
--   iam_permission
--   iam_role_permission
--   iam_user_role_assignment
--   iam_scope_rule
--   iam_session
--
-- 说明：
-- 1. 本脚本为 MySQL 8.x 口径。
-- 2. 角色、权限相关表按“覆盖典型角色与权限组合”为目标，样例数量不强制 10+。

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `iam_session`;
DROP TABLE IF EXISTS `iam_scope_rule`;
DROP TABLE IF EXISTS `iam_user_role_assignment`;
DROP TABLE IF EXISTS `iam_role_permission`;
DROP TABLE IF EXISTS `iam_permission`;
DROP TABLE IF EXISTS `iam_role`;
DROP TABLE IF EXISTS `org_membership`;
DROP TABLE IF EXISTS `org_unit`;
DROP TABLE IF EXISTS `iam_user`;

CREATE TABLE `iam_user` (
  `id` BIGINT NOT NULL,
  `user_no` VARCHAR(64) NOT NULL,
  `user_name` VARCHAR(128) NOT NULL,
  `email` VARCHAR(128) DEFAULT NULL,
  `phone` VARCHAR(32) DEFAULT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_user_user_no` (`user_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主表';

CREATE TABLE `org_unit` (
  `id` BIGINT NOT NULL,
  `parent_id` BIGINT DEFAULT NULL,
  `unit_type` VARCHAR(32) NOT NULL,
  `unit_code` VARCHAR(64) NOT NULL,
  `unit_name` VARCHAR(128) NOT NULL,
  `path` VARCHAR(1024) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_unit_unit_code` (`unit_code`),
  KEY `idx_org_unit_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织树';

CREATE TABLE `org_membership` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `org_unit_id` BIGINT NOT NULL,
  `membership_type` VARCHAR(32) NOT NULL,
  `is_primary` TINYINT(1) NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `joined_at` DATETIME NOT NULL,
  `left_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_org_membership_user_id` (`user_id`),
  KEY `idx_org_membership_org_unit_id` (`org_unit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户组织归属';

CREATE TABLE `iam_role` (
  `id` BIGINT NOT NULL,
  `role_code` VARCHAR(64) NOT NULL,
  `role_name` VARCHAR(128) NOT NULL,
  `role_scope` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_role_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色模板';

CREATE TABLE `iam_permission` (
  `id` BIGINT NOT NULL,
  `permission_code` VARCHAR(128) NOT NULL,
  `permission_name` VARCHAR(128) NOT NULL,
  `permission_group` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限字典';

CREATE TABLE `iam_role_permission` (
  `id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_iam_role_permission_role_id` (`role_id`),
  KEY `idx_iam_role_permission_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关系';

CREATE TABLE `iam_user_role_assignment` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `org_unit_id` BIGINT NOT NULL,
  `source_type` VARCHAR(32) NOT NULL,
  `effective_from` DATETIME NOT NULL,
  `effective_to` DATETIME DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `assigned_by` BIGINT DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_assignment_user_id` (`user_id`),
  KEY `idx_assignment_role_id` (`role_id`),
  KEY `idx_assignment_org_unit_id` (`org_unit_id`),
  KEY `idx_assignment_status_time_window` (`user_id`, `role_id`, `org_unit_id`, `status`, `effective_from`, `effective_to`),
  CONSTRAINT `fk_assignment_user_id` FOREIGN KEY (`user_id`) REFERENCES `iam_user` (`id`),
  CONSTRAINT `fk_assignment_role_id` FOREIGN KEY (`role_id`) REFERENCES `iam_role` (`id`),
  CONSTRAINT `fk_assignment_org_unit_id` FOREIGN KEY (`org_unit_id`) REFERENCES `org_unit` (`id`),
  CONSTRAINT `fk_assignment_assigned_by` FOREIGN KEY (`assigned_by`) REFERENCES `iam_user` (`id`),
  CONSTRAINT `chk_assignment_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色分配';

CREATE TABLE `iam_scope_rule` (
  `id` BIGINT NOT NULL,
  `assignment_id` BIGINT NOT NULL,
  `permission_code` VARCHAR(128) NOT NULL,
  `scope_type` VARCHAR(32) NOT NULL,
  `org_unit_id` BIGINT DEFAULT NULL,
  `category_code` VARCHAR(64) DEFAULT NULL,
  `item_code` VARCHAR(64) DEFAULT NULL,
  `expression_json` JSON DEFAULT NULL,
  `priority` INT NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_scope_rule_assignment_id` (`assignment_id`),
  KEY `idx_scope_rule_permission_code` (`permission_code`),
  KEY `idx_scope_rule_lookup` (`assignment_id`, `permission_code`, `scope_type`, `status`, `priority`),
  CONSTRAINT `fk_scope_rule_assignment_id` FOREIGN KEY (`assignment_id`) REFERENCES `iam_user_role_assignment` (`id`),
  CONSTRAINT `fk_scope_rule_org_unit_id` FOREIGN KEY (`org_unit_id`) REFERENCES `org_unit` (`id`),
  CONSTRAINT `chk_scope_rule_status` CHECK (`status` IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT `chk_scope_rule_scope_type` CHECK (`scope_type` IN ('SELF', 'ALL', 'ORG_UNIT', 'ORG_SUBTREE', 'CATEGORY', 'ITEM', 'ORG_UNIT_ITEM', 'CUSTOM_EXPRESSION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据范围规则';

CREATE TABLE `iam_session` (
  `id` BIGINT NOT NULL,
  `session_no` VARCHAR(64) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `access_token_id` VARCHAR(128) NOT NULL,
  `refresh_token_id` VARCHAR(128) NOT NULL,
  `device_type` VARCHAR(32) DEFAULT NULL,
  `client_ip` VARCHAR(64) NOT NULL,
  `user_agent` VARCHAR(255) DEFAULT NULL,
  `expired_at` DATETIME NOT NULL,
  `revoked_at` DATETIME DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_session_session_no` (`session_no`),
  UNIQUE KEY `uk_iam_session_access_token_id` (`access_token_id`),
  UNIQUE KEY `uk_iam_session_refresh_token_id` (`refresh_token_id`),
  KEY `idx_iam_session_user_id` (`user_id`),
  KEY `idx_iam_session_status` (`status`),
  CONSTRAINT `chk_iam_session_status` CHECK (`status` IN ('ACTIVE', 'REVOKED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录会话';

INSERT INTO `iam_user` (`id`, `user_no`, `user_name`, `email`, `phone`, `password_hash`, `status`, `created_at`, `updated_at`) VALUES
(1001, '2022010101', '张晨', 'zhangchen@whut.edu.cn', '13800000001', '9a4aabf0e5cf71cae2cea646613ce7e2a5919fa758e56819704be25a3a2c1f0b', 'ACTIVE', '2026-05-01 08:00:00', '2026-05-18 09:20:00'),
(1002, '2022010102', '李悦', 'liyue@whut.edu.cn', '13800000002', '9a4aabf0e5cf71cae2cea646613ce7e2a5919fa758e56819704be25a3a2c1f0b', 'ACTIVE', '2026-05-01 08:05:00', '2026-05-18 09:22:00'),
(1003, '2022010103', '王澈', 'wangche@whut.edu.cn', '13800000003', '9a4aabf0e5cf71cae2cea646613ce7e2a5919fa758e56819704be25a3a2c1f0b', 'ACTIVE', '2026-05-01 08:10:00', '2026-05-18 09:24:00'),
(1004, '2022010104', '周宁', 'zhouning@whut.edu.cn', '13800000004', '9a4aabf0e5cf71cae2cea646613ce7e2a5919fa758e56819704be25a3a2c1f0b', 'ACTIVE', '2026-05-01 08:15:00', '2026-05-18 09:26:00'),
(1005, '2022010105', '陈曦', 'chenxi@whut.edu.cn', '13800000005', '9a4aabf0e5cf71cae2cea646613ce7e2a5919fa758e56819704be25a3a2c1f0b', 'ACTIVE', '2026-05-01 08:20:00', '2026-05-18 09:28:00'),
(1006, '2022010106', '何川', 'hechuan@whut.edu.cn', '13800000006', '9a4aabf0e5cf71cae2cea646613ce7e2a5919fa758e56819704be25a3a2c1f0b', 'LOCKED', '2026-05-01 08:25:00', '2026-05-17 21:18:00'),
(1007, '2022010107', '孙琪', 'sunqi@whut.edu.cn', '13800000007', '9a4aabf0e5cf71cae2cea646613ce7e2a5919fa758e56819704be25a3a2c1f0b', 'ACTIVE', '2026-05-01 08:30:00', '2026-05-18 09:32:00'),
(1008, '2022010108', '赵航', 'zhaohang@whut.edu.cn', '13800000008', '9a4aabf0e5cf71cae2cea646613ce7e2a5919fa758e56819704be25a3a2c1f0b', 'ACTIVE', '2026-05-01 08:35:00', '2026-05-18 09:34:00'),
(1009, '2022010109', '黄钰', 'huangyu@whut.edu.cn', '13800000009', '9a4aabf0e5cf71cae2cea646613ce7e2a5919fa758e56819704be25a3a2c1f0b', 'DISABLED', '2026-05-01 08:40:00', '2026-05-15 17:40:00'),
(1010, 'T20260001', '辅导员刘敏', 'liumin@whut.edu.cn', '13900000010', '9a4aabf0e5cf71cae2cea646613ce7e2a5919fa758e56819704be25a3a2c1f0b', 'ACTIVE', '2026-05-01 08:45:00', '2026-05-18 10:00:00'),
(1011, 'T20260002', '审核老师郭峰', 'guofeng@whut.edu.cn', '13900000011', '9a4aabf0e5cf71cae2cea646613ce7e2a5919fa758e56819704be25a3a2c1f0b', 'ACTIVE', '2026-05-01 08:50:00', '2026-05-18 10:02:00'),
(1012, 'A20260001', '平台管理员孟凡', 'mengfan@whut.edu.cn', '13900000012', '9a4aabf0e5cf71cae2cea646613ce7e2a5919fa758e56819704be25a3a2c1f0b', 'ACTIVE', '2026-05-01 08:55:00', '2026-05-18 10:04:00');

INSERT INTO `org_unit` (`id`, `parent_id`, `unit_type`, `unit_code`, `unit_name`, `path`, `status`) VALUES
(2001, NULL, 'SCHOOL', 'WHUT', '武汉理工大学', '/WHUT', 'ACTIVE'),
(2002, 2001, 'COLLEGE', 'CS', '计算机与人工智能学院', '/WHUT/CS', 'ACTIVE'),
(2003, 2001, 'COLLEGE', 'ART', '艺术与设计学院', '/WHUT/ART', 'ACTIVE'),
(2004, 2001, 'COLLEGE', 'ME', '机电工程学院', '/WHUT/ME', 'ACTIVE'),
(2005, 2002, 'GRADE', 'CS2022', '计算机 2022 级', '/WHUT/CS/CS2022', 'ACTIVE'),
(2006, 2002, 'GRADE', 'CS2023', '计算机 2023 级', '/WHUT/CS/CS2023', 'ACTIVE'),
(2007, 2003, 'GRADE', 'ART2022', '艺术设计 2022 级', '/WHUT/ART/ART2022', 'ACTIVE'),
(2008, 2004, 'GRADE', 'ME2022', '机电工程 2022 级', '/WHUT/ME/ME2022', 'ACTIVE'),
(2009, 2002, 'DEPARTMENT', 'CS_YB', '计算机学院研工办', '/WHUT/CS/CS_YB', 'ACTIVE'),
(2010, 2005, 'CLASS', 'CS2201', '计算机 2201 班', '/WHUT/CS/CS2022/CS2201', 'ACTIVE'),
(2011, 2005, 'CLASS', 'CS2202', '计算机 2202 班', '/WHUT/CS/CS2022/CS2202', 'ACTIVE'),
(2012, 2007, 'CLASS', 'ART2201', '艺术设计 2201 班', '/WHUT/ART/ART2022/ART2201', 'ACTIVE');

INSERT INTO `org_membership` (`id`, `user_id`, `org_unit_id`, `membership_type`, `is_primary`, `status`, `joined_at`, `left_at`, `created_at`) VALUES
(3001, 1001, 2010, 'STUDENT', 1, 'ACTIVE', '2022-09-01 08:00:00', NULL, '2026-05-01 09:00:00'),
(3002, 1002, 2010, 'STUDENT', 1, 'ACTIVE', '2022-09-01 08:00:00', NULL, '2026-05-01 09:01:00'),
(3003, 1003, 2010, 'STUDENT', 1, 'ACTIVE', '2022-09-01 08:00:00', NULL, '2026-05-01 09:02:00'),
(3004, 1004, 2011, 'STUDENT', 1, 'ACTIVE', '2022-09-01 08:00:00', NULL, '2026-05-01 09:03:00'),
(3005, 1005, 2011, 'STUDENT', 1, 'ACTIVE', '2022-09-01 08:00:00', NULL, '2026-05-01 09:04:00'),
(3006, 1006, 2011, 'STUDENT', 1, 'ACTIVE', '2022-09-01 08:00:00', NULL, '2026-05-01 09:05:00'),
(3007, 1007, 2012, 'STUDENT', 1, 'ACTIVE', '2022-09-01 08:00:00', NULL, '2026-05-01 09:06:00'),
(3008, 1008, 2012, 'STUDENT', 1, 'ACTIVE', '2022-09-01 08:00:00', NULL, '2026-05-01 09:07:00'),
(3009, 1009, 2012, 'STUDENT', 1, 'INACTIVE', '2022-09-01 08:00:00', '2026-05-15 18:00:00', '2026-05-01 09:08:00'),
(3010, 1010, 2002, 'COUNSELOR', 1, 'ACTIVE', '2024-09-01 08:00:00', NULL, '2026-05-01 09:09:00'),
(3011, 1011, 2002, 'TEACHER', 1, 'ACTIVE', '2024-09-01 08:00:00', NULL, '2026-05-01 09:10:00'),
(3012, 1012, 2001, 'ADMIN', 1, 'ACTIVE', '2024-09-01 08:00:00', NULL, '2026-05-01 09:11:00');

INSERT INTO `iam_role` (`id`, `role_code`, `role_name`, `role_scope`, `status`, `created_at`) VALUES
(4001, 'STUDENT', '学生', 'SELF', 'ACTIVE', '2026-05-01 09:20:00'),
(4002, 'CLASS_MONITOR', '班长', 'ORG_UNIT', 'ACTIVE', '2026-05-01 09:21:00'),
(4003, 'COUNSELOR', '辅导员', 'ORG_SUBTREE', 'ACTIVE', '2026-05-01 09:22:00'),
(4004, 'COLLEGE_REVIEWER', '学院审核员', 'ORG_SUBTREE', 'ACTIVE', '2026-05-01 09:23:00'),
(4005, 'COLLEGE_ADMIN', '学院管理员', 'ORG_SUBTREE', 'ACTIVE', '2026-05-01 09:24:00'),
(4006, 'PLATFORM_ADMIN', '平台管理员', 'ALL', 'ACTIVE', '2026-05-01 09:25:00');

INSERT INTO `iam_permission` (`id`, `permission_code`, `permission_name`, `permission_group`, `status`, `created_at`) VALUES
(5001, 'application.submit', '提交申请', 'application', 'ACTIVE', '2026-05-01 09:30:00'),
(5002, 'application.update', '修改申请', 'application', 'ACTIVE', '2026-05-01 09:30:01'),
(5003, 'application.delete', '删除申请', 'application', 'ACTIVE', '2026-05-01 09:30:02'),
(5004, 'application.view.self', '查看本人申请', 'application', 'ACTIVE', '2026-05-01 09:30:03'),
(5005, 'application.view.assigned', '查看授权范围申请', 'application', 'ACTIVE', '2026-05-01 09:30:04'),
(5006, 'application.review', '审核申请', 'application', 'ACTIVE', '2026-05-01 09:30:05'),
(5007, 'review.task.view', '查看审核任务', 'review', 'ACTIVE', '2026-05-01 09:30:06'),
(5008, 'review.task.assign', '分配审核任务', 'review', 'ACTIVE', '2026-05-01 09:30:07'),
(5009, 'score.view.self', '查看本人成绩', 'score', 'ACTIVE', '2026-05-01 09:30:08'),
(5010, 'score.view.assigned', '查看授权范围成绩', 'score', 'ACTIVE', '2026-05-01 09:30:09'),
(5011, 'score.export.assigned', '导出授权范围成绩', 'score', 'ACTIVE', '2026-05-01 09:30:10'),
(5012, 'final.submit.self', '提交本人最终材料', 'final', 'ACTIVE', '2026-05-01 09:30:11'),
(5013, 'final.view.self', '查看本人最终结果', 'final', 'ACTIVE', '2026-05-01 09:30:12'),
(5014, 'user.manage', '用户管理', 'iam', 'ACTIVE', '2026-05-01 09:30:13'),
(5015, 'user.import', '用户导入', 'iam', 'ACTIVE', '2026-05-01 09:30:14'),
(5016, 'role.manage', '角色模板管理', 'iam', 'ACTIVE', '2026-05-01 09:30:15'),
(5017, 'assignment.manage', '角色分配管理', 'iam', 'ACTIVE', '2026-05-01 09:30:16'),
(5018, 'permission.manage', '权限管理', 'iam', 'ACTIVE', '2026-05-01 09:30:17'),
(5019, 'org.manage', '组织管理', 'iam', 'ACTIVE', '2026-05-01 09:30:18'),
(5020, 'evaluation.item.manage', '评价项目管理', 'platform', 'ACTIVE', '2026-05-01 09:30:19'),
(5021, 'platform.rule.manage', '平台规则管理', 'platform', 'ACTIVE', '2026-05-01 09:30:20'),
(5022, 'platform.switch.manage', '平台开关管理', 'platform', 'ACTIVE', '2026-05-01 09:30:21');

INSERT INTO `iam_role_permission` (`id`, `role_id`, `permission_id`, `created_at`) VALUES
(6001, 4001, 5001, '2026-05-01 09:40:00'),
(6002, 4001, 5002, '2026-05-01 09:40:01'),
(6003, 4001, 5003, '2026-05-01 09:40:02'),
(6004, 4001, 5004, '2026-05-01 09:40:03'),
(6005, 4001, 5009, '2026-05-01 09:40:04'),
(6006, 4001, 5012, '2026-05-01 09:40:05'),
(6007, 4001, 5013, '2026-05-01 09:40:06'),
(6008, 4002, 5005, '2026-05-01 09:40:07'),
(6009, 4002, 5006, '2026-05-01 09:40:08'),
(6010, 4002, 5007, '2026-05-01 09:40:09'),
(6011, 4002, 5010, '2026-05-01 09:40:10'),
(6012, 4003, 5005, '2026-05-01 09:40:11'),
(6013, 4003, 5006, '2026-05-01 09:40:12'),
(6014, 4003, 5007, '2026-05-01 09:40:13'),
(6015, 4003, 5008, '2026-05-01 09:40:14'),
(6016, 4003, 5010, '2026-05-01 09:40:15'),
(6017, 4003, 5011, '2026-05-01 09:40:16'),
(6018, 4004, 5005, '2026-05-01 09:40:17'),
(6019, 4004, 5006, '2026-05-01 09:40:18'),
(6020, 4004, 5010, '2026-05-01 09:40:19'),
(6021, 4004, 5011, '2026-05-01 09:40:20'),
(6022, 4005, 5014, '2026-05-01 09:40:21'),
(6023, 4005, 5015, '2026-05-01 09:40:22'),
(6024, 4005, 5017, '2026-05-01 09:40:23'),
(6025, 4005, 5019, '2026-05-01 09:40:24'),
(6026, 4006, 5001, '2026-05-01 09:40:25'),
(6027, 4006, 5002, '2026-05-01 09:40:26'),
(6028, 4006, 5003, '2026-05-01 09:40:27'),
(6029, 4006, 5004, '2026-05-01 09:40:28'),
(6030, 4006, 5005, '2026-05-01 09:40:29'),
(6031, 4006, 5006, '2026-05-01 09:40:30'),
(6032, 4006, 5007, '2026-05-01 09:40:31'),
(6033, 4006, 5008, '2026-05-01 09:40:32'),
(6034, 4006, 5009, '2026-05-01 09:40:33'),
(6035, 4006, 5010, '2026-05-01 09:40:34'),
(6036, 4006, 5011, '2026-05-01 09:40:35'),
(6037, 4006, 5012, '2026-05-01 09:40:36'),
(6038, 4006, 5013, '2026-05-01 09:40:37'),
(6039, 4006, 5014, '2026-05-01 09:40:38'),
(6040, 4006, 5015, '2026-05-01 09:40:39'),
(6041, 4006, 5016, '2026-05-01 09:40:40'),
(6042, 4006, 5017, '2026-05-01 09:40:41'),
(6043, 4006, 5018, '2026-05-01 09:40:42'),
(6044, 4006, 5019, '2026-05-01 09:40:43'),
(6045, 4006, 5020, '2026-05-01 09:40:44'),
(6046, 4006, 5021, '2026-05-01 09:40:45'),
(6047, 4006, 5022, '2026-05-01 09:40:46');

INSERT INTO `iam_user_role_assignment` (`id`, `user_id`, `role_id`, `org_unit_id`, `source_type`, `effective_from`, `effective_to`, `status`, `assigned_by`, `created_at`) VALUES
(7001, 1001, 4001, 2010, 'SYSTEM', '2026-05-01 10:00:00', NULL, 'ACTIVE', 1012, '2026-05-01 10:00:00'),
(7002, 1002, 4001, 2010, 'SYSTEM', '2026-05-01 10:00:01', NULL, 'ACTIVE', 1012, '2026-05-01 10:00:01'),
(7003, 1003, 4001, 2010, 'SYSTEM', '2026-05-01 10:00:02', NULL, 'ACTIVE', 1012, '2026-05-01 10:00:02'),
(7004, 1004, 4001, 2011, 'SYSTEM', '2026-05-01 10:00:03', NULL, 'ACTIVE', 1012, '2026-05-01 10:00:03'),
(7005, 1005, 4001, 2011, 'SYSTEM', '2026-05-01 10:00:04', NULL, 'ACTIVE', 1012, '2026-05-01 10:00:04'),
(7006, 1006, 4001, 2011, 'SYSTEM', '2026-05-01 10:00:05', NULL, 'ACTIVE', 1012, '2026-05-01 10:00:05'),
(7007, 1007, 4001, 2012, 'SYSTEM', '2026-05-01 10:00:06', NULL, 'ACTIVE', 1012, '2026-05-01 10:00:06'),
(7008, 1008, 4001, 2012, 'SYSTEM', '2026-05-01 10:00:07', NULL, 'ACTIVE', 1012, '2026-05-01 10:00:07'),
(7009, 1002, 4002, 2010, 'MANUAL', '2026-05-01 10:10:00', NULL, 'ACTIVE', 1010, '2026-05-01 10:10:00'),
(7010, 1010, 4003, 2002, 'MANUAL', '2026-05-01 10:10:01', NULL, 'ACTIVE', 1012, '2026-05-01 10:10:01'),
(7011, 1011, 4004, 2002, 'MANUAL', '2026-05-01 10:10:02', NULL, 'ACTIVE', 1012, '2026-05-01 10:10:02'),
(7012, 1012, 4006, 2001, 'MANUAL', '2026-05-01 10:10:03', NULL, 'ACTIVE', 1012, '2026-05-01 10:10:03');

INSERT INTO `iam_scope_rule` (`id`, `assignment_id`, `permission_code`, `scope_type`, `org_unit_id`, `category_code`, `item_code`, `expression_json`, `priority`, `status`, `created_at`) VALUES
(8001, 7001, 'application.view.self', 'SELF', NULL, NULL, NULL, JSON_OBJECT('owner', 'self'), 100, 'ACTIVE', '2026-05-01 10:20:00'),
(8002, 7002, 'application.submit', 'SELF', NULL, NULL, NULL, JSON_OBJECT('owner', 'self'), 100, 'ACTIVE', '2026-05-01 10:20:01'),
(8003, 7003, 'score.view.self', 'SELF', NULL, NULL, NULL, JSON_OBJECT('owner', 'self'), 100, 'ACTIVE', '2026-05-01 10:20:02'),
(8004, 7004, 'final.view.self', 'SELF', NULL, NULL, NULL, JSON_OBJECT('owner', 'self'), 100, 'ACTIVE', '2026-05-01 10:20:03'),
(8005, 7005, 'final.submit.self', 'SELF', NULL, NULL, NULL, JSON_OBJECT('owner', 'self'), 100, 'ACTIVE', '2026-05-01 10:20:04'),
(8006, 7006, 'application.update', 'SELF', NULL, 'LABOR', NULL, JSON_OBJECT('allowDraft', true), 95, 'ACTIVE', '2026-05-01 10:20:05'),
(8007, 7007, 'application.delete', 'SELF', NULL, 'SPORTS', NULL, JSON_OBJECT('allowDeleteDraft', true), 95, 'ACTIVE', '2026-05-01 10:20:06'),
(8008, 7008, 'score.view.self', 'SELF', NULL, NULL, NULL, JSON_OBJECT('allowCompare', false), 95, 'ACTIVE', '2026-05-01 10:20:07'),
(8009, 7009, 'application.review', 'ORG_UNIT', 2010, NULL, NULL, JSON_OBJECT('reviewRole', 'class_monitor'), 90, 'ACTIVE', '2026-05-01 10:20:08'),
(8010, 7010, 'application.review', 'ORG_SUBTREE', 2002, NULL, NULL, JSON_OBJECT('reviewRole', 'counselor'), 80, 'ACTIVE', '2026-05-01 10:20:09'),
(8011, 7010, 'score.view.assigned', 'ORG_SUBTREE', 2002, NULL, NULL, JSON_OBJECT('scoreRole', 'counselor'), 80, 'ACTIVE', '2026-05-01 10:20:10'),
(8012, 7011, 'application.review', 'ORG_SUBTREE', 2002, NULL, NULL, JSON_OBJECT('reviewRole', 'college_reviewer'), 70, 'ACTIVE', '2026-05-01 10:20:11'),
(8013, 7011, 'score.view.assigned', 'ORG_SUBTREE', 2002, NULL, NULL, JSON_OBJECT('scoreRole', 'college_reviewer'), 70, 'ACTIVE', '2026-05-01 10:20:12'),
(8014, 7012, 'permission.manage', 'ALL', NULL, NULL, NULL, JSON_OBJECT('superAdmin', true), 1000, 'ACTIVE', '2026-05-01 10:20:13'),
(8015, 7012, 'org.manage', 'ALL', NULL, NULL, NULL, JSON_OBJECT('superAdmin', true), 1000, 'ACTIVE', '2026-05-01 10:20:14'),
(8016, 7012, 'role.manage', 'ALL', NULL, NULL, NULL, JSON_OBJECT('superAdmin', true), 1000, 'ACTIVE', '2026-05-01 10:20:15'),
(8017, 7012, 'assignment.manage', 'ALL', NULL, NULL, NULL, JSON_OBJECT('superAdmin', true), 1000, 'ACTIVE', '2026-05-01 10:20:16'),
(8018, 7012, 'user.manage', 'ALL', NULL, NULL, NULL, JSON_OBJECT('superAdmin', true), 1000, 'ACTIVE', '2026-05-01 10:20:17');

INSERT INTO `iam_session` (`id`, `session_no`, `user_id`, `access_token_id`, `refresh_token_id`, `device_type`, `client_ip`, `user_agent`, `expired_at`, `revoked_at`, `status`, `created_at`, `updated_at`) VALUES
(9001, 'session-1001-a', 1001, 'access-1001-a', 'refresh-1001-a', 'WEB', '10.20.1.1', 'Chrome/136 macOS', '2099-12-31 07:05:00', NULL, 'ACTIVE', '2026-05-18 08:05:00', '2026-05-18 08:05:00'),
(9002, 'session-1002-a', 1002, 'access-1002-a', 'refresh-1002-a', 'WEB', '10.20.1.2', 'Chrome/136 macOS', '2099-12-31 08:10:00', NULL, 'ACTIVE', '2026-05-18 08:10:00', '2026-05-18 08:10:00'),
(9003, 'session-1003-a', 1003, 'access-1003-a', 'refresh-1003-a', 'WEB', '10.20.1.3', 'Chrome/136 macOS', '2099-12-31 08:15:00', NULL, 'ACTIVE', '2026-05-18 08:15:00', '2026-05-18 08:15:00'),
(9004, 'session-1004-a', 1004, 'access-1004-a', 'refresh-1004-a', 'WEB', '10.20.1.4', 'Chrome/136 macOS', '2099-12-31 09:20:00', NULL, 'ACTIVE', '2026-05-18 08:20:00', '2026-05-18 08:20:00'),
(9005, 'session-1005-a', 1005, 'access-1005-a', 'refresh-1005-a', 'WEB', '10.20.1.5', 'Chrome/136 macOS', '2099-12-31 09:25:00', NULL, 'ACTIVE', '2026-05-18 08:25:00', '2026-05-18 08:25:00'),
(9006, 'session-1006-a', 1006, 'access-1006-a', 'refresh-1006-a', 'WEB', '10.20.1.6', 'Chrome/136 macOS', '2026-05-17 08:50:00', NULL, 'EXPIRED', '2026-05-18 08:30:00', '2026-05-18 08:30:00'),
(9007, 'session-1007-a', 1007, 'access-1007-a', 'refresh-1007-a', 'WEB', '10.20.1.7', 'Chrome/136 macOS', '2099-12-31 10:35:00', NULL, 'ACTIVE', '2026-05-18 08:35:00', '2026-05-18 08:35:00'),
(9008, 'session-1008-a', 1008, 'access-1008-a', 'refresh-1008-a', 'WEB', '10.20.1.8', 'Chrome/136 macOS', '2099-12-31 11:40:00', NULL, 'ACTIVE', '2026-05-18 08:40:00', '2026-05-18 08:40:00'),
(9009, 'session-1009-a', 1009, 'access-1009-a', 'refresh-1009-a', 'WEB', '10.20.1.9', 'Chrome/136 macOS', '2026-05-18 09:20:00', '2026-05-18 09:25:00', 'REVOKED', '2026-05-18 08:45:00', '2026-05-18 08:45:00'),
(9010, 'session-1010-a', 1010, 'access-1010-a', 'refresh-1010-a', 'WEB', '10.20.2.10', 'Chrome/136 macOS', '2099-12-31 12:50:00', NULL, 'ACTIVE', '2026-05-18 08:50:00', '2026-05-18 08:50:00'),
(9011, 'session-1011-a', 1011, 'access-1011-a', 'refresh-1011-a', 'WEB', '10.20.2.11', 'Chrome/136 macOS', '2099-12-31 12:55:00', NULL, 'ACTIVE', '2026-05-18 08:55:00', '2026-05-18 08:55:00'),
(9012, 'session-1012-a', 1012, 'access-1012-a', 'refresh-1012-a', 'WEB', '10.20.2.12', 'Chrome/136 macOS', '2099-12-31 13:00:00', NULL, 'ACTIVE', '2026-05-18 08:00:00', '2026-05-18 08:00:00');
