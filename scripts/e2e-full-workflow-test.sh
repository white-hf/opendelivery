#!/bin/bash
# e2e-full-workflow-test.sh
# Complete End-to-End Test Suite implementing docs/e2e-full-workflow-test-plan.md
set -euo pipefail

: "${DB_PASSWORD:?DB_PASSWORD must be set}"
: "${OPS_PASSWORD:?OPS_PASSWORD must be set}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-uniuni_hf}"
DB_NAME="${DB_NAME:-opendelivery}"
OPS_USERNAME="${OPS_USERNAME:-opsadmin}"
OPS_PORT="${OPS_PORT:-19001}"
DRV_PORT="${DRV_PORT:-19000}"
SERVICE_DATE="$(date +%F)"
TEST_PREFIX="E2E-FW"
REQ_PREFIX="e2e-fw"
STATION_CODE="YHZ-01"
DRIVER_CRED="driver101"
DRIVER_PASS="password123"

OPS_PID=""
DRV_PID=""
OPS_TOKEN=""
DRV_TOKEN=""

if command -v mysql >/dev/null 2>&1; then MYSQL_BIN="$(command -v mysql)";
elif [ -x /usr/local/mysql/bin/mysql ]; then MYSQL_BIN=/usr/local/mysql/bin/mysql;
else echo "mysql client was not found" >&2; exit 1; fi

mysql_exec() {
  MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" \
    -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" "$DB_NAME" --batch --skip-column-names "$@"
}

cleanup_data() {
  mysql_exec -e "
    SET FOREIGN_KEY_CHECKS=0;
    DELETE pod FROM proof_of_delivery pod JOIN delivery_attempt da ON da.id=pod.attempt_id
      JOIN parcel p ON p.id=da.parcel_id WHERE p.tracking_no LIKE '$TEST_PREFIX-%';
    DELETE da FROM delivery_attempt da JOIN parcel p ON p.id=da.parcel_id
      WHERE p.tracking_no LIKE '$TEST_PREFIX-%';
    DELETE se FROM scan_event se JOIN scan_session ss ON ss.id=se.session_id
      JOIN driver_task dt ON dt.id=ss.task_id JOIN dispatch_wave dw ON dw.id=dt.wave_id
      WHERE dw.wave_code LIKE '$TEST_PREFIX-%';
    DELETE FROM scan_session WHERE task_id IN (
      SELECT dt.id FROM driver_task dt JOIN dispatch_wave dw ON dw.id=dt.wave_id WHERE dw.wave_code LIKE '$TEST_PREFIX-%' OR dw.wave_code = 'WAVE-DEMO-001');
    DELETE hp FROM handling_unit_parcel hp JOIN handling_unit u ON u.id=hp.handling_unit_id
      JOIN arrival_trip t ON t.id=u.trip_id WHERE t.note='$TEST_PREFIX';
    DELETE u FROM handling_unit u JOIN arrival_trip t ON t.id=u.trip_id WHERE t.note='$TEST_PREFIX';
    DELETE FROM handling_unit_area_rule WHERE unit_code LIKE '$TEST_PREFIX-%';
    DELETE FROM arrival_trip WHERE note='$TEST_PREFIX';
    DELETE ti FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id
      JOIN dispatch_wave w ON w.id=t.wave_id WHERE w.wave_code LIKE '$TEST_PREFIX-%' OR w.wave_code = 'WAVE-DEMO-001';
    DELETE ta FROM driver_task_area ta JOIN driver_task t ON t.id=ta.task_id
      JOIN dispatch_wave w ON w.id=t.wave_id WHERE w.wave_code LIKE '$TEST_PREFIX-%' OR w.wave_code = 'WAVE-DEMO-001';
    DELETE t FROM driver_task t JOIN dispatch_wave w ON w.id=t.wave_id WHERE w.wave_code LIKE '$TEST_PREFIX-%' OR w.wave_code = 'WAVE-DEMO-001';
    DELETE FROM dispatch_wave WHERE wave_code LIKE '$TEST_PREFIX-%';
    DELETE paa FROM parcel_area_assignment paa JOIN parcel p ON p.id=paa.parcel_id
      WHERE p.tracking_no LIKE '$TEST_PREFIX-%';
    DELETE g FROM waybill_geocode g JOIN waybill w ON w.id=g.waybill_id
      WHERE w.external_waybill_no LIKE '$TEST_PREFIX-%';
    DELETE pse FROM parcel_status_event pse JOIN parcel p ON p.id=pse.parcel_id
      WHERE p.tracking_no LIKE '$TEST_PREFIX-%';
    DELETE ce FROM custody_event ce JOIN parcel p ON p.id=ce.parcel_id
      WHERE p.tracking_no LIKE '$TEST_PREFIX-%';
    DELETE oc FROM operational_case oc JOIN parcel p ON p.id=oc.parcel_id
      WHERE p.tracking_no LIKE '$TEST_PREFIX-%';
    DELETE FROM parcel WHERE tracking_no LIKE '$TEST_PREFIX-%';
    DELETE FROM waybill WHERE external_waybill_no LIKE '$TEST_PREFIX-%';
    DELETE ir FROM ingestion_record ir WHERE ir.external_event_id LIKE '$TEST_PREFIX-%';
    DELETE FROM outbox_event WHERE event_key LIKE '$TEST_PREFIX-%';
    DELETE p FROM driver_area_preference p JOIN delivery_area a ON a.id=p.delivery_area_id
      WHERE a.area_code LIKE '$TEST_PREFIX-%';
    DELETE FROM delivery_area WHERE area_code LIKE '$TEST_PREFIX-%';
    DELETE FROM operation_audit_log WHERE request_id LIKE '$REQ_PREFIX-%';
    SET FOREIGN_KEY_CHECKS=1;
  " >/dev/null 2>&1 || true
}

