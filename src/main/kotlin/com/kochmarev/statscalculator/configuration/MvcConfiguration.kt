package com.kochmarev.statscalculator.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.view.InternalResourceViewResolver

@Configuration
class MvcConfiguration {

    @Bean
    fun messageSource() : ReloadableResourceBundleMessageSource {
        val reloadableResourceBundleMessageSource = ReloadableResourceBundleMessageSource()
        reloadableResourceBundleMessageSource.setBasenames("classpath:validation")
        return reloadableResourceBundleMessageSource
    }

    @Bean
    fun internalResourceViewResolver(): InternalResourceViewResolver {
        val internalResourceViewResolver = InternalResourceViewResolver()
        internalResourceViewResolver.setPrefix("/WEB-INF/views/")
        internalResourceViewResolver.setSuffix(".jsp")
        return internalResourceViewResolver
    }
}