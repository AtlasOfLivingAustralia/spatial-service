import au.org.ala.layers.dto.*

class BootStrap {

    def monitorService
    def slaveService
    def grailsApplication

    def init = { servletContext ->

        //layers-store classes requiring an updated marshaller
        [AnalysisLayer, Distribution, Facet, Field, Layer, Objects, SearchObject, Tabulation, Task].each { clazz ->
            grails.converters.JSON.registerObjectMarshaller(clazz) {
                it.properties.findAll { it.value != null && it.key != 'class' }
            }
        }

        //return dates consistent with layers-service
        grails.converters.JSON.registerObjectMarshaller(Date) {
            return it?.getTime();
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
