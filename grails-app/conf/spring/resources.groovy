// Place your Spring DSL code here


import au.org.ala.spatial.web.NoSSOStrategy
import com.github.ziplet.filter.compression.CompressingFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.core.Ordered

beans = {
    compressionFilter(FilterRegistrationBean) {
        filter = new CompressingFilter()
        order = Ordered.HIGHEST_PRECEDENCE
        urlPatterns = ["/portal/*", "/"]
    }

    // fix for running without cas and without oidc
    if (!application.config.security.cas.enabled && !application.config.security.oidc.enabled) {
        noSSOStrategy(NoSSOStrategy) {}
    }
}
