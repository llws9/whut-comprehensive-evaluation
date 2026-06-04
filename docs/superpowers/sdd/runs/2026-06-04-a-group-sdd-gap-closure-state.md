# SDD Run State: A Group SDD Gap Closure

## Metadata
- branch: chore/a-group-sdd-gap-closure
- worktree: /Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/a-group-sdd-gap-closure
- plan: docs/superpowers/plans/2026-06-04-a-group-sdd-gap-closure.md
- source_requirements: docs/team-delivery/group-a-identity-user-admin.md
- note: repo lacks docs/superpowers/sdd/RUNBOOK.md, state-template.md, and scripts/sdd_run.py; this file is the manual SSOT for this run.

## Task Status
| Task | Status | Owner | Dependencies | Verification |
|---|---|---|---|---|
| T0 Restore test compile baseline | done | main | none | targeted `UserAdminApplicationServiceTest` passed; full `whut-eval-app` reaches runtime test failures |
| T1 Implement A-10/A-11/A-12 | done | main | T0 | role admin WebMvc/service/repository/security tests passed |
| T2 Close session lifecycle | done | main | T0 | login/refresh/protected/logout session lifecycle verified by full `whut-eval-app` regression |
| T3 Align SQL and permission seed | done | main | T0,T1,T2 | SQL seed consistency contract and H2 execution smoke test passed |

## Evidence Log
- 2026-06-04: Created isolated worktree from `main` and applied existing related dirty diff.
- 2026-06-04: Existing baseline command in source worktree failed at `testCompile` due stale imports in `UserAdminApplicationServiceTest`.
- 2026-06-04: T0 reproduced targeted failure with `mvn -q -pl whut-eval-app -am -Dtest=UserAdminApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`; compile failed because `UserAdminApplicationService` and its test still imported `OrgUnitLookupRepository` / `UserMembershipAdminRepository` from stale `domain.iam.repository`.
- 2026-06-04: T0 fixed stale org imports to `domain.org.repository`, replaced removed `OrgUnitSnapshot` test stub with `OrgUnit`, and aligned membership creation to existing `UserMembershipAdminRepository.replaceMemberships(...)` contract.
- 2026-06-04: T0 targeted verification passed: `mvn -q -pl whut-eval-app -am -Dtest=UserAdminApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- 2026-06-04: T0 full verification command `mvn -q -pl whut-eval-app -am test` reached test execution and failed with remaining non-T0 issues: `SecurityProbeControllerWebMvcTest.shouldReturn401WhenTokenIsInvalid` expected `AUTH-4010` but got `AUTH-4012`; role assignment integration tests fail on H2 schema missing `iam_role.role_scope`.

## Risks
- Existing dirty changes were brought into this worktree by user choice; they may represent partial work and must be reviewed before completion.
- SDD bootstrap script is absent, so state management is manual in this file.
- Full `whut-eval-app` regression is not green after T0; remaining failures belong to security error-code alignment and SQL/schema alignment, likely T2/T3 scope.

- 2026-06-04: T1 RED added controller/service/repository tests for `POST /api/admin/roles`, `PATCH /api/admin/roles/{roleId}`, and `POST /api/admin/roles/{roleId}/permissions`; initial command failed at testCompile due missing command/service/repository/DTO classes.
- 2026-06-04: T1 GREEN added `RoleAdminApplicationService`, role command DTOs, role write repository, permission lookup mapper, request DTOs, and controller routes.
- 2026-06-04: T1 targeted verification passed: `mvn -q -pl whut-eval-app -am -Dtest=RoleAdminQueryControllerWebMvcTest,RoleAdminApplicationServiceTest,MybatisPlusRoleAdminCommandRepositoryTest,RoleAdminControllerSecurityAnnotationTest,DefaultRoleAdminQueryApplicationServiceTest,MybatisPlusRoleAdminQueryRepositoryTest,MybatisPlusIamRoleQueryRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- 2026-06-04: Full verification after T1 still fails with known T2/T3 issues: `SecurityProbeControllerWebMvcTest.shouldReturn401WhenTokenIsInvalid` expects `AUTH-4010` vs actual `AUTH-4012`; H2 role assignment integration schema lacks `iam_role.role_scope`.

