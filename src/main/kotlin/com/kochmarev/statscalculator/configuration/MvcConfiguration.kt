package com.kochmarev.statscalculator.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.ResourceResolverChain

import javax.servlet.http.HttpServletRequest
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.resource.ResourceResolver

import java.io.IOException

@Configuration
@EnableWebMvc
class MvcConfiguration : WebMvcConfigurer {

    @Bean
    fun messageSource() : ReloadableResourceBundleMessageSource {
        val reloadableResourceBundleMessageSource = ReloadableResourceBundleMessageSource()
        reloadableResourceBundleMessageSource.setBasenames("classpath:validation")
        return reloadableResourceBundleMessageSource
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val resolver: ResourceResolver = ReactResourceResolver()
        registry.addResourceHandler("/**")
            .resourceChain(true)
            .addResolver(resolver)
    }

    class ReactResourceResolver : ResourceResolver {
        companion object {
            // this is the same directory you are using in package.json
            // "build-spring": "react-scripts build &&  mv build static && mv static ../src/main/resources",
            // example REACT_DIR/index.html
            private const val REACT_DIR = "/static/"

            // this is directory inside REACT_DIR for react static files
            // example REACT_DIR/REACT_STATIC_DIR/js/
            // example REACT_DIR/REACT_STATIC_DIR/css/
            private const val REACT_STATIC_DIR = "static"
        }

        private val index: Resource = ClassPathResource(REACT_DIR + "index.html")
        private val rootStaticFiles: List<String> = listOf(
            "favicon.io",
            "asset-manifest.json", "manifest.json", "service-worker.js"
        )

        override fun resolveResource(
            request: HttpServletRequest?, requestPath: String,
            locations: List<Resource>, chain: ResourceResolverChain
        ): Resource? {
            return resolve(requestPath, locations)
        }

        override fun resolveUrlPath(resourcePath: String, locations: List<Resource>, chain: ResourceResolverChain): String? {
            val resolvedResource: Resource = resolve(resourcePath, locations) ?: return null
            return try {
                resolvedResource.url.toString()
            } catch (e: IOException) {
                resolvedResource.filename
            }
        }

        private fun resolve(requestPath: String?, locations: List<Resource>): Resource? {
            if (requestPath == null) return null
            return if (rootStaticFiles.contains(requestPath)
                || requestPath.startsWith(REACT_STATIC_DIR)
            ) {
                ClassPathResource(REACT_DIR + requestPath)
            } else index
        }
    }

}
