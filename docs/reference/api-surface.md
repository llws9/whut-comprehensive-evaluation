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
- 管理侧用户后台接口
- 管理侧角色模板接口

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
- [11. Admin User 接口](#11-admin-user-接口)
- [12. Admin Role 接口](#12-admin-role-接口)
- [13. 当前边界与后续建议](#13-当前边界与后续建议)
- [14. 响应模型附录](#14-响应模型附录)

## 2. 当前接口分组

当前项目中的 HTTP 入口分散在 `whut-eval-app` 和 `whut-eval-interfaces` 两个模块中，按职责可分为九组：

| 分组 | 路由前缀 | Controller | 所在模块 | 说明 |
|---|---|---|---|---|
| 认证接口 | `/api/auth/*` | `AuthController` | `whut-eval-app` | 负责登录、刷新令牌与当前会话登出 |
| 安全探针接口 | `/api/security/*` | `SecurityProbeController` | `whut-eval-app` | 用于查看当前请求上下文下的认证主体与授权信息 |
| IAM 身份查询接口 | `/api/iam/users/*` | `UserIdentityQueryController` | `whut-eval-interfaces` | 用于按用户编号查询身份、岗位与组织归属 |
| 文件上传接口 | `/api/files/*` | `FileUploadController` | `whut-eval-interfaces` | 对外提供最小文件上传入口，底层走 OSS 上传服务 |
| 学生侧查询接口 | `/api/student/query/*` | `StudentQueryController` | `whut-eval-interfaces` | 面向学生侧的授权查询接口分组 |
| 学生侧写接口 | `/api/student/applications/*`、`/api/student/preferences/*` | `StudentApplicationSubmissionController`、`StudentPreferenceController` | `whut-eval-interfaces` | 包含正式申请写接口和 `P0-2` 最小写入参考实现 |
| 管理侧查询接口 | `/api/admin/query/*` | `AdminQueryController` | `whut-eval-interfaces` | 面向教师 / 管理侧的授权查询接口分组 |
| 管理侧用户后台接口 | `/api/admin/users/*` | `UserAdminController` | `whut-eval-interfaces` | 面向管理员的用户列表、创建、状态修改与导入接口 |
| 管理侧角色模板接口 | `/api/admin/roles/*` | `RoleAdminController` | `whut-eval-interfaces` | 面向管理员的角色模板分页、创建、修改与权限绑定接口 |

补充说明：

- `student` 和 `admin` 当前已经完成接口面拆分。
- `FileUploadController` 是当前上传底座的最小 HTTP 入口，上传结果通过标准化 view 对外返回。
- 两侧当前都先承接既有“申请查询 / 成绩查询”能力。
- 管理侧后台接口进一步拆分为“查询总线”“用户后台”“角色模板后台”三组，便于按权限口径独立演进。
- 学生侧当前按“查看自己的数据”建模，管理侧继续沿用“审核 / 分配查询”权限口径。
- `StudentPreferenceController` 不是正式业务能力，而是 `P0-2` 阶段用于验证写入侧 Repo / Command / 事务 / 409 映射 / 缓存失效的最小参考实现。
- 认证与安全探针接口当前仍放在 `whut-eval-app` 模块中。

阅读顺序说明：

- 本文后续详细接口按 `Auth -> Security Probe -> IAM -> File Upload -> Student Query -> Student Write -> Admin Query -> Admin User -> Admin Role` 展开。
- 若只想快速定位接口归属，先看“完整接口总表”；若要落实现或联调，再下钻到对应章节。

## 3. 完整接口总表

| 分组 | HTTP Method | Path | Controller 方法 | 鉴权方式 | 主要用途 |
|---|---|---|---|---|---|
| 认证 | `POST` | `/api/auth/login` | `AuthController#login(...)` | 匿名可访问 | 用户登录、签发带 `sid` 的 Token 对，并创建当前 `ACTIVE iam_session` |
| 认证 | `POST` | `/api/auth/refresh` | `AuthController#refresh(...)` | 匿名可访问 | 使用 Refresh Token 换发同一 `sid` 的新 Token 对，并续期当前会话 |
| 认证 | `POST` | `/api/auth/logout` | `AuthController#logout()` | 依赖 Bearer Token | 仅撤销当前 `sid` 对应会话，使当前 Access/Refresh Token 立即失效 |
| 安全探针 | `GET` | `/api/security/me` | `SecurityProbeController#currentUser()` | 依赖 Bearer Token | 查看当前请求上下文中的用户、会话、角色、权限、范围 |
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
| Admin User | `GET` | `/api/admin/users` | `UserAdminController#pageUsers(...)` | `user.manage` | 分页查询用户列表，返回组织归属与角色编码摘要 |
| Admin User | `POST` | `/api/admin/users` | `UserAdminController#createUser(...)` | `user.manage` | 创建单个用户账号并返回最小用户快照 |
| Admin User | `PATCH` | `/api/admin/users/{userId}/status` | `UserAdminController#updateUserStatus(...)` | `user.manage` | 修改用户状态，处理 no-op / 冲突语义 |
| Admin User | `POST` | `/api/admin/users/import` | `UserAdminController#importUsers(...)` | `user.import` | 以 `multipart/form-data` 导入用户并返回摘要结果 |
| Admin Role | `GET` | `/api/admin/roles` | `RoleAdminController#pageRoles(...)` | `role.manage` | 分页查询角色模板列表 |
| Admin Role | `POST` | `/api/admin/roles` | `RoleAdminController#createRole(...)` | `role.manage` | 创建角色模板 |
| Admin Role | `PATCH` | `/api/admin/roles/{roleId}` | `RoleAdminController#updateRole(...)` | `role.manage` | 修改角色模板名称、范围与状态 |
| Admin Role | `POST` | `/api/admin/roles/{roleId}/permissions` | `RoleAdminController#replaceRolePermissions(...)` | `permission.manage` | 按整集合替换角色权限 |

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

补充说明：

- 登录成功后，服务端会生成唯一 `sessionId`，并以同一个 `sid` 签发 Access Token / Refresh Token。
- 登录链路会创建一条 `ACTIVE iam_session`，持久化 `sessionId/loginIp/userAgent/expiredAt`。
- 会话 `expiredAt` 与 Refresh Token 过期时间保持一致，后续 refresh 成功时会续期同一会话。

失败路径：

| 场景 | HTTP | 错误码 | 说明 |
|---|---:|---|---|
| 用户名或密码错误 | `401` | `AUTH-4010` | 凭证认证失败 |
| 用户状态不是 `ACTIVE` | `401` | `AUTH-4010` | 登录态认证失败，当前实现统一走认证失败口径 |

### 4.3 刷新令牌

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `POST` |
| Path | `/api/auth/refresh` |
| Controller 方法 | `refresh(...)` |
| 鉴权方式 | 匿名可访问 |
| 请求体 DTO | `RefreshTokenRequest` |
| 应用服务 | `RefreshTokenClaimsMapper#map(...)` + `IamSessionAccessService#assertActive(...)` + `RefreshTokenCurrentUserLoader#load(...)` + `JwtTokenIssuer#issueTokenPair(...)` |

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

补充说明：

- Refresh Token 必须携带 `sid`，并且该 `sid` 对应的 `iam_session` 必须存在、未撤销且未过期。
- refresh 成功后会沿用同一个 `sid` 重新签发 Token 对，不做 session 轮换。
- refresh 成功后会把当前会话的 `expiredAt` 续期到新 Refresh Token 的过期时间。

失败路径：

| 场景 | HTTP | 错误码 | 说明 |
|---|---:|---|---|
| token 签名错误、格式非法、issuer/audience 不匹配 | `401` | `AUTH-4012` | JWT 校验失败 |
| token 已过期 | `401` | `AUTH-4012` | 当前实现将 JWT 过期统一归入 token 非法 |
| `token_type` 不是 `refresh` | `401` | `AUTH-4012` | Access Token 不能调用 refresh |
| token 缺少 `sid` 或其他必填 claims | `401` | `AUTH-4012` | 旧 token 或不完整 token 被拒绝 |
| 会话不存在、已撤销或已过期 | `401` | `AUTH-4012` | 会话无效统一走 `TOKEN_INVALID` |
| token 主体与数据库用户不一致，或用户状态不是 `ACTIVE` | `401` | `AUTH-4010` | token 通过基础校验后，查库阶段认证失败 |

补充说明：

- `AUTH-4011` 虽然仍定义在 `CommonErrorCode` 中，但当前 `refresh` 链路未直接返回该错误码。

### 4.4 当前会话登出

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `POST` |
| Path | `/api/auth/logout` |
| Controller 方法 | `logout()` |
| 鉴权方式 | 依赖 Bearer Token |
| 应用服务 | `CurrentUserProvider#requiredCurrentUser()` + `LogoutSessionCommandService#logout(...)` |

成功响应：

| 项 | 说明 |
|---|---|
| 响应类型 | `ApiResponse<Void>` |
| 返回体 | `ApiResponse.success(null)` |

失败路径：

| 场景 | HTTP | 错误码 | 说明 |
|---|---:|---|---|
| 未登录或未携带 Bearer Token | `401` | `AUTH-4010` | 当前请求没有可用认证主体 |
| token 非法、缺少 `sid` | `401` | `AUTH-4012` | JWT / claims 校验失败 |
| 会话不存在、已撤销或已过期 | `401` | `AUTH-4012` | 会话无效统一走 `TOKEN_INVALID` |

补充说明：

- `logout` 只撤销当前 `sid` 对应的一条会话，不影响同一用户其他会话。
- 撤销成功后，会写入 `revokedAt`；同一会话下后续 Access Token 与 Refresh Token 都会立即失效。

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
| `sessionId` | `String` | 当前会话 ID，对应 JWT `sid` 与 `iam_session.token_id` |
| `scopeRules` | `List<Object>` | 数据范围规则集合 |

响应类型：`ApiResponse<Map<String, Object>>`

失败路径：

| 场景 | HTTP | 错误码 | 说明 |
|---|---:|---|---|
| 未登录或未携带 Bearer Token | `401` | `AUTH-4010` | 当前请求没有可用认证主体 |
| token 非法、缺少 `sid` | `401` | `AUTH-4012` | JWT / claims 校验失败 |
| 会话不存在、已撤销或已过期 | `401` | `AUTH-4012` | 会话无效统一走 `TOKEN_INVALID` |

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
| 路由前缀 | `/api/admin/query` |
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

## 11. Admin User 接口

### 11.1 分组信息

| 项 | 说明 |
|---|---|
| Controller | `edu.whut.eval.interfaces.iam.UserAdminController` |
| 路由前缀 | `/api/admin/users` |
| 所在模块 | `whut-eval-interfaces` |

### 11.2 分页查询用户

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `GET` |
| Path | `/api/admin/users` |
| Controller 方法 | `pageUsers(...)` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")` |
| 权限码 | `user.manage` |
| 应用服务 | `UserAdminQueryApplicationService#pageUsers(...)` |

查询参数：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `pageNo` | `long` | 否 | `1` | 页码 |
| `pageSize` | `long` | 否 | `20` | 每页数量 |
| `keyword` | `String` | 否 | - | 按用户编号或姓名模糊搜索 |
| `status` | `String` | 否 | - | 用户状态过滤 |
| `orgUnitId` | `Long` | 否 | - | 按组织单元过滤 |

成功响应：

| 项 | 说明 |
|---|---|
| 响应类型 | `ApiResponse<PageResult<UserAdminPageItemResponse>>` |
| 列表元素 | `UserAdminPageItemResponse` |

### 11.3 创建用户

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `POST` |
| Path | `/api/admin/users` |
| Controller 方法 | `createUser(...)` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")` |
| 权限码 | `user.manage` |
| 请求体 DTO | `CreateUserRequest` |
| Command | `CreateUserCommand` |
| 应用服务 | `UserAdminCommandApplicationService#createUser(...)` |

请求体字段：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userNo` | `String` | 是 | 用户编号 |
| `userName` | `String` | 是 | 用户姓名 |
| `password` | `String` | 是 | 初始密码 |
| `email` | `String` | 否 | 邮箱 |
| `phone` | `String` | 否 | 手机号 |
| `primaryOrgUnitId` | `Long` | 否 | 主组织单元 ID；传入时需为正数 |

成功响应：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `userId` | `Long` | 用户 ID |
| `userNo` | `String` | 用户编号 |
| `userName` | `String` | 用户姓名 |
| `status` | `String` | 用户状态 |

响应类型：`ApiResponse<UserAdminResponse>`

### 11.4 修改用户状态

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `PATCH` |
| Path | `/api/admin/users/{userId}/status` |
| Controller 方法 | `updateUserStatus(...)` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")` |
| 权限码 | `user.manage` |
| 路径参数 | `userId` |
| 请求体 DTO | `UpdateUserStatusRequest` |
| Command | `UpdateUserStatusCommand` |
| 应用服务 | `UserAdminCommandApplicationService#updateUserStatus(...)` |

请求体字段：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `status` | `String` | 是 | 目标状态 |
| `reason` | `String` | 否 | 变更原因 |

响应类型：`ApiResponse<Void>`

### 11.5 批量导入用户

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `POST` |
| Path | `/api/admin/users/import` |
| Controller 方法 | `importUsers(...)` |
| Content-Type | `multipart/form-data` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_IMPORT)")` |
| 权限码 | `user.import` |
| 应用服务 | `UserAdminCommandApplicationService#importUsers(...)` |

请求参数：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `file` | `MultipartFile` | 是 | 导入文件 |
| `importMode` | `String` | 否 | `UPSERT` | 导入模式，当前实现支持 `UPSERT` 与 `INSERT_ONLY` |

成功响应：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `totalCount` | `long` | 总处理行数 |
| `successCount` | `long` | 成功行数 |
| `failedCount` | `long` | 失败行数 |
| `failedRows` | `List<UserImportFailedRowResponse>` | 失败行摘要 |

响应类型：`ApiResponse<UserImportResponse>`

失败路径：

| 场景 | HTTP | 错误码 | 说明 |
|---|---:|---|---|
| 文件为空 | `400` | `VAL-4001` | 未上传文件或文件大小为 0 |
| `importMode` 非法 | `400` | `VAL-4001` | 仅允许 `UPSERT/INSERT_ONLY` |
| 文件读取或解析失败 | `503` | `EXT-5033` | 导入文件 IO 或解析异常 |

## 12. Admin Role 接口

### 12.1 分组信息

| 项 | 说明 |
|---|---|
| Controller | `edu.whut.eval.interfaces.iam.RoleAdminController` |
| 路由前缀 | `/api/admin/roles` |
| 所在模块 | `whut-eval-interfaces` |

### 12.2 分页查询角色模板

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `GET` |
| Path | `/api/admin/roles` |
| Controller 方法 | `pageRoles(...)` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)")` |
| 权限码 | `role.manage` |
| 应用服务 | `RoleAdminQueryApplicationService#pageRoles(...)` |

查询参数：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `pageNo` | `long` | 否 | `1` | 页码 |
| `pageSize` | `long` | 否 | `20` | 每页数量 |
| `keyword` | `String` | 否 | - | 按角色编码或名称模糊搜索 |
| `status` | `String` | 否 | - | 角色状态过滤 |

成功响应：

| 项 | 说明 |
|---|---|
| 响应类型 | `ApiResponse<PageResult<RoleAdminPageItemResponse>>` |
| 列表元素 | `RoleAdminPageItemResponse` |

### 12.3 创建角色模板

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `POST` |
| Path | `/api/admin/roles` |
| Controller 方法 | `createRole(...)` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)")` |
| 权限码 | `role.manage` |
| 请求体 DTO | `CreateRoleRequest` |
| Command | `CreateRoleCommand` |
| 应用服务 | `RoleAdminCommandApplicationService#createRole(...)` |

请求体字段：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `roleCode` | `String` | 是 | 角色编码 |
| `roleName` | `String` | 是 | 角色名称 |
| `roleScope` | `String` | 是 | 角色范围 |
| `status` | `String` | 是 | 角色状态 |

成功响应：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `roleId` | `Long` | 角色 ID |
| `roleCode` | `String` | 角色编码 |
| `roleName` | `String` | 角色名称 |
| `roleScope` | `String` | 角色范围 |
| `status` | `String` | 角色状态 |

响应类型：`ApiResponse<RoleAdminResponse>`

### 12.4 修改角色模板

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `PATCH` |
| Path | `/api/admin/roles/{roleId}` |
| Controller 方法 | `updateRole(...)` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)")` |
| 权限码 | `role.manage` |
| 路径参数 | `roleId` |
| 请求体 DTO | `UpdateRoleRequest` |
| Command | `UpdateRoleCommand` |
| 应用服务 | `RoleAdminCommandApplicationService#updateRole(...)` |

