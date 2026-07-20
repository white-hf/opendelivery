#!/bin/bash
set -euo pipefail

: "${DB_PASSWORD:?DB_PASSWORD must be set}"
: "${OPS_PASSWORD:?OPS_PASSWORD must be set}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-uniuni_hf}"
DB_NAME="${DB_NAME:-opendelivery}"
OPS_USERNAME="${OPS_USERNAME:-opsadmin}"
APP_PORT="${APP_PORT:-19002}"
APP_JAR="${APP_JAR:-easydelivery-app/target/easydelivery-app-1.0.0.jar}"
TEST_PREFIX="E2E-R01-GATE"
REQUEST_PREFIX="e2e-r01-gate"
APP_PID=""
ACCESS_TOKEN=""

if command -v mysql >/dev/null 2>&1; then
  MYSQL_BIN="$(command -v mysql)"
elif [ -x /usr/local/mysql/bin/mysql ]; then
  MYSQL_BIN=/usr/local/mysql/bin/mysql
else
  echo "mysql client was not found" >&2
  exit 1
fi

mysql_exec() {
  MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" \
    -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" "$DB_NAME" --batch --skip-column-names "$@"
}

cleanup_data() {
  mysql_exec -e "
    DELETE FROM operation_audit_log
      WHERE request_id LIKE '${REQUEST_PREFIX}-%'
         OR (resource_type='DELIVERY_AREA' AND resource_id IN
             (SELECT CAST(id AS CHAR) FROM delivery_area WHERE area_code LIKE '${TEST_PREFIX}-%'));
    DELETE p FROM driver_area_preference p
      JOIN delivery_area a ON a.id=p.delivery_area_id WHERE a.area_code LIKE '${TEST_PREFIX}-%';
    DELETE paa FROM parcel_area_assignment paa
      JOIN delivery_area_version v ON v.id=paa.delivery_area_version_id
      JOIN delivery_area a ON a.id=v.delivery_area_id WHERE a.area_code LIKE '${TEST_PREFIX}-%';
    DELETE v FROM delivery_area_version v
      JOIN delivery_area a ON a.id=v.delivery_area_id WHERE a.area_code LIKE '${TEST_PREFIX}-%';
    DELETE FROM delivery_area WHERE area_code LIKE '${TEST_PREFIX}-%';
  " >/dev/null
}

cleanup() {
  if [ -n "$ACCESS_TOKEN" ]; then
    curl -sS -X POST "http://127.0.0.1:$APP_PORT/ops/auth/logout" \
      -H "Authorization: Bearer $ACCESS_TOKEN" >/dev/null 2>&1 || true
  fi
  if [ -n "$APP_PID" ]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  cleanup_data 2>/dev/null || true
}
trap cleanup EXIT

api() {
  local method="$1" path="$2" station="$3" request_id="$4" body="${5:-}"
  local args=(-sS -X "$method" "http://127.0.0.1:$APP_PORT$path"
    -H "Authorization: Bearer $ACCESS_TOKEN"
    -H "X-Station-Code: $station"
    -H "X-Request-Id: $request_id")
  if [ -n "$body" ]; then
    args+=(-H 'Content-Type: application/json' -d "$body")
  fi
  curl "${args[@]}"
}

create_validate_publish() {
  local station="$1" suffix="$2" geo_json="$3"
  local code="${TEST_PREFIX}-${station}-${suffix}"
  local created area_id version_id validated published
  created=$(api POST /ops/v1/delivery-areas "$station" "${REQUEST_PREFIX}-${station}-${suffix}-create" \
    "$(jq -cn --arg code "$code" --arg name "$code" --argjson geometry "$geo_json" \
      '{areaCode:$code,areaName:$name,areaLevel:1,geoJson:$geometry,changeReason:"R01 automated gate"}')")
  area_id=$(printf '%s' "$created" | jq -er '.biz_data.areaId // .biz_data.area_id')
  version_id=$(printf '%s' "$created" | jq -er '.biz_data.versionId // .biz_data.version_id')
  validated=$(api POST "/ops/v1/delivery-areas/$area_id/versions/$version_id/validate" "$station" \
    "${REQUEST_PREFIX}-${station}-${suffix}-validate")
  printf '%s' "$validated" | jq -e '.biz_code == "COMMON.QUERY.SUCCESS" and .biz_data.valid == true' >/dev/null
  published=$(api POST "/ops/v1/delivery-areas/$area_id/versions/$version_id/publish" "$station" \
    "${REQUEST_PREFIX}-${station}-${suffix}-publish" '{"reason":"R01 automated gate"}')
  printf '%s' "$published" | jq -e '.biz_code == "COMMON.QUERY.SUCCESS" and .biz_data.status == "PUBLISHED"' >/dev/null
  printf '%s|%s\n' "$area_id" "$version_id"
}

assert_overlap_rejected() {
  local station="$1" geo_json="$2"
  local code="${TEST_PREFIX}-${station}-OVERLAP" created area_id version_id result
  created=$(api POST /ops/v1/delivery-areas "$station" "${REQUEST_PREFIX}-${station}-overlap-create" \
    "$(jq -cn --arg code "$code" --argjson geometry "$geo_json" \
      '{areaCode:$code,areaName:$code,areaLevel:1,geoJson:$geometry,changeReason:"Overlap rejection gate"}')")
  area_id=$(printf '%s' "$created" | jq -er '.biz_data.areaId // .biz_data.area_id')
  version_id=$(printf '%s' "$created" | jq -er '.biz_data.versionId // .biz_data.version_id')
  result=$(api POST "/ops/v1/delivery-areas/$area_id/versions/$version_id/validate" "$station" \
    "${REQUEST_PREFIX}-${station}-overlap-validate")
  printf '%s' "$result" | jq -e '.biz_code == "AREA.OVERLAP"' >/dev/null
}

