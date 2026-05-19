-- C 组：审核动作、审核轨迹、审核状态流转
-- 负责表：
--   application_review_log
--
-- 说明：
-- 1. 本脚本只创建审核轨迹表。
-- 2. `application_submission` 的状态流转由 C 组在应用服务层受限执行，不在本脚本额外维护表结构。

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `application_review_log`;

CREATE TABLE `application_review_log` (
  `id` BIGINT NOT NULL,
  `application_id` BIGINT NOT NULL,
  `action` VARCHAR(32) NOT NULL,
  `reviewer_id` BIGINT NOT NULL,
  `review_role` VARCHAR(64) NOT NULL,
  `reason` VARCHAR(1000) DEFAULT NULL,
  `reviewed_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_application_review_log_application_id` (`application_id`),
  KEY `idx_application_review_log_reviewer_id` (`reviewer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核轨迹';

INSERT INTO `application_review_log` (`id`, `application_id`, `action`, `reviewer_id`, `review_role`, `reason`, `reviewed_at`) VALUES
(31001, 21001, 'APPROVE', 1011, 'COLLEGE_REVIEWER', '材料完整，竞赛获奖等级清晰。', '2026-05-16 10:00:00'),
(31002, 21002, 'APPROVE', 1002, 'CLASS_MONITOR', '班级层面核对服务时长无误。', '2026-05-13 13:00:00'),
(31003, 21002, 'APPROVE', 1010, 'COUNSELOR', '志愿服务证明齐全，予以通过。', '2026-05-14 09:30:00'),
(31004, 21003, 'RETURN', 1002, 'CLASS_MONITOR', '需补充多人合作中的本人贡献说明。', '2026-05-14 11:00:00'),
(31005, 21003, 'RETURN', 1010, 'COUNSELOR', '请补充合作分工与作品采用链接。', '2026-05-15 11:40:00'),
(31006, 21004, 'APPROVE', 1010, 'COUNSELOR', '运动会成绩册与现场照片一致。', '2026-05-15 09:10:00'),
(31007, 21005, 'REJECT', 1011, 'COLLEGE_REVIEWER', '论文检索与录用证明不足，当前不满足认定条件。', '2026-05-16 09:00:00'),
(31008, 21007, 'APPROVE', 1010, 'COUNSELOR', '勤工助学记录由用工部门确认，通过。', '2026-05-15 16:20:00'),
(31009, 21008, 'WITHDRAW', 1008, 'STUDENT', '申请人主动撤回，准备补充荣誉证书原件后重提。', '2026-05-14 12:00:00'),
(31010, 21009, 'APPROVE', 1002, 'CLASS_MONITOR', '活动照片与现场说明匹配，建议进入辅导员复核。', '2026-05-17 16:20:00'),
(31011, 21010, 'APPROVE', 1011, 'COLLEGE_REVIEWER', '项目结题证明完整，通过。', '2026-05-15 17:30:00'),
(31012, 21011, 'RETURN', 1010, 'COUNSELOR', '环保实践签到照片不完整，请补充后再审。', '2026-05-18 12:30:00'),
(31013, 21012, 'APPROVE', 1002, 'CLASS_MONITOR', '志愿讲解时长已核实，提交辅导员复审。', '2026-05-18 11:00:00');
