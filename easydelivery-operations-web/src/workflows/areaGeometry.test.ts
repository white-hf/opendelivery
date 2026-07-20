import { describe, expect, it } from 'vitest';
import { parseAreaGeoJson, polygonGeoJson } from './areaGeometry';

describe('area map geometry', () => {
    it('closes a drawn polygon ring', () => {
        expect(polygonGeoJson([[-63.6, 44.6], [-63.5, 44.6], [-63.5, 44.7]])).toEqual({
            type: 'Polygon', coordinates: [[[-63.6, 44.6], [-63.5, 44.6], [-63.5, 44.7], [-63.6, 44.6]]],
        });
    });
    it('requires three points and unwraps a Feature', () => {
        expect(() => polygonGeoJson([[1, 2], [3, 4]])).toThrow();
        expect(parseAreaGeoJson('{"type":"Feature","geometry":{"type":"Polygon","coordinates":[]}}'))
            .toEqual({ type: 'Polygon', coordinates: [] });
    });
});
