#!/bin/bash
# R03-C arrival-batch E2E: three stations, auto batch numbers, default units,
# ingestion auto area-match, area fill, upstream unit auto-link (both directions),
# cross-driver / cross-unit coverage, aggregate=detail, isolation, rejections.
set -euo pipefail

: "${DB_PASSWORD:?DB_PASSWORD must be set}"
: "${OPS_PASSWORD:?OPS_PASSWORD must be set}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-uniuni_hf}"
DB_NAME="${DB_NAME:-opendelivery}"
OPS_USERNAME="${OPS_USERNAME:-opsadmin}"
APP_PORT="${APP_PORT:-19003}"
APP_JAR="${APP_JAR:-easydelivery-app/target/easydelivery-app-1.0.0.jar}"
SERVICE_DATE="$(date +%F)"
TEST_PREFIX="E2E-R03C"
REQ_PREFIX="e2e-r03c"
APP_PID=""
ACCESS_TOKEN=""

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
    DELETE hp FROM handling_unit_parcel hp JOIN handling_unit u ON u.id=hp.handling_unit_id
      JOIN arrival_trip t ON t.id=u.trip_id WHERE t.note='$TEST_PREFIX';
    DELETE u FROM handling_unit u JOIN arrival_trip t ON t.id=u.trip_id WHERE t.note='$TEST_PREFIX';
    DELETE FROM arrival_trip WHERE note='$TEST_PREFIX';
    DELETE ti FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id
      JOIN dispatch_wave w ON w.id=t.wave_id WHERE w.wave_code LIKE '$TEST_PREFIX-%';
    DELETE ta FROM driver_task_area ta JOIN driver_task t ON t.id=ta.task_id
      JOIN dispatch_wave w ON w.id=t.wave_id WHERE w.wave_code LIKE '$TEST_PREFIX-%';
    DELETE t FROM driver_task t JOIN dispatch_wave w ON w.id=t.wave_id WHERE w.wave_code LIKE '$TEST_PREFIX-%';
    DELETE FROM dispatch_wave WHERE wave_code LIKE '$TEST_PREFIX-%';
    DELETE paa FROM parcel_area_assignment paa JOIN parcel p ON p.id=paa.parcel_id
      WHERE p.tracking_no LIKE '$TEST_PREFIX-%';
    DELETE g FROM waybill_geocode g JOIN waybill w ON w.id=g.waybill_id
      WHERE w.external_waybill_no LIKE '$TEST_PREFIX-%';
    DELETE pse FROM parcel_status_event pse JOIN parcel p ON p.id=pse.parcel_id
      WHERE p.tracking_no LIKE '$TEST_PREFIX-%';
    DELETE oc FROM operational_case oc JOIN parcel p ON p.id=oc.parcel_id
      WHERE p.tracking_no LIKE '$TEST_PREFIX-%';
    DELETE FROM parcel WHERE tracking_no LIKE '$TEST_PREFIX-%';
    DELETE FROM waybill WHERE external_waybill_no LIKE '$TEST_PREFIX-%';
    DELETE ir FROM ingestion_record ir WHERE ir.external_event_id LIKE '$TEST_PREFIX-%';
    DELETE ib FROM ingestion_batch ib LEFT JOIN ingestion_record ir ON ir.batch_id=ib.id
      WHERE ir.id IS NULL AND ib.started_at > CURRENT_TIMESTAMP - INTERVAL 1 DAY;
    DELETE FROM driver_shift WHERE driver_id IN (SELECT id FROM driver WHERE credential_id LIKE 'r03c.e2e.%');
    DELETE FROM driver WHERE credential_id LIKE 'r03c.e2e.%';
    DELETE v FROM delivery_area_version v JOIN delivery_area a ON a.id=v.delivery_area_id
      WHERE a.area_code LIKE '$TEST_PREFIX-%';
    DELETE FROM delivery_area WHERE area_code LIKE '$TEST_PREFIX-%';
    DELETE FROM operation_audit_log WHERE request_id LIKE '$REQ_PREFIX-%';
    SET FOREIGN_KEY_CHECKS=1;
  " >/dev/null 2>&1 || true
}

