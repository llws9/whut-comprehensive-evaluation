#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
DB_NAME="${WHUT_SEED_SMOKE_DB:-whut_seed_smoke_$(date +%Y%m%d%H%M%S)_$$}"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"

MYSQL_ARGS=(
  --protocol=TCP
  --host="$MYSQL_HOST"
  --port="$MYSQL_PORT"
  --user="$MYSQL_USER"
  --default-character-set=utf8mb4
  --batch
  --raw
)

if [[ -n "$MYSQL_PASSWORD" ]]; then
  MYSQL_ARGS+=("--password=$MYSQL_PASSWORD")
fi

mysql_exec() {
  mysql "${MYSQL_ARGS[@]}" "$@"
}

cleanup() {
  mysql_exec -e "DROP DATABASE IF EXISTS \`$DB_NAME\`;" >/dev/null 2>&1 || true
}

trap cleanup EXIT

assert_scalar() {
  local label="$1"
  local expected="$2"
  local sql="$3"
  local actual

  actual="$(mysql_exec "$DB_NAME" --skip-column-names -e "$sql")"
  if [[ "$actual" != "$expected" ]]; then
    printf 'Assertion failed: %s expected [%s] but got [%s]\n' "$label" "$expected" "$actual" >&2
    exit 1
  fi

  printf '%s=%s\n' "$label" "$actual"
}

mysql_exec -e "CREATE DATABASE \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

for sql_file in \
  docs/team-delivery/group-a-identity-user-admin.sql \
  docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql \
  docs/team-delivery/group-b-student-application.safe-init.sql \
  docs/team-delivery/group-c-review-workflow.safe-init.sql \
  docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql \
  docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql \
  docs/team-delivery/group-b-student-application.safe-init.sql \
  docs/team-delivery/group-c-review-workflow.safe-init.sql \
  docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql; do
  mysql_exec "$DB_NAME" < "$ROOT_DIR/$sql_file"
done

assert_scalar "permission_count" "1" \
  "SELECT COUNT(*) FROM iam_permission WHERE permission_code = 'score.confirm.assigned';"
assert_scalar "counselor_binding" "1" \
  "SELECT COUNT(*) FROM iam_role_permission WHERE role_id = 4003 AND permission_id = (SELECT id FROM iam_permission WHERE permission_code = 'score.confirm.assigned');"
assert_scalar "college_reviewer_binding" "1" \
  "SELECT COUNT(*) FROM iam_role_permission WHERE role_id = 4004 AND permission_id = (SELECT id FROM iam_permission WHERE permission_code = 'score.confirm.assigned');"
assert_scalar "scope_8019" "1" \
  "SELECT COUNT(*) FROM iam_scope_rule WHERE id = 8019 AND assignment_id = 7010 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002;"
assert_scalar "scope_8020" "1" \
  "SELECT COUNT(*) FROM iam_scope_rule WHERE id = 8020 AND assignment_id = 7011 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002;"
assert_scalar "org_path_2010" "/WHUT/CS/CS2022/CS2201" \
  "SELECT path FROM org_unit WHERE id = 2010;"
assert_scalar "file_0008" "1" \
  "SELECT COUNT(*) FROM file_asset WHERE file_id = 'FILE-0008';"
assert_scalar "public_attachment_14001" "1" \
  "SELECT COUNT(*) FROM public_attachment_entry WHERE id = 14001;"

printf 'mysql_full_seed_smoke=ok db=%s\n' "$DB_NAME"
