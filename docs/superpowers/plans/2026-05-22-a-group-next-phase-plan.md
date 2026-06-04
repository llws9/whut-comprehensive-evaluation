# A组下一阶段收口与联调 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 A 组剩余缺口收口，先对齐文档，再完成后端语义精修，最后把前端从 mock 壳层接到真实后端接口。

**Architecture:** 按三条线并行规划、串行落地：先做文档一致性，冻结对外契约；再做后端最小语义精修，避免前端接入时反复返工；最后按“认证 -> 用户/角色 -> 分配/组织”顺序完成前端联调。所有任务都以当前已推送分支 `feat/a-group-phase3-user-role-admin` 为基线，不再回退到主线漂移状态。

**Tech Stack:** Spring Boot, MyBatis-Plus, JWT, Vue 3, Pinia, Vue Router, TypeScript, Maven

---

## Scope

- 文档线：统一 `docs/team-delivery` 与 `docs/reference` 的 A 组口径。
- 后端精修线：只处理 A 组现有实现与文档之间的语义差异，不新增下一阶段功能。
- 前端联调线：把管理端现有 mock 页面接入真实后端接口，优先打通认证与用户/角色管理，再补角色分配与组织相关能力。

## File Map

**文档线**
- Modify: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/docs/team-delivery/group-a-identity-user-admin.md`
- Modify: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/docs/reference/api-surface.md`
- Modify: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/docs/reference/auth-login-permission-scope-flow.md`

**后端精修线**
- Modify: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/whut-eval-app/src/main/java/edu/whut/eval/app/security/AuthController.java`
- Modify: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/whut-eval-infra/src/main/java/edu/whut/eval/infra/security/web/RestAuthenticationEntryPoint.java`
- Modify: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/whut-eval-infra/src/main/java/edu/whut/eval/infra/security/jwt/JwtClaimsParser.java`
- Test: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/whut-eval-app/src/test/java/edu/whut/eval/app/security/AuthControllerWebMvcTest.java`
- Test: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/whut-eval-app/src/test/java/edu/whut/eval/app/security/SecurityProbeControllerWebMvcTest.java`

**前端联调线**
- Modify: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/stores/auth.ts`
- Modify: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/views/auth/AdminLoginView.vue`
- Modify: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/layouts/AdminLayout.vue`
- Modify: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/views/students/StudentsView.vue`
- Modify: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/views/permissions/PermissionsView.vue`
- Create: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/api/auth.ts`
- Create: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/api/users.ts`
- Create: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/api/roles.ts`
- Create: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/api/roleAssignments.ts`
- Create: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/api/org.ts`

### Task 1: 冻结 A 组文档口径

**Files:**
- Modify: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/docs/team-delivery/group-a-identity-user-admin.md`
- Modify: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/docs/reference/api-surface.md`
- Modify: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/docs/reference/auth-login-permission-scope-flow.md`

- [ ] **Step 1: 对照当前 feature branch 做差异清单**

Run:

```bash
cd /Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin
git grep -n "A-10\\|A-11\\|A-19\\|AUTH-4011\\|roleType\\|description" docs/team-delivery docs/reference
```

Expected:
- 输出所有可能的旧口径位置，重点定位 `roleType/description` 与 `A-19` 老描述。

- [ ] **Step 2: 修改 A 组总交付文档**

Edit:
- 将 `A-10/A-11` 统一改成当前实际口径：`roleScope/status`
- 将 `A-19` 改成 `iam_session + sid + 当前会话撤销`
- 将错误码描述改成当前真实行为：会话无效统一归 `AUTH-4012`

- [ ] **Step 3: 核对参考文档与交付文档一致**

Run:

```bash
cd /Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin
git diff -- docs/team-delivery/group-a-identity-user-admin.md docs/reference/api-surface.md docs/reference/auth-login-permission-scope-flow.md
```

Expected:
- 三份文档描述一致，不再出现同一接口多种口径。

- [ ] **Step 4: 诊断并提交文档收口**

Run:

```bash
git add docs/team-delivery/group-a-identity-user-admin.md docs/reference/api-surface.md docs/reference/auth-login-permission-scope-flow.md
git commit -m "docs(iam): align a-group delivery documents"
```

Expected:
- 文档口径固定，为后端精修和前端联调提供稳定契约。

### Task 2: 完成后端语义精修

**Files:**
- Modify: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/whut-eval-app/src/main/java/edu/whut/eval/app/security/AuthController.java`
- Modify: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/whut-eval-infra/src/main/java/edu/whut/eval/infra/security/web/RestAuthenticationEntryPoint.java`
- Modify: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/whut-eval-infra/src/main/java/edu/whut/eval/infra/security/jwt/JwtClaimsParser.java`
- Test: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/whut-eval-app/src/test/java/edu/whut/eval/app/security/AuthControllerWebMvcTest.java`
- Test: `/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin/whut-eval-app/src/test/java/edu/whut/eval/app/security/SecurityProbeControllerWebMvcTest.java`

- [ ] **Step 1: 先写失败测试锁定错误码策略**

Add tests for:
- JWT 过期是否返回 `AUTH-4011`
- JWT 非法 / 缺少 `sid` / 会话失效是否返回 `AUTH-4012`

Run:

```bash
cd /Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin
mvn -pl whut-eval-app -am -Dtest=AuthControllerWebMvcTest,SecurityProbeControllerWebMvcTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected:
- 至少 1 个测试失败，且失败原因是当前错误码映射与目标策略不一致。