cleanup() {
  if [ -n "$ACCESS_TOKEN" ]; then
    curl -sS -X POST "http://127.0.0.1:$APP_PORT/ops/auth/logout" \
      -H "Authorization: Bearer $ACCESS_TOKEN" >/dev/null 2>&1 || true
  fi
  if [ -n "$APP_PID" ]; then kill "$APP_PID" 2>/dev/null || true; wait "$APP_PID" 2>/dev/null || true; fi
  cleanup_data 2>/dev/null || true
}
trap cleanup EXIT

fail() { echo "FAIL: $1" >&2; exit 1; }

api() {
  local method="$1" path="$2" station="$3" request_id="$4" body="${5:-}"
  local args=(-sS -X "$method" "http://127.0.0.1:$APP_PORT$path"
    -H "Authorization: Bearer $ACCESS_TOKEN"
    -H "X-Station-Code: $station"
    -H "X-Request-Id: $request_id")
  if [ -n "$body" ]; then args+=(-H 'Content-Type: application/json' -d "$body"); fi
  curl "${args[@]}"
}

ingest() { # $1 payload
  curl -sS -X POST "http://127.0.0.1:$APP_PORT/integration/v1/partners/DEMO_UPSTREAM/shipments" \
    -H 'Content-Type: application/json' -H 'X-Upstream-Api-Key: e2e-upstream-key' -d "$1"
}

station_params() {
  case "$1" in
    YHZ-01) CITY=Halifax; PROV=NS; POSTAL='B3H 1A1'; LNG_A=-63.61; LAT_A=44.59; LNG_B=-63.59; LAT_B=44.59
      NEXT_CITY=Toronto; NEXT_PROV=ON; NEXT_POSTAL='M5H 2N2'; NEXT_STATION=YYZ-01
      POLY_A='{"type":"Polygon","coordinates":[[[-63.62,44.58],[-63.60,44.58],[-63.60,44.60],[-63.62,44.60],[-63.62,44.58]]]}'
      POLY_B='{"type":"Polygon","coordinates":[[[-63.60,44.58],[-63.58,44.58],[-63.58,44.60],[-63.60,44.60],[-63.60,44.58]]]}';;
    YYZ-01) CITY=Toronto; PROV=ON; POSTAL='M5H 2N2'; LNG_A=-79.41; LAT_A=43.59; LNG_B=-79.39; LAT_B=43.59
      NEXT_CITY=Vancouver; NEXT_PROV=BC; NEXT_POSTAL='V6B 1A1'; NEXT_STATION=YVR-01
      POLY_A='{"type":"Polygon","coordinates":[[[-79.42,43.58],[-79.40,43.58],[-79.40,43.60],[-79.42,43.60],[-79.42,43.58]]]}'
      POLY_B='{"type":"Polygon","coordinates":[[[-79.40,43.58],[-79.38,43.58],[-79.38,43.60],[-79.40,43.60],[-79.40,43.58]]]}';;
    YVR-01) CITY=Vancouver; PROV=BC; POSTAL='V6B 1A1'; LNG_A=-123.13; LAT_A=49.23; LNG_B=-123.11; LAT_B=49.23
      NEXT_CITY=Halifax; NEXT_PROV=NS; NEXT_POSTAL='B3H 1A1'; NEXT_STATION=YHZ-01
      POLY_A='{"type":"Polygon","coordinates":[[[-123.14,49.22],[-123.12,49.22],[-123.12,49.24],[-123.14,49.24],[-123.14,49.22]]]}'
      POLY_B='{"type":"Polygon","coordinates":[[[-123.12,49.22],[-123.10,49.22],[-123.10,49.24],[-123.12,49.24],[-123.12,49.22]]]}';;
    *) fail "unknown station $1";;
  esac
}

