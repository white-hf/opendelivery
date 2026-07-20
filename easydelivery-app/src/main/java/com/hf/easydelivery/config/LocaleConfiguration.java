package com.hf.easydelivery.config;

import com.hf.easydelivery.common.i18n.SupportedLocale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class LocaleConfiguration {
    @Bean
    LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver=new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SupportedLocale.TAGS.stream().map(SupportedLocale::locale).toList());
        resolver.setDefaultLocale(SupportedLocale.locale(SupportedLocale.DEFAULT_TAG));
        return resolver;
    }

}