- [ ] **Step 2: 做最小实现修正**

Change:
- 若你决定让 `AUTH-4011` 真正用于 token 过期，则只在 parser / entry point 上拆分过期异常
- 若继续保持 `AUTH-4012` 统一口径，则删除文档中的 `AUTH-4011` 命中承诺，不再改代码

- [ ] **Step 3: 运行 A 组认证回归**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=AuthControllerWebMvcTest,SecurityProbeControllerWebMvcTest,DefaultIamSessionAccessServiceTest,DefaultLoginSessionCommandServiceTest,DefaultLogoutSessionCommandServiceTest,JwtTokenIssuerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected:
- 所有认证与会话相关测试通过。

- [ ] **Step 4: 编译并提交**

Run:

```bash
mvn -q -DskipTests compile
git add whut-eval-app/src/main/java/edu/whut/eval/app/security/AuthController.java whut-eval-infra/src/main/java/edu/whut/eval/infra/security/web/RestAuthenticationEntryPoint.java whut-eval-infra/src/main/java/edu/whut/eval/infra/security/jwt/JwtClaimsParser.java whut-eval-app/src/test/java/edu/whut/eval/app/security/AuthControllerWebMvcTest.java whut-eval-app/src/test/java/edu/whut/eval/app/security/SecurityProbeControllerWebMvcTest.java
git commit -m "fix(auth): align session error semantics"
```

Expected:
- 后端契约与文档语义一致。

### Task 3: 前端接入认证真实链路

**Files:**
- Create: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/api/auth.ts`
- Modify: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/stores/auth.ts`
- Modify: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/views/auth/AdminLoginView.vue`
- Modify: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/layouts/AdminLayout.vue`

- [ ] **Step 1: 先写 store / view 层失败测试或最小联调断言**

If test infra exists:
- 写登录成功后持久化 token、失败时显示错误、logout 调服务端接口的测试

If test infra absent:
- 先在本地用最小 API wrapper 替换 mock，保留页面结构不变

- [ ] **Step 2: 实现真实认证 API**

Implement:
- `login() -> POST /api/auth/login`
- `refresh() -> POST /api/auth/refresh`
- `logout() -> POST /api/auth/logout`
- `getCurrentUser() -> GET /api/security/me`

- [ ] **Step 3: 替换本地 mock session**

Change:
- `auth.ts` 不再调用 `loginWithMockSession()`
- 登录成功后保存真实 token pair 与当前用户上下文
- logout 时先调后端，再清本地状态

- [ ] **Step 4: 手工验证认证主链路**

Run dev server and verify:
- 登录成功进入后台
- 刷新页面后仍保持登录
- logout 后返回登录页
- 被撤销会话再次访问受保护页会被拦截

- [ ] **Step 5: 提交**

Run:

```bash
git add apps/manage/src/api/auth.ts apps/manage/src/stores/auth.ts apps/manage/src/views/auth/AdminLoginView.vue apps/manage/src/layouts/AdminLayout.vue
git commit -m "feat(frontend): connect auth flows to backend"
```

