# Minimal E Foundation SDD State

## Run Metadata

- Branch/worktree: `feature/minimal-e-foundation @ /Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation`
- Base commit: `3f9446268c83ab3468cddcec9b9ea71b1758aa40`
- Target branch: `main`
- Plan: `docs/superpowers/plans/active/2026-07-06/2026-07-06-minimal-e-foundation.md`
- Spec: `docs/superpowers/specs/2026-07-06-minimal-e-foundation-design.md`
- Started: `2026-07-06 Asia/Shanghai`

## Task Matrix

| Task | Status | Write Set | Verification |
|---|---|---|---|
| Activate plan and state | done | `docs/superpowers/plans/**`, `docs/superpowers/sdd/runs/2026-07-06-minimal-e-foundation-state.md` | `git status --short --branch` |
| Milestone 1: safe-init SQL and typed config fields | done | `docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql`, `whut-eval-domain/src/main/java/edu/whut/eval/domain/config/model/PlatformRuleConfig.java`, `whut-eval-app/src/test/java/edu/whut/eval/app/config/PlatformRuleConfigProviderTest.java`, `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java` | `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PlatformRuleConfigProviderTest,TeamDeliverySqlConsistencyTest test` |
| Milestone 2: platform reads and item list | done | `whut-eval-application/src/main/java/edu/whut/eval/application/platform/**`, `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/platform/**`, `whut-eval-app/src/test/java/edu/whut/eval/app/platform/**` | `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PlatformReadControllerWebMvcTest,EvaluationItemQueryControllerWebMvcTest,EvaluationItemQueryApplicationServiceTest test` |
| Milestone 3: upload response safe subset | done | `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/file/FileUploadController.java`, `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/file/view/StoredFileDescriptorView.java`, `whut-eval-app/src/test/java/edu/whut/eval/app/file/FileUploadControllerWebMvcTest.java` | `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FileUploadControllerWebMvcTest,FileUploadApplicationServiceTest,MybatisFileAssetRegistryIntegrationTest test` |
| Milestone 4: file metadata/access-url/public attachments | done | `whut-eval-application/src/main/java/edu/whut/eval/application/file/query/**`, `whut-eval-application/src/main/java/edu/whut/eval/application/file/service/FileQuery*`, `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/**`, `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisFileQueryRepository.java`, `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/file/FileQueryController.java`, `whut-eval-app/src/test/java/edu/whut/eval/app/file/**` | `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FileQueryControllerWebMvcTest,FileQueryApplicationServiceTest,PublicAttachmentQueryRepositoryIntegrationTest test` |
| Milestone 5: security and focused regression | done | `whut-eval-app/src/test/java/edu/whut/eval/app/platform/MinimalEReadSecurityIntegrationTest.java`, related tests only as needed | `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FileQueryControllerWebMvcTest,PlatformReadControllerWebMvcTest,EvaluationItemQueryControllerWebMvcTest,FileQueryApplicationServiceTest,PublicAttachmentQueryRepositoryIntegrationTest,MinimalEReadSecurityIntegrationTest,FileUploadControllerWebMvcTest,PlatformRuleConfigProviderTest,TeamDeliverySqlConsistencyTest test` |

## Wave Plan

Milestones execute sequentially because later endpoints depend on contracts and DDL from earlier milestones.

## Runtime Sources

No local service started yet.

## Evidence Log

- `2026-07-06`: Repository SDD script/runbook files were absent, so this state file is maintained manually.
- `2026-07-06 19:44:04+08:00`: Milestone 1 RED verified. Maven failed during test compilation because `PlatformRuleConfig` lacked `getStudentApplyDeadline()` and `getFinalSubmitDeadline()`.
- `2026-07-06 19:46:23+08:00`: Milestone 1 GREEN verified. `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PlatformRuleConfigProviderTest,TeamDeliverySqlConsistencyTest test` returned `BUILD SUCCESS`; 10 tests, 0 failures, 0 errors, 0 skipped.
- `2026-07-06`: `rg -n "DROP TABLE" docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql` returned no output.
- `2026-07-06 19:49:19+08:00`: Milestone 2 RED verified. Maven failed during test compilation because platform query DTOs, service, and controller did not exist.
- `2026-07-06 19:51:37+08:00`: Milestone 2 GREEN verified. `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PlatformReadControllerWebMvcTest,EvaluationItemQueryControllerWebMvcTest,EvaluationItemQueryApplicationServiceTest test` returned `BUILD SUCCESS`; 9 tests, 0 failures, 0 errors, 0 skipped.
- `2026-07-06 19:52:42+08:00`: Milestone 3 RED verified. `FileUploadControllerWebMvcTest.shouldUploadFileSuccessfully` failed because `$.data.bucket` still existed.
- `2026-07-06 19:53:30+08:00`: Milestone 3 GREEN verified. `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FileUploadControllerWebMvcTest,FileUploadApplicationServiceTest,MybatisFileAssetRegistryIntegrationTest test` returned `BUILD SUCCESS`; 7 tests, 0 failures, 0 errors, 0 skipped.
- `2026-07-06 19:57:55+08:00`: Milestone 4 RED verified. Controller shape tests passed; service and repository tests failed on the planned `UnsupportedOperationException` skeletons.
- `2026-07-06 19:59:12+08:00`: Milestone 4 GREEN verified. `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FileQueryControllerWebMvcTest,FileQueryApplicationServiceTest,PublicAttachmentQueryRepositoryIntegrationTest test` returned `BUILD SUCCESS`; 13 tests, 0 failures, 0 errors, 0 skipped.
- `2026-07-06 20:00:33+08:00`: Milestone 5 security test verified. `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=MinimalEReadSecurityIntegrationTest test` returned `BUILD SUCCESS`; 5 tests, 0 failures, 0 errors, 0 skipped.
- `2026-07-06 20:00:49+08:00`: Focused regression verified. `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FileQueryControllerWebMvcTest,PlatformReadControllerWebMvcTest,EvaluationItemQueryControllerWebMvcTest,FileQueryApplicationServiceTest,PublicAttachmentQueryRepositoryIntegrationTest,MinimalEReadSecurityIntegrationTest,FileUploadControllerWebMvcTest,PlatformRuleConfigProviderTest,TeamDeliverySqlConsistencyTest test` returned `BUILD SUCCESS`; 37 tests, 0 failures, 0 errors, 0 skipped.
- `2026-07-06`: `rg -n "AutoConfigureMockMvc\\(addFilters = false\\)" whut-eval-app/src/test/java/edu/whut/eval/app/platform/MinimalEReadSecurityIntegrationTest.java` returned no output.
