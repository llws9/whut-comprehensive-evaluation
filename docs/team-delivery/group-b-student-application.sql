-- B 组：学生申请写链路、申请事实、申请附件绑定
-- 负责表：
--   application_submission
--   application_fact
--   application_attachment
--
-- 依赖：
-- 1. 申请人和组织数据来自 A 组脚本。
-- 2. item_code、file_id 来自 E 组脚本。

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `application_attachment`;
DROP TABLE IF EXISTS `application_fact`;
DROP TABLE IF EXISTS `application_submission`;

CREATE TABLE `application_submission` (
  `id` BIGINT NOT NULL,
  `applicant_user_id` BIGINT NOT NULL,
  `org_unit_id` BIGINT NOT NULL,
  `category_code` VARCHAR(64) NOT NULL,
  `item_code` VARCHAR(64) NOT NULL,
  `academic_year` VARCHAR(32) NOT NULL,
  `term` VARCHAR(16) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT,
  `status` VARCHAR(32) NOT NULL,
  `submitted_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `version` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_application_submission_applicant_user_id` (`applicant_user_id`),
  KEY `idx_application_submission_org_unit_id` (`org_unit_id`),
  KEY `idx_application_submission_item_code` (`item_code`),
  KEY `idx_application_submission_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='申请表头';

CREATE TABLE `application_fact` (
  `id` BIGINT NOT NULL,
  `application_id` BIGINT NOT NULL,
  `score_value` DECIMAL(10,2) NOT NULL,
  `display_text` VARCHAR(1000) DEFAULT NULL,
  `evidence_count` INT NOT NULL,
  `extra_json` JSON DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_application_fact_application_id` (`application_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='申请事实明细';

CREATE TABLE `application_attachment` (
  `id` BIGINT NOT NULL,
  `application_id` BIGINT NOT NULL,
  `file_id` VARCHAR(64) NOT NULL,
  `selected_source` VARCHAR(32) NOT NULL,
  `sort_no` INT NOT NULL,
  `snapshot_filename` VARCHAR(255) NOT NULL,
  `snapshot_content_type` VARCHAR(128) NOT NULL,
  `snapshot_size` BIGINT NOT NULL,
  `snapshot_storage_key` VARCHAR(512) NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_application_attachment_application_id` (`application_id`),
  KEY `idx_application_attachment_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='申请附件绑定';

INSERT INTO `application_submission` (`id`, `applicant_user_id`, `org_unit_id`, `category_code`, `item_code`, `academic_year`, `term`, `title`, `description`, `status`, `submitted_at`, `created_at`, `updated_at`, `version`) VALUES
(21001, 1001, 2010, 'INTELLECTUAL', 'INTELLECTUAL_COMPETITION', '2025-2026', '上学期', '全国大学生数学建模竞赛校赛一等奖', '参加数学建模校赛并获得一等奖，已提交证书与获奖通知。', 'APPROVED', '2026-05-12 09:00:00', '2026-05-11 18:00:00', '2026-05-16 10:00:00', 3),
(21002, 1002, 2010, 'MORAL', 'MORAL_VOLUNTEER', '2025-2026', '上学期', '敬老院志愿服务项目', '参与社区敬老院志愿服务 24 小时，附服务证明。', 'SUBMITTED', '2026-05-13 10:00:00', '2026-05-13 09:20:00', '2026-05-13 10:00:00', 1),
(21003, 1003, 2010, 'SPORTS', 'SPORTS_ART_CONTRIBUTION', '2025-2026', '上学期', '校园文化作品征稿录用', '摄影作品被校级媒体采用，需补充合作分工说明。', 'RETURNED', '2026-05-13 14:00:00', '2026-05-12 16:30:00', '2026-05-15 11:40:00', 2),
(21004, 1004, 2011, 'SPORTS', 'SPORTS_COMPETITION', '2025-2026', '上学期', '校运会男子 100 米二等奖', '参加校运会短跑项目并获得二等奖。', 'APPROVED', '2026-05-11 15:00:00', '2026-05-10 20:10:00', '2026-05-15 09:10:00', 2),
(21005, 1005, 2011, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', '2025-2026', '上学期', '期刊论文录用申请', '论文已收到录用通知，但检索材料不完整。', 'REJECTED', '2026-05-12 17:30:00', '2026-05-12 12:00:00', '2026-05-16 09:00:00', 2),
(21006, 1006, 2011, 'LABOR', 'LABOR_PRACTICE', '2025-2026', '上学期', '暑期社会实践先进个人', '已录入实践总结，待补充学院盖章扫描件。', 'DRAFT', NULL, '2026-05-14 08:30:00', '2026-05-18 08:30:00', 5),
(21007, 1007, 2012, 'LABOR', 'LABOR_SERVICE', '2025-2026', '上学期', '图书馆勤工助学服务', '按月累计值班 42 小时。', 'APPROVED', '2026-05-12 08:10:00', '2026-05-11 19:00:00', '2026-05-15 16:20:00', 2),
(21008, 1008, 2012, 'MORAL', 'MORAL_HONOR', '2025-2026', '上学期', '优秀学生干部荣誉申请', '曾获学院优秀学生干部称号，申请后主动撤回重新提交。', 'WITHDRAWN', '2026-05-10 13:40:00', '2026-05-10 10:00:00', '2026-05-14 12:00:00', 4),
(21009, 1001, 2010, 'SPORTS', 'SPORTS_OTHER', '2025-2026', '上学期', '艺术展演志愿协助', '协助学院艺术展演现场布置与接待。', 'SUBMITTED', '2026-05-17 09:10:00', '2026-05-16 18:10:00', '2026-05-17 09:10:00', 1),
(21010, 1002, 2010, 'INTELLECTUAL', 'INTELLECTUAL_PROJECT', '2025-2026', '上学期', '大学生创新创业训练项目结题', '项目完成结题验收并提交结题证明。', 'APPROVED', '2026-05-12 11:10:00', '2026-05-11 13:20:00', '2026-05-15 17:30:00', 2),
(21011, 1003, 2010, 'LABOR', 'LABOR_PRACTICE', '2025-2026', '下学期', '社区环保行动实践', '参加社区环保宣传和垃圾分类入户活动。', 'RETURNED', '2026-05-18 09:30:00', '2026-05-17 15:00:00', '2026-05-18 12:30:00', 2),
(21012, 1004, 2011, 'MORAL', 'MORAL_VOLUNTEER', '2025-2026', '下学期', '校庆志愿讲解服务', '承担校庆展览讲解与引导工作。', 'SUBMITTED', '2026-05-18 10:15:00', '2026-05-17 20:00:00', '2026-05-18 10:15:00', 1);

INSERT INTO `application_fact` (`id`, `application_id`, `score_value`, `display_text`, `evidence_count`, `extra_json`, `created_at`, `updated_at`) VALUES
(22001, 21001, 5.00, '校赛一等奖，按竞赛档位计 5 分', 2, JSON_OBJECT('optionCode', 'COMP_FIRST_PRIZE', 'teamRole', 'leader'), '2026-05-11 18:00:00', '2026-05-16 10:00:00'),
(22002, 21002, 1.50, '累计志愿服务 24 小时', 2, JSON_OBJECT('serviceHours', 24, 'servicePlace', '洪山区敬老院'), '2026-05-13 09:20:00', '2026-05-13 10:00:00'),
(22003, 21003, 0.50, '作品被校级媒体采用', 2, JSON_OBJECT('optionCode', 'ART_MULTI_AUTHOR', 'publishChannel', '校报记者团'), '2026-05-12 16:30:00', '2026-05-15 11:40:00'),
(22004, 21004, 0.80, '校运会个人项目二等奖', 2, JSON_OBJECT('optionCode', 'SPORTS_SECOND_PRIZE', 'eventName', '男子100米'), '2026-05-10 20:10:00', '2026-05-15 09:10:00'),
(22005, 21005, 2.00, '期刊论文拟录用', 2, JSON_OBJECT('journal', '计算机工程应用', 'indexStatus', '待补充'), '2026-05-12 12:00:00', '2026-05-16 09:00:00'),
(22006, 21006, 2.50, '实践总结已形成，待提交盖章件', 1, JSON_OBJECT('practiceLocation', '黄冈市团风县', 'needSeal', true), '2026-05-14 08:30:00', '2026-05-18 08:30:00'),
(22007, 21007, 1.20, '图书馆勤工助学 42 小时', 2, JSON_OBJECT('workHours', 42, 'department', '图书馆借阅部'), '2026-05-11 19:00:00', '2026-05-15 16:20:00'),
(22008, 21008, 1.00, '优秀学生干部荣誉', 1, JSON_OBJECT('honorLevel', 'college', 'withdrawReason', '需补证书原件'), '2026-05-10 10:00:00', '2026-05-14 12:00:00'),
(22009, 21009, 0.60, '艺术展演志愿协助', 2, JSON_OBJECT('supportRole', '接待与布展', 'allowCustomPoints', true), '2026-05-16 18:10:00', '2026-05-17 09:10:00'),
(22010, 21010, 3.00, '创新创业训练项目结题通过', 2, JSON_OBJECT('projectLevel', '校级', 'projectStatus', '结题'), '2026-05-11 13:20:00', '2026-05-15 17:30:00'),
(22011, 21011, 2.20, '社区环保实践活动', 2, JSON_OBJECT('practiceTheme', '垃圾分类', 'needSupplement', '签到照片'), '2026-05-17 15:00:00', '2026-05-18 12:30:00'),
(22012, 21012, 1.00, '校庆志愿讲解服务', 2, JSON_OBJECT('serviceHours', 16, 'servicePlace', '校史馆'), '2026-05-17 20:00:00', '2026-05-18 10:15:00');

INSERT INTO `application_attachment` (`id`, `application_id`, `file_id`, `selected_source`, `sort_no`, `snapshot_filename`, `snapshot_content_type`, `snapshot_size`, `snapshot_storage_key`, `created_at`) VALUES
(23001, 21001, 'FILE-0001', 'SELF_UPLOAD', 1, '数学建模校赛一等奖.pdf', 'application/pdf', 256000, 'attachments/2026/05/certificate-mcm.pdf', '2026-05-11 18:01:00'),
(23002, 21001, 'FILE-0008', 'PUBLIC_POOL', 2, '综测申请模板.pdf', 'application/pdf', 142000, 'attachments/2026/05/guide-template.pdf', '2026-05-11 18:02:00'),
(23003, 21002, 'FILE-0002', 'SELF_UPLOAD', 1, '志愿服务时长证明.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 88000, 'attachments/2026/05/volunteer-hours.docx', '2026-05-13 09:21:00'),
(23004, 21002, 'FILE-0005', 'PUBLIC_POOL', 2, '志愿服务证明示例', 'application/pdf', 156000, 'attachments/2026/05/old-template.pdf', '2026-05-13 09:22:00'),
(23005, 21003, 'FILE-0003', 'SELF_UPLOAD', 1, '征稿录用函.pdf', 'application/pdf', 198000, 'attachments/2026/05/essay-accepted.pdf', '2026-05-12 16:31:00'),
(23006, 21003, 'FILE-0010', 'PUBLIC_POOL', 2, '公共素材包.zip', 'application/zip', 804000, 'attachments/2026/05/public-photo.zip', '2026-05-12 16:32:00'),
(23007, 21004, 'FILE-0004', 'SELF_UPLOAD', 1, '校运会获奖照片.jpg', 'image/jpeg', 620000, 'attachments/2026/05/sports-award.jpg', '2026-05-10 20:11:00'),
(23008, 21005, 'FILE-0005', 'SELF_UPLOAD', 1, '论文检索截图.png', 'image/png', 480000, 'attachments/2026/05/paper-index.png', '2026-05-12 12:01:00'),
(23009, 21005, 'FILE-0008', 'PUBLIC_POOL', 2, '综测申请模板.pdf', 'application/pdf', 142000, 'attachments/2026/05/guide-template.pdf', '2026-05-12 12:02:00'),
(23010, 21006, 'FILE-0006', 'SELF_UPLOAD', 1, '社会实践报告.pdf', 'application/pdf', 310000, 'attachments/2026/05/labor-practice.pdf', '2026-05-14 08:31:00'),
(23011, 21007, 'FILE-0008', 'PUBLIC_POOL', 1, '综测申请模板.pdf', 'application/pdf', 142000, 'attachments/2026/05/guide-template.pdf', '2026-05-11 19:01:00'),
(23012, 21007, 'FILE-0012', 'SELF_UPLOAD', 2, '活动海报.png', 'image/png', 512000, 'attachments/2026/05/poster.png', '2026-05-11 19:02:00'),
(23013, 21008, 'FILE-0007', 'SELF_UPLOAD', 1, '班级活动总结.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 102000, 'attachments/2026/05/class-monitor.docx', '2026-05-10 10:01:00'),
(23014, 21009, 'FILE-0012', 'SELF_UPLOAD', 1, '活动海报.png', 'image/png', 512000, 'attachments/2026/05/poster.png', '2026-05-16 18:11:00'),
(23015, 21010, 'FILE-0009', 'PUBLIC_POOL', 1, '审核口径说明.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 94000, 'attachments/2026/05/review-spec.docx', '2026-05-11 13:21:00'),
(23016, 21011, 'FILE-0006', 'SELF_UPLOAD', 1, '社会实践报告.pdf', 'application/pdf', 310000, 'attachments/2026/05/labor-practice.pdf', '2026-05-17 15:01:00'),
(23017, 21012, 'FILE-0002', 'SELF_UPLOAD', 1, '志愿服务时长证明.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 88000, 'attachments/2026/05/volunteer-hours.docx', '2026-05-17 20:01:00');
