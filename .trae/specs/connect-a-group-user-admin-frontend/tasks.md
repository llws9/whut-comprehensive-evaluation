# Tasks
- [ ] Task 1: 建立用户管理前端 API 模块
  - [ ] SubTask 1.1: 新增 `apps/manage/src/api/users.ts`，封装 `listUsers`
  - [ ] SubTask 1.2: 在同一模块封装 `createUser`、`updateUserStatus`、`importUsers`
  - [ ] SubTask 1.3: 为用户 API 补最小失败测试，覆盖查询参数、状态更新路径和导入 `FormData`

- [ ] Task 2: 将用户管理页切到真实列表查询
  - [ ] SubTask 2.1: 移除 `StudentsView.vue` 的本地 mock 数组与 `grade/role` 假筛选
  - [ ] SubTask 2.2: 将筛选条件收敛为 `keyword + status + pageNo + pageSize`
  - [ ] SubTask 2.3: 页面初始化与查询动作统一调用 `GET /api/admin/users`
  - [ ] SubTask 2.4: 展示真实返回的 `userNo/userName/status/roleCodes/orgUnits/createdAt`

- [ ] Task 3: 接入用户状态修改
  - [ ] SubTask 3.1: 将状态按钮改为调用 `PATCH /api/admin/users/{userId}/status`
  - [ ] SubTask 3.2: 成功后重新拉取列表，失败时展示错误信息
  - [ ] SubTask 3.3: 为状态修改补最小回归测试

- [ ] Task 4: 接入创建用户最小闭环
  - [ ] SubTask 4.1: 在用户页新增最小创建表单入口
  - [ ] SubTask 4.2: 调用 `POST /api/admin/users` 创建用户
  - [ ] SubTask 4.3: 成功后关闭表单并刷新列表，失败时展示错误信息

- [ ] Task 5: 接入导入用户最小闭环
  - [ ] SubTask 5.1: 新增文件选择与 `importMode` 入口
  - [ ] SubTask 5.2: 调用 `POST /api/admin/users/import`
  - [ ] SubTask 5.3: 展示导入统计结果与失败行摘要
  - [ ] SubTask 5.4: 导入成功后重新拉取列表

- [ ] Task 6: 完成联调验证与收口
  - [ ] SubTask 6.1: 运行前端定向测试
  - [ ] SubTask 6.2: 运行 `typecheck`
  - [ ] SubTask 6.3: 运行 `build`
  - [ ] SubTask 6.4: 核对页面范围未扩散到角色模板管理页

# Task Dependencies
- `Task 2` depends on `Task 1`
- `Task 3` depends on `Task 1` and `Task 2`
- `Task 4` depends on `Task 1` and `Task 2`
- `Task 5` depends on `Task 1` and `Task 2`
- `Task 6` depends on `Task 3`、`Task 4`、`Task 5`
