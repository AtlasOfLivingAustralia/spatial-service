import au.org.ala.layers.dto.*
import au.org.ala.layers.grid.GridCutter
import au.org.ala.layers.intersect.IntersectConfig
import grails.converters.JSON
import grails.util.GrailsUtil
import org.apache.naming.ContextAccessController

import javax.naming.InitialContext

class BootStrap {

    def monitorService
    def slaveService
    def grailsApplication
    def masterService
    def tasksService
    def legacyService

    def init = { servletContext ->

        layersStoreConfig()

        casConfig()

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

    def layersStoreConfig = {
        //set layers-store values that are determined from other config
        grailsApplication.config.with {
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
            grailsApplication.config.layers_store.toProperties().store(stream, '')
            stream.flush()
            stream.close()
        } catch (Exception ex) {
            log.error("layers-store configuration failed.", ex)
        }
        //init layers-store IntersectConfig
        IntersectConfig.getAlaspatialOutputPath()

        //correct for au.org.ala.layers.client.Client in au.org.ala.layers.grid.GridCutter
        GridCutter.setLayersUrl(grailsApplication.config.grails.serverURL)
    }

    def casConfig = {
        if (GrailsUtil.environment != "test") {
            //set CAS values that are determined from other config
            def url = new URL(grailsApplication.config.grails.serverURL)
            grailsApplication.config.security.cas.appServerName =
                    url.getProtocol() + "://" + url.getHost() + (url.port > 0 ? ':' + url.port : '')
            grailsApplication.config.security.cas.serverName = grailsApplication.config.security.cas.appServerName
            grailsApplication.config.security.cas.contextPath = url.getPath()
            grailsApplication.config.security.cas.casProperties = grailsApplication.config.security.cas.keySet().join(',')

            //set CAS values for ala-cas-client
            grailsApplication.config.security.cas.each { k, v ->
                grailsApplication.config[k] = v
            }

            //ensure ala-cas-client uses these config values
            try {
                File casConfig = File.createTempFile("casConfig", "")
                log.info("cas config file path: ${casConfig.getPath()}")

                def stream = new FileOutputStream(casConfig)
                grailsApplication.config.security.cas.toProperties().store(stream, '')
                stream.flush()
                stream.close()

                java.lang.reflect.Field readOnlyContextsField = ContextAccessController.class.getDeclaredField("readOnlyContexts")
                readOnlyContextsField.setAccessible(true)
                Hashtable hashtable = (Hashtable) readOnlyContextsField.get(null)
                Hashtable backup = hashtable.clone()
                hashtable.clear()

                try {
                    def ctx = new InitialContext()
                    ctx.unbind("java:comp/env/configPropFile")
                    ctx.bind("java:comp/env/configPropFile", casConfig.getPath())
                } catch (Exception e) {
                    e.printStackTrace()
                }

                hashtable.putAll(backup)
            } catch (Exception ex) {
                log.error("CAS configuration failed.", ex)
            }
        }
    }
}
