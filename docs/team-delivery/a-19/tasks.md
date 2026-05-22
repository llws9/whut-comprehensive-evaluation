# A-19 Tasks

- [x] Task 1: 落地 `iam_session` 基础骨架，对齐 DDL、领域模型、仓储契约与持久化实现。
- [x] Task 2: 为 access/refresh token 注入 `sid`，让 JWT claims / runtime identity 可读取 `sessionId`，并让旧 token 缺失 `sid` 时返回 `AUTH-4012`。