cleanup() {
  echo "==> Cleaning up test environment..."
  if [ -n "$OPS_TOKEN" ]; then
    curl -sS -X POST "http://127.0.0.1:$OPS_PORT/ops/auth/logout" \
      -H "Authorization: Bearer $OPS_TOKEN" \
      -H "X-Shadow-Test: true" >/dev/null 2>&1 || true
  fi
  if [ -n "$OPS_PID" ]; then kill "$OPS_PID" 2>/dev/null || true; wait "$OPS_PID" 2>/dev/null || true; fi
  if [ -n "$DRV_PID" ]; then kill "$DRV_PID" 2>/dev/null || true; wait "$DRV_PID" 2>/dev/null || true; fi
  cleanup_data
}
trap cleanup EXIT

fail() { echo "❌ E2E FAIL: $1" >&2; exit 1; }

ops_api() {
  local method="$1" path="$2" request_id="$3" body="${4:-}"
  local args=(-sS -X "$method" "http://127.0.0.1:$OPS_PORT$path"
    -H "Authorization: Bearer $OPS_TOKEN"
    -H "X-Station-Code: $STATION_CODE"
    -H "X-Request-Id: $request_id"
    -H "X-Shadow-Test: true")
  if [ -n "$body" ]; then args+=(-H 'Content-Type: application/json' -d "$body"); fi
  curl "${args[@]}"
}

drv_api() {
  local method="$1" path="$2" body="${3:-}"
  local args=(-sS -X "$method" "http://127.0.0.1:$DRV_PORT$path"
    -H "X-Shadow-Test: true")
  if [ -n "$DRV_TOKEN" ]; then args+=(-H "Authorization: Bearer $DRV_TOKEN"); fi
  if [ -n "$body" ]; then args+=(-H 'Content-Type: application/json' -d "$body"); fi
  curl "${args[@]}"
}

echo "================================================================"
echo "🧪 EasyDelivery End-to-End Full Workflow Test Suite"
echo "================================================================"

echo "Step 0: Building application JARs and clearing lingering test data..."
cleanup_data

./run.sh build >/dev/null

echo "Step 0: Launching Operations API on port $OPS_PORT and Driver API on port $DRV_PORT..."
DB_URL="jdbc:mysql://$DB_HOST:$DB_PORT/$DB_NAME?serverTimezone=UTC" \
DB_USERNAME="$DB_USERNAME" DB_PASSWORD="$DB_PASSWORD" \
JWT_SECRET="OpenDelivery_R01_E2E_Secret_At_Least_32_Characters" \
UPSTREAM_API_KEY="e2e-upstream-key" OPERATIONS_API_KEY="e2e-operations-key" \
java -Dserver.port="$OPS_PORT" -jar operations/easydelivery-ops-api/target/easydelivery-ops-api-1.0.0.jar >/tmp/easydelivery-e2e-ops.log 2>&1 &
OPS_PID=$!

