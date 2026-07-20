export type Position = [number, number];

export function polygonGeoJson(points: Position[]) {
    if (points.length < 3) throw new Error('At least three points are required');
    return { type: 'Polygon' as const, coordinates: [[...points, points[0]]] };
}

export function parseAreaGeoJson(value?: string): object | undefined {
    if (!value?.trim()) return undefined;
    const parsed = JSON.parse(value) as { type?: string; geometry?: object };
    if (parsed.type === 'Feature') return parsed.geometry;
    if (parsed.type === 'Polygon' || parsed.type === 'MultiPolygon') return parsed;
    throw new Error('Only Feature, Polygon, or MultiPolygon is supported');
}
