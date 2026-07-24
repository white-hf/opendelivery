package com.hf.easydelivery.config;

import com.hf.easydelivery.common.response.AppResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class LocalizedResponseAdvice implements ResponseBodyAdvice<Object> {
    private final MessageSource messages;

    public LocalizedResponseAdvice(MessageSource messages) { this.messages=messages; }

    @Override public boolean supports(MethodParameter parameter,Class<? extends HttpMessageConverter<?>> converter) { return true; }

    @Override
    public Object beforeBodyWrite(Object body,MethodParameter returnType,MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converter,
                                  org.springframework.http.server.ServerHttpRequest request,
                                  org.springframework.http.server.ServerHttpResponse response) {
        if(body instanceof AppResponse<?> envelope) {
            envelope.setBiz_message(messages.getMessage(envelope.getBiz_code(),null,envelope.getBiz_message(),LocaleContextHolder.getLocale()));
        }
        return body;
    }
}
