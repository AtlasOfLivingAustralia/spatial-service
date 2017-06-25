
import au.org.ala.layers.dto.AnalysisLayer
import au.org.ala.layers.dto.Distribution
import au.org.ala.layers.dto.Facet
import au.org.ala.layers.dto.Field
import au.org.ala.layers.dto.Layer
import au.org.ala.layers.dto.Objects
import au.org.ala.layers.dto.SearchObject
import au.org.ala.layers.dto.Tabulation
import au.org.ala.spatial.service.InputParameter
import au.org.ala.spatial.service.OutputParameter
import grails.converters.JSON
import au.org.ala.spatial.service.*

import java.lang.reflect.Array

class BootStrap {

    def monitorService
    def slaveService
    def grailsApplication
    def masterService
    def tasksService
    def legacyService
    def fieldDao

    def init = { servletContext ->

        legacyService.apply()

        //avoid circular reference
        masterService._tasksService = tasksService

        //layers-store and domain classes requiring an updated marshaller
        [AnalysisLayer, Distribution, Facet, Field, Layer, Objects, SearchObject, Tabulation,
            Task, Log, InputParameter, OutputParameter].each { clazz ->
            JSON.registerObjectMarshaller(clazz) { i ->
                i.properties.findAll {
                    it.value != null && it.key != 'class' && it.key != '_ref' &&
                        (!(it.value instanceof Collection) || it.value.size() > 0) &&
                        (!(it.value instanceof Array) || it.value.length > 0) &&
                            (it.key != 'task' || !(i instanceof InputParameter)) &&
                            (it.key != 'task' || !(i instanceof OutputParameter))
                } + (i instanceof InputParameter || i instanceof OutputParameter || i instanceof Task ||
                        i instanceof Log? [id: i.id] : [:])
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

        //create user objects field if it is missing
        if (!fieldDao.getFieldById(grailsApplication.config.userObjectsField)) {
            Field f = new Field()
            f.id = grailsApplication.config.userObjectsField
            f.name = 'user'
            f.desc = ''
            f.type = 'c'
            f.indb = false
            f.enabled = true
            f.namesearch = false
            fieldDao.addField(defaultObjects)
        }
    }

    def destroy = {
    }
}