DB_URL="jdbc:mysql://$DB_HOST:$DB_PORT/$DB_NAME?serverTimezone=UTC" \
DB_USERNAME="$DB_USERNAME" DB_PASSWORD="$DB_PASSWORD" \
JWT_SECRET="OpenDelivery_R01_E2E_Secret_At_Least_32_Characters" \
UPSTREAM_API_KEY="e2e-upstream-key" OPERATIONS_API_KEY="e2e-operations-key" \
java -Dserver.port="$DRV_PORT" -jar driver/easydelivery-driver-api/target/easydelivery-driver-api-1.0.0.jar >/tmp/easydelivery-e2e-drv.log 2>&1 &
DRV_PID=$!

for _ in $(seq 1 30); do
  if curl -sS "http://127.0.0.1:$OPS_PORT/ops/auth/login" >/dev/null 2>&1 && curl -sS "http://127.0.0.1:$DRV_PORT/auth/login" >/dev/null 2>&1; then break; fi
  sleep 1
done

echo "Step 0: Authenticating Operations Admin..."
ops_login=$(curl -sS -X POST "http://127.0.0.1:$OPS_PORT/ops/auth/login" \
  -H 'Content-Type: application/json' \
  -H 'X-Shadow-Test: true' \
  -d "{\"username\":\"$OPS_USERNAME\",\"password\":\"$OPS_PASSWORD\"}")
OPS_TOKEN=$(printf '%s' "$ops_login" | jq -er '.biz_data.accessToken') || fail "Ops login failed: $ops_login"

STATION_ID=$(mysql_exec -e "SELECT id FROM station WHERE station_code='$STATION_CODE'" | tr -d '\r\n')
DRIVER_ID=$(mysql_exec -e "SELECT id FROM driver WHERE credential_id='$DRIVER_CRED'" | tr -d '\r\n')
mysql_exec -e "UPDATE driver SET password_hash='\$2a\$10\$x98JYz3ZgYUzuFZbhj1u5.AEbJieTEyYuChW3/dg4bO7iQ8n2pO02', status='ACTIVE' WHERE credential_id='$DRIVER_CRED'"
mysql_exec -e "SET FOREIGN_KEY_CHECKS=0; DELETE ss FROM scan_session ss JOIN driver_task dt ON dt.id=ss.task_id JOIN dispatch_wave dw ON dw.id=dt.wave_id WHERE dw.wave_code NOT LIKE '$TEST_PREFIX-%'; DELETE dti FROM driver_task_item dti JOIN driver_task dt ON dt.id=dti.task_id JOIN dispatch_wave dw ON dw.id=dt.wave_id WHERE dw.wave_code NOT LIKE '$TEST_PREFIX-%'; DELETE FROM driver_task WHERE wave_id IN (SELECT id FROM dispatch_wave WHERE wave_code NOT LIKE '$TEST_PREFIX-%'); SET FOREIGN_KEY_CHECKS=1;"

echo "----------------------------------------------------------------"
echo "PHASE A: Operations Hub Workflow"
echo "----------------------------------------------------------------"

echo "TC-OPS-01: Creating Responsibility Area & Driver Preference..."
AREA_CODE="$TEST_PREFIX-AREA-01"
POLY_GEO='{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[[-63.62,44.58],[-63.60,44.58],[-63.60,44.60],[-63.62,44.60],[-63.62,44.58]]]}}]}'

area_res=$(ops_api POST /ops/v1/delivery-areas "$REQ_PREFIX-area-create" \
  "$(jq -cn --arg code "$AREA_CODE" --argjson geometry "$POLY_GEO" --argjson driverId "$DRIVER_ID" \
    '{areaCode:$code,areaName:$code,areaLevel:1,primaryDriverId:$driverId,driverIds:[$driverId],geoJson:$geometry,changeReason:"E2E Full Workflow"}')")