create_area() { # $1 station $2 suffix $3 polygon -> echoes "area_id|version_id"
  local station="$1" suffix="$2" poly="$3"
  local code="$TEST_PREFIX-$station-$suffix" created area_id version_id
  created=$(api POST /ops/v1/delivery-areas "$station" "$REQ_PREFIX-$1-$suffix-create" \
    "$(jq -cn --arg code "$code" --argjson geometry "$poly" \
      '{areaCode:$code,areaName:$code,areaLevel:1,geoJson:$geometry,changeReason:"R03-C e2e"}')")
  area_id=$(printf '%s' "$created" | jq -er '.biz_data.areaId // .biz_data.area_id') || return 1
  version_id=$(printf '%s' "$created" | jq -er '.biz_data.versionId // .biz_data.version_id') || return 1
  api POST "/ops/v1/delivery-areas/$area_id/versions/$version_id/validate" "$station" "$REQ_PREFIX-$1-$suffix-val" \
    | jq -e '.biz_data.valid == true' >/dev/null || return 1
  api POST "/ops/v1/delivery-areas/$area_id/versions/$version_id/publish" "$station" "$REQ_PREFIX-$1-$suffix-pub" \
    '{"reason":"R03-C e2e"}' | jq -e '.biz_data.status == "PUBLISHED"' >/dev/null || return 1
  printf '%s|%s\n' "$area_id" "$version_id"
}

seed_drivers() { # $1 station
  mysql_exec -e "
    INSERT INTO driver (home_station_id,credential_id,password_hash,driver_name,status)
    SELECT s.id, CONCAT('r03c.e2e.',LOWER(s.station_code),'.$2'), 'x', 'R03C E2E Driver $2', 'ACTIVE'
    FROM station s WHERE s.station_code='$1'
      AND NOT EXISTS (SELECT 1 FROM driver d WHERE d.credential_id=CONCAT('r03c.e2e.',LOWER(s.station_code),'.$2'));" >/dev/null
  mysql_exec -e "SELECT id FROM driver WHERE credential_id=CONCAT('r03c.e2e.',LOWER('$1'),'.$2')"
}

