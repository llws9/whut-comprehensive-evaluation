# A-10 / A-11 代码审查问题记录（2026-06-03）

> 范围：本次分支 `main...HEAD` 及工作区相关变更（角色写接口 + 用户导入/分页链路）。

## 问题清单

### 1) A-11 快照并发校验非原子，存在丢失更新
- **文件**：`whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/DefaultRoleAdminApplicationService.java`
- **行号**：54, 57-60, 73-78
- **问题**：当前采用“先查再比快照再更新”的两步流程，更新 SQL 未携带快照条件，两个并发请求可能都通过校验并先后覆盖。
- **失败场景**：两个管理员带同一 snapshot 同时 PATCH 同一 role，后提交者覆盖先提交者，且不会返回 409。

### 2) A-11 更新未检查受影响行数，可能“假成功”
- **文件**：
  - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/DefaultRoleAdminApplicationService.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusRoleAdminCommandRepository.java`
- **行号**：服务层 73-78；仓储层 55
- **问题**：`updateById` 命中 0 行不会抛错，服务层也未校验，接口可能返回 200 但实际未更新。
- **失败场景**：先 `findById` 查到记录后，该记录被并发删除；当前请求 update 命中 0 行，最终仍返回成功。

### 3) INSERT_ONLY 未识别同文件内重复 userNo（后行覆盖前行）
- **文件**：`whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
- **行号**：154-160, 186-191
- **问题**：INSERT_ONLY 仅在导入前检查“数据库是否已存在”，未检查上传文件内部重复；逐行处理仍会走 UPSERT 分支。
- **失败场景**：同一文件两行 userNo 相同且库中原本不存在，第一行 create，第二行走 update 覆盖第一行数据。

### 4) UPSERT 更新分支忽略 update 返回值，成功统计不准确
- **文件**：`whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
- **行号**：190, 192
- **问题**：`updateForImportByUserNo` 返回值未被使用，`successCount` 无条件加一。
- **失败场景**：并发删除导致 update 命中 0 行，结果仍计为成功，导入统计与实际落库不一致。

### 5) INSERT_ONLY 并发窗口语义破坏（预检查后被并发插入）
- **文件**：`whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`
- **行号**：154-160, 186-191
- **问题**：预检查与写入之间存在时间窗；若他事务在窗口内插入同 userNo，本批会走 update 而非冲突。
- **失败场景**：导入开始时 userNo 不存在，预检查通过；处理中该行前其他事务插入同 userNo，当前行转为 update 并返回成功，未按 INSERT_ONLY 返回 409。

---

## 备注
- 上述问题以“可复现失败场景”为准，优先级建议：
  1. 并发一致性（问题 1、2）
  2. INSERT_ONLY 语义正确性（问题 3、5）
  3. 导入统计准确性（问题 4）

## 修复同步（2026-06-04）

### 总体状态
- 问题 1~5：**均已修复**

### 修复点与代码落位

1. **A-11 快照并发校验非原子（已修复）**
   - 修复方式：角色更新改为仓储层**原子条件更新**（`id + snapshot`）
   - 主要文件：
     - `whut-eval-domain/src/main/java/edu/whut/eval/domain/iam/repository/RoleAdminCommandRepository.java`
     - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusRoleAdminCommandRepository.java`
     - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/DefaultRoleAdminApplicationService.java`

2. **A-11 更新未检查命中行数（已修复）**
   - 修复方式：应用层检查原子更新返回值，未命中统一按冲突返回（避免“假成功”）
   - 主要文件：
     - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/DefaultRoleAdminApplicationService.java`

3. **INSERT_ONLY 未识别同文件重复 userNo（已修复）**
   - 修复方式：导入前新增同文件 `userNo` 去重校验，重复即冲突
   - 主要文件：
     - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`

4. **UPSERT 忽略 update 返回值导致统计不准（已修复）**
   - 修复方式：`updateForImportByUserNo == false` 时转失败行，不计入 success
   - 主要文件：
     - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`

5. **INSERT_ONLY 并发窗口语义破坏（已修复）**
   - 修复方式：逐行处理阶段若 INSERT_ONLY 且发现已存在（含窗口并发插入）直接冲突，不再走 update
   - 主要文件：
     - `whut-eval-application/src/main/java/edu/whut/eval/application/iam/service/UserAdminApplicationService.java`

### 测试同步

- 新增/更新测试：
  - `whut-eval-app/src/test/java/edu/whut/eval/app/iam/RoleAdminApplicationServiceTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/iam/UserAdminApplicationServiceTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/infra/MybatisPlusRoleAdminCommandRepositoryTest.java`

- 回归命令：
  - `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=RoleAdminApplicationServiceTest,UserAdminApplicationServiceTest,MybatisPlusRoleAdminCommandRepositoryTest test`

- 回归结果：
  - `BUILD SUCCESS`
  - `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`