- 2026-06-05: T2 partial fix aligned `SecurityProbeControllerWebMvcTest.shouldReturn401WhenTokenIsInvalid` with existing `AUTH-4012` token-invalid semantics; targeted `SecurityProbeControllerWebMvcTest` passed. Full session/sid lifecycle remains pending.
- 2026-06-05: T3 partial fix updated H2 `iam_role` test schemas in role assignment integration tests to include `role_scope` and `created_at`, matching `IamRoleDO`; targeted role assignment integration tests passed.
- 2026-06-05: Final verification checkpoint passed: `mvn -q -pl whut-eval-app -am test` exited 0; VS Code diagnostics returned no diagnostics.

- 2026-06-05: T2a RED added tests for login-created server session and shared `sid` claim; initial targeted command failed at `testCompile` because `LoginSessionService`, session-aware `JwtTokenPair`, `issueTokenPair(currentUser, sessionNo)`, and `IamSessionRepository.create(...)` did not exist.
- 2026-06-05: T2a GREEN implemented login session creation via `LoginSessionCreateCommand` + `LoginSessionService`, added `sid` claim and token IDs to `JwtTokenPair`, made `AuthController.login` persist the session after token issuance, and mapped `IamSession` to `IamSessionDO` in `MybatisPlusIamSessionRepository.create(...)`.
- 2026-06-05: T2a targeted verification passed: `mvn -q -pl whut-eval-app -am -Dtest=JwtTokenIssuerTest,AuthControllerWebMvcTest,LoginSessionServiceTest,MybatisPlusIamSessionRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- 2026-06-05: T2a full verification passed: `mvn -q -pl whut-eval-app -am test` exited 0; VS Code diagnostics returned no diagnostics.
- 2026-06-05: T2 remaining scope: refresh must validate/continue the existing session, protected requests must reject revoked/expired sessions, and logout should fail closed for invalid/nonexistent sessions.

- 2026-06-05: T2b-refresh RED added tests for refresh claim `sid/jti` mapping, server-side refresh session validation, same-`sid` token reissue, session token-id rotation, and MyBatis session continuation mapping; initial targeted command failed at `testCompile` due missing `RefreshSessionService`, refresh session commands, `RefreshTokenSubject.sessionNo/refreshTokenId`, and `IamSessionRepository.continueRefreshSession(...)`.
- 2026-06-05: T2b-refresh GREEN implemented `RefreshSessionValidationCommand`, `RefreshSessionContinueCommand`, `RefreshSessionService`, refresh-token `sid/jti` mapping, same-session token reissue in `AuthController.refresh`, and `MybatisPlusIamSessionRepository.continueRefreshSession(...)`.
- 2026-06-05: T2b-refresh targeted verification passed: `mvn -q -pl whut-eval-app -am -Dtest=RefreshTokenClaimsMapperTest,RefreshSessionServiceTest,AuthControllerWebMvcTest,MybatisPlusIamSessionRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- 2026-06-05: T2b-refresh full verification passed: `mvn -q -pl whut-eval-app -am test` exited 0; VS Code diagnostics returned no diagnostics.
- 2026-06-05: T2 remaining scope after refresh: protected requests must reject revoked/expired sessions, and logout should fail closed for invalid/nonexistent sessions.

