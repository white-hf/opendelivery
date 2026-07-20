package com.hf.easydelivery.i18n;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageBundleCompletenessTest {
    @Test void launchLocaleBundlesHaveIdenticalKeys() throws Exception {
        Properties base=load("i18n/messages.properties");
        assertEquals(base.keySet(),load("i18n/messages_en_CA.properties").keySet());
        assertEquals(base.keySet(),load("i18n/messages_fr_CA.properties").keySet());
        assertEquals(base.keySet(),load("i18n/messages_zh_CN.properties").keySet());
    }

    private Properties load(String path) throws Exception {
        Properties properties=new Properties();
        try(var stream=getClass().getClassLoader().getResourceAsStream(path)) {
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
