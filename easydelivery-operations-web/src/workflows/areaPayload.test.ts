import { describe, expect, it } from 'vitest';
import { areaPayload } from './areaPayload';

describe('areaPayload', () => {
    it('normalizes operator input and parses GeoJSON', () => {
        expect(areaPayload({
            areaCode: ' dt-01 ', areaName: ' Downtown ', driverIds: [101, 102], geoJson: '{"type":"Polygon","coordinates":[[[0,0],[1,0],[0,0]]]}',
            changeReason: ' Initial boundary ',
        })).toEqual({
            areaCode: 'DT-01', areaName: 'Downtown', areaLevel: 1, driverIds: [101, 102],
            geoJson: { type: 'Polygon', coordinates: [[[0, 0], [1, 0], [0, 0]]] }, changeReason: 'Initial boundary',
        });
    });

    it('rejects malformed GeoJSON before sending it', () => {
        expect(() => areaPayload({ areaCode: 'A', areaName: 'A', driverIds: [101], geoJson: '{', changeReason: 'test' })).toThrow();
    });
});