run_station_flow() { # $1 station
  local ST="$1"
  station_params "$ST"
  local P="$TEST_PREFIX-$ST"
  local A B A_ID A_VID B_ID B_VID D1 D2
  A=$(create_area "$ST" A "$POLY_A") || fail "$ST area A publish"; A_ID="${A%%|*}"; A_VID="${A##*|}"
  B=$(create_area "$ST" B "$POLY_B") || fail "$ST area B publish"; B_ID="${B%%|*}"; B_VID="${B##*|}"
  D1=$(seed_drivers "$ST" 1); D2=$(seed_drivers "$ST" 2)
  api PUT /ops/v1/planning/shifts "$ST" "$REQ_PREFIX-$ST-shift1" \
    "$(jq -cn --argjson d "$D1" --arg date "$SERVICE_DATE" '{driverId:$d,serviceDate:$date,availabilityStatus:"AVAILABLE",parcelCapacity:100,note:"e2e"}')" >/dev/null
  api PUT /ops/v1/planning/shifts "$ST" "$REQ_PREFIX-$ST-shift2" \
    "$(jq -cn --argjson d "$D2" --arg date "$SERVICE_DATE" '{driverId:$d,serviceDate:$date,availabilityStatus:"AVAILABLE",parcelCapacity:100,note:"e2e"}')" >/dev/null

  # Upstream pushes: 4 parcels geocoded in area A, 2 in area B, UP1 with unit label L2, X1 labelled L2 but routed away.
  local L2="$P-UPL2" L3="$P-UPL3"
  local i trackings
  trackings=$(jq -cn '[1,2,3,4] | map("'"$P"'-A\(.)")')
  ingest "$(jq -cn --arg p "$P" --arg city "$CITY" --arg prov "$PROV" --arg pc "$POSTAL" \
      --argjson lng "$LNG_A" --argjson lat "$LAT_A" --argjson tn "$trackings" \
      '{externalEventId:"E2E-R03C-EVT-\($p)-A",externalWaybillNo:"\($p)-WBA",recipientName:"E2E",addressLine1:"1 A St",city:$city,province:$prov,postalCode:$pc,trackingNumbers:$tn,deliveryLatitude:$lat,deliveryLongitude:$lng}')" \
    | jq -e --arg st "$ST" '.biz_data.routingStatus == "ROUTED" and .biz_data.stationCode == $st' >/dev/null || fail "$ST ingest A"
  trackings=$(jq -cn '[1,2] | map("'"$P"'-B\(.)")')
  ingest "$(jq -cn --arg p "$P" --arg city "$CITY" --arg prov "$PROV" --arg pc "$POSTAL" \
      --argjson lng "$LNG_B" --argjson lat "$LAT_B" --argjson tn "$trackings" \
      '{externalEventId:"E2E-R03C-EVT-\($p)-B",externalWaybillNo:"\($p)-WBB",recipientName:"E2E",addressLine1:"1 B St",city:$city,province:$prov,postalCode:$pc,trackingNumbers:$tn,deliveryLatitude:$lat,deliveryLongitude:$lng}')" \
    | jq -e --arg st "$ST" '.biz_data.routingStatus == "ROUTED" and .biz_data.stationCode == $st' >/dev/null || fail "$ST ingest B"
  ingest "$(jq -cn --arg p "$P" --arg city "$CITY" --arg prov "$PROV" --arg pc "$POSTAL" --arg l2 "$L2" \
      '{externalEventId:"E2E-R03C-EVT-\($p)-UP1",externalWaybillNo:"\($p)-WBUP1",recipientName:"E2E",addressLine1:"1 C St",city:$city,province:$prov,postalCode:$pc,trackingNumbers:["\($p)-UP1"],handlingUnits:[{externalUnitNo:$l2,unitType:"PALLET",trackingNumbers:["\($p)-UP1"]}]}')" \
    | jq -e '.biz_data.routingStatus == "ROUTED"' >/dev/null || fail "$ST ingest UP1"
  ingest "$(jq -cn --arg p "$P" --arg city "$NEXT_CITY" --arg prov "$NEXT_PROV" --arg pc "$NEXT_POSTAL" --arg l2 "$L2" \
      '{externalEventId:"E2E-R03C-EVT-\($p)-X1",externalWaybillNo:"\($p)-WBX1",recipientName:"E2E",addressLine1:"1 X St",city:$city,province:$prov,postalCode:$pc,trackingNumbers:["\($p)-X1"],handlingUnits:[{externalUnitNo:$l2,trackingNumbers:["\($p)-X1"]}]}')" \
    | jq -e --arg st "$NEXT_STATION" '.biz_data.routingStatus == "ROUTED" and .biz_data.stationCode == $st' >/dev/null || fail "$ST ingest X1"

  # Auto batch number + default units.
  local BATCH1 BATCH2 TRIP U01
  BATCH1=$(api POST /ops/v1/arrival-trips "$ST" "$REQ_PREFIX-$ST-batch1" "{\"note\":\"$TEST_PREFIX\"}")
  printf '%s' "$BATCH1" | jq -e --arg st "$ST" \
    '.biz_data.trip.external_trip_no | test("^" + $st + "-[0-9]{8}-[0-9]{2}$")' >/dev/null || fail "$ST batch1 number"
  printf '%s' "$BATCH1" | jq -e \
    '(.biz_data.units | length) == 10 and ([.biz_data.units[].unit_type] | unique == ["PALLET"])' >/dev/null || fail "$ST default units"
  TRIP=$(printf '%s' "$BATCH1" | jq -er '.biz_data.trip.id')
  U01=$(printf '%s' "$BATCH1" | jq -er '.biz_data.units[0].id')
  printf '%s' "$BATCH1" | jq -e '.biz_data.units[0].external_unit_no | endswith("-U01")' >/dev/null || fail "$ST U01 label"
  BATCH2=$(api POST /ops/v1/arrival-trips "$ST" "$REQ_PREFIX-$ST-batch2" "{\"note\":\"$TEST_PREFIX\"}")
  printf '%s\n%s' "$BATCH1" "$BATCH2" | jq -se \
    '(.[0].biz_data.trip.external_trip_no | capture("-(?<s>[0-9]{2})$").s | tonumber) as $a
     | (.[1].biz_data.trip.external_trip_no | capture("-(?<s>[0-9]{2})$").s | tonumber) == ($a + 1)' \
    >/dev/null || fail "$ST batch2 sequence"

  # Area fill: U01 gets every parcel of areas A+B.
  local DETAIL
  DETAIL=$(api POST "/ops/v1/handling-units/$U01/area-fill" "$ST" "$REQ_PREFIX-$ST-fill" \
    "$(jq -cn --argjson a "$A_VID" --argjson b "$B_VID" '{areaVersionIds:[$a,$b],reason:"e2e area fill"}')")
  printf '%s' "$DETAIL" | jq -e --argjson u "$U01" \
    '.biz_data.units[] | select(.id == $u) | .linked_piece_count == 6' >/dev/null || fail "$ST area fill count"

  # Upstream auto-link, parcels declared before the unit exists (L2), and unit created before parcels arrive (L3).
  DETAIL=$(api POST "/ops/v1/arrival-trips/$TRIP/handling-units" "$ST" "$REQ_PREFIX-$ST-l2" \
    "$(jq -cn --arg l2 "$L2" '{externalUnitNo:$l2,unitType:"PALLET",reason:"e2e upstream unit"}')")
  printf '%s' "$DETAIL" | jq -e --arg l2 "$L2" \
    '.biz_data.units[] | select(.external_unit_no == $l2) | .linked_piece_count == 1' >/dev/null || fail "$ST L2 auto-link"
  api POST "/ops/v1/arrival-trips/$TRIP/handling-units" "$ST" "$REQ_PREFIX-$ST-l3" \
    "$(jq -cn --arg l3 "$L3" '{externalUnitNo:$l3,unitType:"CAGE",reason:"e2e empty unit"}')" >/dev/null
  ingest "$(jq -cn --arg p "$P" --arg city "$CITY" --arg prov "$PROV" --arg pc "$POSTAL" --arg l3 "$L3" \
      '{externalEventId:"E2E-R03C-EVT-\($p)-UP2",externalWaybillNo:"\($p)-WBUP2",recipientName:"E2E",addressLine1:"1 D St",city:$city,province:$prov,postalCode:$pc,trackingNumbers:["\($p)-UP2"],handlingUnits:[{externalUnitNo:$l3,trackingNumbers:["\($p)-UP2"]}]}')" >/dev/null
  DETAIL=$(api GET "/ops/v1/arrival-trips/$TRIP" "$ST" "$REQ_PREFIX-$ST-detail1")
  printf '%s' "$DETAIL" | jq -e --arg l3 "$L3" \
    '.biz_data.units[] | select(.external_unit_no == $l3) | .linked_piece_count == 1' >/dev/null || fail "$ST L3 ingest-link"

  # Unknown tracking rejection.
  api POST "/ops/v1/arrival-trips/$TRIP/handling-units" "$ST" "$REQ_PREFIX-$ST-ghost" \
    '{"externalUnitNo":"E2E-R03C-GHOST-U","unitType":"BAG","trackingNumbers":["E2E-R03C-GHOST"],"reason":"e2e"}' \
    | jq -e '.biz_code == "ARRIVAL.PARCEL.INVALID"' >/dev/null || fail "$ST ghost tracking rejection"

  # Planning: driver1 gets area A + the UP2 parcel (task spans two units), driver2 gets area B.
  local WAVE UP2_ID TASK1
  WAVE=$(api POST /ops/v1/planning/waves "$ST" "$REQ_PREFIX-$ST-wave" \
    "$(jq -cn --arg p "$P" --arg date "$SERVICE_DATE" '{waveCode:"\($p)-W1",serviceDate:$date}')")
  WAVE=$(printf '%s' "$WAVE" | jq -er '.biz_data.wave.id')
  UP2_ID=$(mysql_exec -e "SELECT id FROM parcel WHERE tracking_no='$P-UP2'")
  api POST "/ops/v1/planning/waves/$WAVE/assignments" "$ST" "$REQ_PREFIX-$ST-asg1" \
    "$(jq -cn --argjson d "$D1" --argjson a "$A_VID" --argjson p "$UP2_ID" '{driverId:$d,areaVersionIds:[$a],parcelIds:[$p],reason:"e2e"}')" \
    | jq -e '.biz_data.changedCount == 5' >/dev/null || fail "$ST assign driver1"
  api POST "/ops/v1/planning/waves/$WAVE/assignments" "$ST" "$REQ_PREFIX-$ST-asg2" \
    "$(jq -cn --argjson d "$D2" --argjson b "$B_VID" '{driverId:$d,areaVersionIds:[$b],reason:"e2e"}')" \
    | jq -e '.biz_data.changedCount == 2' >/dev/null || fail "$ST assign driver2"
  TASK1=$(api GET "/ops/v1/planning/waves/$WAVE" "$ST" "$REQ_PREFIX-$ST-wsum" \
    | jq -er --argjson d "$D1" '.biz_data.drivers[] | select(.driver_id == $d) | .task_code')

  # Coverage assertions: cross-driver unit, cross-unit task, aggregate=detail, exceptions.
  DETAIL=$(api GET "/ops/v1/arrival-trips/$TRIP" "$ST" "$REQ_PREFIX-$ST-detail2")
  printf '%s' "$DETAIL" | jq -e --argjson u "$U01" \
    '.biz_data.units[] | select(.id == $u) | .linked_piece_count == 6 and .driver_count == 2 and .wave_count == 1 and .scanned_piece_count == 0' \
    >/dev/null || fail "$ST U01 cross-driver coverage"
  printf '%s' "$DETAIL" | jq -e --arg l2 "$L2" \
    '.biz_data.units[] | select(.external_unit_no == $l2) | .linked_piece_count == 1 and .upstream_declared_count == 2 and .exception_piece_count == 1' \
    >/dev/null || fail "$ST L2 exception counts"
  printf '%s' "$DETAIL" | jq -e --arg x "$P-X1" --arg ns "$NEXT_STATION" \
    '[.biz_data.unlinkedDeclarations[] | select(.tracking_no == $x and .station_code == $ns)] | length == 1' \
    >/dev/null || fail "$ST unlinked declaration"
  printf '%s' "$DETAIL" | jq -e \
    '.biz_data as $d | [$d.units[] | . as $u | ([$d.parcels[] | select(.unit_id == $u.id)] | length) == $u.linked_piece_count] | all' \
    >/dev/null || fail "$ST aggregate equals detail"
  printf '%s' "$DETAIL" | jq -e --arg t "$TASK1" \
    '[.biz_data.parcels[] | select(.task_code == $t) | .unit_id] | unique | length == 2' \
    >/dev/null || fail "$ST task spans two units"
  printf '%s' "$DETAIL" | jq -e --argjson u "$U01" \
    '[.biz_data.parcels[] | select(.unit_id == $u) | .driver_id] | unique | length == 2' \
    >/dev/null || fail "$ST unit spans two drivers"

  # Targeted recompute after assignment loss (explicit parcel list keeps shared-DB fixtures untouched).
  ingest "$(jq -cn --arg p "$P" --arg city "$CITY" --arg prov "$PROV" --arg pc "$POSTAL" \
      --argjson lng "$LNG_A" --argjson lat "$LAT_A" \
      '{externalEventId:"E2E-R03C-EVT-\($p)-R1",externalWaybillNo:"\($p)-WBR1",recipientName:"E2E",addressLine1:"1 R St",city:$city,province:$prov,postalCode:$pc,trackingNumbers:["\($p)-R1"],deliveryLatitude:$lat,deliveryLongitude:$lng}')" >/dev/null
  local R1_ID
  R1_ID=$(mysql_exec -e "SELECT id FROM parcel WHERE tracking_no='$P-R1'")
  mysql_exec -e "DELETE paa FROM parcel_area_assignment paa JOIN parcel p ON p.id=paa.parcel_id WHERE p.tracking_no='$P-R1';
                 UPDATE parcel SET current_area_version_id=NULL WHERE tracking_no='$P-R1'" >/dev/null
  api POST /ops/v1/parcels/area-recompute "$ST" "$REQ_PREFIX-$ST-recompute" \
    "$(jq -cn --argjson r "$R1_ID" '{parcelIds:[$r],reason:"e2e recompute"}')" \
    | jq -e '.biz_data.matched == 1' >/dev/null || fail "$ST area recompute"
  mysql_exec -e "SELECT current_area_version_id IS NOT NULL FROM parcel WHERE tracking_no='$P-R1'" \
    | grep -q 1 || fail "$ST recompute projection restored"

  # SQL-level truth checks.
  local SQL
  SQL=$(mysql_exec -e "
    SELECT CONCAT(
      (SELECT COUNT(*) FROM parcel WHERE tracking_no LIKE '$P-%' AND upstream_unit_no IS NOT NULL),'|',
      (SELECT COUNT(*) FROM handling_unit_parcel hp JOIN handling_unit u ON u.id=hp.handling_unit_id
         WHERE u.trip_id=$TRIP AND hp.link_source='AREA_PLAN'),'|',
      (SELECT COUNT(*) FROM handling_unit_parcel hp JOIN handling_unit u ON u.id=hp.handling_unit_id
         WHERE u.trip_id=$TRIP AND hp.link_source='UPSTREAM'),'|',
      (SELECT COUNT(*) FROM parcel_area_assignment paa JOIN parcel p ON p.id=paa.parcel_id
         WHERE p.tracking_no LIKE '$P-%' AND paa.assignment_source='GEO_POLYGON' AND paa.ended_at IS NULL),'|',
      (SELECT COUNT(*) FROM parcel WHERE tracking_no LIKE '$P-%' AND current_area_version_id IS NOT NULL));")
  [ "$SQL" = "3|6|2|7|7" ] || fail "$ST persisted facts: $SQL"

  printf '%s\n' "$ST ok (trip=$TRIP u01=$U01)"
  case "$ST" in
    YHZ-01) YHZ_TRIP="$TRIP"; YHZ_U01="$U01";;
    YYZ-01) YYZ_BVID="$B_VID";;
  esac
}