if [ ! -f "$APP_JAR" ]; then
  echo "Application JAR not found: $APP_JAR; run ./run.sh build first" >&2
  exit 1
fi

cleanup_data
DB_URL="jdbc:mysql://$DB_HOST:$DB_PORT/$DB_NAME?serverTimezone=UTC" \
DB_USERNAME="$DB_USERNAME" DB_PASSWORD="$DB_PASSWORD" \
JWT_SECRET="OpenDelivery_R01_E2E_Secret_At_Least_32_Characters" \
UPSTREAM_API_KEY="e2e-upstream-key" OPERATIONS_API_KEY="e2e-operations-key" \
java -Dserver.port="$APP_PORT" -jar "$APP_JAR" >/private/tmp/opendelivery-area-e2e.log 2>&1 &
APP_PID=$!

ready=false
for attempt in {1..30}; do
  if curl -sS "http://127.0.0.1:$APP_PORT/error" >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
if [ "$ready" != true ]; then
  echo "Application did not become ready; see /private/tmp/opendelivery-area-e2e.log" >&2
  exit 1
fi

login=$(curl -sS -X POST "http://127.0.0.1:$APP_PORT/ops/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(jq -cn --arg username "$OPS_USERNAME" --arg password "$OPS_PASSWORD" '{username:$username,password:$password}')")
ACCESS_TOKEN=$(printf '%s' "$login" | jq -er '.biz_data.accessToken // .biz_data.access_token')

YHZ_A='{"type":"Polygon","coordinates":[[[-63.62,44.62],[-63.60,44.62],[-63.60,44.64],[-63.62,44.64],[-63.62,44.62]]]}'
YHZ_B='{"type":"Polygon","coordinates":[[[-63.60,44.62],[-63.58,44.62],[-63.58,44.64],[-63.60,44.64],[-63.60,44.62]]]}'
YHZ_O='{"type":"Polygon","coordinates":[[[-63.61,44.63],[-63.59,44.63],[-63.59,44.65],[-63.61,44.65],[-63.61,44.63]]]}'
YYZ_A='{"type":"Polygon","coordinates":[[[-79.42,43.64],[-79.40,43.64],[-79.40,43.66],[-79.42,43.66],[-79.42,43.64]]]}'
YYZ_B='{"type":"Polygon","coordinates":[[[-79.40,43.64],[-79.38,43.64],[-79.38,43.66],[-79.40,43.66],[-79.40,43.64]]]}'
YYZ_O='{"type":"Polygon","coordinates":[[[-79.41,43.65],[-79.39,43.65],[-79.39,43.67],[-79.41,43.67],[-79.41,43.65]]]}'
YVR_A='{"type":"Polygon","coordinates":[[[-123.14,49.26],[-123.12,49.26],[-123.12,49.28],[-123.14,49.28],[-123.14,49.26]]]}'
YVR_B='{"type":"Polygon","coordinates":[[[-123.12,49.26],[-123.10,49.26],[-123.10,49.28],[-123.12,49.28],[-123.12,49.26]]]}'
YVR_O='{"type":"Polygon","coordinates":[[[-123.13,49.27],[-123.11,49.27],[-123.11,49.29],[-123.13,49.29],[-123.13,49.27]]]}'

yhz_a=$(create_validate_publish YHZ-01 A "$YHZ_A")
create_validate_publish YHZ-01 B "$YHZ_B" >/dev/null
assert_overlap_rejected YHZ-01 "$YHZ_O"
create_validate_publish YYZ-01 A "$YYZ_A" >/dev/null
create_validate_publish YYZ-01 B "$YYZ_B" >/dev/null
assert_overlap_rejected YYZ-01 "$YYZ_O"
create_validate_publish YVR-01 A "$YVR_A" >/dev/null
create_validate_publish YVR-01 B "$YVR_B" >/dev/null
assert_overlap_rejected YVR-01 "$YVR_O"

yhz_area_id="${yhz_a%%|*}"
cross_station=$(api GET "/ops/v1/delivery-areas/$yhz_area_id/versions" YYZ-01 \
  "${REQUEST_PREFIX}-cross-station-read")
printf '%s' "$cross_station" | jq -e '.biz_code == "AUTH.FORBIDDEN"' >/dev/null

for station in YHZ-01 YYZ-01 YVR-01; do
  areas=$(api GET /ops/v1/delivery-areas "$station" "${REQUEST_PREFIX}-${station}-list")
  count=$(printf '%s' "$areas" | jq --arg prefix "${TEST_PREFIX}-${station}-" \
    '[.biz_data[] | select((.area_code // .areaCode) | startswith($prefix)) | select((.version_status // .versionStatus) == "PUBLISHED")] | length')
  if [ "$count" -ne 2 ]; then
    echo "Expected two published test areas for $station, found $count" >&2
    exit 1
  fi
done

persisted=$(mysql_exec -e "
  SELECT CONCAT(COUNT(*),'|',COUNT(DISTINCT s.station_code))
  FROM delivery_area a JOIN station s ON s.id=a.station_id
  JOIN delivery_area_version v ON v.delivery_area_id=a.id AND v.status='PUBLISHED'
  WHERE a.area_code LIKE '${TEST_PREFIX}-%';")
if [ "$persisted" != "6|3" ]; then
  echo "Unexpected persisted area result: $persisted" >&2
  exit 1
fi

echo "Delivery-area E2E passed: 6 published areas across 3 stations; shared edges accepted, overlaps and cross-station access rejected"
