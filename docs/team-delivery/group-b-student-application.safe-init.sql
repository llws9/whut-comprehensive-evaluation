CREATE TABLE IF NOT EXISTS `application_submission` (
  `application_id` BIGINT NOT NULL AUTO_INCREMENT,
  `applicant_user_id` BIGINT NOT NULL,
  `org_unit_id` BIGINT NOT NULL,
  `category_code` VARCHAR(64) NOT NULL,
  `item_code` VARCHAR(64) NOT NULL,
  `academic_year` VARCHAR(32) NOT NULL,
  `term` VARCHAR(32) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` VARCHAR(1000) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `submitted_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `version` BIGINT NOT NULL,
  PRIMARY KEY (`application_id`),
  KEY `idx_application_submission_active_claim` (`applicant_user_id`, `item_code`, `academic_year`, `term`, `status`)
);

CREATE TABLE IF NOT EXISTS `application_attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `file_id` VARCHAR(128) NOT NULL,
  `storage_key` VARCHAR(512) NOT NULL,
  `original_filename` VARCHAR(255) NOT NULL,
  `content_type` VARCHAR(128) NOT NULL,
  `size` BIGINT NOT NULL,
  `uploaded_by` BIGINT NOT NULL,
  `sort_no` INT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_application_attachment_application_id` (`application_id`),
  KEY `idx_application_attachment_uploaded_by` (`uploaded_by`)
);

CREATE TABLE IF NOT EXISTS `application_fact` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `score_value` DECIMAL(10,2) DEFAULT NULL,
  `display_text` VARCHAR(1000) DEFAULT NULL,
  `evidence_count` INT NOT NULL,
  `extra_json` VARCHAR(2000) DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_application_fact_application_id` (`application_id`)
);