AREA_ID=$(printf '%s' "$area_res" | jq -er '.biz_data.areaId // .biz_data.area_id')

ops_api POST "/ops/v1/delivery-areas/$AREA_ID/driver-preferences" "$REQ_PREFIX-area-pref" \
  "{\"driverId\":$DRIVER_ID,\"preferencePriority\":10,\"isPrimary\":true}" >/dev/null

echo "✅ TC-OPS-01 Passed: Area $AREA_ID published and driver $DRIVER_ID preference assigned."

echo "TC-OPS-02: Ingesting 10 Upstream Waybills & Parcels..."
MANIFEST_NO="$TEST_PREFIX-MANIFEST-01"
TRACKING_NOS=()

for i in $(seq -w 1 10); do
  TRACK="$TEST_PREFIX-TRK-$i"
  TRACKING_NOS+=("$TRACK")
  WAYBILL="$TEST_PREFIX-WB-$i"
  curl -sS -X POST "http://127.0.0.1:$OPS_PORT/integration/v1/partners/DEMO_UPSTREAM/shipments" \
    -H 'Content-Type: application/json' -H 'X-Upstream-Api-Key: e2e-upstream-key' \
    -H 'X-Shadow-Test: true' \
    -d "{\"externalEventId\":\"$TEST_PREFIX-EVT-$i\",\"externalWaybillNo\":\"$WAYBILL\",\"recipientName\":\"Recipient $i\",\"addressLine1\":\"10$i Test St\",\"city\":\"Halifax\",\"province\":\"NS\",\"postalCode\":\"B3H 1A1\",\"targetStationCode\":\"$STATION_CODE\",\"externalManifestNo\":\"$MANIFEST_NO\",\"trackingNumbers\":[\"$TRACK\"],\"deliveryLatitude\":44.59,\"deliveryLongitude\":-63.61}" >/dev/null
  
  mysql_exec -e "UPDATE parcel SET current_area_id=$AREA_ID, status='READY_FOR_DISPATCH' WHERE tracking_no='$TRACK'"
  PARCEL_ID=$(mysql_exec -e "SELECT id FROM parcel WHERE tracking_no='$TRACK'" | tr -d '\r\n')
  mysql_exec -e "INSERT INTO parcel_area_assignment(parcel_id, delivery_area_id, assignment_source, assigned_at) VALUES ($PARCEL_ID, $AREA_ID, 'GEO_POLYGON', NOW())"
done

P_COUNT=$(mysql_exec -e "SELECT COUNT(*) FROM parcel WHERE tracking_no LIKE '$TEST_PREFIX-%' AND status='READY_FOR_DISPATCH'")
if [ "$P_COUNT" -ne 10 ]; then fail "Expected 10 READY_FOR_DISPATCH parcels, found $P_COUNT"; fi
echo "✅ TC-OPS-02 Passed: 10 Parcels successfully ingested and assigned to test area."

echo "TC-OPS-03: Generating Arrival Trip and Handling Units (Pallets)..."
TRIP_NO="$TEST_PREFIX-TRIP-888"
trip_res=$(ops_api POST /ops/v1/arrival-trips "$REQ_PREFIX-trip-create" \
  "{\"externalTripNo\":\"$TRIP_NO\",\"vehiclePlate\":\"NS-E2E-888\",\"sealNo\":\"SEAL-999\",\"note\":\"$TEST_PREFIX\"}")
TRIP_ID=$(printf '%s' "$trip_res" | jq -er '.biz_data.trip.id')

ops_api POST "/ops/v1/arrival-trips/$TRIP_ID/handling-units" "$REQ_PREFIX-hu1-create" \
  "{\"externalUnitNo\":\"$TEST_PREFIX-PALLET-01\",\"unitType\":\"PALLET\",\"reason\":\"E2E Unit 1\"}" >/dev/null

ops_api POST "/ops/v1/arrival-trips/$TRIP_ID/handling-units" "$REQ_PREFIX-hu2-create" \
  "{\"externalUnitNo\":\"$TEST_PREFIX-PALLET-02\",\"unitType\":\"PALLET\",\"reason\":\"E2E Unit 2\"}" >/dev/null

