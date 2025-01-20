package au.org.ala.spatial

import groovy.transform.CompileStatic
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@CompileStatic
@Configuration
@ConfigurationProperties(prefix = "")
class SpatialConfig {

    DotGrails grails

    static
    class DotGrails {
        String serverURL
    }

    String serviceKey   // used to authenticate copying layers from one spatial service to another

    DotTask task

    Double [] shpResolutions
    Double [] grdResolutions


    DotTimeout admin

    static
    class DotTask {
        DotThreads general
        DotThreads admin
    }

    static
    class DotThreads {
        Integer threads
    }

    DotSecurity security

    static
    class DotSecurity {
        DotEnabled cas
        DotEnabled oidc
    }

    DotRole auth

    static
    class DotRole {
        String admin_role
    }

    static
    class DotEnabled {
        Boolean enabled
    }

    DotUrl namematching
    DotUrl records
    String api_key
    DotUrl lists
    DotUrl collections
    DotGeoserver geoserver

    static class DotGeoserver {
        String url
        Boolean canDeploy
        String username
        String password
        DotRemote remote
        SpatialService spatialservice
    }

    static class SpatialService {
        boolean colocated
    }

    static class DotRemote {
        String geoserver_data_dir
    }
    DotBaseUrl bie
    String sandboxHubUrl
    String sandboxBiocacheServiceUrl
    String biocacheServiceUrl
    String phyloServiceUrl
    String spatialHubUrl
    String gazField
    String userObjectsField
    DotSpatialService spatialService
    JournalMap journalmap

    static
    class DotUrl {
        String url
    }

    static
    class DotBaseUrl {
        String baseURL
    }

    DotUrl openstreetmap

    static
    class DotSpatialService {
        String remote
        String url
    }

    static
    class JournalMap {
        String url
        String api_key
    }

    LayersStore layersStore

    static
    class LayersStore {
        Integer BATCH_THREAD_COUNT
        String LAYER_INDEX_URL
        Boolean LOCAL_SAMPLING
        Long CONFIG_RELOAD_WAIT
        String PRELOADED_SHAPE_FILES
        Integer GRID_BUFFER_SIZE
        Integer GRID_CACHE_READER_COUNT
    }

    Distributions distributions

    DotDownload download
    DotReporting reporting

    static
    class DotReporting {
        List<String> excludedUsers
    }

    static
    class DotDownload {
        DotLayer layer
    }

    static
    class Distributions {
        DotDir cache
        DotGeoserverDistributions geoserver
    }

    DotDir data

    static
    class DotGeoserverDistributions {
        DotUrl image
    }

    static
    class DotDir {
        String dir
    }

    static
    class DotLayer {
        List<String> licence_levels
    }

    DotDataSource dataSource
    static
    class DotDataSource {
        String username
        String password
        String url
    }

    DotGoogle google
    static
    class DotGoogle {
        String apikey
    }

    DotMaxent maxent
    static
    class DotMaxent {
        String mx
        Integer threads
        Integer timeout
    }

    DotAloc aloc
    static
    class DotAloc {
        Integer threads
        String xmx
        Integer timeout
    }

    DotDir gdal

    DotDir publish

    String batch_sampling_passwords
    Integer batch_sampling_points_limit
    Integer batch_sampling_fields_limit
    Integer batch_thread_count
    Integer grid_buffer_size
    String occurrence_species_records_filename

    DotThreads sampling

    DotTimeout controller
    static
    class DotTimeout {
        Integer timeout
    }

    String biocacheUrl

    Boolean sandboxEnabled
    String sandboxSolrUrl
    String pipelinesCmd
    String pipelinesConfig
    String sandboxSolrCollection
    Integer sandboxThreadCount
}
