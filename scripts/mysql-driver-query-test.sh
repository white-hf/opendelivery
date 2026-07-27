#!/usr/bin/env bash
set -euo pipefail

# Opt-in JDBC query smoke test. No writes are performed.
MYSQL_BIN="${MYSQL_BIN:-mysql}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-uniuni_hf}"
DB_NAME="${DB_NAME:-opendelivery}"
: "${DB_PASSWORD:?Set DB_PASSWORD before running this script}"

export MYSQL_PWD="$DB_PASSWORD"
MYSQL_ENDPOINT=(-h"$DB_HOST" -P"$DB_PORT")
if [[ -n "${DB_SOCKET:-}" ]]; then MYSQL_ENDPOINT=(--socket="$DB_SOCKET"); fi
"$MYSQL_BIN" "${MYSQL_ENDPOINT[@]}" -u"$DB_USER" "$DB_NAME" --batch --skip-column-names <<'SQL'
SELECT 'driver_task_item', COUNT(*) FROM driver_task_item;
SELECT 'scan_event_unique', COUNT(*) FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'scan_event' AND index_name = 'uk_scan_device_event';
SELECT 'delivery_attempt_unique', COUNT(*) FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'delivery_attempt' AND index_name = 'uk_attempt_idempotency';
EXPLAIN SELECT p.id, p.tracking_no, p.status
  FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id
  JOIN parcel p ON p.id=ti.parcel_id
  WHERE t.driver_id=101 AND p.status='ASSIGNED' AND ti.item_status='ASSIGNED'
  ORDER BY COALESCE(ti.stop_sequence, 2147483647), ti.id LIMIT 100;
SQL