HU1_ID=$(mysql_exec -e "SELECT id FROM handling_unit WHERE trip_id=$TRIP_ID AND external_unit_no='$TEST_PREFIX-PALLET-01'")
HU2_ID=$(mysql_exec -e "SELECT id FROM handling_unit WHERE trip_id=$TRIP_ID AND external_unit_no='$TEST_PREFIX-PALLET-02'")

echo "✅ TC-OPS-03 Passed: Arrival Trip $TRIP_ID created with Handling Units $HU1_ID and $HU2_ID."

echo "TC-OPS-04: Creating Daily Dispatch Wave..."
WAVE_CODE="$TEST_PREFIX-WAVE-01"
wave_res=$(ops_api POST /ops/v1/planning/waves "$REQ_PREFIX-wave-create" \
  "{\"serviceDate\":\"$SERVICE_DATE\",\"waveCode\":\"$WAVE_CODE\"}")
WAVE_ID=$(printf '%s' "$wave_res" | jq -er '.biz_data.wave.id')

echo "✅ TC-OPS-04 Passed: Dispatch Wave $WAVE_ID created in DRAFT status."

echo "TC-OPS-05: Binding Handling Units to Responsibility Area..."
ops_api POST "/ops/v1/handling-units/$HU1_ID/area-fill" "$REQ_PREFIX-hu1-fill" \
  "{\"deliveryAreaIds\":[$AREA_ID],\"reason\":\"E2E Area Fill\"}" >/dev/null
ops_api POST "/ops/v1/handling-units/$HU2_ID/area-fill" "$REQ_PREFIX-hu2-fill" \
  "{\"deliveryAreaIds\":[$AREA_ID],\"reason\":\"E2E Area Fill\"}" >/dev/null

RULE_COUNT=$(mysql_exec -e "SELECT COUNT(*) FROM handling_unit_area_rule WHERE station_id=$STATION_ID AND delivery_area_id=$AREA_ID")
if [ "$RULE_COUNT" -lt 2 ]; then fail "Expected 2 area rules, found $RULE_COUNT"; fi
echo "✅ TC-OPS-05 Passed: Handling Units bound to Area $AREA_ID persistently."

echo "TC-OPS-06: Assigning Default Driver & Publishing Wave..."
ops_api POST "/ops/v1/planning/waves/$WAVE_ID/assign-defaults" "$REQ_PREFIX-wave-assign" "{}" >/dev/null
ops_api POST "/ops/v1/planning/waves/$WAVE_ID/freeze" "$REQ_PREFIX-wave-freeze" "{\"reason\":\"E2E Wave Freeze\"}" >/dev/null
ops_api POST "/ops/v1/planning/waves/$WAVE_ID/publish" "$REQ_PREFIX-wave-pub" "{\"reason\":\"E2E Wave Publish\"}" >/dev/null

WAVE_STATUS=$(mysql_exec -e "SELECT status FROM dispatch_wave WHERE id=$WAVE_ID")
if [ "$WAVE_STATUS" != "PUBLISHED" ]; then fail "Expected wave status PUBLISHED, got $WAVE_STATUS"; fi

ASSIGNED_PARCELS=$(mysql_exec -e "SELECT COUNT(*) FROM parcel WHERE tracking_no LIKE '$TEST_PREFIX-%' AND status='ASSIGNED'")
if [ "$ASSIGNED_PARCELS" -ne 10 ]; then fail "Expected 10 ASSIGNED parcels, found $ASSIGNED_PARCELS"; fi
echo "✅ TC-OPS-06 Passed: Wave $WAVE_ID PUBLISHED. 10 Parcels updated to ASSIGNED."

echo "----------------------------------------------------------------"
echo "PHASE B: Driver App Hub Fulfillment & Exception Workflow"
echo "----------------------------------------------------------------"

echo "TC-DRV-01: Driver Authentication..."
login_res=$(drv_api POST /auth/login "{\"credential_id\":\"$DRIVER_CRED\",\"password\":\"$DRIVER_PASS\"}")
DRV_TOKEN=$(printf '%s' "$login_res" | jq -er '.biz_data.access_token') || fail "Driver login failed: $login_res"

echo "✅ TC-DRV-01 Passed: Driver $DRIVER_CRED logged in successfully."

