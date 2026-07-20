import { useEffect, useMemo, useRef, useState } from 'react';
import { importLibrary, setOptions } from '@googlemaps/js-api-loader';
import { Alert, Button, Space, Typography, notification } from 'antd';
import { useTranslation } from 'react-i18next';
import { parseAreaGeoJson, polygonGeoJson, type Position } from './areaGeometry';

const CENTERS: Record<string, google.maps.LatLngLiteral> = {
    'YHZ-01': { lat: 44.6488, lng: -63.5752 },
    'YYZ-01': { lat: 43.6532, lng: -79.3832 },
    'YVR-01': { lat: 49.2827, lng: -123.1207 },
};
let mapsPromise: Promise<google.maps.MapsLibrary> | undefined;

function loadMaps(key: string) {
    if (!mapsPromise) {
        setOptions({ key, v: 'weekly', authReferrerPolicy: 'origin' });
        mapsPromise = importLibrary('maps');
    }
    return mapsPromise;
}

function extendBounds(bounds: google.maps.LatLngBounds, coordinates: unknown) {
    if (!Array.isArray(coordinates)) return;
    if (coordinates.length >= 2 && typeof coordinates[0] === 'number' && typeof coordinates[1] === 'number') {
        bounds.extend({ lng: coordinates[0], lat: coordinates[1] });
        return;
    }
    coordinates.forEach((child) => extendBounds(bounds, child));
}

export function AreaMapEditor({ station, value, onChange }: { station: string; value?: string; onChange: (value: string) => void }) {
    const { t } = useTranslation();
    const [notice, noticeContext] = notification.useNotification();
    const containerRef = useRef<HTMLDivElement>(null);
    const mapRef = useRef<google.maps.Map | undefined>(undefined);
    const dataRef = useRef<google.maps.Data | undefined>(undefined);
    const clickRef = useRef<google.maps.MapsEventListener | undefined>(undefined);
    const drawingRef = useRef<Array<google.maps.Polygon | google.maps.Polyline>>([]);
    const [points, setPoints] = useState<Position[]>([]);
    const [mapReady, setMapReady] = useState(false);
    const [loadError, setLoadError] = useState('');
    const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY?.trim();
    const geometry = useMemo(() => { try { return parseAreaGeoJson(value); } catch { return undefined; } }, [value]);

    useEffect(() => {
        if (!apiKey || !containerRef.current) return;
        let active = true;
        void loadMaps(apiKey).then(({ Map }) => {
            if (!active || !containerRef.current) return;
            const map = new Map(containerRef.current, {
                center: CENTERS[station] ?? CENTERS['YHZ-01'], zoom: 11,
                mapTypeControl: false, streetViewControl: false, fullscreenControl: true,
                gestureHandling: 'greedy', clickableIcons: false,
            });
            mapRef.current = map;
            dataRef.current = new google.maps.Data({ map });
            dataRef.current.setStyle({ fillColor: '#1677ff', fillOpacity: 0.22, strokeColor: '#0958d9', strokeWeight: 3 });
            clickRef.current = map.addListener('click', (event: google.maps.MapMouseEvent) => {
                if (event.latLng) setPoints((current) => [...current, [event.latLng!.lng(), event.latLng!.lat()]]);
            });
            setMapReady(true);
        }).catch(() => {
            if (active) setLoadError(t('areas.googleMapsLoadFailed'));
        });
        return () => {
            active = false;
            clickRef.current?.remove();
            dataRef.current?.setMap(null);
            drawingRef.current.forEach((shape) => shape.setMap(null));
        };
    }, [apiKey, station, t]);

    useEffect(() => {
        if (!mapRef.current || !mapReady) return;
        mapRef.current.setCenter(CENTERS[station] ?? CENTERS['YHZ-01']);
        mapRef.current.setZoom(11);
    }, [mapReady, station]);

    useEffect(() => {
        const map = mapRef.current;
        const data = dataRef.current;
        if (!map || !data || !mapReady) return;
        data.forEach((feature) => data.remove(feature));
        if (!geometry) return;
        data.addGeoJson({ type: 'Feature', properties: {}, geometry });
        const bounds = new google.maps.LatLngBounds();
        extendBounds(bounds, geometry.coordinates);
        if (!bounds.isEmpty()) map.fitBounds(bounds, 32);
    }, [geometry, mapReady]);

    useEffect(() => {
        const map = mapRef.current;
        if (!map || !mapReady) return;
        drawingRef.current.forEach((shape) => shape.setMap(null));
        drawingRef.current = [];
        const path = points.map(([lng, lat]) => ({ lng, lat }));
        if (path.length > 2) {
            drawingRef.current.push(new google.maps.Polygon({
                map, paths: path, clickable: false, strokeColor: '#fa8c16', strokeWeight: 4,
                fillColor: '#ffd591', fillOpacity: 0.35,
            }));
        } else if (path.length > 1) {
            drawingRef.current.push(new google.maps.Polyline({ map, path, clickable: false, strokeColor: '#fa8c16', strokeWeight: 4 }));
        }
        points.forEach(([lng, lat]) => drawingRef.current.push(new google.maps.Polygon({
            map, paths: [[{ lng: lng - 0.00008, lat: lat - 0.00005 }, { lng: lng + 0.00008, lat: lat - 0.00005 },
                { lng: lng + 0.00008, lat: lat + 0.00005 }, { lng: lng - 0.00008, lat: lat + 0.00005 }]],
            clickable: false, strokeColor: '#d46b08', fillColor: '#fa8c16', fillOpacity: 1, strokeWeight: 1,
        })));
    }, [mapReady, points]);

    return <Space direction="vertical" style={{ width: '100%' }}>
        {noticeContext}
        <Typography.Text type="secondary">{t('areas.drawHelp')}</Typography.Text>
        {!apiKey && <Alert type="warning" showIcon message={t('areas.googleMapsMissing')} />}
        {loadError && <Alert type="error" showIcon message={loadError} />}
        <div ref={containerRef} className="area-map" />
        <Space wrap>
            <Typography.Text>{t('areas.pointCount', { count: points.length })}</Typography.Text>
            <Button disabled={!points.length} onClick={() => setPoints((current) => current.slice(0, -1))}>{t('areas.undoPoint')}</Button>
            <Button onClick={() => setPoints([])} disabled={!points.length}>{t('areas.clearDrawing')}</Button>
            <Button type="primary" disabled={points.length < 3} onClick={() => {
                try { onChange(JSON.stringify(polygonGeoJson(points))); setPoints([]); }
                catch { notice.error({ message: t('areas.minimumPoints'), placement: 'topRight' }); }
            }}>{t('areas.useDrawing')}</Button>
        </Space>
    </Space>;
}
