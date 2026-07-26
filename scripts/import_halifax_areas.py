#!/usr/bin/env python3
"""
import_halifax_areas.py
Imports 4 Halifax adjacent community delivery areas into EasyDelivery Operations API.
"""

import os
import sys
import json
import urllib.request
import urllib.error

GEOJSON_PATH = "docs/data/halifax_4_communities.geojson"
OPS_URL = os.getenv("OPS_URL", "http://127.0.0.1:9001")
OPS_USERNAME = os.getenv("OPS_USERNAME", "opsadmin")
OPS_PASSWORD = os.getenv("OPS_PASSWORD", "password123")
STATION_CODE = os.getenv("STATION_CODE", "YHZ-01")

def log(msg):
    print(f"[Import-Script] {msg}")

def http_post(url, data_dict, token=None, headers_extra=None):
    payload = json.dumps(data_dict).encode('utf-8')
    headers = {
        'Content-Type': 'application/json',
        'X-Shadow-Test': 'true'
    }
    if token:
        headers['Authorization'] = f"Bearer {token}"
    if headers_extra:
        headers.update(headers_extra)

    req = urllib.request.Request(url, data=payload, headers=headers, method='POST')
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode('utf-8')
        log(f"HTTP Error {e.code} on {url}: {err_body}")
        sys.exit(1)
    except Exception as e:
        log(f"Connection Error on {url}: {e}")
        sys.exit(1)

def main():
    log(f"Checking GeoJSON file: {GEOJSON_PATH}")
    if not os.path.exists(GEOJSON_PATH):
        log(f"File not found: {GEOJSON_PATH}")
        sys.exit(1)

    with open(GEOJSON_PATH, 'r', encoding='utf-8') as f:
        geojson_data = json.load(f)

    features = geojson_data.get('features', [])
    log(f"Loaded {len(features)} community features from GeoJSON.")

    # 1. Authenticate with Ops API
    log(f"Authenticating with Operations API at {OPS_URL}...")
    login_url = f"{OPS_URL}/ops/auth/login"
    login_resp = http_post(login_url, {
        "username": OPS_USERNAME,
        "password": OPS_PASSWORD
    })

    token = login_resp.get('biz_data', {}).get('accessToken')
    if not token:
        log(f"Login failed, response: {login_resp}")
        sys.exit(1)

    log("Operations Admin authenticated successfully!")

    # 2. Iterate features and import delivery areas
    create_area_url = f"{OPS_URL}/ops/v1/delivery-areas"
    imported_count = 0

    for idx, feature in enumerate(features, start=1):
        props = feature.get('properties', {})
        community_name = props.get('GSA_NAME') or f"COMMUNITY-{idx}"
        area_code = f"HLFX-{community_name.replace(' ', '_').upper()}"
        area_name = f"Halifax - {community_name.title()}"
        geometry = feature.get('geometry')

        geo_json_payload = {
            "type": "FeatureCollection",
            "features": [
                {
                    "type": "Feature",
                    "properties": {
                        "communityName": community_name,
                        "gsaKey": props.get('GSA_KEY')
                    },
                    "geometry": geometry
                }
            ]
        }

        req_body = {
            "areaCode": area_code,
            "areaName": area_name,
            "areaLevel": 1,
            "driverIds": [101],
            "geoJson": geo_json_payload,
            "changeReason": f"Imported from Halifax Open Data (ArcGIS HRM GSA: {community_name})"
        }

        log(f"[{idx}/{len(features)}] Importing area {area_code} ({area_name})...")
        resp = http_post(
            create_area_url,
            req_body,
            token=token,
            headers_extra={
                "X-Station-Code": STATION_CODE,
                "X-Request-Id": f"import-area-{idx}"
            }
        )

        biz_data = resp.get('biz_data', {})
        area_id = biz_data.get('areaId') or biz_data.get('area_id')
        log(f"  -> Successfully imported {area_code} (Area ID: {area_id})")
        imported_count += 1

    log(f"🎉 Successfully imported all {imported_count} Halifax delivery areas into EasyDelivery system!")

if __name__ == "__main__":
    main()
