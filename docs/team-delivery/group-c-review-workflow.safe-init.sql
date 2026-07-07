CREATE TABLE IF NOT EXISTS `application_review_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `action` VARCHAR(32) NOT NULL,
  `reviewer_id` BIGINT NOT NULL,
  `review_role` VARCHAR(64) NOT NULL,
  `reason` VARCHAR(1000) DEFAULT NULL,
  `reviewed_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_application_review_log_application_id` (`application_id`),
  KEY `idx_application_review_log_reviewer_id` (`reviewer_id`)
);
