#!/bin/bash
set -euo pipefail

: "${DB_PASSWORD:?DB_PASSWORD must be set}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-uniuni_hf}"
DB_NAME="${DB_NAME:-opendelivery}"
APP_PORT="${APP_PORT:-19001}"
APP_JAR="easydelivery-app/target/easydelivery-app-1.0.0.jar"
TEST_ID=990001
TEST_TRACK="E2E-TRACK-$TEST_ID"
TEST_WAYBILL="E2E-$TEST_ID"
TEST_MANIFEST="E2E-MANIFEST-$TEST_ID"
TEST_WAVE="E2E-WAVE-$TEST_ID"
APP_PID=""

mysql_exec() {
  MYSQL_PWD="$DB_PASSWORD" /usr/local/mysql/bin/mysql \
    -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" "$DB_NAME" --batch --skip-column-names "$@"
}

cleanup() {
  if [ -n "$APP_PID" ]; then kill "$APP_PID" 2>/dev/null || true; fi
  mysql_exec -e "
    SET FOREIGN_KEY_CHECKS=0;
    DELETE ca FROM callback_attempt ca JOIN outbox_event oe ON oe.id=ca.outbox_event_id JOIN parcel p ON p.id=oe.aggregate_id WHERE p.tracking_no='$TEST_TRACK';
    DELETE oe FROM outbox_event oe JOIN parcel p ON p.id=oe.aggregate_id WHERE p.tracking_no='$TEST_TRACK';
    DELETE se FROM parcel_status_event se JOIN parcel p ON p.id=se.parcel_id WHERE p.tracking_no='$TEST_TRACK';
    DELETE ce FROM custody_event ce JOIN parcel p ON p.id=ce.parcel_id WHERE p.tracking_no='$TEST_TRACK';
    DELETE pod FROM proof_of_delivery pod JOIN delivery_attempt a ON a.id=pod.attempt_id JOIN parcel p ON p.id=a.parcel_id WHERE p.tracking_no='$TEST_TRACK';
    DELETE a FROM delivery_attempt a JOIN parcel p ON p.id=a.parcel_id WHERE p.tracking_no='$TEST_TRACK';
    DELETE se FROM scan_event se JOIN parcel p ON p.id=se.parcel_id WHERE p.tracking_no='$TEST_TRACK';
    DELETE ss FROM scan_session ss JOIN driver_task t ON t.id=ss.task_id WHERE t.task_code=CONCAT('$TEST_WAVE','-D101');
    DELETE ti FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id WHERE t.task_code=CONCAT('$TEST_WAVE','-D101');
    DELETE FROM driver_task WHERE task_code=CONCAT('$TEST_WAVE','-D101');
    DELETE FROM dispatch_wave WHERE wave_code='$TEST_WAVE';
    DELETE mi FROM inbound_manifest_item mi JOIN inbound_manifest m ON m.id=mi.manifest_id WHERE m.external_manifest_no='$TEST_MANIFEST';
    DELETE FROM inbound_manifest WHERE external_manifest_no='$TEST_MANIFEST';
    DELETE FROM parcel WHERE tracking_no='$TEST_TRACK';
    DELETE FROM waybill WHERE external_waybill_no='$TEST_WAYBILL';
    DELETE ir FROM ingestion_record ir WHERE ir.external_event_id='E2E-EVENT-$TEST_ID';
    DELETE ib FROM ingestion_batch ib LEFT JOIN ingestion_record ir ON ir.batch_id=ib.id WHERE ir.id IS NULL AND ib.started_at > CURRENT_TIMESTAMP - INTERVAL 1 DAY;
    SET FOREIGN_KEY_CHECKS=1;
  " >/dev/null 2>&1 || true
}
trap cleanup EXIT

cleanup
DB_URL="jdbc:mysql://$DB_HOST:$DB_PORT/$DB_NAME?serverTimezone=UTC" \
DB_USERNAME="$DB_USERNAME" DB_PASSWORD="$DB_PASSWORD" \
JWT_SECRET="OpenDelivery_E2E_Test_Secret_At_Least_32_Characters" \
UPSTREAM_API_KEY="e2e-upstream-key" OPERATIONS_API_KEY="e2e-operations-key" \
java -Dserver.port="$APP_PORT" -jar "$APP_JAR" >/private/tmp/opendelivery-e2e.log 2>&1 &
APP_PID=$!

for attempt in {1..20}; do
  if curl -sS "http://127.0.0.1:$APP_PORT/error" >/dev/null 2>&1; then break; fi
  sleep 1
done

curl -fsS -X POST "http://127.0.0.1:$APP_PORT/integration/v1/partners/DEMO_UPSTREAM/shipments" \
  -H 'Content-Type: application/json' -H 'X-Upstream-Api-Key: e2e-upstream-key' \
  -d "{\"externalEventId\":\"E2E-EVENT-$TEST_ID\",\"externalWaybillNo\":\"$TEST_WAYBILL\",\"recipientName\":\"E2E Recipient\",\"addressLine1\":\"1 Test Street\",\"city\":\"Halifax\",\"province\":\"NS\",\"postalCode\":\"B3H 1A1\",\"targetStationCode\":\"YHZ-01\",\"externalManifestNo\":\"$TEST_MANIFEST\",\"trackingNumbers\":[\"$TEST_TRACK\"]}" \
  | jq -e '.biz_code == "COMMON.QUERY.SUCCESS" and .biz_data.duplicate == false' >/dev/null

