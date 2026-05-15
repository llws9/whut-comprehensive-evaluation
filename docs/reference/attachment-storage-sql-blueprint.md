# 附件存储与公共附件池 SQL 蓝图

## 1. 文档目标

本文档给出当前 rewrite 方案下附件体系的第一版 SQL 蓝图，覆盖三张核心表：

- `file_asset`
- `public_attachment_entry`
- `application_attachment`

目标是解决以下问题：

1. 上传接口返回一个稳定的 `fileId`，后续申请请求体只传 `fileId`
2. 附件既支持“用户自己上传后绑定”，也支持“管理员发布到公共附件池后被选择”
3. 同一条申请可以混合选择多个附件，来源既可以是用户上传，也可以是公共附件池
4. 数据库层面能够区分附件来源、发布状态和申请绑定关系

## 2. 设计原则

本蓝图采用“三层模型”：

1. `file_asset`：统一保存文件本体与上传元数据
2. `public_attachment_entry`：表达某个 `fileId` 是否已被发布到公共附件池
3. `application_attachment`：表达某条申请最终绑定了哪些 `fileId`

关键约束如下：

- 不拆“用户上传附件表”和“公共附件附件表”两套主表
- 统一以 `file_id` 作为业务稳定 ID
- 申请请求体后续只传 `attachmentFileIds`
- 是否来自公共池，不由前端声明，而由后端在绑定时解析并写入 `selected_source`

## 3. 枚举建议

为了保证 SQL 可直接落地，第一版建议枚举字段统一使用 `VARCHAR`，而不是数据库原生 `ENUM`。

推荐取值：

| 字段 | 建议值 | 说明 |
|---|---|---|
| `uploader_type` | `USER` / `ADMIN` / `SYSTEM` | 谁上传了文件 |
| `upload_channel` | `SELF_UPLOAD` / `ADMIN_UPLOAD` / `SYSTEM_IMPORT` | 文件产生方式 |
| `status` in `file_asset` | `ACTIVE` / `DELETED` / `ARCHIVED` | 文件主记录状态 |
| `status` in `public_attachment_entry` | `DRAFT` / `PUBLISHED` / `OFFLINE` | 公共附件池发布状态 |
| `scope_type` | `ALL` / `ORG_UNIT` / `ROLE` | 公共池可见范围 |
| `selected_source` | `SELF_UPLOAD` / `PUBLIC_POOL` | 申请绑定时的实际选择来源 |

## 4. `file_asset`

### 4.1 表职责

`file_asset` 是统一文件主表，负责保存：

- 稳定业务文件 ID
- 对象存储定位信息
- 上传者与上传来源
- 文件名、类型、大小、校验摘要
- 生命周期状态

### 4.2 建表 SQL

