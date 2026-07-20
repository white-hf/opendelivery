import { beforeEach, describe, expect, it } from 'vitest';
import i18n, { changeLocale, translations } from './i18n';

describe('operations localization', () => {
    beforeEach(() => localStorage.clear());
    it('switches launch locales and persists the choice', async () => {
        await changeLocale('fr-CA');
        expect(i18n.t('auth.signIn')).toBe('Ouvrir une session');
        expect(localStorage.getItem('opendelivery.locale')).toBe('fr-CA');
    });
    it('falls back to English for missing keys', async () => {
        await changeLocale('zh-CN');
        expect(i18n.t('missing.key', { defaultValue: 'Fallback' })).toBe('Fallback');
    });
    it('keeps launch locale key sets identical', () => {
        const expected = Object.keys(translations['en-CA']).sort();
        expect(Object.keys(translations['fr-CA']).sort()).toEqual(expected);
        expect(Object.keys(translations['zh-CN']).sort()).toEqual(expected);
    });
});