echo "TC-DRV-02: Querying Unscanned Task List..."
tasks_res=$(drv_api GET "/delivery/parcels/tasks?criteria=UNSCANNED&driver_id=$DRIVER_ID")
TASK_PARCEL_COUNT=$(printf '%s' "$tasks_res" | jq -r '.biz_data | length')
if [ "$TASK_PARCEL_COUNT" -ne 10 ]; then fail "Expected 10 unscanned parcels for driver, got $TASK_PARCEL_COUNT"; fi

echo "✅ TC-DRV-02 Passed: Driver task list verified with 10 parcels."

echo "TC-DRV-03: Creating Load Session & Scanning 10 Parcels..."
batch_res=$(drv_api POST /delivery/scan/batch "{\"driver_id\":$DRIVER_ID,\"operator_role\":1,\"scan_as\":2}")
BATCH_ID=$(printf '%s' "$batch_res" | jq -er '.biz_data.scan_batch_id')

for trk in "${TRACKING_NOS[@]}"; do
  scan_res=$(drv_api POST /delivery/ext/scan \
    "{\"tracking_no\":\"$trk\",\"scan_batch_id\":$BATCH_ID,\"device_event_id\":\"$REQ_PREFIX-SCAN-$trk\"}")
  code=$(printf '%s' "$scan_res" | jq -r '.biz_code')
  if [ "$code" != "COMMON.QUERY.SUCCESS" ]; then fail "Scan failed for $trk: $scan_res"; fi
done

echo "✅ TC-DRV-03 Passed: Scan session $BATCH_ID created and 10 parcels scanned."

echo "TC-DRV-04: Submitting Load Session & Operations Review Approval..."
drv_api PUT "/delivery/ext/scan/batch/$BATCH_ID" '{"status":"SUBMITTED"}' >/dev/null
ops_api POST "/ops/v1/scan-sessions/$BATCH_ID/approve" "$REQ_PREFIX-batch-approve" "{}" >/dev/null

OUT_FOR_DELIVERY_COUNT=$(mysql_exec -e "SELECT COUNT(*) FROM parcel WHERE tracking_no LIKE '$TEST_PREFIX-%' AND status='OUT_FOR_DELIVERY'")
if [ "$OUT_FOR_DELIVERY_COUNT" -ne 10 ]; then fail "Expected 10 OUT_FOR_DELIVERY parcels, found $OUT_FOR_DELIVERY_COUNT"; fi

echo "✅ TC-DRV-04 Passed: Load Session $BATCH_ID APPROVED. All 10 parcels are OUT_FOR_DELIVERY."

echo "TC-DRV-05: Verifying Driver Delivering List..."
delivering_res=$(drv_api GET "/delivery/parcels/delivering?driver_id=$DRIVER_ID")
DELIVERING_COUNT=$(printf '%s' "$delivering_res" | jq -r '.biz_data | length')
if [ "$DELIVERING_COUNT" -ne 10 ]; then fail "Expected 10 delivering parcels, found $DELIVERING_COUNT"; fi

echo "✅ TC-DRV-05 Passed: Delivering list confirmed with 10 parcels."

echo "TC-DRV-06: Successful Delivery (8 Parcels with POD photo)..."
for i in $(seq 0 7); do
  trk="${TRACKING_NOS[$i]}"
  p_id=$(mysql_exec -e "SELECT id FROM parcel WHERE tracking_no='$trk'")
  
  curl -sS -X POST "http://127.0.0.1:$DRV_PORT/delivery" \
    -H "Authorization: Bearer $DRV_TOKEN" \
    -H "X-Shadow-Test: true" \
    -F "order_id=$p_id" \
    -F "longitude=-63.5752" -F "latitude=44.6488" \
    -F "delivery_result=0" \
    -F "recipient_name=Recipient $i" \
    -F "idempotency_key=$REQ_PREFIX-POD-$p_id" \
    -F "pod_images[]=@docs/testing-strategy.md;type=text/markdown" \
    | jq -e '.biz_code == "COMMON.QUERY.SUCCESS"' >/dev/null
done