curl -fsS -X POST "http://127.0.0.1:$APP_PORT/ops/v1/manifests/$TEST_MANIFEST/receipts" \
  -H 'Content-Type: application/json' -H 'X-Ops-Api-Key: e2e-operations-key' \
  -d "{\"trackingNumber\":\"$TEST_TRACK\"}" \
  | jq -e '.biz_data.status == "AT_STATION"' >/dev/null

curl -fsS -X POST "http://127.0.0.1:$APP_PORT/ops/v1/waves" \
  -H 'Content-Type: application/json' -H 'X-Ops-Api-Key: e2e-operations-key' \
  -d "{\"stationCode\":\"YHZ-01\",\"waveCode\":\"$TEST_WAVE\",\"serviceDate\":\"$(date +%F)\",\"routeCode\":\"99\",\"driverId\":101,\"trackingNumbers\":[\"$TEST_TRACK\"]}" \
  | jq -e '.biz_data.status == "PUBLISHED" and .biz_data.parcelCount == 1' >/dev/null

login_json=$(curl -sS -X POST "http://127.0.0.1:$APP_PORT/auth/login" \
  -H 'Content-Type: application/json' -d '{"credential_id":"driver123","password":"password123"}')
access_token=$(printf '%s' "$login_json" | jq -er '.biz_data.access_token')

batch_json=$(curl -sS -X POST "http://127.0.0.1:$APP_PORT/delivery/scan/batch" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $access_token" \
  -d '{"driver_id":101,"operator_role":1,"scan_as":2}')
batch_id=$(printf '%s' "$batch_json" | jq -er '.biz_data.scan_batch_id')

curl -fsS -X POST "http://127.0.0.1:$APP_PORT/delivery/ext/scan" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $access_token" \
  -d "{\"tracking_no\":\"$TEST_TRACK\",\"scan_batch_id\":$batch_id,\"device_event_id\":\"E2E-SCAN-$TEST_ID\"}" \
  | jq -e '.biz_code == "COMMON.QUERY.SUCCESS"' >/dev/null

curl -fsS -X PUT "http://127.0.0.1:$APP_PORT/delivery/ext/scan/batch/$batch_id" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $access_token" \
  -d '{"status":"APPROVED"}' | jq -e '.biz_data.status == "APPROVED"' >/dev/null

parcel_id=$(mysql_exec -e "SELECT id FROM parcel WHERE tracking_no='$TEST_TRACK'")

other_login=$(curl -sS -X POST "http://127.0.0.1:$APP_PORT/auth/login" \
  -H 'Content-Type: application/json' -d '{"credential_id":"test","password":"test"}')
other_token=$(printf '%s' "$other_login" | jq -er '.biz_data.access_token')
curl -sS -X POST "http://127.0.0.1:$APP_PORT/delivery" \
  -H "Authorization: Bearer $other_token" -F order_id=$parcel_id \
  -F longitude=-63.5752 -F latitude=44.6488 -F delivery_result=0 \
  -F idempotency_key="E2E-CROSS-DRIVER-$TEST_ID" \
  | jq -e '.biz_code == "DELIVERY.TASK.INVALID"' >/dev/null

curl -fsS -X POST "http://127.0.0.1:$APP_PORT/delivery" \
  -H "Authorization: Bearer $access_token" -F order_id=$parcel_id \
  -F longitude=-63.5752 -F latitude=44.6488 -F delivery_result=0 \
  -F recipient_name='E2E Recipient' -F idempotency_key="E2E-DELIVERY-$TEST_ID" \
  | jq -e '.biz_code == "COMMON.QUERY.SUCCESS"' >/dev/null

curl -fsS -X POST "http://127.0.0.1:$APP_PORT/delivery" \
  -H "Authorization: Bearer $access_token" -F order_id=$parcel_id \
  -F longitude=-63.5752 -F latitude=44.6488 -F delivery_result=0 \
  -F recipient_name='E2E Recipient' -F idempotency_key="E2E-DELIVERY-$TEST_ID" \
  | jq -e '.biz_code == "COMMON.QUERY.SUCCESS"' >/dev/null

result=$(mysql_exec -e "
  SELECT CONCAT(p.status,'|',COUNT(DISTINCT a.id),'|',COUNT(DISTINCT se.id),'|',COUNT(DISTINCT oe.id))
  FROM parcel p
  LEFT JOIN delivery_attempt a ON a.parcel_id=p.id
  LEFT JOIN parcel_status_event se ON se.parcel_id=p.id
  LEFT JOIN outbox_event oe ON oe.aggregate_id=p.id
  WHERE p.id=$parcel_id GROUP BY p.status;
")

if [ "$result" != "DELIVERED|1|7|6" ]; then
  echo "Unexpected persisted result: $result" >&2
  exit 1
fi

echo "MySQL E2E passed: $result"
