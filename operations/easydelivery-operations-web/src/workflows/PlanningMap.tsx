import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Empty, Space, Tag } from 'antd';
import { loadGoogleMaps, STATION_CENTERS } from './AreaMapEditor';
import { parseAreaGeoJson } from './areaGeometry';

export type PlanningParcel = {
    parcel_id: number;
    tracking_no: string;
    status?: string;
    longitude?: number;
    latitude?: number;
    exception_code?: string;
    driver_id?: number;
    driver_name?: string;
    area_code?: string;
    area_version_id?: number;
    promised_date?: string;
    service_code?: string;
    [key: string]: unknown;
};

export type DeliveryAreaItem = {
    id: number;
    area_code: string;
    area_name: string;
    geo_json?: string;
    geojson_snapshot?: any;
};

function pointColor(parcel: PlanningParcel, selected: boolean) {
    if (selected) return '#722ed1';
    if (parcel.exception_code === 'OPEN_CASE') return '#cf1322';
    if (parcel.exception_code) return '#d46b08';
    if (parcel.service_code === 'EXPRESS' || parcel.service_code === 'SAME_DAY') return '#eb2f96';
    if (parcel.driver_id) return '#389e0d';
    return '#1677ff';
}

function getCentroidFromCoords(coords: any): { lat: number; lng: number } | null {
    const points: Array<[number, number]> = [];

    function collect(c: any) {
        if (!Array.isArray(c) || c.length === 0) return;
        if (typeof c[0] === 'number' && typeof c[1] === 'number') {
            points.push([c[0], c[1]]);
            return;
        }
        for (const item of c) {
            collect(item);
        }
    }

    collect(coords);

    if (points.length === 0) return null;

    let sumLng = 0;
    let sumLat = 0;
    for (const p of points) {
        sumLng += p[0];
        sumLat += p[1];
    }

    return { lng: sumLng / points.length, lat: sumLat / points.length };
}