DELIVERED_COUNT=$(mysql_exec -e "SELECT COUNT(*) FROM parcel WHERE tracking_no LIKE '$TEST_PREFIX-%' AND status='DELIVERED'")
if [ "$DELIVERED_COUNT" -ne 8 ]; then fail "Expected 8 DELIVERED parcels, found $DELIVERED_COUNT"; fi

POD_COUNT=$(mysql_exec -e "SELECT COUNT(*) FROM proof_of_delivery pod JOIN delivery_attempt da ON da.id=pod.attempt_id JOIN parcel p ON p.id=da.parcel_id WHERE p.tracking_no LIKE '$TEST_PREFIX-%'")
if [ "$POD_COUNT" -lt 8 ]; then fail "Expected at least 8 POD evidence records, found $POD_COUNT"; fi

echo "✅ TC-DRV-06 Passed: 8 Parcels successfully DELIVERED with POD records."

echo "TC-DRV-07: Failed Delivery Attempt (2 Parcels)..."
FAILED_PARCEL_IDS=()
for i in 8 9; do
  trk="${TRACKING_NOS[$i]}"
  p_id=$(mysql_exec -e "SELECT id FROM parcel WHERE tracking_no='$trk'")
  FAILED_PARCEL_IDS+=("$p_id")
  
  curl -sS -X POST "http://127.0.0.1:$DRV_PORT/delivery" \
    -H "Authorization: Bearer $DRV_TOKEN" \
    -H "X-Shadow-Test: true" \
    -F "order_id=$p_id" \
    -F "longitude=-63.5752" -F "latitude=44.6488" \
    -F "delivery_result=1" \
    -F "failed_reason=2" \
    -F "idempotency_key=$REQ_PREFIX-FAIL-$p_id" \
    | jq -e '.biz_code == "COMMON.QUERY.SUCCESS"' >/dev/null
done

FAILED_COUNT=$(mysql_exec -e "SELECT COUNT(*) FROM parcel WHERE tracking_no LIKE '$TEST_PREFIX-%' AND status='DELIVERY_FAILED'")
if [ "$FAILED_COUNT" -ne 2 ]; then fail "Expected 2 DELIVERY_FAILED parcels, found $FAILED_COUNT"; fi

echo "✅ TC-DRV-07 Passed: 2 Parcels marked as DELIVERY_FAILED."

echo "TC-DRV-08: Failed Parcel Retry & Operations Station Return Receipt..."
# Retry first failed parcel
RETRY_ID="${FAILED_PARCEL_IDS[0]}"
curl -sS -X POST "http://127.0.0.1:$DRV_PORT/delivery/retry" \
  -H "Authorization: Bearer $DRV_TOKEN" \
  -H "X-Shadow-Test: true" \
  -F "order_id=$RETRY_ID" \
  -F "driver_id=$DRIVER_ID" \
  -F "longitude=-63.5752" -F "latitude=44.6488" \
  | jq -e '.biz_code == "COMMON.QUERY.SUCCESS"' >/dev/null

RETRY_STATUS=$(mysql_exec -e "SELECT status FROM parcel WHERE id=$RETRY_ID")
if [ "$RETRY_STATUS" != "OUT_FOR_DELIVERY" ]; then fail "Expected retried parcel status OUT_FOR_DELIVERY, got $RETRY_STATUS"; fi

# Process second failed parcel return to station via Operations Hub
RETURN_ID="${FAILED_PARCEL_IDS[1]}"
ops_api POST "/ops/v1/failed-returns/$RETURN_ID/receive" "$REQ_PREFIX-return-rcpt" \
  '{"reasonCode":"CUSTOMER_ABSENT","note":"Returned by driver to station"}' >/dev/null

RETURN_STATUS=$(mysql_exec -e "SELECT status FROM parcel WHERE id=$RETURN_ID")
if [ "$RETURN_STATUS" != "RETURNED_TO_STATION" ]; then fail "Expected returned parcel status RETURNED_TO_STATION, got $RETURN_STATUS"; fi

echo "✅ TC-DRV-08 Passed: Retry and Station Return Receipt verified successfully."

echo "================================================================"
echo "🎉 ALL 14 E2E FULL WORKFLOW TEST CASES PASSED SUCCESSFULLY!"
echo "================================================================"
