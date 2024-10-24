// Place your Spring DSL code here


import au.org.ala.spatial.web.NoSSOStrategy
import com.github.ziplet.filter.compression.CompressingFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.core.Ordered
import org.springframework.web.servlet.i18n.FixedLocaleResolver

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

    // Use fixed English locale, prevents issues with parsing of BBox decimal values in some locales
    // https://github.com/AtlasOfLivingAustralia/spatial-service/issues/247
    localeResolver(FixedLocaleResolver, new Locale('en'))
}
