package au.org.ala.spatial.service

import au.org.ala.layers.dto.AnalysisLayer
import au.org.ala.layers.dto.Distribution
import au.org.ala.layers.dto.Facet
import au.org.ala.layers.dto.Field
import au.org.ala.layers.dto.Layer
import au.org.ala.layers.dto.Objects
import au.org.ala.layers.dto.SearchObject
import au.org.ala.layers.dto.Tabulation
import au.org.ala.layers.grid.GridCutter
import au.org.ala.layers.intersect.IntersectConfig
import grails.config.Config
import grails.converters.JSON

import java.lang.reflect.Array

class BootStrap {

    def monitorService
    def slaveService
    def grailsApplication
    def masterService
    def tasksService
    def legacyService
    def fieldDao
    def groovySql

    def init = { servletContext ->

        layersStoreConfig(grailsApplication.config)

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
        try {
            def rs = groovySql.executeQuery("SELECT * FROM fields WHERE id = '${grailsApplication.config.userObjectsField;}'")
            if (rs.isClosed() || rs.getRow() == 0) {
                groovySql.execute("INSERT INTO fields (id, name, \"desc\", type, indb, enabled, namesearch) VALUES " +
                        "('${grailsApplication.config.userObjectsField}', 'user', '', 'c', false, true, false);")
            }
        } catch (Exception e) {
            log.error("Error ", e)
        }

        //create missing azimuth function from st_azimuth
        try {
            groovySql.execute('CREATE OR REPLACE FUNCTION azimuth (anyelement, anyelement) returns double precision language sql as $$ select st_azimuth($1, $2) $$')
        } catch (Exception e) {
            log.error("Error creating missing azimuth function frmo st_azimuth", e)
        }
    }

    def layersStoreConfig(Config config) {
        //set layers-store values that are determined from other config
        config.with {
            layers_store.ALASPATIAL_OUTPUT_PATH=data.dir + File.separator + 'layer'
            layers_store.LAYER_FILES_PATH=data.dir + File.separator
            layers_store.GRID_CACHE_PATH=data.dir + File.separator + 'doesnotexist' + File.separator
            layers_store.GEOSERVER_URL=geoserver.url
            layers_store.GEOSERVER_USERNAME=geoserver.username
            layers_store.GEOSERVER_PASSWORD=geoserver.password
            layers_store.GDAL_PATH=gdal.dir
            layers_store.ANALYSIS_RESOLUTIONS=grdResolutions.join(',')
            layers_store.ANALYSIS_LAYER_FILES_PATH=data.dir + File.separator + 'standard_layer'
            layers_store.ANALYSIS_TMP_LAYER_FILES_PATH=data.dir + File.separator + 'private' + File.separator
            layers_store.OCCURRENCE_SPECIES_RECORDS_FILENAME=data.dir + File.separator + 'private' + File.separator + 'occurrenceSpeciesRecords.csv'
            layers_store.UPLOADED_SHAPES_FIELD_ID=userObjectsField
            layers_store.API_KEY_CHECK_URL_TEMPLATE=apiKeyCheckUrlTemplate
            layers_store.SPATIAL_PORTAL_APP_NAME=spatialhub
            layers_store.BIOCACHE_SERVICE_URL=biocacheServiceUrl
            layers_store.SHP2PGSQL_PATH=shp2pgsql.path
        }

        //ensure layers-store uses these config values
        try {
            File layersStoreConfig = File.createTempFile("layersStoreConfig","")
            System.setProperty("layers.store.config.path", layersStoreConfig.getPath())

            log.info("layers-store config file path: ${layersStoreConfig.getPath()}")

            def stream = new FileOutputStream(layersStoreConfig)
            config.layers_store.toProperties().store(stream, '')
            stream.flush()
            stream.close()
        } catch (Exception ex) {
            log.error("layers-store configuration failed.", ex)
        }
        //init layers-store IntersectConfig
        IntersectConfig.getAlaspatialOutputPath()

        //correct for au.org.ala.layers.client.Client in au.org.ala.layers.grid.GridCutter
        GridCutter.setLayersUrl((String) config.grails.serverURL)
    }

    def destroy = {
    }
}