请求体字段：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `roleName` | `String` | 是 | 角色名称 |
| `roleScope` | `String` | 是 | 角色范围 |
| `status` | `String` | 是 | 角色状态 |

响应类型：`ApiResponse<Void>`

### 12.5 整集合替换角色权限

基本信息：

| 项 | 说明 |
|---|---|
| HTTP Method | `POST` |
| Path | `/api/admin/roles/{roleId}/permissions` |
| Controller 方法 | `replaceRolePermissions(...)` |
| 权限注解 | `@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).PERMISSION_MANAGE)")` |
| 权限码 | `permission.manage` |
| 路径参数 | `roleId` |
| 请求体 DTO | `ReplaceRolePermissionsRequest` |
| Command | `ReplaceRolePermissionsCommand` |
| 应用服务 | `RoleAdminCommandApplicationService#replaceRolePermissions(...)` |

请求体字段：

| 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `permissionCodes` | `List<String>` | 是 | 需要整集合绑定的权限码列表；不能为空且元素不能为空白 |
| `replaceAll` | `Boolean` | 否 | 是否整集合替换；未传时默认 `true` |

响应类型：`ApiResponse<Void>`

## 13. 当前边界与后续建议

当前接口面已经完成“按 student / admin 分组”的正式拆分，但还有几个实现边界需要明确：

