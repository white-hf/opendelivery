import { describe, expect, it } from 'vitest';
import { allowedPages, PAGE_KEYS } from './permissions';

describe('allowedPages', () => {
    it('gives administrators every pilot workspace', () => {
        expect(allowedPages(['ADMIN'])).toEqual(PAGE_KEYS);
    });

    it('keeps inbound operators out of dispatch and callbacks', () => {
        expect(allowedPages(['INBOUND'])).toEqual(['dashboard','orders','manifests','scanning','handover','delivery','closeout','cases']);
    });

    it('unions multiple roles in stable navigation order', () => {
        expect(allowedPages(['DISPATCHER', 'INBOUND'])).toEqual([
            'dashboard','orders','dispatch','manifests','scanning','handover','delivery','closeout','cases','areas','drivers',
        ]);
    });

    it('grants nothing for an unknown role', () => {
        expect(allowedPages(['DRIVER'])).toEqual([]);
    });
});