```sql
CREATE TABLE `file_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `file_id` VARCHAR(64) NOT NULL COMMENT '业务稳定文件ID，返回给前端并用于业务请求体',
  `storage_key` VARCHAR(512) NOT NULL COMMENT '对象存储key，例如 uploads/dev/application/20260514/uuid-award.pdf',
  `bucket` VARCHAR(128) NOT NULL COMMENT '对象存储bucket',
  `original_filename` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `content_type` VARCHAR(128) NOT NULL COMMENT 'MIME类型',
  `size` BIGINT NOT NULL COMMENT '文件大小，单位字节',
  `sha256` CHAR(64) DEFAULT NULL COMMENT '文件内容SHA-256摘要，用于去重或完整性校验',
  `uploader_user_id` BIGINT NOT NULL COMMENT '上传者用户ID',
  `uploader_type` VARCHAR(32) NOT NULL COMMENT '上传者类型：USER/ADMIN/SYSTEM',
  `upload_channel` VARCHAR(32) NOT NULL COMMENT '上传渠道：SELF_UPLOAD/ADMIN_UPLOAD/SYSTEM_IMPORT',
  `status` VARCHAR(32) NOT NULL COMMENT '文件状态：ACTIVE/DELETED/ARCHIVED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_asset_file_id` (`file_id`),
  UNIQUE KEY `uk_file_asset_storage_key` (`storage_key`),
  KEY `idx_file_asset_uploader_status` (`uploader_user_id`, `status`),
  KEY `idx_file_asset_channel_status_created` (`upload_channel`, `status`, `created_at`),
  KEY `idx_file_asset_sha256` (`sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一文件主表';
```

### 4.3 字段说明

| 字段 | 是否必填 | 说明 |
|---|---|---|
| `file_id` | 是 | 前端和业务接口唯一应感知的稳定文件标识 |
| `storage_key` | 是 | 真正落到 OSS 的对象 key |
| `bucket` | 是 | 当前存储桶 |
| `sha256` | 否 | 第一版可选，但建议预留 |
| `uploader_user_id` | 是 | 用于校验“当前用户是否有权直接使用自己的附件” |
| `uploader_type` | 是 | 用于审计区分用户上传和管理员上传 |
| `upload_channel` | 是 | 用于区分自助上传、管理员上传、系统导入 |
| `status` | 是 | 文件逻辑删除或归档时使用 |

### 4.4 索引理由

| 索引 | 用途 |
|---|---|
| `uk_file_asset_file_id` | 按 `fileId` 查文件元数据，申请绑定主链路必须使用 |
| `uk_file_asset_storage_key` | 保证同一对象 key 不重复注册 |
| `idx_file_asset_uploader_status` | 查询某个用户自己上传的可用附件列表 |
| `idx_file_asset_channel_status_created` | 支持后台按上传来源和状态分页 |
| `idx_file_asset_sha256` | 后续做内容去重时可直接复用 |

## 5. `public_attachment_entry`

### 5.1 表职责

`public_attachment_entry` 不保存文件本体，而是表达：

- 某个 `fileId` 是否已发布到公共附件池
- 它当前是否上架
- 它按什么范围对外可见
- 它在公共池中的展示元数据

### 5.2 建表 SQL

```sql
CREATE TABLE `public_attachment_entry` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `file_id` VARCHAR(64) NOT NULL COMMENT '关联 file_asset.file_id',
  `display_name` VARCHAR(255) NOT NULL COMMENT '公共附件展示名，可与原始文件名不同',
  `description` VARCHAR(1000) DEFAULT NULL COMMENT '公共附件说明',
  `category_code` VARCHAR(64) DEFAULT NULL COMMENT '公共附件分类，例如 policy/template/manual',
  `scope_type` VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT '可见范围类型：ALL/ORG_UNIT/ROLE',
  `scope_value` VARCHAR(128) DEFAULT NULL COMMENT '范围值；ALL时可为空',
  `status` VARCHAR(32) NOT NULL COMMENT '发布状态：DRAFT/PUBLISHED/OFFLINE',
  `published_by` BIGINT DEFAULT NULL COMMENT '发布人用户ID',
  `published_at` DATETIME(3) DEFAULT NULL COMMENT '发布时间',
  `sort_no` INT NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_attachment_entry_file_id` (`file_id`),
  KEY `idx_public_attachment_status_category_sort` (`status`, `category_code`, `sort_no`, `id`),
  KEY `idx_public_attachment_scope` (`scope_type`, `scope_value`, `status`),
  KEY `idx_public_attachment_published_at` (`published_at`),
  CONSTRAINT `fk_public_attachment_entry_file_id`
    FOREIGN KEY (`file_id`) REFERENCES `file_asset` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公共附件池发布表';
```

### 5.3 字段说明

| 字段 | 是否必填 | 说明 |
|---|---|---|
| `file_id` | 是 | 只引用统一文件主表 |
| `display_name` | 是 | 公共池展示名称，不要求等于原文件名 |
| `category_code` | 否 | 便于后续公共池分类筛选 |
| `scope_type/scope_value` | 是/否 | 用于表达公共附件的访问范围 |
| `status` | 是 | 只有 `PUBLISHED` 才允许被普通用户选择 |
| `published_by/published_at` | 否 | `DRAFT` 时可为空，正式发布后应补齐 |

### 5.4 索引理由

| 索引 | 用途 |
|---|---|
| `uk_public_attachment_entry_file_id` | 保证一个 `fileId` 在公共池里只有一条主发布记录 |
| `idx_public_attachment_status_category_sort` | 支持公共附件池列表页按状态、分类和排序分页 |
| `idx_public_attachment_scope` | 支持按组织 / 角色 / 全局范围过滤 |
| `idx_public_attachment_published_at` | 支持按发布时间倒序查询 |

### 5.5 为什么这里不拆第二张文件表

因为公共附件池的“公共性”是发布属性，不是文件本体属性。

同一个文件可能先经历：

1. 管理员上传到 `file_asset`
2. 管理员将其发布到 `public_attachment_entry`
3. 学生在申请中选择该 `fileId`

这更适合一张主文件表 + 一张发布表，而不是两张文件主表。

## 6. `application_attachment`

### 6.1 表职责

`application_attachment` 负责记录：

- 某条申请最终绑定了哪些 `fileId`
- 本次绑定时它的来源是“用户自己上传”还是“公共附件池”
- 绑定时的文件快照信息

这里保留快照字段，是为了避免后续 `file_asset` 元数据变化后影响历史申请回显。

### 6.2 建表 SQL

```sql
CREATE TABLE `application_attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `application_id` BIGINT NOT NULL COMMENT '关联 application_submission.application_id',
  `file_id` VARCHAR(64) NOT NULL COMMENT '关联 file_asset.file_id',
  `selected_source` VARCHAR(32) NOT NULL COMMENT '本次绑定来源：SELF_UPLOAD/PUBLIC_POOL',
  `sort_no` INT NOT NULL DEFAULT 0 COMMENT '附件顺序',
  `snapshot_filename` VARCHAR(255) NOT NULL COMMENT '绑定时文件名快照',
  `snapshot_content_type` VARCHAR(128) NOT NULL COMMENT '绑定时MIME类型快照',
  `snapshot_size` BIGINT NOT NULL COMMENT '绑定时文件大小快照',
  `snapshot_storage_key` VARCHAR(512) NOT NULL COMMENT '绑定时对象key快照',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_application_attachment_app_file` (`application_id`, `file_id`),
  UNIQUE KEY `uk_application_attachment_app_sort` (`application_id`, `sort_no`),
  KEY `idx_application_attachment_file_id` (`file_id`),
  KEY `idx_application_attachment_source_created` (`selected_source`, `created_at`),
  CONSTRAINT `fk_application_attachment_file_id`
    FOREIGN KEY (`file_id`) REFERENCES `file_asset` (`file_id`),
  CONSTRAINT `fk_application_attachment_application_id`
    FOREIGN KEY (`application_id`) REFERENCES `application_submission` (`application_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='申请附件关联表';
```

### 6.3 字段说明

| 字段 | 是否必填 | 说明 |
|---|---|---|
| `application_id` | 是 | 绑定目标申请 |
| `file_id` | 是 | 绑定的统一文件 ID |
| `selected_source` | 是 | 记录本次选择来源，不信前端传值，由后端推断 |
| `sort_no` | 是 | 支持前端拖拽排序或按上传顺序展示 |
| `snapshot_*` | 是 | 申请视角的附件快照 |

### 6.4 索引理由

| 索引 | 用途 |
|---|---|
| `uk_application_attachment_app_file` | 防止同一申请重复绑定同一个文件 |
| `uk_application_attachment_app_sort` | 保证同一申请的附件排序唯一 |
| `idx_application_attachment_file_id` | 支持后续查哪些申请引用了某个文件 |
| `idx_application_attachment_source_created` | 支持按来源做统计或审计 |

## 7. 申请绑定时的后端判定规则

为了让三张表真正闭环，后端在处理 `attachmentFileIds` 时建议按以下规则判断：

1. 先按 `file_id` 读取 `file_asset`
2. 文件必须存在，且 `status = 'ACTIVE'`
3. 当前用户可使用该附件，当且仅当满足以下之一：
   - `file_asset.uploader_user_id = currentUserId`
   - `public_attachment_entry.status = 'PUBLISHED'` 且范围允许当前用户访问
4. 若命中第一条，`selected_source = 'SELF_UPLOAD'`
5. 若命中第二条，`selected_source = 'PUBLIC_POOL'`
6. 再把 `file_asset` 中的元数据写入 `application_attachment.snapshot_*`

因此，后续申请请求体建议收敛为：

```json
{
  "title": "2025 学年校级竞赛加分申请",
  "description": "提交获奖证书和成绩证明",
  "attachmentFileIds": [
    "file_01USERUPLOAD001",
    "file_01PUBLICPOOL009",
    "file_01USERUPLOAD010"
  ]
}
```

## 8. 推荐原子权限

围绕“自助上传 + 公共附件池”场景，建议至少定义以下权限码：

| 权限码 | 说明 |
|---|---|
| `attachment.upload.self` | 允许用户上传自己的附件 |
| `attachment.pool.read` | 允许浏览和选择公共附件池 |
| `attachment.pool.publish` | 允许管理员把 `fileId` 发布到公共附件池 |
| `attachment.pool.offline` | 允许管理员下架公共附件 |

如果第一阶段只做最小版本，也可以先落前三个。

## 9. 索引与查询关系总结

### 9.1 典型查询 1：查当前用户自己上传的附件

使用：

- `file_asset.idx_file_asset_uploader_status`

### 9.2 典型查询 2：查当前可选公共附件列表

使用：

- `public_attachment_entry.idx_public_attachment_status_category_sort`
- `public_attachment_entry.idx_public_attachment_scope`

### 9.3 典型查询 3：查某条申请已绑定的附件

使用：

- `application_attachment.uk_application_attachment_app_sort`

### 9.4 典型查询 4：查某个文件被哪些申请引用

使用：

- `application_attachment.idx_application_attachment_file_id`

## 10. 迁移建议

当前文档只是 SQL 蓝图，不等同于正式迁移脚本。

如果下一步要落到 Flyway，建议拆成如下顺序：

1. `V1__create_file_asset.sql`
2. `V2__create_public_attachment_entry.sql`
3. `V3__create_application_attachment.sql`

如果 `application_submission` 尚未迁移到正式库，`application_attachment` 中对 `application_submission` 的外键可以在对应表创建后再补。

## 11. 一句话结论

这套表设计的关键不是把“用户上传附件”和“公共附件”拆成两种文件，而是把它们统一建模成“一种文件，两种可选来源”。因此应使用：

- `file_asset` 作为统一文件主表
- `public_attachment_entry` 作为公共池发布表
- `application_attachment` 作为申请绑定关系表

这样后续扩展公共附件池、权限控制、附件混选、孤儿文件清理和历史回显时，成本最低。