Expected:
- 管理端认证不再依赖 mock。

### Task 4: 前端接入用户与角色模板管理

**Files:**
- Create: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/api/users.ts`
- Create: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/api/roles.ts`
- Modify: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/views/students/StudentsView.vue`
- Modify: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/views/permissions/PermissionsView.vue`

- [ ] **Step 1: 定义最小 API 模块**

Implement:
- 用户分页、创建、状态修改、导入
- 角色分页、创建、修改、权限整集合替换

- [ ] **Step 2: 替换页面内本地数组**

Change:
- `StudentsView.vue` 改成调用真实用户 API
- `PermissionsView.vue` 改成调用真实角色与权限 API

- [ ] **Step 3: 联调关键场景**

Verify:
- 用户搜索、分页、状态切换
- 用户导入结果摘要
- 角色创建/编辑
- 角色权限整集合替换

- [ ] **Step 4: 提交**

Run:

```bash
git add apps/manage/src/api/users.ts apps/manage/src/api/roles.ts apps/manage/src/views/students/StudentsView.vue apps/manage/src/views/permissions/PermissionsView.vue
git commit -m "feat(frontend): connect user and role admin pages"
```

Expected:
- A-5~A-12 从“页面壳层”升级为真实业务页面。

### Task 5: 前端补角色分配、组织树、成员归属

**Files:**
- Create: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/api/roleAssignments.ts`
- Create: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/api/org.ts`
- Create or Modify: role assignment / organization / membership views under `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/views`
- Modify: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/apps/manage/src/app/router/routes.ts`
- Modify: `/Users/bytedance/whut/whut-comprehensive-evaluation-frontend/packages/shared/src/router/menu.ts`

- [ ] **Step 1: 增加页面与路由骨架**

Create views for:
- 角色分配列表与编辑
- 组织树选择
- 用户成员归属整集合编辑

- [ ] **Step 2: 接入后端接口**

Implement:
- `A-13 ~ A-18`
- `A-21 ~ A-23`

- [ ] **Step 3: 联调范围与归属逻辑**

Verify:
- 角色分配创建/修改/撤销
- 范围规则查询/新增
- 组织树加载
- 成员归属整集合更新

- [ ] **Step 4: 提交**

Run:

```bash
git add apps/manage/src/api/roleAssignments.ts apps/manage/src/api/org.ts apps/manage/src/app/router/routes.ts packages/shared/src/router/menu.ts apps/manage/src/views
git commit -m "feat(frontend): add role assignment and organization admin pages"
```

Expected:
- A-13~A-18、A-21~A-23 前端链路闭合。

### Task 6: 交付级总验收

**Files:**
- Modify: 文档与前后端少量收口文件，按实际情况确定

- [ ] **Step 1: 跑后端回归**

Run:

```bash
cd /Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-phase3-user-role-admin
mvn -q -DskipTests compile
```

- [ ] **Step 2: 跑前端构建**

Run:

```bash
cd /Users/bytedance/whut/whut-comprehensive-evaluation-frontend
pnpm install
pnpm -C apps/manage build
```

- [ ] **Step 3: 按 A-1~A-23 做最终验收**

Verify:
- 认证：登录、refresh、当前用户、logout
- 用户与角色：A-5~A-12
- 角色分配与范围：A-13~A-18
- 基础字典与组织：A-20~A-23

- [ ] **Step 4: 提交最终收口**

Run:

```bash
git add .
git commit -m "chore(a-group): finish delivery alignment and integration"
```

Expected:
- A 组进入可合并、可联调、可验收状态。

## Recommended Order

1. 文档线：Task 1
2. 后端精修线：Task 2
3. 前端联调线第一阶段：Task 3
4. 前端联调线第二阶段：Task 4
5. 前端联调线第三阶段：Task 5
6. 总验收：Task 6

## Why This Order

- 先冻结文档口径，避免前端和后端各接一套不同契约。
- 后端精修在前，避免前端联调时再返工认证错误语义。
- 前端先打通认证，再接用户/角色页面，再补复杂的角色分配与组织能力，能持续产生可见进展。
