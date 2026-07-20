import { useEffect, useMemo, useState } from 'react';
import L from 'leaflet';
import { CircleMarker, GeoJSON, MapContainer, Polygon, Polyline, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import type { GeoJsonObject } from 'geojson';
import { Button, Space, Typography, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { parseAreaGeoJson, polygonGeoJson, type Position } from './areaGeometry';

const CENTERS: Record<string, [number, number]> = {
    'YHZ-01': [44.6488, -63.5752], 'YYZ-01': [43.6532, -79.3832], 'YVR-01': [49.2827, -123.1207],
};

function ClickCapture({ onPoint }: { onPoint: (point: Position) => void }) {
    useMapEvents({ click: (event) => onPoint([event.latlng.lng, event.latlng.lat]) });
    return null;
}

function FitBoundary({ geometry }: { geometry?: object }) {
    const map = useMap();
    useEffect(() => {
        if (geometry) {
            const bounds = L.geoJSON(geometry as GeoJsonObject).getBounds();
            if (bounds.isValid()) map.fitBounds(bounds, { padding: [24, 24] });
        }
    }, [geometry, map]);
    return null;
}

export function AreaMapEditor({ station, value, onChange }: { station: string; value?: string; onChange: (value: string) => void }) {
    const { t } = useTranslation();
    const [points, setPoints] = useState<Position[]>([]);
    const geometry = useMemo(() => { try { return parseAreaGeoJson(value); } catch { return undefined; } }, [value]);
    const latLngs = points.map(([lng, lat]) => [lat, lng] as [number, number]);
    return <Space direction="vertical" style={{ width: '100%' }}>
        <Typography.Text type="secondary">{t('areas.drawHelp')}</Typography.Text>
        <div className="area-map">
            <MapContainer center={CENTERS[station] ?? CENTERS['YHZ-01']} zoom={11} scrollWheelZoom>
                <TileLayer attribution={import.meta.env.VITE_MAP_ATTRIBUTION ?? '&copy; OpenStreetMap contributors'}
                    url={import.meta.env.VITE_MAP_TILE_URL ?? 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'} />
                <ClickCapture onPoint={(point) => setPoints((current) => [...current, point])} />
                {geometry && <GeoJSON key={value} data={geometry as GeoJsonObject} style={{ color: '#1677ff', weight: 3 }} />}
                <FitBoundary geometry={geometry} />
                {latLngs.length > 2
                    ? <Polygon positions={latLngs} pathOptions={{ color: '#fa8c16', weight: 4, fillColor: '#ffd591', fillOpacity: 0.35 }} />
                    : latLngs.length > 1 && <Polyline positions={latLngs} pathOptions={{ color: '#fa8c16', weight: 4 }} />}
                {latLngs.map((point, index) => <CircleMarker key={`${point[0]}-${point[1]}-${index}`} center={point} radius={5} />)}
            </MapContainer>
        </div>
        <Space>
            <Typography.Text>{t('areas.pointCount', { count: points.length })}</Typography.Text>
            <Button disabled={!points.length} onClick={() => setPoints((current) => current.slice(0, -1))}>{t('areas.undoPoint')}</Button>
            <Button onClick={() => setPoints([])} disabled={!points.length}>{t('areas.clearDrawing')}</Button>
            <Button type="primary" disabled={points.length < 3} onClick={() => {
                try { onChange(JSON.stringify(polygonGeoJson(points))); setPoints([]); }
                catch { message.error(t('areas.minimumPoints')); }
            }}>{t('areas.useDrawing')}</Button>
        </Space>
    </Space>;
}
