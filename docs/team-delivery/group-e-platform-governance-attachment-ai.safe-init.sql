CREATE TABLE IF NOT EXISTS `evaluation_category` (
  `id` BIGINT NOT NULL,
  `category_code` VARCHAR(64) NOT NULL,
  `category_name` VARCHAR(128) NOT NULL,
  `display_name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(500) DEFAULT NULL,
  `sort_no` INT NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evaluation_category_code` (`category_code`)
);

CREATE TABLE IF NOT EXISTS `evaluation_item` (
  `id` BIGINT NOT NULL,
  `category_code` VARCHAR(64) NOT NULL,
  `item_code` VARCHAR(64) NOT NULL,
  `item_name` VARCHAR(128) NOT NULL,
  `apply_mode` VARCHAR(32) NOT NULL,
  `review_mode` VARCHAR(32) NOT NULL,
  `score_mode` VARCHAR(32) NOT NULL,
  `cap_rule_json` VARCHAR(1000) DEFAULT NULL,
  `description` VARCHAR(1000) DEFAULT NULL,
  `sort_no` INT NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evaluation_item_code` (`item_code`),
  KEY `idx_evaluation_item_category_code` (`category_code`)
);

CREATE TABLE IF NOT EXISTS `file_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `file_id` VARCHAR(64) NOT NULL,
  `storage_key` VARCHAR(512) NOT NULL,
  `bucket` VARCHAR(128) NOT NULL,
  `original_filename` VARCHAR(255) NOT NULL,
  `content_type` VARCHAR(128) NOT NULL,
  `size` BIGINT NOT NULL,
  `sha256` VARCHAR(128) DEFAULT NULL,
  `uploader_user_id` BIGINT NOT NULL,
  `uploader_type` VARCHAR(32) NOT NULL,
  `upload_channel` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_asset_file_id` (`file_id`)
);

CREATE TABLE IF NOT EXISTS `public_attachment_entry` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `file_id` VARCHAR(64) NOT NULL,
  `display_name` VARCHAR(255) NOT NULL,
  `description` VARCHAR(1000) DEFAULT NULL,
  `category_code` VARCHAR(64) NOT NULL,
  `scope_type` VARCHAR(32) NOT NULL,
  `scope_value` VARCHAR(128) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `published_by` BIGINT NOT NULL,
  `published_at` DATETIME DEFAULT NULL,
  `sort_no` INT NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_public_attachment_entry_file_id` (`file_id`),
  KEY `idx_public_attachment_entry_category_code` (`category_code`)
);

INSERT INTO `evaluation_category` (`id`, `category_code`, `category_name`, `display_name`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 11001, 'MORAL', '德育', '德育', '当前启用的德育大类', 10, 'ACTIVE', '2026-05-01 11:00:00', '2026-05-18 11:00:00'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_category` WHERE `id` = 11001);
INSERT INTO `evaluation_category` (`id`, `category_code`, `category_name`, `display_name`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 11002, 'INTELLECTUAL', '智育', '智育', '当前启用的智育大类', 20, 'ACTIVE', '2026-05-01 11:00:01', '2026-05-18 11:00:01'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_category` WHERE `id` = 11002);
INSERT INTO `evaluation_category` (`id`, `category_code`, `category_name`, `display_name`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 11003, 'SPORTS', '体育与美育', '体育与美育', '当前启用的体育与美育大类', 30, 'ACTIVE', '2026-05-01 11:00:02', '2026-05-18 11:00:02'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_category` WHERE `id` = 11003);
INSERT INTO `evaluation_category` (`id`, `category_code`, `category_name`, `display_name`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 11004, 'LABOR', '劳育', '劳育', '当前启用的劳育大类', 40, 'ACTIVE', '2026-05-01 11:00:03', '2026-05-18 11:00:03'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_category` WHERE `id` = 11004);
INSERT INTO `evaluation_category` (`id`, `category_code`, `category_name`, `display_name`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 11005, 'RESERVE_SOCIAL', '社会工作预留类', '社会工作预留类', '预留测试类目，默认禁用', 50, 'INACTIVE', '2026-05-01 11:00:04', '2026-05-18 11:00:04'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_category` WHERE `id` = 11005);
INSERT INTO `evaluation_category` (`id`, `category_code`, `category_name`, `display_name`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 11006, 'RESERVE_ART', '艺术拓展预留类', '艺术拓展预留类', '预留测试类目，默认禁用', 60, 'INACTIVE', '2026-05-01 11:00:05', '2026-05-18 11:00:05'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_category` WHERE `id` = 11006);
INSERT INTO `evaluation_category` (`id`, `category_code`, `category_name`, `display_name`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 11007, 'RESERVE_RESEARCH', '科研拓展预留类', '科研拓展预留类', '预留测试类目，默认禁用', 70, 'INACTIVE', '2026-05-01 11:00:06', '2026-05-18 11:00:06'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_category` WHERE `id` = 11007);
INSERT INTO `evaluation_category` (`id`, `category_code`, `category_name`, `display_name`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 11008, 'RESERVE_INTERNATIONAL', '国际交流预留类', '国际交流预留类', '预留测试类目，默认禁用', 80, 'INACTIVE', '2026-05-01 11:00:07', '2026-05-18 11:00:07'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_category` WHERE `id` = 11008);
INSERT INTO `evaluation_category` (`id`, `category_code`, `category_name`, `display_name`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 11009, 'RESERVE_COMMUNITY', '社区服务预留类', '社区服务预留类', '预留测试类目，默认禁用', 90, 'INACTIVE', '2026-05-01 11:00:08', '2026-05-18 11:00:08'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_category` WHERE `id` = 11009);
INSERT INTO `evaluation_category` (`id`, `category_code`, `category_name`, `display_name`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 11010, 'RESERVE_INNOVATION', '创新创业预留类', '创新创业预留类', '预留测试类目，默认禁用', 100, 'INACTIVE', '2026-05-01 11:00:09', '2026-05-18 11:00:09'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_category` WHERE `id` = 11010);

