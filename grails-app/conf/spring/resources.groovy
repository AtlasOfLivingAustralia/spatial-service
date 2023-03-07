// Place your Spring DSL code here


import au.org.ala.web.NoSSOStrategy
import au.org.ala.web.SSOStrategy
import com.github.ziplet.filter.compression.CompressingFilter
import grails.util.Holders
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.core.Ordered
import au.org.ala.UnsanitizedUrlMappingsHandlerMapping

beans = {
    groovySql(groovy.sql.Sql, ref('dataSource'))

    compressionFilter(FilterRegistrationBean) {
        filter = new CompressingFilter()
        order = Ordered.HIGHEST_PRECEDENCE
        urlPatterns = ["/portal/*", "/"]
    }

    urlMappingsHandlerMapping(UnsanitizedUrlMappingsHandlerMapping) {}

    // fix for running without cas and without oidc
    if (!Holders.config.security.cas.enabled && !Holders.config.security.oidc.enabled) {
        noSSOStrategy(NoSSOStrategy) {}
    }
}
