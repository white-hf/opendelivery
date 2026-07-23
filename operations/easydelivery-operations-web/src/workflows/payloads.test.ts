import { describe, expect, it } from 'vitest';
import { dispatchDraftPayload, inboundDecision, scanPayload } from './payloads';

describe('workflow payloads', () => {
    it('normalizes a damaged inbound scan and preserves its idempotency key', () => {
        expect(scanPayload('  PKG-1 ', true, 'device-event-1')).toEqual({
            trackingNo: 'PKG-1', deviceEventId: 'device-event-1', conditionCode: 'DAMAGED',
        });
    });

    it('normalizes dispatch identifiers and removes duplicate parcels', () => {
        expect(dispatchDraftPayload({
            waveCode: ' yhz-am ', serviceDate: '2026-07-20', routeCode: ' r1 ', driverId: 9,
        }, [' PKG-1 ', 'PKG-1', 'PKG-2'])).toEqual({
            waveCode: 'YHZ-AM', serviceDate: '2026-07-20', routeCode: 'R1', driverId: 9,
            trackingNumbers: ['PKG-1', 'PKG-2'],
        });
    });

    it('maps only discrepancy states with a safe pilot resolution', () => {
        expect(inboundDecision('MISSING')).toBe('CONFIRM_MISSING');
        expect(inboundDecision('EXTRA')).toBe('QUARANTINE');
        expect(inboundDecision('WRONG_STATION')).toBe('QUARANTINE');
        expect(inboundDecision('DAMAGED')).toBe('ACCEPT_DAMAGED');
        expect(inboundDecision('RECEIVED')).toBeUndefined();
    });
});
