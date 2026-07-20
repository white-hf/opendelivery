#!/usr/bin/env bash
set -euo pipefail

base_url="${OPENDELIVERY_URL:-http://127.0.0.1:19004}"
username="${OPENDELIVERY_OPS_USERNAME:-opsadmin}"
: "${OPENDELIVERY_OPS_PASSWORD:?Set OPENDELIVERY_OPS_PASSWORD}"
service_date="${OPENDELIVERY_SERVICE_DATE:-$(date +%F)}"

login=$(curl -fsS -X POST "$base_url/ops/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$username\",\"password\":\"$OPENDELIVERY_OPS_PASSWORD\"}")
token=$(jq -er '.biz_data.accessToken' <<<"$login")

for station in YHZ-01 YYZ-01 YVR-01; do
  tower=$(curl -fsS "$base_url/ops/v1/control-tower?serviceDate=$service_date" \
    -H "Authorization: Bearer $token" -H "X-Station-Code: $station")
  parcels=$(curl -fsS "$base_url/ops/v1/planning/parcels?serviceDate=$service_date&limit=200" \
    -H "Authorization: Bearer $token" -H "X-Station-Code: $station")
  expected=$(jq -er '.biz_data.metrics[]|select(.code=="EXPECTED")|.count' <<<"$tower")
  matched=$(jq -er '.biz_data.metrics[]|select(.code=="AREA_MATCHED")|.count' <<<"$tower")
  demo=$(jq -er --arg prefix "DEMO-R02-${station:0:3}" '[.biz_data[]|select(.tracking_no|startswith($prefix))]|length' <<<"$parcels")
  locatable=$(jq -er --arg prefix "DEMO-R02-${station:0:3}" '[.biz_data[]|select(.tracking_no|startswith($prefix))|select(.longitude!=null and .latitude!=null)]|length' <<<"$parcels")
  [[ "$expected" -eq 72 && "$matched" -eq 66 && "$demo" -eq 72 && "$locatable" -eq 69 ]] || {
    echo "$station failed: expected=$expected matched=$matched demo=$demo locatable=$locatable" >&2; exit 1;
  }
  echo "$station passed: expected=72 matched=66 locatable=69"
done
