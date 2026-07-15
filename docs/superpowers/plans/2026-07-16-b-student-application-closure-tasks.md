# Tasks

- [x] T001: B-6 delete draft or returned application
  - [x] Add RED tests for domain/service/controller/repository delete behavior.
  - [x] Implement logical deletion for owned `DRAFT` and `RETURNED` submissions.
  - [x] Verify deleted submissions are excluded from normal detail/query paths.
  - [x] Record main-agent RED/GREEN/focused regression evidence in SDD state.

- [x] T002: B-1 student application overview
  - [x] Add RED tests for current-user scoped status counts and empty overview.
  - [x] Implement overview query service/repository and `GET /api/student/applications/overview`.
  - [x] Verify deleted submissions are excluded and `latestAcademicYear` is derived from visible own rows.
  - [x] Record main-agent RED/GREEN/focused regression evidence in SDD state.

- [x] T003: B-10 and B-11 student evaluation routes
  - [x] Add RED tests for `GET /api/student/evaluation/items` and `POST /api/student/evaluation/calculate-points`.
  - [x] Implement student-facing adapters over existing evaluation config/rule capabilities.
  - [x] Verify current-user context is used instead of trusting caller-provided student identity.
  - [x] Record main-agent RED/GREEN/focused regression evidence in SDD state.

- [x] T004: B-9 lecture candidate query
  - [x] Add RED tests for `GET /api/student/lectures` pagination, required academic year, keyword, and empty-page behavior.
  - [x] Implement lecture candidate query against the current persisted source available in this rewrite.
  - [x] Document any source-data limitation in the B delivery doc.
  - [x] Record main-agent RED/GREEN/focused regression evidence in SDD state.

# Task Dependencies

- `T002` depends on `T001`
- `T003` depends on `T001`
- `T004` depends on `T001`, `T002`
