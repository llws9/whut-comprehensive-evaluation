# Full Seed Init Smoke Gate

## Purpose

This gate verifies that the team delivery initialization chain can start from the
A-group IAM schema and then apply each runtime-safe team initializer in the same
database without breaking shared contracts.

It is meant to catch integration bugs that single-team SQL tests can miss, such
as missing required IAM columns, fixed-id collisions, wrong organization path
assumptions, or unsafe reruns that overwrite runtime rows.

## Initialization Order

Run the scripts in this order for a local integration database:

1. `docs/team-delivery/group-a-identity-user-admin.sql`
2. `docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql`
3. `docs/team-delivery/group-b-student-application.safe-init.sql`
4. `docs/team-delivery/group-c-review-workflow.safe-init.sql`
5. `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql`

The A-group script owns the base IAM and organization schema. The E/B/C/D
safe-init scripts must be rerunnable and must not drop or overwrite runtime data.

## Automated Gate

The automated smoke gate is:

```bash
mvn -pl whut-eval-app -am -Dtest=TeamDeliverySqlConsistencyTest#shouldInitializeFullSeedChainOnAGroupSchemaAndKeepSafeInitRerunnable -Dsurefire.failIfNoSpecifiedTests=false test
```

The test executes the A-group schema and seed first, then executes the E/B/C/D
safe-init chain twice in one H2 MySQL-mode database. Between the two safe-init
runs, it inserts representative runtime B and D rows and verifies they survive.

The assertions cover:

- A-group organization path format such as `/WHUT/CS/CS2022/CS2201`
- D-owned `score.confirm.assigned` permission seed and role bindings
- D-owned ORG_SUBTREE scope rules for counselor and college reviewer
- B runtime application and application fact rows surviving safe-init reruns
- D runtime final record and component rows surviving safe-init reruns
- E public file and public attachment seed rows remaining available

## Real MySQL Check

H2 MySQL-mode is the CI safety net, not a complete replacement for a real MySQL
smoke test. The repeatable real MySQL smoke gate is:

```bash
scripts/full-seed-init-mysql-smoke.sh
```

By default, the script connects to `127.0.0.1:3306` as `root` without a
password. Override `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, or
`MYSQL_PASSWORD` when needed. The script creates a throwaway schema, runs the
same initialization order, reruns E/B/C/D safe-init, verifies the shared seed
contracts, and drops the schema on exit.

Before a demo or deployment, run the same initialization order on a throwaway
MySQL schema and verify:

- every script exits successfully;
- rerunning E/B/C/D safe-init exits successfully;
- seed permission `score.confirm.assigned` appears exactly once;
- roles `COUNSELOR` and `COLLEGE_REVIEWER` have that permission;
- scope rules `8019` and `8020` point at org unit `2002`;
- `org_unit.path` uses code paths, not numeric-id paths.
