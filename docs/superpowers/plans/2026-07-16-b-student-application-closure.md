# B Student Application Closure SDD Plan

> For this run, execute tasks sequentially with TDD evidence in the SDD state file. Do not broaden scope beyond B-6, B-1, B-10/B-11, and B-9.

## Goal

Close the remaining non-AI B-group student-facing gaps:

1. B-6 delete draft/returned applications.
2. B-1 student application overview.
3. B-10/B-11 student-facing evaluation item and point-calculation routes that wrap existing config/rule capabilities.
4. B-9 lecture/activity candidate query, based on current D import/final-component source data where available.

## Current Evidence

- `docs/team-delivery/group-b-student-application.md` still marks B as `PARTIAL_IMPLEMENTED`.
- `StudentApplicationSubmissionController` exposes create/update/submit/withdraw/detail, but not `DELETE /api/student/applications/{applicationId}`.
- `StudentQueryController` exposes `/api/student/query/applications`, but not `/api/student/applications/overview`.
- `EvaluationConfigController` exposes `/api/config/evaluation/**`; the B document requires student-facing `/api/student/evaluation/items` and `/api/student/evaluation/calculate-points`.
- No current student controller exposes `/api/student/lectures`.

## Execution Rules

- Work on `feature/b-student-application-closure` in `.worktrees/b-student-application-closure`.
- Keep tasks sequential because they share student-facing controllers and B-domain services.
- For each task:
  - write the failing test first;
  - run the focused test and record the expected RED failure;
  - implement the smallest matching production change;
  - run focused GREEN verification;
  - run at least one broader affected regression command;
  - write main-agent verification evidence into the SDD state after the last edit.
- Do not use storage internals in student-facing responses.
- Preserve current exception mapping and permission constants unless the task explicitly requires a new constant.
- Update `docs/team-delivery/group-b-student-application.md` when an endpoint becomes implemented or when a route is intentionally an adapter over existing config/source data.

## Task Order

### T001 - B-6 Delete Application

Implement `DELETE /api/student/applications/{applicationId}`.

Contract:

- permission: `application.update` via `AuthorizationPermissionCodes.APPLICATION_UPDATE`;
- only current applicant can delete;
- only `DRAFT` and `RETURNED` are deletable;
- deletion is logical: mark the submission deleted so normal query/detail paths no longer expose it;
- do not delete `file_asset`; application attachment/fact rows may remain for audit unless current repository constraints require association cleanup.

Expected implementation areas:

- `ApplicationSubmission` domain state or deletion marker support;
- `ApplicationSubmissionRepository`;
- MyBatis repository/mapper/data object;
- `ApplicationSubmissionCommandApplicationService`;
- `StudentApplicationSubmissionController`;
- tests under app/domain/student controller/repository packages.

### T002 - B-1 Application Overview

Implement `GET /api/student/applications/overview`.

Contract:

- authenticated student/self permission path;
- returns counts for `draftCount`, `submittedCount`, `returnedCount`, `approvedCount`, `rejectedCount`, and `latestAcademicYear`;
- current-user scoped only;
- deleted submissions are excluded;
- no data returns zero counts and `latestAcademicYear = null`.

Expected implementation areas:

- new application query view/service/repository method;
- `StudentApplicationSubmissionController` or a dedicated student overview controller, keeping route exactly `/api/student/applications/overview`;
- WebMvc and service/repository tests.

### T003 - B-10/B-11 Student Evaluation Routes

Implement student-facing adapters:

- `GET /api/student/evaluation/items?categoryCode=...`;
- `POST /api/student/evaluation/calculate-points`.

Contract:

- authenticated student-facing routes;
- use existing `EvaluationConfigApplicationService` / rule engine capabilities;
- `items` returns enabled item definitions with option list when `optionsKey` exists;
- `calculate-points` returns `itemCode`, `optionCode`, `points`, and `optionName`;
- do not require callers to send arbitrary `studentId`; use current authorization context for student context where needed.

Expected implementation areas:

- student request/response DTOs;
- a student evaluation application service or thin controller adapter;
- WebMvc and application-service tests.

### T004 - B-9 Lecture Candidate Query

Implement `GET /api/student/lectures`.

Contract:

- authenticated student-facing route;
- required `academicYear`;
- supports `keyword`, `pageNo`, `pageSize`;
- returns `PageResult<LectureCandidateView>`;
- empty result is an empty page, not `404`;
- use D imported/final-component source data if that is the only current persisted source, and document the limitation.

Expected implementation areas:

- query model/view/service/repository;
- MyBatis query over current final component/import data, if no dedicated lecture source table exists;
- student controller route;
- WebMvc, service, and repository tests.

## Verification Plan

Focused commands will be selected per task. The final run must include:

```bash
mvn -pl whut-eval-app -am test
```

Before final handoff, perform an objective audit mapping B-6, B-1, B-10, B-11, B-9, SDD evidence, tests, docs, commit, merge, push, and cleanup to concrete artifacts.
