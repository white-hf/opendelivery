export type Position = [number, number];
type Geometry = { type: 'Polygon' | 'MultiPolygon'; coordinates: unknown[] };

export function polygonGeoJson(points: Position[]) {
    if (points.length < 3) throw new Error('At least three points are required');
    return { type: 'Polygon' as const, coordinates: [[...points, points[0]]] };
}

export function parseAreaGeoJson(value?: string): Geometry | undefined {
    if (!value?.trim()) return undefined;
    return normalizeAreaGeoJson(JSON.parse(value));
}

export function normalizeAreaGeoJson(input: unknown): Geometry {
    if (!input || typeof input !== 'object') throw new Error('GeoJSON must be an object');
    const parsed = input as { type?: string; geometry?: unknown; features?: unknown[]; coordinates?: unknown[] };
    if (parsed.type === 'Feature') return normalizeAreaGeoJson(parsed.geometry);
    if (parsed.type === 'Polygon' || parsed.type === 'MultiPolygon') {
        if (!Array.isArray(parsed.coordinates) || parsed.coordinates.length === 0) throw new Error('Geometry is empty');
        return { type: parsed.type, coordinates: parsed.coordinates };
    }
    if (parsed.type === 'FeatureCollection') {
        if (!Array.isArray(parsed.features) || parsed.features.length === 0) throw new Error('FeatureCollection is empty');
        const polygons: unknown[] = [];
        parsed.features.forEach((feature) => {
            const candidate = feature as { type?: string; geometry?: { type?: string } };
            if (candidate?.type !== 'Feature') throw new Error('FeatureCollection contains an invalid feature');
            if (candidate.geometry?.type !== 'Polygon' && candidate.geometry?.type !== 'MultiPolygon') return;
            const geometry = normalizeAreaGeoJson(feature);
            if (geometry.type === 'Polygon') polygons.push(geometry.coordinates);
            else polygons.push(...geometry.coordinates);
        });
        if (!polygons.length) throw new Error('FeatureCollection contains no polygon geometry');
        return { type: 'MultiPolygon', coordinates: polygons };
    }
    throw new Error('Only FeatureCollection, Feature, Polygon, or MultiPolygon is supported');
}

export function areaGeoJsonSummary(value: string) {
    const parsed = JSON.parse(value) as { type?: string; features?: Array<{ geometry?: { type?: string } }> };
    if (parsed.type !== 'FeatureCollection' || !Array.isArray(parsed.features)) {
        return { polygonCount: 1, ignoredFeatureCount: 0 };
    }
    const polygonCount = parsed.features.filter((feature) =>
        feature.geometry?.type === 'Polygon' || feature.geometry?.type === 'MultiPolygon').length;
    return { polygonCount, ignoredFeatureCount: parsed.features.length - polygonCount };
}
