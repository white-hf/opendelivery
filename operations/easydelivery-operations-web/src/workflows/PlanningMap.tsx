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
    stop_sequence?: number;
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
    selectedDriverName,
    serviceAreas = [],
    selected,
    activeAreaId,
    lassoActive = false,
    onSelectArea,
    onToggle,
    onSelect,
    onLassoSelect
}: {
    station: number | string;

    parcels: PlanningParcel[];
    selectedDriverName?: string;
    serviceAreas?: DeliveryAreaItem[];
    selected: Set<number>;
    activeAreaId?: number;
    lassoActive?: boolean;
    onSelectArea?: (areaId: number | undefined) => void;
    onToggle: (id: number) => void;
    onSelect: (parcel: PlanningParcel) => void;
    onLassoSelect?: (selectedParcelIds: number[]) => void;

}) {
    const node = useRef<HTMLDivElement>(null);
    const map = useRef<google.maps.Map | undefined>(undefined);
    const parcelLayer = useRef<google.maps.Data | undefined>(undefined);
    const areaLayer = useRef<google.maps.Data | undefined>(undefined);
    const markersRef = useRef<google.maps.Marker[]>([]);
    const routePolylineRef = useRef<google.maps.Polyline | null>(null);
    const parcelRef = useRef<Map<number, PlanningParcel>>(new Map());
    const [lassoPoints, setLassoPoints] = useState<Array<[number, number]>>([]);
    const drawingRef = useRef<Array<google.maps.Polygon | google.maps.Polyline>>([]);

    const [ready, setReady] = useState(false);
    const [error, setError] = useState('');

    const key = import.meta.env.VITE_GOOGLE_MAPS_API_KEY?.trim();
    const byId = useMemo(() => new Map(parcels.map(parcel => [parcel.parcel_id, parcel])), [parcels]);

    const activeAreaRef = useRef<number | undefined>(activeAreaId);
    useEffect(() => { activeAreaRef.current = activeAreaId; }, [activeAreaId]);

    const lassoActiveRef = useRef<boolean>(lassoActive);
    useEffect(() => { lassoActiveRef.current = lassoActive; }, [lassoActive]);
    const lastLassoMatchedKeyRef = useRef<string>('');


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

            // Check if matchedAreaId belongs to current active serviceAreas
            const isKnownArea = serviceAreas.some(a => Number(a.id) === Number(matchedAreaId) || (matchedAreaCode && a.area_code === matchedAreaCode));

            // If parcel missing active area mapping, fallback to point-in-polygon spatial check
            if (!isKnownArea && p.latitude != null && p.longitude != null) {
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
                if (lassoActiveRef.current && event.latLng) {
                    setLassoPoints(prev => [...prev, [event.latLng!.lng(), event.latLng!.lat()]]);
                    return;
                }
                const areaId = Number(event.feature.getProperty('areaId'));
                if (onSelectArea) {
                    const currentActive = activeAreaRef.current;
                    onSelectArea(currentActive != null && Number(currentActive) === areaId ? undefined : areaId);
                }
            });

            map.current.addListener('click', (event: google.maps.MapMouseEvent) => {
                if (lassoActiveRef.current && event.latLng) {
                    setLassoPoints(prev => [...prev, [event.latLng!.lng(), event.latLng!.lat()]]);
                }
            });

            parcelLayer.current.addListener('click', (event: google.maps.Data.MouseEvent) => {
                if (lassoActiveRef.current && event.latLng) {
                    setLassoPoints(prev => [...prev, [event.latLng!.lng(), event.latLng!.lat()]]);
                    return;
                }
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
            if (routePolylineRef.current) {
                routePolylineRef.current.setMap(null);
                routePolylineRef.current = null;
            }
            map.current = undefined;
            setReady(false);
        };
    }, [key, station]);

    const fittedStationRef = useRef<number | string | null>(null);

    // Reset fittedStationRef on station change
    useEffect(() => {
        fittedStationRef.current = null;
    }, [station]);

    // Draw Delivery Area Polygons & Cluster Centroid Badges
    useEffect(() => {
        if (!ready || !areaLayer.current) return;

        areaLayer.current.forEach(feature => areaLayer.current?.remove(feature));
        markersRef.current.forEach(m => m.setMap(null));
        markersRef.current = [];

        if (serviceAreas.length > 0) {
            const bounds = new google.maps.LatLngBounds();
            const features = serviceAreas.map(area => {
                let geom: any;
                try {
                    const str = typeof area.geo_json === 'string' ? area.geo_json : JSON.stringify(area.geojson_snapshot || area.geo_json);
                    geom = parseAreaGeoJson(str);
                } catch {
                    geom = null;
                }
                if (!geom) return null;

                // Extend bounds for map view positioning
                if (geom.coordinates) {
                    const collectPts = (arr: any) => {
                        if (Array.isArray(arr) && arr.length === 2 && typeof arr[0] === 'number' && typeof arr[1] === 'number') {
                            bounds.extend({ lng: arr[0], lat: arr[1] });
                        } else if (Array.isArray(arr)) {
                            arr.forEach(collectPts);
                        }
                    };
                    collectPts(geom.coordinates);
                }

                return {
                    type: 'Feature' as const,
                    properties: { areaId: area.id, areaCode: area.area_code, areaName: area.area_name },
                    geometry: geom
                };
            }).filter((f): f is NonNullable<typeof f> => f !== null);

            areaLayer.current.addGeoJson({ type: 'FeatureCollection', features });

            // Fit bounds ONLY ONCE per station load to prevent jarring jumps on zoom/selection
            if (map.current && !bounds.isEmpty() && fittedStationRef.current !== station) {
                map.current.fitBounds(bounds, 48);
                fittedStationRef.current = station;
            }

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
    }, [ready, serviceAreas, activeAreaId, parcelsByArea, onSelectArea, station]);

    // Draw Individual Parcel Points
    useEffect(() => {
        if (!ready || !parcelLayer.current) return;

        parcelLayer.current.forEach(feature => parcelLayer.current?.remove(feature));

        // Render individual parcel points:
        // - Always render pins if a specific driver is selected or a specific area filter is active!
        // - If viewing global all-parcels overview, show pins when area is expanded via activeAreaId or show all pins if activeAreaId is set.
        const activeAreaItem = serviceAreas.find(a => Number(a.id) === Number(activeAreaId));
        let activeVs: Array<[number, number]> = [];
        if (activeAreaItem) {
            try {
                const str = typeof activeAreaItem.geo_json === 'string' ? activeAreaItem.geo_json : JSON.stringify(activeAreaItem.geojson_snapshot || activeAreaItem.geo_json);
                const geom: any = parseAreaGeoJson(str);
                if (geom?.type === 'Polygon' && Array.isArray(geom.coordinates?.[0])) activeVs = geom.coordinates[0];
                else if (geom?.type === 'MultiPolygon' && Array.isArray(geom.coordinates?.[0]?.[0])) activeVs = geom.coordinates[0][0];
            } catch {
                activeVs = [];
            }
        }

        const visibleParcels = parcels.filter(p => {
            if (p.latitude == null || p.longitude == null) return false;
            // If filtered by driver or filtered by specific area, show all matching parcel pins immediately!
            if (selectedDriverName || serviceAreas.length === 1) return true;
            // Otherwise, show pins for expanded active area
            if (!activeAreaId) return false;
            const matchedId = p.area_id ?? p.area_version_id;
            if (matchedId != null && Number(matchedId) === Number(activeAreaId)) return true;
            if (activeAreaItem && p.area_code && p.area_code === activeAreaItem.area_code) return true;
            if (activeVs.length > 0 && isPointInPolygon({ lat: Number(p.latitude), lng: Number(p.longitude) }, activeVs)) return true;
            return false;
        });

        // Separate sequenced parcels for custom Waterdrop Markers & Route Polyline
        // When selectedDriverName is set, strictly restrict sequenced parcels to that driver only!
        const sequenced = visibleParcels
            .filter(p => {
                if (p.stop_sequence == null || p.stop_sequence <= 0) return false;
                if (selectedDriverName && p.driver_name && p.driver_name !== selectedDriverName) return false;
                return true;
            })
            .sort((a, b) => (a.stop_sequence ?? 0) - (b.stop_sequence ?? 0));

        const unsequenced = visibleParcels.filter(p => !sequenced.includes(p));

        // Clear previous route polyline
        if (routePolylineRef.current) {
            routePolylineRef.current.setMap(null);
            routePolylineRef.current = null;
        }

        // 1. Render sequenced parcels as SVG Waterdrop Markers with centered Sequence Numbers (#1, #2, ...)
        sequenced.forEach(parcel => {
            const isSel = selected.has(parcel.parcel_id);
            const seq = parcel.stop_sequence;
            const color = isSel ? '#722ed1' : '#1677ff';

            const waterdropSvg = `
                <svg xmlns="http://www.w3.org/2000/svg" width="30" height="38" viewBox="0 0 30 38">
                    <path d="M15 0C6.72 0 0 6.72 0 15c0 11.25 15 23 15 23s15-11.75 15-23C30 6.72 23.28 0 15 0z" fill="${color}" stroke="#ffffff" stroke-width="2"/>
                    <text x="15" y="19" fill="#ffffff" font-size="12" font-family="Arial, sans-serif" font-weight="bold" text-anchor="middle">
                        ${seq}
                    </text>
                </svg>
            `;

            const marker = new google.maps.Marker({
                position: { lat: Number(parcel.latitude), lng: Number(parcel.longitude) },
                map: map.current,
                title: `#${seq} - ${parcel.tracking_no}${parcel.driver_name ? ' (' + parcel.driver_name + ')' : ''}`,
                icon: {
                    url: 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(waterdropSvg),
                    anchor: new google.maps.Point(15, 38)
                },
                zIndex: isSel ? 300 : 150
            });

            marker.addListener('click', () => {
                if (lassoActiveRef.current) return;
                onToggle(parcel.parcel_id);
                onSelect(parcel);
            });

            markersRef.current.push(marker);
        });

        // 2. Draw Polyline route connecting sequenced stops per driver
        // Group sequenced parcels by driver to draw distinct, unmixed routes for each driver
        const driverGroups = new Map<string, PlanningParcel[]>();
        sequenced.forEach(p => {
            const dKey = p.driver_name || (p.driver_id ? `DRIVER-${p.driver_id}` : 'UNKNOWN');
            if (selectedDriverName && p.driver_name !== selectedDriverName) return;
            if (!driverGroups.has(dKey)) driverGroups.set(dKey, []);
            driverGroups.get(dKey)!.push(p);
        });

        if (map.current) {
            driverGroups.forEach((dParcels) => {
                if (dParcels.length > 1) {
                    const routePath = dParcels.map(p => ({ lat: Number(p.latitude), lng: Number(p.longitude) }));
                    const routePolyline = new google.maps.Polyline({
                        map: map.current,
                        path: routePath,
                        clickable: false,
                        strokeColor: '#1890ff',
                        strokeOpacity: 0.85,
                        strokeWeight: 3.5,
                        zIndex: 120
                    });
                    drawingRef.current.push(routePolyline);
                }
            });
        }

        // 3. Render unsequenced parcels on standard DataLayer
        const features = unsequenced.map(parcel => ({
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
    }, [byId, ready, parcels, activeAreaId, serviceAreas, selected, selectedDriverName, onToggle, onSelect]);

    // Draw Interactive Lasso Polygon / Polyline Overlay

    useEffect(() => {
        const m = map.current;
        if (!m || !ready) return;

        drawingRef.current.forEach(shape => shape.setMap(null));
        drawingRef.current = [];

        const path = lassoPoints.map(([lng, lat]) => ({ lng, lat }));
        if (path.length > 2) {
            const polygon = new google.maps.Polygon({
                map: m,
                paths: path,
                clickable: false,
                strokeColor: '#fa8c16',
                strokeWeight: 3,
                fillColor: '#ffd591',
                fillOpacity: 0.35,
                zIndex: 200
            });
            drawingRef.current.push(polygon);

            // Compute enclosed parcels using point-in-polygon algorithm
            if (onLassoSelect) {
                const matchedIds: number[] = [];
                parcels.forEach(p => {
                    if (p.latitude != null && p.longitude != null) {
                        const inside = isPointInPolygon({ lat: Number(p.latitude), lng: Number(p.longitude) }, lassoPoints);
                        if (inside) matchedIds.push(p.parcel_id);
                    }
                });
                const key = matchedIds.sort((a, b) => a - b).join(',');
                if (lastLassoMatchedKeyRef.current !== key) {
                    lastLassoMatchedKeyRef.current = key;
                    onLassoSelect(matchedIds);
                }
            }
        } else if (path.length > 1) {
            const polyline = new google.maps.Polyline({
                map: m,
                path: path,
                clickable: false,
                strokeColor: '#fa8c16',
                strokeWeight: 3,
                zIndex: 200
            });
            drawingRef.current.push(polyline);
        }

        lassoPoints.forEach(([lng, lat]) => {
            const ptMarker = new google.maps.Marker({
                position: { lat, lng },
                map: m,
                clickable: false,
                icon: {
                    path: google.maps.SymbolPath.CIRCLE,
                    scale: 5,
                    fillColor: '#fa8c16',
                    fillOpacity: 1,
                    strokeColor: '#ffffff',
                    strokeWeight: 2
                },
                zIndex: 205
            });
            drawingRef.current.push(ptMarker as any);
        });
    }, [ready, lassoPoints, parcels, onLassoSelect]);

    // Clear lasso points if lasso mode turned off
    useEffect(() => {
        if (!lassoActive) {
            setLassoPoints([]);
        }
    }, [lassoActive]);

    const locatable = parcels.filter(p => p.latitude != null && p.longitude != null).length;

    if (!key) return <Alert type="warning" showIcon message="Google Maps API key is not configured" />;

    return (
        <div className="planning-map-wrap" style={{ position: 'relative' }}>
            {error && <Alert type="error" showIcon message={error} />}
            
            {lassoActive && (
                <div style={{
                    position: 'absolute',
                    top: 10,
                    left: 10,
                    zIndex: 300,
                    background: 'rgba(0,0,0,0.85)',
                    color: '#fff',
                    padding: '8px 12px',
                    borderRadius: '6px',
                    fontSize: '12px',
                    boxShadow: '0 4px 12px rgba(0,0,0,0.25)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px'
                }}>
                    <span>
                        <i className="fa-solid fa-draw-polygon" style={{ color: '#fa8c16', marginRight: 6 }}></i>
                        🖊️ <b>地图套索圈选模式:</b> 请在地图上连续点击 3 个以上点位围成封闭多边形圈选包裹
                    </span>
                    <Tag color="orange">{lassoPoints.length} 个顶点</Tag>
                    {lassoPoints.length > 0 && (
                        <Button size="small" type="primary" danger onClick={() => setLassoPoints([])}>
                            重置画圈
                        </Button>
                    )}
                </div>
            )}

            <div ref={node} className="planning-map" aria-label="Parcel planning map" style={{ minHeight: '480px', borderRadius: '8px' }} />

            <Space className="map-status" wrap style={{ marginTop: '8px' }}>
                {selectedDriverName && <Tag color="blue">👤 正在查看司机【{selectedDriverName}】已分配的包裹 ({parcels.length} 件)</Tag>}
                <Button size="small" onClick={fitAll} disabled={!ready || !locatable}>全图适应</Button>
                {activeAreaId && <Button size="small" type="link" onClick={() => onSelectArea?.(undefined)}>重置区域视图</Button>}
            </Space>

        </div>
    );
}


