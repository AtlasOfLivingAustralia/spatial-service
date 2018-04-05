// Place your Spring DSL code here
import com.github.ziplet.filter.compression.CompressingFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.core.Ordered

beans = {
    groovySql(groovy.sql.Sql, ref('dataSource'))

    compressionFilter(FilterRegistrationBean) {
        filter = new CompressingFilter()
        order = Ordered.HIGHEST_PRECEDENCE
        urlPatterns = ["/portal/*", "/"]
    }
}
