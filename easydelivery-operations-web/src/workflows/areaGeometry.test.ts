import { describe, expect, it } from 'vitest';
import { normalizeAreaGeoJson, parseAreaGeoJson, polygonGeoJson } from './areaGeometry';

describe('area map geometry', () => {
    it('closes a drawn polygon ring', () => {
        expect(polygonGeoJson([[-63.6, 44.6], [-63.5, 44.6], [-63.5, 44.7]])).toEqual({
            type: 'Polygon', coordinates: [[[-63.6, 44.6], [-63.5, 44.6], [-63.5, 44.7], [-63.6, 44.6]]],
        });
    });
    it('requires three points and unwraps a Feature', () => {
        expect(() => polygonGeoJson([[1, 2], [3, 4]])).toThrow();
        expect(parseAreaGeoJson('{"type":"Feature","geometry":{"type":"Polygon","coordinates":[[[0,0],[1,0],[0,0]]]}}'))
            .toEqual({ type: 'Polygon', coordinates: [[[0, 0], [1, 0], [0, 0]]] });
    });
    it('combines geojson.io FeatureCollection polygon features', () => {
        expect(normalizeAreaGeoJson({
            type: 'FeatureCollection', features: [
                { type: 'Feature', geometry: { type: 'Polygon', coordinates: [[[0, 0], [1, 0], [0, 0]]] } },
                { type: 'Feature', geometry: { type: 'MultiPolygon', coordinates: [[[[2, 2], [3, 2], [2, 2]]]] } },
            ],
        })).toEqual({
            type: 'MultiPolygon',
            coordinates: [[[[0, 0], [1, 0], [0, 0]]], [[[2, 2], [3, 2], [2, 2]]]],
        });
    });
    it('rejects empty collections and non-polygon features', () => {
        expect(() => normalizeAreaGeoJson({ type: 'FeatureCollection', features: [] })).toThrow();
        expect(() => normalizeAreaGeoJson({ type: 'Feature', geometry: { type: 'Point', coordinates: [0, 0] } })).toThrow();
    });
});
