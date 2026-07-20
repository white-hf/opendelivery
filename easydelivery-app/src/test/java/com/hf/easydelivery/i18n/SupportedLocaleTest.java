package com.hf.easydelivery.i18n;

import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.common.i18n.SupportedLocale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupportedLocaleTest {
    @Test void canonicalizesSupportedTags() {
        assertEquals("fr-CA",SupportedLocale.canonicalTag("FR-ca"));
        assertEquals("en-CA",SupportedLocale.canonicalTag(null));
    }

    @Test void rejectsUnsupportedProfileLocale() {
        assertThrows(BizException.class,()->SupportedLocale.canonicalTag("de-DE"));
    }
}
