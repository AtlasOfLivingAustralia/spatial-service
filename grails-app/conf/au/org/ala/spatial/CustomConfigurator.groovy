package au.org.ala.spatial

import au.org.ala.layers.grid.GridCutter
import au.org.ala.layers.intersect.IntersectConfig
import grails.util.Environment
import org.apache.naming.ContextAccessController
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.spring.GrailsRuntimeConfigurator
import org.springframework.context.ApplicationContext

import javax.naming.InitialContext

class CustomConfigurator extends GrailsRuntimeConfigurator {

    CustomConfigurator(GrailsApplication application) {
        this(application, null)
    }

    CustomConfigurator(GrailsApplication application, ApplicationContext parent) {
        super(application, parent)

        layersStoreConfig(application.config)
        casConfig(application.config)
    }

    def layersStoreConfig(config) {
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

    def casConfig(config) {
        if (Environment.getCurrent() != "test") {
            //set CAS values that are determined from other config
            def url = new URL((String) config.grails.serverURL)
            config.security.cas.appServerName =
                    url.getProtocol() + "://" + url.getHost() + (url.port > 0 ? ':' + url.port : '')
            config.security.cas.serverName = config.security.cas.appServerName
            config.security.cas.contextPath = url.getPath()
            config.security.cas.casProperties = config.security.cas.keySet().join(',')

            //set CAS values for ala-cas-client
            config.security.cas.each { k, v ->
                config[k] = v
            }

            //ensure ala-cas-client uses these config values
            try {
                File casConfig = File.createTempFile("casConfig", "")
                log.info("cas config file path: ${casConfig.getPath()}")

                def stream = new FileOutputStream(casConfig)
                config.security.cas.toProperties().store(stream, '')
                stream.flush()
                stream.close()

                java.lang.reflect.Field readOnlyContextsField = ContextAccessController.class.getDeclaredField("readOnlyContexts")
                readOnlyContextsField.setAccessible(true)
                Hashtable hashtable = (Hashtable) readOnlyContextsField.get(null)
                Hashtable backup = (Hashtable) hashtable.clone()
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