1. 两侧当前仍复用同一套查询应用服务，但已通过显式传入 `permissionCode` 的方式拆开权限口径。
2. student 侧现在使用 `application.view.self` 与 `score.view.self`，默认语义是“查看自己的数据”。
3. admin 侧继续使用 `application.review` 与 `score.view.assigned`，默认语义仍是管理 / 审核视角的数据访问。
4. `FileUploadController` 现在会在上传成功后登记 `file_asset` 并返回 `fileId`；`ApplicationSubmission` 创建 / 更新链路已经改为消费 `attachmentFileIds`。
5. `AuthController` 与 `SecurityProbeController` 当前仍位于 `whut-eval-app`，后续如果希望接口职责更集中，可以考虑评估是否迁移到 `whut-eval-interfaces`。
6. `UserIdentityQueryController` 当前未显式声明 `@PreAuthorize`；如果后续要纳入正式权限模型，应先补齐权限码与接口文档，再调整实现。

## 14. 响应模型附录

本节补充当前查询接口中被直接引用、但前文未展开字段清单的 3 个响应模型。

### 14.1 `ApplicationRecordView`

来源：`edu.whut.eval.application.application.query.ApplicationRecordView`

| 字段名 | 类型 | 说明 |
|---|---|---|
| `applicationId` | `Long` | 申请记录 ID |
| `applicantUserId` | `Long` | 申请人用户 ID |
| `orgUnitId` | `Long` | 归属组织单元 ID |
| `orgPath` | `String` | 组织路径字符串 |
| `categoryCode` | `String` | 综测类别编码 |
| `itemCode` | `String` | 综测项目编码 |

### 14.2 `ScoreRecordView`

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

### 14.3 `UserIdentityView`

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
