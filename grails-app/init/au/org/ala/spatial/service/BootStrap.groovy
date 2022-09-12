package au.org.ala.spatial.service

import au.org.ala.layers.dto.*
import au.org.ala.layers.grid.GridCutter
import au.org.ala.layers.intersect.IntersectConfig
import au.org.ala.spatial.service.Task
import grails.config.Config
import grails.converters.JSON
import grails.util.Holders
import groovy.util.logging.Slf4j

import java.lang.reflect.Array

@Slf4j
class BootStrap {

    def grailsApplication
    def legacyService
    def groovySql
    def messageSource

    def init = { servletContext ->
        messageSource.setBasenames(
                "file:///var/opt/atlas/i18n/spatial-service/messages",
                "file:///opt/atlas/i18n/spatial-service/messages",
                "WEB-INF/grails-app/i18n/messages",
                "classpath:messages"
        )

        layersStoreConfig(grailsApplication.config)

        legacyService.apply()

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

        //create database required by layers-store
        try {
            def rs = groovySql.rows("SELECT * FROM fields WHERE id = ?", [grailsApplication.config.userObjectsField])
            if (rs.size() == 0) {
                groovySql.execute("INSERT INTO fields (id, name, \"desc\", type, indb, enabled, namesearch) VALUES " +
                        "('${grailsApplication.config.userObjectsField}', 'user', '', 'c', false, false, false);")
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("duplicate key value")) {
                log.error("Error ", e)
            }
        }

        //create missing azimuth function from st_azimuth
        try {
            groovySql.execute('CREATE OR REPLACE FUNCTION azimuth (anyelement, anyelement) returns double precision language sql as $$ select st_azimuth($1, $2) $$')
        } catch (Exception e) {
            log.error("Error creating missing azimuth function frmo st_azimuth", e)
        }

        //create objects name idx if it is missing
        try {
            def rs = groovySql.rows("SELECT * FROM pg_class WHERE relname = 'objects_name_idx';")
            if (rs.size() == 0) {
                groovySql.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm;")
                groovySql.execute("CREATE INDEX objects_name_idx ON objects USING gin (name gin_trgm_ops) WHERE namesearch is true;")
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("already exists")) {
                log.error("Error ", e)
            }
        }
    }

    def layersStoreConfig(Config config) {
        //set layers-store values that are determined from other config
        def resolutions = config.grdResolutions
        if (!(resolutions instanceof List)) {
            // comma separated or JSON list
            if (resolutions.toString().startsWith("[")) {
                resolutions = new org.json.simple.parser.JSONParser().parse(resolutions.toString())
            } else {
                resolutions = Arrays.asList(resolutions.toString().split(","))
            }
        }
        config.with {
            layers_store.ALASPATIAL_OUTPUT_PATH=data.dir + File.separator + 'layer'
            layers_store.LAYER_FILES_PATH=data.dir + File.separator
            layers_store.GRID_CACHE_PATH=data.dir + File.separator + 'doesnotexist' + File.separator
            layers_store.GEOSERVER_URL=geoserver.url
            layers_store.GEOSERVER_USERNAME=geoserver.username
            layers_store.GEOSERVER_PASSWORD=geoserver.password
            layers_store.GDAL_PATH=gdal.dir
            layers_store.ANALYSIS_RESOLUTIONS = resolutions.join(',')
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
