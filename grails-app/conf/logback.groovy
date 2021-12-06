import grails.util.Environment
import org.springframework.boot.logging.logback.ColorConverter
import org.springframework.boot.logging.logback.WhitespaceThrowableProxyConverter
import java.nio.charset.Charset

conversionRule 'clr', ColorConverter
conversionRule 'wex', WhitespaceThrowableProxyConverter

// See http://logback.qos.ch/manual/groovy.html for details on configuration
appender('STDOUT', ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        charset = Charset.forName('UTF-8')
        pattern =
                '[SPATIAL-SERVICE] %clr(%d{HH:mm:ss}){faint} ' + // Date
                        '%clr(%logger{5}){cyan} %clr(:){faint} ' + // Logger
                        '%m%n%wex' // Message
    }
}
if (Environment.isDevelopmentMode()) {
    logger("au.org.ala.spatial.service.MonitorService", INFO, ['STDOUT'], false)
    logger("au.org.ala.layers", DEBUG, ['STDOUT'], false)
    logger("au.org.ala.spatial", DEBUG, ['STDOUT'], false)
    logger("org.hibernate", ERROR, ['STDOUT'], false)
    logger("au.org.ala",DEBUG, ['STDOUT'],false)
} else {
    logger("au.org.ala.spatial.service.MonitorService", INFO, ['STDOUT'], false)
    logger("au.org.ala.layers", INFO, ['STDOUT'], false)
    logger("au.org.ala.spatial", INFO, ['STDOUT'], false)
    logger("au.org.ala", DEBUG, ['STDOUT'],false)
}


root(ERROR, ['STDOUT'])
