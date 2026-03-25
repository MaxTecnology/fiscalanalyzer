package br.com.techbr.fiscalanalyzer.agent.config;

import br.com.techbr.fiscalanalyzer.agent.security.ApiSecurityInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    private final ApiSecurityInterceptor apiSecurityInterceptor;

    public WebSecurityConfig(ApiSecurityInterceptor apiSecurityInterceptor) {
        this.apiSecurityInterceptor = apiSecurityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiSecurityInterceptor)
                .addPathPatterns("/**");
    }
}
