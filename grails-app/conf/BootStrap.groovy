import au.org.ala.layers.dto.*
import au.org.ala.layers.grid.GridCutter
import au.org.ala.layers.intersect.IntersectConfig
import grails.converters.JSON
import org.apache.naming.ContextAccessController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import grails.util.Environment

import javax.naming.InitialContext

class BootStrap {

    def monitorService
    def slaveService
    def grailsApplication
    def masterService
    def tasksService
    def legacyService

    def init = { servletContext ->

        legacyService.apply()

        //avoid circular reference
        masterService._tasksService = tasksService

        //layers-store classes requiring an updated marshaller
        [AnalysisLayer, Distribution, Facet, Field, Layer, Objects, SearchObject, Tabulation, Task].each { clazz ->
            JSON.registerObjectMarshaller(clazz) {
                it.properties.findAll { it.value != null && it.key != 'class' }
            }
        }

        //return dates consistent with layers-service
        JSON.registerObjectMarshaller(Date) {
            return it?.getTime()
        }

        if (grailsApplication.config.service.enable) {
            monitorService.init()
        }
        if (grailsApplication.config.slave.enable) {
            slaveService.monitor()
        }
    }

    def destroy = {
    }
}