INSERT INTO `evaluation_item` (`id`, `category_code`, `item_code`, `item_name`, `apply_mode`, `review_mode`, `score_mode`, `cap_rule_json`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 12001, 'MORAL', 'MORAL_VOLUNTEER', '志愿服务', 'STUDENT_APPLY', 'COUNSELOR_REVIEW', 'MANUAL', '{"maxPoints":3.00,"allowOverflow":false}', '志愿服务、公益活动等德育加分项', 10, 'ACTIVE', '2026-05-01 11:10:00', '2026-05-18 11:10:00'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_item` WHERE `id` = 12001);
INSERT INTO `evaluation_item` (`id`, `category_code`, `item_code`, `item_name`, `apply_mode`, `review_mode`, `score_mode`, `cap_rule_json`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 12002, 'MORAL', 'MORAL_HONOR', '荣誉表彰', 'STUDENT_APPLY', 'COUNSELOR_REVIEW', 'OPTION', '{"maxPoints":4.00,"allowOverflow":false}', '先进个人、优秀干部等荣誉加分项', 20, 'ACTIVE', '2026-05-01 11:10:01', '2026-05-18 11:10:01'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_item` WHERE `id` = 12002);
INSERT INTO `evaluation_item` (`id`, `category_code`, `item_code`, `item_name`, `apply_mode`, `review_mode`, `score_mode`, `cap_rule_json`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 12003, 'INTELLECTUAL', 'INTELLECTUAL_COMPETITION', '学科竞赛', 'STUDENT_APPLY', 'COLLEGE_REVIEW', 'OPTION', '{"maxPoints":8.00,"allowOverflow":true}', '学科竞赛与创新竞赛类项目', 30, 'ACTIVE', '2026-05-01 11:10:02', '2026-05-18 11:10:02'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_item` WHERE `id` = 12003);
INSERT INTO `evaluation_item` (`id`, `category_code`, `item_code`, `item_name`, `apply_mode`, `review_mode`, `score_mode`, `cap_rule_json`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 12004, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', '论文发表', 'STUDENT_APPLY', 'COLLEGE_REVIEW', 'OPTION', '{"maxPoints":6.00,"allowOverflow":false}', '论文、期刊、会议录用等学术成果', 40, 'ACTIVE', '2026-05-01 11:10:03', '2026-05-18 11:10:03'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_item` WHERE `id` = 12004);
INSERT INTO `evaluation_item` (`id`, `category_code`, `item_code`, `item_name`, `apply_mode`, `review_mode`, `score_mode`, `cap_rule_json`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 12005, 'INTELLECTUAL', 'INTELLECTUAL_PROJECT', '科研项目', 'TEACHER_IMPORT', 'SYSTEM_PASS', 'IMPORT', '{"maxPoints":5.00,"allowOverflow":false}', '立项、结项、科研平台项目成果', 50, 'ACTIVE', '2026-05-01 11:10:04', '2026-05-18 11:10:04'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_item` WHERE `id` = 12005);
INSERT INTO `evaluation_item` (`id`, `category_code`, `item_code`, `item_name`, `apply_mode`, `review_mode`, `score_mode`, `cap_rule_json`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 12006, 'SPORTS', 'SPORTS_COMPETITION', '文体竞赛', 'STUDENT_APPLY', 'COUNSELOR_REVIEW', 'OPTION', '{"maxPoints":4.00,"allowOverflow":true}', '运动会、球赛、艺术比赛等文体竞赛', 60, 'ACTIVE', '2026-05-01 11:10:05', '2026-05-18 11:10:05'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_item` WHERE `id` = 12006);
INSERT INTO `evaluation_item` (`id`, `category_code`, `item_code`, `item_name`, `apply_mode`, `review_mode`, `score_mode`, `cap_rule_json`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 12007, 'SPORTS', 'SPORTS_ART_CONTRIBUTION', '文艺征稿', 'STUDENT_APPLY', 'COUNSELOR_REVIEW', 'OPTION', '{"maxPoints":2.00,"allowOverflow":false}', '征文、摄影、书法、短视频等作品征稿', 70, 'ACTIVE', '2026-05-01 11:10:06', '2026-05-18 11:10:06'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_item` WHERE `id` = 12007);
INSERT INTO `evaluation_item` (`id`, `category_code`, `item_code`, `item_name`, `apply_mode`, `review_mode`, `score_mode`, `cap_rule_json`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 12008, 'SPORTS', 'SPORTS_OTHER', '其他体育美育成果', 'STUDENT_APPLY', 'COUNSELOR_REVIEW', 'MANUAL', '{"maxPoints":2.00,"allowOverflow":false}', '其他体育与美育类活动成果', 80, 'ACTIVE', '2026-05-01 11:10:07', '2026-05-18 11:10:07'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_item` WHERE `id` = 12008);
INSERT INTO `evaluation_item` (`id`, `category_code`, `item_code`, `item_name`, `apply_mode`, `review_mode`, `score_mode`, `cap_rule_json`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 12009, 'LABOR', 'LABOR_PRACTICE', '社会实践', 'STUDENT_APPLY', 'COUNSELOR_REVIEW', 'MANUAL', '{"maxPoints":4.00,"allowOverflow":false}', '寒暑期社会实践、校内劳动实践', 90, 'ACTIVE', '2026-05-01 11:10:08', '2026-05-18 11:10:08'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_item` WHERE `id` = 12009);
INSERT INTO `evaluation_item` (`id`, `category_code`, `item_code`, `item_name`, `apply_mode`, `review_mode`, `score_mode`, `cap_rule_json`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 12010, 'LABOR', 'LABOR_SERVICE', '劳动服务', 'TEACHER_IMPORT', 'SYSTEM_PASS', 'IMPORT', '{"maxPoints":3.00,"allowOverflow":false}', '勤工助学、服务岗值守等劳动服务记录', 100, 'ACTIVE', '2026-05-01 11:10:09', '2026-05-18 11:10:09'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_item` WHERE `id` = 12010);
INSERT INTO `evaluation_item` (`id`, `category_code`, `item_code`, `item_name`, `apply_mode`, `review_mode`, `score_mode`, `cap_rule_json`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 12011, 'RESERVE_RESEARCH', 'RESERVE_RESEARCH_VISIT', '学术访学预留项', 'STUDENT_APPLY', 'COLLEGE_REVIEW', 'OPTION', '{"maxPoints":2.00,"allowOverflow":false}', '保留给二期使用的科研拓展项目', 110, 'INACTIVE', '2026-05-01 11:10:10', '2026-05-18 11:10:10'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_item` WHERE `id` = 12011);
INSERT INTO `evaluation_item` (`id`, `category_code`, `item_code`, `item_name`, `apply_mode`, `review_mode`, `score_mode`, `cap_rule_json`, `description`, `sort_no`, `status`, `created_at`, `updated_at`)
SELECT 12012, 'RESERVE_SOCIAL', 'RESERVE_SOCIAL_ORGANIZATION', '社团治理预留项', 'STUDENT_APPLY', 'COUNSELOR_REVIEW', 'OPTION', '{"maxPoints":2.00,"allowOverflow":false}', '保留给二期使用的社会工作项目', 120, 'INACTIVE', '2026-05-01 11:10:11', '2026-05-18 11:10:11'
WHERE NOT EXISTS (SELECT 1 FROM `evaluation_item` WHERE `id` = 12012);

INSERT INTO `file_asset` (`id`, `file_id`, `storage_key`, `bucket`, `original_filename`, `content_type`, `size`, `sha256`, `uploader_user_id`, `uploader_type`, `upload_channel`, `status`, `created_at`, `updated_at`)
SELECT 13001, 'FILE-0001', 'attachments/2026/05/certificate-mcm.pdf', 'whut-eval-dev', '数学建模校赛一等奖.pdf', 'application/pdf', 256000, 'sha256-file-0001', 1001, 'USER', 'SELF_UPLOAD', 'ACTIVE', '2026-05-10 10:00:00', '2026-05-10 10:00:00'
WHERE NOT EXISTS (SELECT 1 FROM `file_asset` WHERE `file_id` = 'FILE-0001');
INSERT INTO `file_asset` (`id`, `file_id`, `storage_key`, `bucket`, `original_filename`, `content_type`, `size`, `sha256`, `uploader_user_id`, `uploader_type`, `upload_channel`, `status`, `created_at`, `updated_at`)
SELECT 13002, 'FILE-0002', 'attachments/2026/05/volunteer-hours.docx', 'whut-eval-dev', '志愿服务时长证明.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 88000, 'sha256-file-0002', 1002, 'USER', 'SELF_UPLOAD', 'ACTIVE', '2026-05-10 10:05:00', '2026-05-10 10:05:00'
WHERE NOT EXISTS (SELECT 1 FROM `file_asset` WHERE `file_id` = 'FILE-0002');
INSERT INTO `file_asset` (`id`, `file_id`, `storage_key`, `bucket`, `original_filename`, `content_type`, `size`, `sha256`, `uploader_user_id`, `uploader_type`, `upload_channel`, `status`, `created_at`, `updated_at`)
SELECT 13003, 'FILE-0003', 'attachments/2026/05/essay-accepted.pdf', 'whut-eval-dev', '征稿录用函.pdf', 'application/pdf', 198000, 'sha256-file-0003', 1003, 'USER', 'SELF_UPLOAD', 'ACTIVE', '2026-05-10 10:10:00', '2026-05-10 10:10:00'
WHERE NOT EXISTS (SELECT 1 FROM `file_asset` WHERE `file_id` = 'FILE-0003');
INSERT INTO `file_asset` (`id`, `file_id`, `storage_key`, `bucket`, `original_filename`, `content_type`, `size`, `sha256`, `uploader_user_id`, `uploader_type`, `upload_channel`, `status`, `created_at`, `updated_at`)
SELECT 13004, 'FILE-0004', 'attachments/2026/05/sports-award.jpg', 'whut-eval-dev', '校运会获奖照片.jpg', 'image/jpeg', 620000, 'sha256-file-0004', 1004, 'USER', 'SELF_UPLOAD', 'ACTIVE', '2026-05-10 10:15:00', '2026-05-10 10:15:00'
WHERE NOT EXISTS (SELECT 1 FROM `file_asset` WHERE `file_id` = 'FILE-0004');
INSERT INTO `file_asset` (`id`, `file_id`, `storage_key`, `bucket`, `original_filename`, `content_type`, `size`, `sha256`, `uploader_user_id`, `uploader_type`, `upload_channel`, `status`, `created_at`, `updated_at`)
SELECT 13005, 'FILE-0005', 'attachments/2026/05/paper-index.png', 'whut-eval-dev', '论文检索截图.png', 'image/png', 480000, 'sha256-file-0005', 1005, 'USER', 'SELF_UPLOAD', 'ACTIVE', '2026-05-10 10:20:00', '2026-05-10 10:20:00'
WHERE NOT EXISTS (SELECT 1 FROM `file_asset` WHERE `file_id` = 'FILE-0005');
INSERT INTO `file_asset` (`id`, `file_id`, `storage_key`, `bucket`, `original_filename`, `content_type`, `size`, `sha256`, `uploader_user_id`, `uploader_type`, `upload_channel`, `status`, `created_at`, `updated_at`)
SELECT 13006, 'FILE-0006', 'attachments/2026/05/labor-practice.pdf', 'whut-eval-dev', '社会实践报告.pdf', 'application/pdf', 310000, 'sha256-file-0006', 1006, 'USER', 'SELF_UPLOAD', 'ACTIVE', '2026-05-10 10:25:00', '2026-05-10 10:25:00'
WHERE NOT EXISTS (SELECT 1 FROM `file_asset` WHERE `file_id` = 'FILE-0006');
INSERT INTO `file_asset` (`id`, `file_id`, `storage_key`, `bucket`, `original_filename`, `content_type`, `size`, `sha256`, `uploader_user_id`, `uploader_type`, `upload_channel`, `status`, `created_at`, `updated_at`)
SELECT 13007, 'FILE-0007', 'attachments/2026/05/class-monitor.docx', 'whut-eval-dev', '班级活动总结.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 102000, 'sha256-file-0007', 1002, 'USER', 'SELF_UPLOAD', 'ACTIVE', '2026-05-10 10:30:00', '2026-05-10 10:30:00'
WHERE NOT EXISTS (SELECT 1 FROM `file_asset` WHERE `file_id` = 'FILE-0007');
INSERT INTO `file_asset` (`id`, `file_id`, `storage_key`, `bucket`, `original_filename`, `content_type`, `size`, `sha256`, `uploader_user_id`, `uploader_type`, `upload_channel`, `status`, `created_at`, `updated_at`)
SELECT 13008, 'FILE-0008', 'attachments/2026/05/guide-template.pdf', 'whut-eval-dev', '综测申请模板.pdf', 'application/pdf', 142000, 'sha256-file-0008', 1012, 'ADMIN', 'ADMIN_UPLOAD', 'ACTIVE', '2026-05-10 10:35:00', '2026-05-10 10:35:00'
WHERE NOT EXISTS (SELECT 1 FROM `file_asset` WHERE `file_id` = 'FILE-0008');
INSERT INTO `file_asset` (`id`, `file_id`, `storage_key`, `bucket`, `original_filename`, `content_type`, `size`, `sha256`, `uploader_user_id`, `uploader_type`, `upload_channel`, `status`, `created_at`, `updated_at`)
SELECT 13009, 'FILE-0009', 'attachments/2026/05/review-spec.docx', 'whut-eval-dev', '审核口径说明.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 94000, 'sha256-file-0009', 1011, 'ADMIN', 'ADMIN_UPLOAD', 'ACTIVE', '2026-05-10 10:40:00', '2026-05-10 10:40:00'
WHERE NOT EXISTS (SELECT 1 FROM `file_asset` WHERE `file_id` = 'FILE-0009');
INSERT INTO `file_asset` (`id`, `file_id`, `storage_key`, `bucket`, `original_filename`, `content_type`, `size`, `sha256`, `uploader_user_id`, `uploader_type`, `upload_channel`, `status`, `created_at`, `updated_at`)
SELECT 13010, 'FILE-0010', 'attachments/2026/05/public-photo.zip', 'whut-eval-dev', '公共素材包.zip', 'application/zip', 804000, 'sha256-file-0010', 1012, 'ADMIN', 'ADMIN_UPLOAD', 'ACTIVE', '2026-05-10 10:45:00', '2026-05-10 10:45:00'
WHERE NOT EXISTS (SELECT 1 FROM `file_asset` WHERE `file_id` = 'FILE-0010');
INSERT INTO `file_asset` (`id`, `file_id`, `storage_key`, `bucket`, `original_filename`, `content_type`, `size`, `sha256`, `uploader_user_id`, `uploader_type`, `upload_channel`, `status`, `created_at`, `updated_at`)
SELECT 13011, 'FILE-0011', 'attachments/2026/05/old-template.pdf', 'whut-eval-dev', '旧版模板.pdf', 'application/pdf', 156000, 'sha256-file-0011', 1012, 'SYSTEM', 'SYSTEM_IMPORT', 'ARCHIVED', '2026-05-10 10:50:00', '2026-05-10 10:50:00'
WHERE NOT EXISTS (SELECT 1 FROM `file_asset` WHERE `file_id` = 'FILE-0011');
INSERT INTO `file_asset` (`id`, `file_id`, `storage_key`, `bucket`, `original_filename`, `content_type`, `size`, `sha256`, `uploader_user_id`, `uploader_type`, `upload_channel`, `status`, `created_at`, `updated_at`)
SELECT 13012, 'FILE-0012', 'attachments/2026/05/poster.png', 'whut-eval-dev', '活动海报.png', 'image/png', 512000, 'sha256-file-0012', 1007, 'USER', 'SELF_UPLOAD', 'ACTIVE', '2026-05-10 10:55:00', '2026-05-10 10:55:00'
WHERE NOT EXISTS (SELECT 1 FROM `file_asset` WHERE `file_id` = 'FILE-0012');

INSERT INTO `public_attachment_entry` (`id`, `file_id`, `display_name`, `description`, `category_code`, `scope_type`, `scope_value`, `status`, `published_by`, `published_at`, `sort_no`, `created_at`, `updated_at`)
SELECT 14001, 'FILE-0008', '综测申请模板', '学生申请材料填写模板', 'INTELLECTUAL', 'ALL', NULL, 'PUBLISHED', 1012, '2026-05-11 09:00:00', 10, '2026-05-11 09:00:00', '2026-05-11 09:00:00'
WHERE NOT EXISTS (SELECT 1 FROM `public_attachment_entry` WHERE `id` = 14001);
INSERT INTO `public_attachment_entry` (`id`, `file_id`, `display_name`, `description`, `category_code`, `scope_type`, `scope_value`, `status`, `published_by`, `published_at`, `sort_no`, `created_at`, `updated_at`)
SELECT 14002, 'FILE-0009', '审核口径说明', '审核老师使用的统一口径说明', 'MORAL', 'ROLE', 'COUNSELOR', 'PUBLISHED', 1011, '2026-05-11 09:05:00', 20, '2026-05-11 09:05:00', '2026-05-11 09:05:00'
WHERE NOT EXISTS (SELECT 1 FROM `public_attachment_entry` WHERE `id` = 14002);
INSERT INTO `public_attachment_entry` (`id`, `file_id`, `display_name`, `description`, `category_code`, `scope_type`, `scope_value`, `status`, `published_by`, `published_at`, `sort_no`, `created_at`, `updated_at`)
SELECT 14003, 'FILE-0010', '公共素材包', '图文宣传与证明材料公共素材', 'SPORTS', 'ALL', NULL, 'PUBLISHED', 1012, '2026-05-11 09:10:00', 30, '2026-05-11 09:10:00', '2026-05-11 09:10:00'
WHERE NOT EXISTS (SELECT 1 FROM `public_attachment_entry` WHERE `id` = 14003);
INSERT INTO `public_attachment_entry` (`id`, `file_id`, `display_name`, `description`, `category_code`, `scope_type`, `scope_value`, `status`, `published_by`, `published_at`, `sort_no`, `created_at`, `updated_at`)
SELECT 14004, 'FILE-0001', '竞赛证书示例', '学科竞赛获奖证书示例', 'INTELLECTUAL', 'ALL', NULL, 'PUBLISHED', 1012, '2026-05-11 09:15:00', 40, '2026-05-11 09:15:00', '2026-05-11 09:15:00'
WHERE NOT EXISTS (SELECT 1 FROM `public_attachment_entry` WHERE `id` = 14004);
INSERT INTO `public_attachment_entry` (`id`, `file_id`, `display_name`, `description`, `category_code`, `scope_type`, `scope_value`, `status`, `published_by`, `published_at`, `sort_no`, `created_at`, `updated_at`)
SELECT 14005, 'FILE-0002', '志愿服务证明示例', '志愿服务时长证明示例', 'MORAL', 'ALL', NULL, 'PUBLISHED', 1012, '2026-05-11 09:20:00', 50, '2026-05-11 09:20:00', '2026-05-11 09:20:00'
WHERE NOT EXISTS (SELECT 1 FROM `public_attachment_entry` WHERE `id` = 14005);
INSERT INTO `public_attachment_entry` (`id`, `file_id`, `display_name`, `description`, `category_code`, `scope_type`, `scope_value`, `status`, `published_by`, `published_at`, `sort_no`, `created_at`, `updated_at`)
SELECT 14006, 'FILE-0003', '征稿录用示例', '文艺征稿录用结果示例', 'SPORTS', 'ALL', NULL, 'PUBLISHED', 1012, '2026-05-11 09:25:00', 60, '2026-05-11 09:25:00', '2026-05-11 09:25:00'
WHERE NOT EXISTS (SELECT 1 FROM `public_attachment_entry` WHERE `id` = 14006);
INSERT INTO `public_attachment_entry` (`id`, `file_id`, `display_name`, `description`, `category_code`, `scope_type`, `scope_value`, `status`, `published_by`, `published_at`, `sort_no`, `created_at`, `updated_at`)
SELECT 14007, 'FILE-0004', '文体竞赛照片示例', '运动会与比赛现场材料示例', 'SPORTS', 'ORG_UNIT', '2002', 'PUBLISHED', 1010, '2026-05-11 09:30:00', 70, '2026-05-11 09:30:00', '2026-05-11 09:30:00'
WHERE NOT EXISTS (SELECT 1 FROM `public_attachment_entry` WHERE `id` = 14007);
INSERT INTO `public_attachment_entry` (`id`, `file_id`, `display_name`, `description`, `category_code`, `scope_type`, `scope_value`, `status`, `published_by`, `published_at`, `sort_no`, `created_at`, `updated_at`)
SELECT 14008, 'FILE-0005', '论文检索截图示例', '论文发表检索截图说明', 'INTELLECTUAL', 'ALL', NULL, 'PUBLISHED', 1011, '2026-05-11 09:35:00', 80, '2026-05-11 09:35:00', '2026-05-11 09:35:00'
WHERE NOT EXISTS (SELECT 1 FROM `public_attachment_entry` WHERE `id` = 14008);
INSERT INTO `public_attachment_entry` (`id`, `file_id`, `display_name`, `description`, `category_code`, `scope_type`, `scope_value`, `status`, `published_by`, `published_at`, `sort_no`, `created_at`, `updated_at`)
SELECT 14009, 'FILE-0006', '社会实践报告示例', '社会实践报告和总结示例', 'LABOR', 'ALL', NULL, 'PUBLISHED', 1012, '2026-05-11 09:40:00', 90, '2026-05-11 09:40:00', '2026-05-11 09:40:00'
WHERE NOT EXISTS (SELECT 1 FROM `public_attachment_entry` WHERE `id` = 14009);
INSERT INTO `public_attachment_entry` (`id`, `file_id`, `display_name`, `description`, `category_code`, `scope_type`, `scope_value`, `status`, `published_by`, `published_at`, `sort_no`, `created_at`, `updated_at`)
SELECT 14010, 'FILE-0007', '班级活动总结示例', '德育活动材料整理样例', 'MORAL', 'ORG_UNIT', '2010', 'PUBLISHED', 1010, '2026-05-11 09:45:00', 100, '2026-05-11 09:45:00', '2026-05-11 09:45:00'
WHERE NOT EXISTS (SELECT 1 FROM `public_attachment_entry` WHERE `id` = 14010);
INSERT INTO `public_attachment_entry` (`id`, `file_id`, `display_name`, `description`, `category_code`, `scope_type`, `scope_value`, `status`, `published_by`, `published_at`, `sort_no`, `created_at`, `updated_at`)
SELECT 14011, 'FILE-0011', '旧版模板（下线）', '保留旧版样例供历史对比', 'INTELLECTUAL', 'ALL', NULL, 'OFFLINE', 1012, '2026-05-11 09:50:00', 110, '2026-05-11 09:50:00', '2026-05-11 09:50:00'
WHERE NOT EXISTS (SELECT 1 FROM `public_attachment_entry` WHERE `id` = 14011);
INSERT INTO `public_attachment_entry` (`id`, `file_id`, `display_name`, `description`, `category_code`, `scope_type`, `scope_value`, `status`, `published_by`, `published_at`, `sort_no`, `created_at`, `updated_at`)
SELECT 14012, 'FILE-0012', '活动海报示例', '活动宣传与现场图示例', 'LABOR', 'ALL', NULL, 'DRAFT', 1012, NULL, 120, '2026-05-11 09:55:00', '2026-05-11 09:55:00'
WHERE NOT EXISTS (SELECT 1 FROM `public_attachment_entry` WHERE `id` = 14012);