- 2026-06-05: T2b-protected RED added tests for protected request access-session validation and revoked/expired session rejection; initial targeted command failed at `testCompile` due missing `AccessSessionValidationCommand` and `AccessSessionService`.
- 2026-06-05: T2b-protected GREEN implemented `AccessSessionValidationCommand`, `AccessSessionService`, and `JwtAuthenticationFilter` validation before loading authorization context; invalid access sessions are converted to `JwtAuthenticationException` and return `AUTH-4012`.
- 2026-06-05: T2b-protected regression root cause: `AuthControllerWebMvcTest` imported `JwtAuthenticationFilter` without an `AccessSessionService` mock and used a legacy test access token without `sid`; fixed test context and token shape to match T2a token contract.
- 2026-06-05: T2b-protected targeted verification passed: `mvn -q -pl whut-eval-app -am -Dtest=AuthControllerWebMvcTest,SecurityProbeControllerWebMvcTest,AccessSessionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- 2026-06-05: T2b-protected full verification passed: `mvn -q -pl whut-eval-app -am test` exited 0; VS Code diagnostics returned no diagnostics.
- 2026-06-05: T2 remaining scope after protected validation: logout should fail closed for invalid/nonexistent sessions.

- 2026-06-05: T2b-logout RED added tests for logout returning `AUTH-4012` when `LogoutService.logoutByAccessTokenId(...)` returns false and for `DefaultLogoutService` not revoking inactive/expired sessions; initial targeted command failed because `AuthController.logout` still returned `200 OK` when `revoked=false`.
- 2026-06-05: T2b-logout GREEN changed `AuthController.logout` to fail closed with `AUTH-4012` when session revocation fails, and changed `DefaultLogoutService` to return false for non-active sessions without invoking revocation.
- 2026-06-05: T2b-logout regression root cause: existing successful logout test used an `ACTIVE` session without `expiredAt`, which is invalid under the T2 `IamSession.isActive()` contract; fixed test fixture to include a future expiration.
- 2026-06-05: T2b-logout targeted verification passed: `mvn -q -pl whut-eval-app -am -Dtest=AuthControllerWebMvcTest,DefaultLogoutServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- 2026-06-05: T2b-logout full verification passed: `mvn -q -pl whut-eval-app -am test` exited 0; VS Code diagnostics returned no diagnostics.
- 2026-06-05: T2 lifecycle closure achieved for login-created session, refresh same-session continuation, protected request session validation, and logout fail-closed behavior.

- 2026-06-05: T3 RED added `GroupAIdentitySqlSeedConsistencyTest` to compare `iam_permission` seed against `AuthorizationPermissionCodes`, ensure scope rules reference seeded permissions, and require current `iam_session` insert columns; initial targeted command failed because SQL still used old `student.*`/`manage.*` permissions and obsolete `token_id`/`login_ip` session columns.
- 2026-06-05: T3 GREEN updated `docs/team-delivery/group-a-identity-user-admin.sql` permission seed to exactly match `AuthorizationPermissionCodes`, rewired role-permission and scope-rule seed data to canonical permission codes, and fixed `iam_session` seed columns to `session_no/access_token_id/refresh_token_id/client_ip/updated_at`.
- 2026-06-05: T3 added H2 compatibility execution smoke test for the A group SQL seed; first attempt exposed a test converter issue while stripping FK constraints, then converter was fixed to drop FK constraint lines without corrupting table DDL.
- 2026-06-05: T3 targeted verification passed: `mvn -q -pl whut-eval-app -am -Dtest=GroupAIdentitySqlSeedConsistencyTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- 2026-06-05: T3 full verification passed: `mvn -q -pl whut-eval-app -am test` exited 0; VS Code diagnostics returned no diagnostics.

- 2026-06-05: Code review found two issues after T3: protected JWT filter hardcoded `sid` instead of `JwtProperties.sessionIdClaim`; A-group SQL seed had `ACTIVE` session rows expired by current date.
- 2026-06-05: Review-fix RED added `JwtAuthenticationFilterSessionClaimTest` and active-session seed semantic check in `GroupAIdentitySqlSeedConsistencyTest`; targeted command failed with expected 401 for custom `session_id` claim and 10 expired ACTIVE seed sessions.
- 2026-06-05: Review-fix GREEN injected `SecurityProperties` into `JwtAuthenticationFilter`, read session claim via `jwt.sessionIdClaim`, and moved ACTIVE `iam_session` seed expirations to future dates while keeping explicit EXPIRED/REVOKED rows.
- 2026-06-05: Review-fix verification passed: `mvn -q -pl whut-eval-app -am -Dtest=JwtAuthenticationFilterSessionClaimTest,GroupAIdentitySqlSeedConsistencyTest -Dsurefire.failIfNoSpecifiedTests=false test` and full `mvn -q -pl whut-eval-app -am test` exited 0; VS Code diagnostics returned no diagnostics.
