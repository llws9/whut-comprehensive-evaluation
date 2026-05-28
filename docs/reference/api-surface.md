# HTTP 接口面说明

## 1. 文档目的

本文档用于说明 `rewrite/whut-comprehensive-evaluation` 当前已经落地的 HTTP 接口分组方式，方便后续开发时快速回答三个问题：

1. 某个接口归属于哪个端口分组
2. 某个接口对应哪个 Controller 和权限码
3. 某个接口当前已经暴露哪些请求参数、请求体和响应体

当前文档覆盖的是“当前已落地的全部 HTTP 入口总表”，包括：

- 认证接口
- 安全探针接口
- IAM 身份查询接口
- 文件上传接口
- 学生侧查询接口
- 学生侧写接口
- 管理侧查询接口

不在本文覆盖范围内、但已在交付文档中冻结契约的能力：

- `A-9 ~ A-12` 角色模板管理接口
- `A-13 ~ A-18` 角色分配与范围规则接口

这些能力当前应以 `docs/team-delivery/group-a-identity-user-admin.md` 为准；只有在代码仓中形成实际 Controller / DTO / 安全注解后，才纳入本文的“当前接口面”总表。

### 1.1 快速导航

- [2. 当前接口分组](#2-当前接口分组)
- [3. 完整接口总表](#3-完整接口总表)
- [4. Auth 接口](#4-auth-接口)
- [5. Security Probe 接口](#5-security-probe-接口)
- [6. IAM 接口](#6-iam-接口)
- [7. File Upload 接口](#7-file-upload-接口)
- [8. Student Query 接口](#8-student-query-接口)
- [9. Student Write 接口](#9-student-write-接口)
- [10. Admin Query 接口](#10-admin-query-接口)
- [11. 当前边界与后续建议](#11-当前边界与后续建议)

## 2. 当前接口分组

当前项目中的 HTTP 入口分散在 `whut-eval-app` 和 `whut-eval-interfaces` 两个模块中，按职责可分为七组：

| 分组 | 路由前缀 | Controller | 所在模块 | 说明 |
|---|---|---|---|---|
| 认证接口 | `/api/auth/*` | `AuthController` | `whut-eval-app` | 负责登录、刷新令牌等认证入口 |
| 安全探针接口 | `/api/security/*` | `SecurityProbeController` | `whut-eval-app` | 用于查看当前请求上下文下的认证主体与授权信息 |
| IAM 身份查询接口 | `/api/iam/users/*` | `UserIdentityQueryController` | `whut-eval-interfaces` | 用于按用户编号查询身份、岗位与组织归属 |
| 文件上传接口 | `/api/files/*` | `FileUploadController` | `whut-eval-interfaces` | 对外提供最小文件上传入口，底层走 OSS 上传服务 |
| 学生侧查询接口 | `/api/student/query/*` | `StudentQueryController` | `whut-eval-interfaces` | 面向学生侧的授权查询接口分组 |
| 学生侧写接口 | `/api/student/applications/*`、`/api/student/preferences/*` | `StudentApplicationSubmissionController`、`StudentPreferenceController` | `whut-eval-interfaces` | 包含正式申请写接口和 `P0-2` 最小写入参考实现 |
| 管理侧查询接口 | `/api/admin/query/*` | `AdminQueryController` | `whut-eval-interfaces` | 面向教师 / 管理侧的授权查询接口分组 |

补充说明：

- `student` 和 `admin` 当前已经完成接口面拆分。
- `FileUploadController` 是当前上传底座的最小 HTTP 入口，上传结果通过标准化 view 对外返回。
- 两侧当前都先承接既有“申请查询 / 成绩查询”能力。
- 学生侧当前按“查看自己的数据”建模，管理侧继续沿用“审核 / 分配查询”权限口径。
- `StudentPreferenceController` 不是正式业务能力，而是 `P0-2` 阶段用于验证写入侧 Repo / Command / 事务 / 409 映射 / 缓存失效的最小参考实现。
- 认证与安全探针接口当前仍放在 `whut-eval-app` 模块中。

阅读顺序说明：

- 本文后续详细接口按 `Auth -> Security Probe -> IAM -> File Upload -> Student Query -> Student Write -> Admin Query` 展开。
- 若只想快速定位接口归属，先看“完整接口总表”；若要落实现或联调，再下钻到对应章节。

## 3. 完整接口总表

| 分组 | HTTP Method | Path | Controller 方法 | 鉴权方式 | 主要用途 |
|---|---|---|---|---|---|
| 认证 | `POST` | `/api/auth/login` | `AuthController#login(...)` | 匿名可访问 | 用户登录并签发 Access Token / Refresh Token |
| 认证 | `POST` | `/api/auth/refresh` | `AuthController#refresh(...)` | 匿名可访问 | 使用 Refresh Token 换发新 Token 对 |
| 安全探针 | `GET` | `/api/security/me` | `SecurityProbeController#currentUser()` | 依赖 Bearer Token | 查看当前请求上下文中的用户、角色、权限、范围 |
| IAM | `GET` | `/api/iam/users/{userNo}/identity` | `UserIdentityQueryController#getUserIdentity(...)` | 当前代码未显式声明 `@PreAuthorize` | 按学号 / 工号查询身份信息 |
| File Upload | `POST` | `/api/files/upload` | `FileUploadController#upload(...)` | 依赖当前登录态 | 上传单个文件到 OSS，登记 `file_asset`，并返回可供业务绑定的 `fileId` |
| Student Query | `GET` | `/api/student/query/applications` | `StudentQueryController#pageApplications(...)` | `application.view.self` | 查询当前学生本人可见申请列表 |
| Student Query | `GET` | `/api/student/query/scores` | `StudentQueryController#pageScores(...)` | `score.view.self` | 查询当前学生本人成绩列表 |
| Student Write | `POST` | `/api/student/applications/drafts` | `StudentApplicationSubmissionController#createDraft(...)` | 依赖当前登录态 | 创建申请草稿，请求体通过 `attachmentFileIds` 绑定已上传附件 |
| Student Write | `PUT` | `/api/student/applications/{applicationId}/draft` | `StudentApplicationSubmissionController#updateDraft(...)` | 依赖当前登录态 | 更新申请草稿，并按新的 `attachmentFileIds` 替换附件集合 |
| Student Write | `POST` | `/api/student/applications/{applicationId}/submit` | `StudentApplicationSubmissionController#submit(...)` | 依赖当前登录态 | 提交指定申请 |
| Student Write | `POST` | `/api/student/applications/{applicationId}/withdraw` | `StudentApplicationSubmissionController#withdraw(...)` | 依赖当前登录态 | 撤回指定申请 |
| Student Write | `POST` | `/api/student/preferences` | `StudentPreferenceController#createPreference(...)` | 依赖当前登录态 | `P0-2` 最小写入参考实现，创建当前用户偏好设置 |
| Admin Query | `GET` | `/api/admin/query/applications` | `AdminQueryController#pageApplications(...)` | `application.review` | 查询当前用户可见申请列表 |
| Admin Query | `GET` | `/api/admin/query/scores` | `AdminQueryController#pageScores(...)` | `score.view.assigned` | 查询当前用户可见成绩列表 |
| Admin Query | `GET` | `/api/admin/permissions` | `AdminQueryController#listPermissions(...)` | `permission.manage` | 查询权限字典，供角色模板权限选择器使用 |
| Admin Query | `GET` | `/api/admin/org-units/tree` | `AdminQueryController#listOrgUnitTree(...)` | `org.manage` | 查询组织树字典 |

## 4. Auth 接口

### 4.1 分组信息

| 项 | 说明 |
|---|---|
| Controller | `edu.whut.eval.app.security.AuthController` |
| 路由前缀 | `/api/auth` |
| 所在模块 | `whut-eval-app` |

### 4.2 登录

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `POST` |
| Path | `/api/auth/login` |
| Controller 方法 | `login(...)` |
| 鉴权方式 | 匿名可访问 |
| 请求体 DTO | `LoginRequest` |
| 应用服务 | `LoginAuthenticationService#authenticate(...)` |

请求体字段：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `credential` | `String` | 是 | 登录凭证，当前实现按学号 / 工号一类凭证处理 |
| `password` | `String` | 是 | 原始密码 |

成功响应：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `accessToken` | `String` | Access Token |
| `accessTokenType` | `String` | Token 类型 |
| `accessTokenExpiresAt` | `String` | Access Token 过期时间 |
| `refreshToken` | `String` | Refresh Token |
| `refreshTokenType` | `String` | Refresh Token 类型 |
| `refreshTokenExpiresAt` | `String` | Refresh Token 过期时间 |

响应类型：`ApiResponse<AuthTokenResponse>`

### 4.3 刷新令牌

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `POST` |
| Path | `/api/auth/refresh` |
| Controller 方法 | `refresh(...)` |
| 鉴权方式 | 匿名可访问 |
| 请求体 DTO | `RefreshTokenRequest` |
| 应用服务 | `RefreshTokenCurrentUserLoader#load(...)` + `JwtTokenIssuer#issueTokenPair(...)` |

请求体字段：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `refreshToken` | `String` | 是 | 已签发的 Refresh Token |

成功响应：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `accessToken` | `String` | 新签发的 Access Token |
| `accessTokenType` | `String` | Token 类型 |
| `accessTokenExpiresAt` | `String` | Access Token 过期时间 |
| `refreshToken` | `String` | 新签发的 Refresh Token |
| `refreshTokenType` | `String` | Token 类型 |
| `refreshTokenExpiresAt` | `String` | Refresh Token 过期时间 |

响应类型：`ApiResponse<AuthTokenResponse>`

## 5. Security Probe 接口

### 5.1 分组信息

| 项 | 说明 |
|---|---|
| Controller | `edu.whut.eval.app.security.SecurityProbeController` |
| 路由前缀 | `/api/security` |
| 所在模块 | `whut-eval-app` |

### 5.2 查询当前认证上下文

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `GET` |
| Path | `/api/security/me` |
| Controller 方法 | `currentUser()` |
| 鉴权方式 | 依赖 Bearer Token；缺失或非法时返回 `401` |
| 应用服务 | `UserAuthorizationContextAssembler#requiredAuthorizationContext()` |

成功响应：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `userId` | `Long` | 当前用户 ID |
| `userNo` | `String` | 用户编号 |
| `userName` | `String` | 用户姓名 |
| `identity` | `String` | 当前身份标识 |
| `roles` | `List<String>` | 角色集合 |
| `authorities` | `List<String>` | 权限码集合 |
| `scopeRules` | `List<Object>` | 数据范围规则集合 |

响应类型：`ApiResponse<Map<String, Object>>`

## 6. IAM 接口

### 6.1 分组信息

| 项 | 说明 |
|---|---|
| Controller | `edu.whut.eval.interfaces.iam.UserIdentityQueryController` |
| 路由前缀 | `/api/iam/users` |
| 所在模块 | `whut-eval-interfaces` |

### 6.2 按用户编号查询身份

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `GET` |
| Path | `/api/iam/users/{userNo}/identity` |
| Controller 方法 | `getUserIdentity(...)` |
| 鉴权方式 | 当前代码未显式声明 `@PreAuthorize` |
| 路径参数 | `userNo` |
| 应用服务 | `UserIdentityQueryApplicationService#getUserIdentityByUserNo(...)` |

成功响应：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `user` | `object` | 用户主信息 |
| `assignments` | `object[]` | 角色分配列表 |
| `memberships` | `object[]` | 组织归属列表 |

响应类型：`ApiResponse<UserIdentityView>`

## 7. File Upload 接口

### 7.1 分组信息

| 项 | 说明 |
|---|---|
| Controller | `edu.whut.eval.interfaces.file.FileUploadController` |
| 路由前缀 | `/api/files` |
| 所在模块 | `whut-eval-interfaces` |

### 7.2 上传单个文件

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `POST` |
| Path | `/api/files/upload` |
| Controller 方法 | `upload(...)` |
| Content-Type | `multipart/form-data` |
| 鉴权方式 | 依赖当前登录态；当前未显式声明 `@PreAuthorize`，默认受全局安全链路保护 |
| 应用服务 | `FileUploadApplicationService#upload(...)` |
| 存储实现 | `OssFileStorageService#store(...)` |

请求参数：

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | `MultipartFile` | 是 | 上传文件本体 |
| `bizType` | `String` | 否 | 业务分组目录，例如 `profile`、`attachment/import` |

成功响应：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `fileId` | `String` | 稳定业务文件 ID |
| `bucket` | `String` | 对象存储 bucket |
| `objectKey` | `String` | 对象存储 key |
| `publicUrl` | `String` | 公共访问地址；可能为空 |
| `originalFilename` | `String` | 原始文件名 |
| `contentType` | `String` | MIME 类型 |
| `size` | `long` | 文件大小，单位字节 |

响应类型：`ApiResponse<StoredFileDescriptorView>`

失败路径：

| 场景 | HTTP | 错误码 | 说明 |
|---|---:|---|---|
| 空文件 | `400` | `VAL-4001` | 上传文件不能为空 |
| OSS typed config 缺失 | `503` | `CFG-5031` | 对象存储配置未加载成功 |
| OSS 上传失败 | `503` | `EXT-5033` | 对象存储调用失败 |

补充说明：

- 当前对象 key 规则为 `keyPrefix/bizType/yyyyMMdd/uuid-filename`
- 上传成功后会先登记 `file_asset`，并返回稳定业务 ID `fileId`
- `fileId` 用于后续正式业务对象绑定附件；当前 `ApplicationSubmission` 创建 / 更新链路已经按 `attachmentFileIds` 消费该字段
- 当前上传链路已经补齐 `request.received`、`storage.started`、`storage.completed`、`oss.put-object.failed` 等结构化日志
- 更完整的配置与验收说明见 `docs/reference/object-storage-config.md`

## 8. Student Query 接口

### 8.1 分组信息

| 项 | 说明 |
|---|---|
| Controller | `edu.whut.eval.interfaces.student.StudentQueryController` |
| 路由前缀 | `/api/student/query` |
| 所在模块 | `whut-eval-interfaces` |

### 8.2 查询本人可见申请列表

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `GET` |
| Path | `/api/student/query/applications` |
| Controller 方法 | `pageApplications(...)` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_VIEW_SELF)")` |
| 权限码 | `application.view.self` |
| 应用服务 | `ApplicationQueryApplicationService#pageAccessibleApplications(..., permissionCode)` |

查询参数：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `pageNo` | `long` | 否 | `1` | 页码 |
| `pageSize` | `long` | 否 | `20` | 每页数量 |
| `applicationId` | `Long` | 否 | - | 按申请 ID 过滤 |
| `applicantUserId` | `Long` | 否 | - | 按申请人用户 ID 过滤 |
| `orgUnitId` | `Long` | 否 | - | 按组织单元过滤 |
| `categoryCode` | `String` | 否 | - | 按类别编码过滤 |
| `itemCode` | `String` | 否 | - | 按项目编码过滤 |

成功响应：

| 项 | 说明 |
|---|---|
| 响应类型 | `ApiResponse<PageResult<ApplicationRecordView>>` |
| 列表元素 | `ApplicationRecordView` |

### 8.3 查询本人成绩列表

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `GET` |
| Path | `/api/student/query/scores` |
| Controller 方法 | `pageScores(...)` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_SELF)")` |
| 权限码 | `score.view.self` |
| 应用服务 | `ScoreQueryApplicationService#pageAccessibleScores(..., permissionCode)` |

查询参数：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `pageNo` | `long` | 否 | `1` | 页码 |
| `pageSize` | `long` | 否 | `20` | 每页数量 |
| `scoreId` | `Long` | 否 | - | 按成绩记录 ID 过滤 |
| `studentUserId` | `Long` | 否 | - | 按学生用户 ID 过滤 |
| `orgUnitId` | `Long` | 否 | - | 按组织单元过滤 |
| `categoryCode` | `String` | 否 | - | 按类别编码过滤 |
| `itemCode` | `String` | 否 | - | 按项目编码过滤 |
| `academicYear` | `String` | 否 | - | 按学年过滤 |

成功响应：

| 项 | 说明 |
|---|---|
| 响应类型 | `ApiResponse<PageResult<ScoreRecordView>>` |
| 列表元素 | `ScoreRecordView` |

## 9. Student Write 接口

### 9.1 分组信息

| 项 | 说明 |
|---|---|
| Controller | `edu.whut.eval.interfaces.student.StudentApplicationSubmissionController`、`edu.whut.eval.interfaces.student.StudentPreferenceController` |
| 路由前缀 | `/api/student/applications`、`/api/student/preferences` |
| 所在模块 | `whut-eval-interfaces` |
| 说明 | 当前分组同时包含正式申请写接口和 `P0-2` 最小写入参考接口 |

### 9.2 创建申请草稿

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `POST` |
| Path | `/api/student/applications/drafts` |
| Controller 方法 | `createDraft(...)` |
| 鉴权方式 | 依赖当前登录态；由 `UserAuthorizationContextAssembler#requiredAuthorizationContext()` 读取当前用户 |
| 请求体 DTO | `CreateApplicationDraftRequest` |
| Command | `CreateApplicationDraftCommand` |
| 应用服务 | `ApplicationSubmissionCommandApplicationService#createDraft(...)` |

请求体字段：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `orgUnitId` | `Long` | 是 | 申请所属组织单元 |
| `categoryCode` | `String` | 是 | 申请类别编码 |
| `itemCode` | `String` | 是 | 申请项目编码 |
| `academicYear` | `String` | 是 | 学年 |
| `term` | `String` | 是 | 学期 |
| `title` | `String` | 是 | 申请标题 |
| `description` | `String` | 是 | 申请说明 |
| `attachmentFileIds` | `List<String>` | 是 | 已上传附件对应的 `fileId` 集合；由 `/api/files/upload` 返回 |

成功响应：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `applicationId` | `Long` | 申请 ID |
| `status` | `String` | 当前申请状态 |
| `title` | `String` | 申请标题 |
| `description` | `String` | 申请说明 |
| `attachmentCount` | `int` | 附件数量 |
| `version` | `Long` | 乐观锁版本 |

响应类型：`ApiResponse<ApplicationSubmissionView>`

补充说明：

- `attachmentFileIds` 会通过 `ApplicationAttachmentResolver` 查询 `file_asset` / `public_attachment_entry`
- 当前支持绑定“本人上传的 ACTIVE 附件”和“公共池中 `PUBLISHED + ALL` 的附件”

### 9.3 更新申请草稿

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `PUT` |
| Path | `/api/student/applications/{applicationId}/draft` |
| Controller 方法 | `updateDraft(...)` |
| 鉴权方式 | 依赖当前登录态；由 `UserAuthorizationContextAssembler#requiredAuthorizationContext()` 读取当前用户 |
| 路径参数 | `applicationId` |
| 请求体 DTO | `UpdateApplicationDraftRequest` |
| Command | `UpdateApplicationDraftCommand` |
| 应用服务 | `ApplicationSubmissionCommandApplicationService#updateDraft(...)` |

请求体字段：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `title` | `String` | 是 | 申请标题 |
| `description` | `String` | 是 | 申请说明 |
| `attachmentFileIds` | `List<String>` | 是 | 更新后的附件 `fileId` 集合，会整体替换旧附件集合 |
| `expectedVersion` | `Long` | 是 | 乐观锁版本 |

成功响应：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `applicationId` | `Long` | 申请 ID |
| `status` | `String` | 当前申请状态 |
| `title` | `String` | 申请标题 |
| `description` | `String` | 申请说明 |
| `attachmentCount` | `int` | 附件数量 |
| `version` | `Long` | 乐观锁版本 |

响应类型：`ApiResponse<ApplicationSubmissionView>`

### 9.4 提交申请

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `POST` |
| Path | `/api/student/applications/{applicationId}/submit` |
| Controller 方法 | `submit(...)` |
| 鉴权方式 | 依赖当前登录态；由 `UserAuthorizationContextAssembler#requiredAuthorizationContext()` 读取当前用户 |
| 路径参数 | `applicationId` |
| 请求体 DTO | `SubmitApplicationRequest` |
| Command | `SubmitApplicationCommand` |
| 应用服务 | `ApplicationSubmissionCommandApplicationService#submit(...)` |

请求体字段：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `expectedVersion` | `Long` | 是 | 乐观锁版本 |

成功响应：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `applicationId` | `Long` | 申请 ID |
| `status` | `String` | 当前申请状态 |
| `title` | `String` | 申请标题 |
| `description` | `String` | 申请说明 |
| `attachmentCount` | `int` | 附件数量 |
| `version` | `Long` | 乐观锁版本 |

响应类型：`ApiResponse<ApplicationSubmissionView>`

### 9.5 撤回申请

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `POST` |
| Path | `/api/student/applications/{applicationId}/withdraw` |
| Controller 方法 | `withdraw(...)` |
| 鉴权方式 | 依赖当前登录态；由 `UserAuthorizationContextAssembler#requiredAuthorizationContext()` 读取当前用户 |
| 路径参数 | `applicationId` |
| 请求体 DTO | `WithdrawApplicationRequest` |
| Command | `WithdrawApplicationCommand` |
| 应用服务 | `ApplicationSubmissionCommandApplicationService#withdraw(...)` |

请求体字段：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `reason` | `String` | 是 | 撤回原因 |
| `expectedVersion` | `Long` | 是 | 乐观锁版本 |

成功响应：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `applicationId` | `Long` | 申请 ID |
| `status` | `String` | 当前申请状态 |
| `title` | `String` | 申请标题 |
| `description` | `String` | 申请说明 |
| `attachmentCount` | `int` | 附件数量 |
| `version` | `Long` | 乐观锁版本 |

响应类型：`ApiResponse<ApplicationSubmissionView>`

### 9.6 创建当前用户偏好设置

定位说明：

- 该接口不是最终业务需求的一部分，而是当前 `P0-2` 底座建设中的“最小写入参考实现”。
- 它用于验证写入侧最小闭环是否已经成立：
- `Command -> ApplicationService -> Domain Repository -> Infra Repository -> Mapper`
- `@Transactional` 事务边界
- 重复创建映射为 `409`
- 写后缓存失效调用点

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `POST` |
| Path | `/api/student/preferences` |
| Controller 方法 | `createPreference(...)` |
| 鉴权方式 | 依赖当前登录态；由 `UserAuthorizationContextAssembler#requiredAuthorizationContext()` 读取当前用户 |
| 请求体 DTO | `CreateUserPreferenceRequest` |
| Command | `CreateUserPreferenceCommand` |
| 应用服务 | `UserPreferenceCommandApplicationService#createCurrentUserPreference(...)` |

请求体字段：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `preferredTheme` | `String` | 是 | 偏好主题，例如 `dark` / `light` |
| `notificationsEnabled` | `Boolean` | 是 | 是否开启通知 |

成功响应：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `id` | `Long` | 偏好记录 ID |
| `userId` | `Long` | 当前用户 ID |
| `preferredTheme` | `String` | 偏好主题 |
| `notificationsEnabled` | `Boolean` | 是否开启通知 |

响应类型：`ApiResponse<UserPreferenceView>`

失败路径：

| 场景 | HTTP | 错误码 | 说明 |
|---|---:|---|---|
| 当前用户已存在偏好设置 | `409` | `BIZ-4090` | 应用服务抛出 `ConflictException`，由全局异常处理器统一映射 |

## 10. Admin Query 接口

### 10.1 分组信息

| 项 | 说明 |
|---|---|
| Controller | `edu.whut.eval.interfaces.admin.AdminQueryController` |
| 路由前缀 | `/api/admin` |
| 所在模块 | `whut-eval-interfaces` |

### 10.2 查询可见申请列表

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `GET` |
| Path | `/api/admin/query/applications` |
| Controller 方法 | `pageApplications(...)` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")` |
| 权限码 | `application.review` |
| 应用服务 | `ApplicationQueryApplicationService#pageAccessibleApplications(..., permissionCode)` |

查询参数：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `pageNo` | `long` | 否 | `1` | 页码 |
| `pageSize` | `long` | 否 | `20` | 每页数量 |
| `applicationId` | `Long` | 否 | - | 按申请 ID 过滤 |
| `applicantUserId` | `Long` | 否 | - | 按申请人用户 ID 过滤 |
| `orgUnitId` | `Long` | 否 | - | 按组织单元过滤 |
| `categoryCode` | `String` | 否 | - | 按类别编码过滤 |
| `itemCode` | `String` | 否 | - | 按项目编码过滤 |

成功响应：

| 项 | 说明 |
|---|---|
| 响应类型 | `ApiResponse<PageResult<ApplicationRecordView>>` |
| 列表元素 | `ApplicationRecordView` |

### 10.3 查询可见成绩列表

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `GET` |
| Path | `/api/admin/query/scores` |
| Controller 方法 | `pageScores(...)` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_ASSIGNED)")` |
| 权限码 | `score.view.assigned` |
| 应用服务 | `ScoreQueryApplicationService#pageAccessibleScores(..., permissionCode)` |

查询参数：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `pageNo` | `long` | 否 | `1` | 页码 |
| `pageSize` | `long` | 否 | `20` | 每页数量 |
| `scoreId` | `Long` | 否 | - | 按成绩记录 ID 过滤 |
| `studentUserId` | `Long` | 否 | - | 按学生用户 ID 过滤 |
| `orgUnitId` | `Long` | 否 | - | 按组织单元过滤 |
| `categoryCode` | `String` | 否 | - | 按类别编码过滤 |
| `itemCode` | `String` | 否 | - | 按项目编码过滤 |
| `academicYear` | `String` | 否 | - | 按学年过滤 |

成功响应：

| 项 | 说明 |
|---|---|
| 响应类型 | `ApiResponse<PageResult<ScoreRecordView>>` |
| 列表元素 | `ScoreRecordView` |

### 10.4 查询权限字典

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `GET` |
| Path | `/api/admin/permissions` |
| Controller 方法 | `listPermissions(...)` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).PERMISSION_MANAGE)")` |
| 权限码 | `permission.manage` |
| 应用服务 | `AdminDictionaryQueryApplicationService#listPermissions(...)` |

查询参数：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `keyword` | `String` | 否 | - | 按权限码或权限名称模糊过滤 |
| `module` | `String` | 否 | - | 按模块过滤 |
| `status` | `String` | 否 | `ACTIVE` | 按权限状态过滤 |

成功响应：

| 项 | 说明 |
|---|---|
| 响应类型 | `ApiResponse<List<PermissionDictionaryResponse>>` |
| 列表元素 | `PermissionDictionaryResponse` |

### 10.5 查询组织树

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `GET` |
| Path | `/api/admin/org-units/tree` |
| Controller 方法 | `listOrgUnitTree(...)` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ORG_MANAGE)")` |
| 权限码 | `org.manage` |
| 应用服务 | `AdminDictionaryQueryApplicationService#listOrgUnitTree(...)` |

查询参数：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `rootId` | `Long` | 否 | - | 指定树根组织单元 ID |
| `unitType` | `String` | 否 | - | 按组织类型过滤 |
| `includeDisabled` | `boolean` | 否 | `false` | 是否包含禁用节点 |

成功响应：

| 项 | 说明 |
|---|---|
| 响应类型 | `ApiResponse<List<OrgUnitTreeResponse>>` |
| 列表元素 | `OrgUnitTreeResponse` |

## 11. 当前边界与后续建议

当前接口面已经完成“按 student / admin 分组”的正式拆分，但还有几个实现边界需要明确：

1. 两侧当前仍复用同一套查询应用服务，但已通过显式传入 `permissionCode` 的方式拆开权限口径。
2. student 侧现在使用 `application.view.self` 与 `score.view.self`，默认语义是“查看自己的数据”。
3. admin 侧继续使用 `application.review` 与 `score.view.assigned`，默认语义仍是管理 / 审核视角的数据访问。
4. `FileUploadController` 现在会在上传成功后登记 `file_asset` 并返回 `fileId`；`ApplicationSubmission` 创建 / 更新链路已经改为消费 `attachmentFileIds`。
5. `AuthController` 与 `SecurityProbeController` 当前仍位于 `whut-eval-app`，后续如果希望接口职责更集中，可以考虑评估是否迁移到 `whut-eval-interfaces`。
6. `UserIdentityQueryController` 当前未显式声明 `@PreAuthorize`；如果后续要纳入正式权限模型，应先补齐权限码与接口文档，再调整实现。
7. `A-9 ~ A-18` 在交付文档中已经冻结了角色模板与角色分配契约，但当前代码仓尚未形成对应的 `/api/admin/roles*`、`/api/admin/role-assignments*` HTTP 入口，因此本文暂不把它们当作“已落地接口”列入总表。
8. 交付文档中的 `role.manage` 目前尚未在 `AuthorizationPermissionCodes` 中定义常量；在角色模板接口真正落地前，应先补齐该权限常量，再补安全注解与联调文档。

## 12. 响应模型附录

本节补充当前查询接口中被直接引用、但前文未展开字段清单的 3 个响应模型。

### 12.1 `ApplicationRecordView`

来源：`edu.whut.eval.application.application.query.ApplicationRecordView`

| 字段名 | 类型 | 说明 |
|---|---|---|
| `applicationId` | `Long` | 申请记录 ID |
| `applicantUserId` | `Long` | 申请人用户 ID |
| `orgUnitId` | `Long` | 归属组织单元 ID |
| `orgPath` | `String` | 组织路径字符串 |
| `categoryCode` | `String` | 综测类别编码 |
| `itemCode` | `String` | 综测项目编码 |

### 12.2 `ScoreRecordView`

来源：`edu.whut.eval.application.score.query.ScoreRecordView`

| 字段名 | 类型 | 说明 |
|---|---|---|
| `scoreId` | `Long` | 成绩记录 ID |
| `studentUserId` | `Long` | 学生用户 ID |
| `orgUnitId` | `Long` | 归属组织单元 ID |
| `orgPath` | `String` | 组织路径字符串 |
| `categoryCode` | `String` | 综测类别编码 |
| `itemCode` | `String` | 综测项目编码 |
| `academicYear` | `String` | 学年 |

### 12.3 `UserIdentityView`

来源：`edu.whut.eval.application.iam.query.UserIdentityView`

顶层结构：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `user` | `IamUser` | 用户主信息 |
| `assignments` | `List<IamRoleAssignment>` | 当前用户的有效角色分配列表 |
| `memberships` | `List<OrgMembership>` | 当前用户的组织归属列表 |

`IamUser` 字段：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `id` | `Long` | 用户 ID |
| `userNo` | `String` | 用户编号，学号或工号 |
| `userName` | `String` | 用户姓名 |
| `email` | `String` | 邮箱 |
| `phone` | `String` | 手机号 |
| `status` | `String` | 用户状态 |

`IamRoleAssignment` 字段：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `assignmentId` | `Long` | 角色分配记录 ID |
| `roleId` | `Long` | 角色 ID |
| `roleCode` | `String` | 角色编码 |
| `roleName` | `String` | 角色名称 |
| `orgUnitId` | `Long` | 角色分配关联的组织单元 ID |
| `status` | `String` | 分配状态 |

`OrgMembership` 字段：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `id` | `Long` | 组织归属记录 ID |
| `userId` | `Long` | 用户 ID |
| `orgUnitId` | `Long` | 组织单元 ID |
| `membershipType` | `String` | 归属类型，例如主归属、兼职归属 |
| `status` | `String` | 归属状态 |