if [ ! -f "$APP_JAR" ]; then echo "Application JAR not found: $APP_JAR; run ./run.sh build first" >&2; exit 1; fi

cleanup_data
DB_URL="jdbc:mysql://$DB_HOST:$DB_PORT/$DB_NAME?serverTimezone=UTC" \
DB_USERNAME="$DB_USERNAME" DB_PASSWORD="$DB_PASSWORD" \
JWT_SECRET="OpenDelivery_R03C_E2E_Secret_At_Least_32_Chars" \
UPSTREAM_API_KEY="e2e-upstream-key" OPERATIONS_API_KEY="e2e-operations-key" \
java -Dserver.port="$APP_PORT" -jar "$APP_JAR" >/private/tmp/opendelivery-arrival-e2e.log 2>&1 &
APP_PID=$!

ready=false
for attempt in {1..30}; do
  if curl -sS "http://127.0.0.1:$APP_PORT/error" >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
if [ "$ready" != true ]; then echo "Application did not become ready; see /private/tmp/opendelivery-arrival-e2e.log" >&2; exit 1; fi

login=$(curl -sS -X POST "http://127.0.0.1:$APP_PORT/ops/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(jq -cn --arg username "$OPS_USERNAME" --arg password "$OPS_PASSWORD" '{username:$username,password:$password}')")
ACCESS_TOKEN=$(printf '%s' "$login" | jq -er '.biz_data.accessToken // .biz_data.access_token')

YHZ_TRIP=""; YHZ_U01=""; YYZ_BVID=""
run_station_flow YHZ-01
run_station_flow YYZ-01
run_station_flow YVR-01

# Cross-station: another station may not read the trip, and may not fill it with foreign areas.
api GET "/ops/v1/arrival-trips/$YHZ_TRIP" YYZ-01 "$REQ_PREFIX-cross-read" \
  | jq -e '.biz_code == "AUTH.FORBIDDEN"' >/dev/null || fail "cross-station read isolation"
api POST "/ops/v1/handling-units/$YHZ_U01/area-fill" YHZ-01 "$REQ_PREFIX-cross-fill" \
  "$(jq -cn --argjson v "$YYZ_BVID" '{areaVersionIds:[$v],reason:"e2e"}')" \
  | jq -e '.biz_code == "AREA.NOT.AVAILABLE"' >/dev/null || fail "cross-station area fill rejection"

echo "Arrival-batch E2E passed: 3 stations, auto batch numbers, 10 default units, area fill, upstream auto-link (both directions), cross-driver/cross-unit coverage, aggregate=detail, isolation and rejections verified"
