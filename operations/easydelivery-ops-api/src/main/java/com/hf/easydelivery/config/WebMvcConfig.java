package com.hf.easydelivery.config;

import com.hf.easydelivery.common.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final java.util.Optional<OperationsAuthInterceptor> operationsAuthInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor, java.util.Optional<OperationsAuthInterceptor> operationsAuthInterceptor) {
        this.authInterceptor = authInterceptor;
        this.operationsAuthInterceptor = operationsAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        operationsAuthInterceptor.ifPresent(interceptor -> registry.addInterceptor(interceptor)
                .addPathPatterns("/ops/v1/**"));
    }
}