function isPointInPolygon(point: { lat: number; lng: number }, vs: Array<[number, number]>) {
    const x = point.lng, y = point.lat;
    let inside = false;
    for (let i = 0, j = vs.length - 1; i < vs.length; j = i++) {
        const xi = vs[i][0], yi = vs[i][1];
        const xj = vs[j][0], yj = vs[j][1];
        const intersect = ((yi > y) !== (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
        if (intersect) inside = !inside;
    }
    return inside;
}

export function PlanningMap({
    station,
    parcels,
    serviceAreas = [],
    selected,
    activeAreaId,
    onSelectArea,
    onToggle,
    onSelect
}: {
    station: string;
    parcels: PlanningParcel[];
    serviceAreas?: DeliveryAreaItem[];
    selected: Set<number>;
    activeAreaId?: number;
    onSelectArea?: (areaId: number | undefined) => void;
    onToggle: (id: number) => void;
    onSelect: (parcel: PlanningParcel) => void;
}) {
    const node = useRef<HTMLDivElement>(null);
    const map = useRef<google.maps.Map | undefined>(undefined);
    const parcelLayer = useRef<google.maps.Data | undefined>(undefined);
    const areaLayer = useRef<google.maps.Data | undefined>(undefined);
    const markersRef = useRef<google.maps.Marker[]>([]);
    const parcelRef = useRef<Map<number, PlanningParcel>>(new Map());

    const [ready, setReady] = useState(false);
    const [error, setError] = useState('');

    const key = import.meta.env.VITE_GOOGLE_MAPS_API_KEY?.trim();
    const byId = useMemo(() => new Map(parcels.map(parcel => [parcel.parcel_id, parcel])), [parcels]);

    const activeAreaRef = useRef<number | undefined>(activeAreaId);
    useEffect(() => { activeAreaRef.current = activeAreaId; }, [activeAreaId]);

    const parcelsByArea = useMemo(() => {
        const mapById = new Map<number, PlanningParcel[]>();
        const mapByCode = new Map<string, PlanningParcel[]>();
        
        // Extract polygon coordinates for spatial fallback
        const areaPolygons = serviceAreas.map(area => {
            let geom: any;
            try {
                const str = typeof area.geo_json === 'string' ? area.geo_json : JSON.stringify(area.geojson_snapshot || area.geo_json);
                geom = parseAreaGeoJson(str);
            } catch {
                geom = null;
            }
            let vs: Array<[number, number]> = [];
            if (geom?.type === 'Polygon' && Array.isArray(geom.coordinates?.[0])) {
                vs = geom.coordinates[0];
            } else if (geom?.type === 'MultiPolygon' && Array.isArray(geom.coordinates?.[0]?.[0])) {
                vs = geom.coordinates[0][0];
            }
            return { area, vs };
        }).filter(item => item.vs.length > 0);

        parcels.forEach(p => {
            let matchedAreaId = p.area_id ?? p.area_version_id;
            let matchedAreaCode = p.area_code;

            // If parcel missing explicit area_code/area_version_id, do point-in-polygon check
            if ((!matchedAreaId && !matchedAreaCode) && p.latitude != null && p.longitude != null) {
                const pt = { lat: Number(p.latitude), lng: Number(p.longitude) };
                const found = areaPolygons.find(item => isPointInPolygon(pt, item.vs));
                if (found) {
                    matchedAreaId = found.area.id;
                    matchedAreaCode = found.area.area_code;
                }
            }

            if (matchedAreaId != null) {
                const numId = Number(matchedAreaId);
                if (!mapById.has(numId)) mapById.set(numId, []);
                mapById.get(numId)!.push(p);
            }
            if (matchedAreaCode) {
                if (!mapByCode.has(matchedAreaCode)) mapByCode.set(matchedAreaCode, []);
                mapByCode.get(matchedAreaCode)!.push(p);
            }
        });

        return { mapById, mapByCode };
    }, [parcels, serviceAreas]);

    const fitAll = useCallback(() => {
        if (!map.current) return;
        const bounds = new google.maps.LatLngBounds();
        parcels.forEach(parcel => {
            if (parcel.latitude != null && parcel.longitude != null) {
                bounds.extend({ lat: Number(parcel.latitude), lng: Number(parcel.longitude) });
            }
        });
        if (!bounds.isEmpty()) map.current.fitBounds(bounds, 48);
    }, [parcels]);

    // Initialize Map
    useEffect(() => {
        if (!key || !node.current) return;
        let active = true;
        setError('');

        void loadGoogleMaps(key).then(({ Map }) => {
            if (!active || !node.current) return;
            map.current = new Map(node.current, {
                center: STATION_CENTERS[station] ?? STATION_CENTERS['YHZ-01'],
                zoom: 11,
                mapTypeControl: false,
                streetViewControl: false,
                fullscreenControl: true,
                gestureHandling: 'greedy',
                clickableIcons: false
            });

            areaLayer.current = new google.maps.Data({ map: map.current });
            parcelLayer.current = new google.maps.Data({ map: map.current });

            areaLayer.current.addListener('click', (event: google.maps.Data.MouseEvent) => {
                const areaId = Number(event.feature.getProperty('areaId'));
                if (onSelectArea) {
                    const currentActive = activeAreaRef.current;
                    onSelectArea(currentActive != null && Number(currentActive) === areaId ? undefined : areaId);
                }
            });

            parcelLayer.current.addListener('click', (event: google.maps.Data.MouseEvent) => {
                const id = Number(event.feature.getProperty('parcelId'));
                const parcel = parcelRef.current.get(id);
                if (parcel) {
                    onToggle(id);
                    onSelect(parcel);
                }
            });

            setReady(true);
        }).catch(() => setError('Google Maps could not load.'));

        return () => {
            active = false;
            areaLayer.current?.setMap(null);
            parcelLayer.current?.setMap(null);
            markersRef.current.forEach(m => m.setMap(null));
            map.current = undefined;
            setReady(false);
        };
    }, [key, station]);

    // Draw Delivery Area Polygons & Cluster Centroid Badges
    useEffect(() => {
        if (!ready || !areaLayer.current) return;

        areaLayer.current.forEach(feature => areaLayer.current?.remove(feature));
        markersRef.current.forEach(m => m.setMap(null));
        markersRef.current = [];

        if (serviceAreas.length > 0) {
            const features = serviceAreas.map(area => {
                let geom: any;
                try {
                    const str = typeof area.geo_json === 'string' ? area.geo_json : JSON.stringify(area.geojson_snapshot || area.geo_json);
                    geom = parseAreaGeoJson(str);
                } catch {
                    geom = null;
                }
                if (!geom) return null;
                return {
                    type: 'Feature' as const,
                    properties: { areaId: area.id, areaCode: area.area_code, areaName: area.area_name },
                    geometry: geom
                };
            }).filter((f): f is NonNullable<typeof f> => f !== null);

            areaLayer.current.addGeoJson({ type: 'FeatureCollection', features });

            areaLayer.current.setStyle(feature => {
                const areaId = Number(feature.getProperty('areaId'));
                const isActive = activeAreaId != null && Number(activeAreaId) === areaId;

                return {
                    fillColor: isActive ? '#1677ff' : '#722ed1',
                    fillOpacity: isActive ? 0.35 : 0.15,
                    strokeColor: isActive ? '#0958d9' : '#531dab',
                    strokeWeight: isActive ? 3 : 2,
                    zIndex: isActive ? 10 : 1
                };
            });

            // Render Watermark Area Labels & Google-style Circular Cluster Badges
            serviceAreas.forEach(area => {
                const areaParcels = parcelsByArea.mapById.get(Number(area.id)) || parcelsByArea.mapByCode.get(area.area_code) || [];

                let geom: any;
                try {
                    const str = typeof area.geo_json === 'string' ? area.geo_json : JSON.stringify(area.geojson_snapshot || area.geo_json);
                    geom = parseAreaGeoJson(str);
                } catch {
                    geom = null;
                }

                if (geom?.coordinates) {
                    const centroid = getCentroidFromCoords(geom.coordinates);

                    if (centroid) {
                        const isActive = activeAreaId && Number(area.id) === Number(activeAreaId);
                        
                        // 1. Watermark Text Marker for Area Name / Area Code
                        const labelText = area.area_code || area.area_name;
                        const watermarkSvg = `
                            <svg xmlns="http://www.w3.org/2000/svg" width="180" height="30" viewBox="0 0 180 30">
                                <text x="90" y="20" fill="#722ed1" font-size="14" font-family="system-ui, sans-serif" font-weight="800" text-anchor="middle" opacity="0.65" letter-spacing="1px">
                                    ${labelText}
                                </text>
                            </svg>
                        `;

                        const watermarkMarker = new google.maps.Marker({
                            position: { lat: centroid.lat + 0.003, lng: centroid.lng },
                            map: map.current,
                            clickable: false,
                            icon: {
                                url: 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(watermarkSvg),
                                anchor: new google.maps.Point(90, 15)
                            },
                            zIndex: 5
                        });
                        markersRef.current.push(watermarkMarker);

                        // 2. Google Maps style Circular Cluster Marker with Quantity only (shown when area not expanded)
                        if (!isActive && areaParcels.length > 0) {
                            const count = areaParcels.length;
                            const size = count < 10 ? 38 : count < 50 ? 44 : 50;
                            const color = count < 10 ? '#1890ff' : count < 30 ? '#52c41a' : '#fa8c16';
                            const radius = size / 2;

                            const clusterSvg = `
                                <svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
                                    <circle cx="${radius}" cy="${radius}" r="${radius - 2}" fill="${color}" fill-opacity="0.88" stroke="#ffffff" stroke-width="2.5" />
                                    <text x="${radius}" y="${radius + 5}" fill="#ffffff" font-size="14" font-family="system-ui, sans-serif" font-weight="bold" text-anchor="middle">
                                        ${count}
                                    </text>
                                </svg>
                            `;

                            const clusterMarker = new google.maps.Marker({
                                position: centroid,
                                map: map.current,
                                title: `${area.area_name || area.area_code}: ${count} 件包裹 (点击展开)`,
                                icon: {
                                    url: 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(clusterSvg),
                                    anchor: new google.maps.Point(radius, radius)
                                },
                                zIndex: 60
                            });

                            clusterMarker.addListener('click', () => {
                                if (onSelectArea) onSelectArea(area.id);
                            });

                            markersRef.current.push(clusterMarker);
                        }
                    }
                }
            });
        }
    }, [ready, serviceAreas, activeAreaId, parcelsByArea, onSelectArea]);

    // Draw Individual Parcel Points
    useEffect(() => {
        if (!ready || !parcelLayer.current) return;

        parcelLayer.current.forEach(feature => parcelLayer.current?.remove(feature));

        // When NO activeAreaId is selected, hide individual parcel points (showing ONLY cluster circles and area watermarks)
        // When activeAreaId IS selected, show ONLY that expanded area's individual parcel points
        const activeArea = serviceAreas.find(a => Number(a.id) === Number(activeAreaId));
        const visibleParcels = parcels.filter(p => {
            if (p.latitude == null || p.longitude == null) return false;
            if (!activeAreaId) return false; // Hide individual parcel pins in global aggregated cluster view!
            const matchedId = p.area_id ?? p.area_version_id;
            if (matchedId != null && Number(matchedId) === Number(activeAreaId)) return true;
            if (activeArea && p.area_code && p.area_code === activeArea.area_code) return true;
            return false;
        });

        const features = visibleParcels.map(parcel => ({
            type: 'Feature' as const,
            properties: { parcelId: parcel.parcel_id },
            geometry: {
                type: 'Point' as const,
                coordinates: [Number(parcel.longitude), Number(parcel.latitude)]
            }
        }));

        parcelLayer.current.addGeoJson({ type: 'FeatureCollection', features });

        parcelLayer.current.setStyle(feature => {
            const pId = Number(feature.getProperty('parcelId'));
            const parcel = byId.get(pId);
            if (!parcel) return { visible: false };

            const isSel = selected.has(parcel.parcel_id);
            return {
                icon: {
                    path: google.maps.SymbolPath.CIRCLE,
                    scale: isSel ? 9 : 7,
                    fillColor: pointColor(parcel, isSel),
                    fillOpacity: 0.92,
                    strokeColor: '#ffffff',
                    strokeWeight: 2
                },
                title: `${parcel.tracking_no}${parcel.driver_name ? ' · ' + parcel.driver_name : ''}`,
                zIndex: isSel ? 100 : 10
            };
        });
    }, [byId, ready, parcels, activeAreaId, serviceAreas, selected]);

    const locatable = parcels.filter(p => p.latitude != null && p.longitude != null).length;

    if (!key) return <Alert type="warning" showIcon message="Google Maps API key is not configured" />;

    return (
        <div className="planning-map-wrap">
            {error && <Alert type="error" showIcon message={error} />}
            <div ref={node} className="planning-map" aria-label="Parcel planning map" style={{ minHeight: '480px', borderRadius: '8px' }} />

            <Space className="map-status" wrap style={{ marginTop: '8px' }}>
                <Tag color="purple">{activeAreaId ? `已展开区域 ID: ${activeAreaId}` : '全局聚合视图 (点击区域气泡展开明细)'}</Tag>
                <Tag>查询包裹 {parcels.length}</Tag>
                <Tag color="green">准准确定位 {locatable}</Tag>
                <Button size="small" onClick={fitAll} disabled={!ready || !locatable}>全图适应</Button>
                {activeAreaId && <Button size="small" type="link" onClick={() => onSelectArea?.(undefined)}>重置全局聚合</Button>}
            </Space>
        </div>
    );
}

