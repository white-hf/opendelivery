package com.hf.easydelivery.common.i18n;

import com.hf.easydelivery.common.exception.BizException;

import java.util.List;
import java.util.Locale;

public final class SupportedLocale {
    public static final String DEFAULT_TAG="en-CA";
    public static final List<String> TAGS=List.of("en-CA","fr-CA","zh-CN");

    private SupportedLocale() {}

    public static Locale locale(String tag) {
        String canonical=canonicalTag(tag);
        return Locale.forLanguageTag(canonical);
    }

    public static String canonicalTag(String tag) {
        if(tag==null||tag.isBlank()) return DEFAULT_TAG;
        return TAGS.stream().filter(value->value.equalsIgnoreCase(tag.trim())).findFirst()
                .orElseThrow(()->new BizException("LOCALE.NOT.SUPPORTED","Supported locales are "+String.join(", ",TAGS)));
    }
}
