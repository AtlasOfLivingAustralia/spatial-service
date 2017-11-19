grails.project.groupId = "au.org.ala" // change this to alter the default package name and Maven publishing destination
appName = "spatial-service"

grails.appName = appName

default_config = "/data/${appName}/config/${appName}-config.properties"
if (!grails.config.locations || !(grails.config.locations instanceof List)) {
    grails.config.locations = []
}
if (new File(default_config).exists()) {
    println "[${appName}] Including default configuration file: " + default_config;
    grails.config.locations.add "file:" + default_config
} else {
    println "[${appName}] No external configuration file defined."
}

// The ACCEPT header will not be used for content negotiation for user agents containing the following strings (defaults to the 4 major rendering engines)
grails.mime.disable.accept.header.userAgents = ['Gecko', 'WebKit', 'Presto', 'Trident']
grails.mime.use.accept.header = true
grails.mime.types = [ // the first one is the default format
                      all          : '*/*', // 'all' maps to '*' or the first available format in withFormat
                      atom         : 'application/atom+xml',
                      css          : 'text/css',
                      csv          : 'text/csv',
                      form         : 'application/x-www-form-urlencoded',
                      html         : ['text/html', 'application/xhtml+xml'],
                      js           : 'text/javascript',
                      json         : ['application/json', 'text/json'],
                      multipartForm: 'multipart/form-data',
                      rss          : 'application/rss+xml',
                      text         : 'text/plain',
                      hal          : ['application/hal+json', 'application/hal+xml'],
                      xml          : ['text/xml', 'application/xml']
]

// URL Mapping Cache Max Size, defaults to 5000
//grails.urlmapping.cache.maxsize = 1000

// What URL patterns should be processed by the resources plugin
grails.resources.adhoc.patterns = ['/images/*', '/css/*', '/js/*', '/plugins/*']
grails.resources.adhoc.includes = ['/images/**', '/css/**', '/js/**', '/plugins/**']

// Legacy setting for codec used to encode data with ${}
grails.views.default.codec = "html"

// The default scope for controllers. May be prototype, session or singleton.
// If unspecified, controllers are prototype scoped.
grails.controllers.defaultScope = 'singleton'

//grails.config.locations = [ "file:/data/spatial-service/config/spatial-service-config.properties"]

// GSP settings
grails {
    views {
        gsp {
            encoding = 'UTF-8'
            htmlcodec = 'xml' // use xml escaping instead of HTML4 escaping
            codecs {
                expression = 'html' // escapes values inside ${}
                scriptlet = 'html' // escapes output from scriptlets in GSPs
                taglib = 'none' // escapes output from taglibs
                staticparts = 'none' // escapes output from static template parts
            }
        }
        // escapes all not-encoded output at final stage of outputting
        // filteringCodecForContentType.'text/html' = 'html'
    }
}


grails.converters.encoding = "UTF-8"
// scaffolding templates configuration
grails.scaffolding.templates.domainSuffix = 'Instance'

// Set to false to use the new Grails 1.2 JSONBuilder in the render method
grails.json.legacy.builder = false
// enabled native2ascii conversion of i18n properties files
grails.enable.native2ascii = true
// packages to include in Spring bean scanning
grails.spring.bean.packages = []
// whether to disable processing of multi part requests
grails.web.disable.multipart = false

// request parameters to mask when logging exceptions
grails.exceptionresolver.params.exclude = ['password']

// configure auto-caching of queries by default (if false you can cache individual queries with 'cache: true')
grails.hibernate.cache.queries = false

// configure passing transaction's read-only attribute to Hibernate session, queries and criterias
// set "singleSession = false" OSIV mode in hibernate configuration after enabling
grails.hibernate.pass.readonly = false
// configure passing read-only to OSIV session by default, requires "singleSession = false" OSIV mode
grails.hibernate.osiv.readonly = false

environments {
    development {
        grails.logging.jul.usebridge = true
        grails.serverURL = 'http://localhost:8080/spatial-service'
    }
    production {
        grails.logging.jul.usebridge = false
        grails.serverURL = 'http://spatial-test.ala.org.au/spatial-service'
        // TODO: grails.serverURL = "http://www.changeme.com"
    }
}

// log4j configuration
log4j = {
    appenders {
        console name: 'stdout', layout: pattern(conversionPattern: '%d %c{1} %m%n')
    }

    error 'org.codehaus.groovy.grails.web.servlet',        // controllers
            'org.codehaus.groovy.grails.web.pages',          // GSP
            'org.codehaus.groovy.grails.web.sitemesh',       // layouts
            'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
            'org.codehaus.groovy.grails.web.mapping',        // URL mapping
            'org.codehaus.groovy.grails.commons',            // core / classloading
            'org.codehaus.groovy.grails.plugins',            // plugins
            'org.codehaus.groovy.grails.orm.hibernate',      // hibernate integration
            'org.springframework',
            'org.hibernate',
            'net.sf.ehcache.hibernate'
    error 'org', 'net'

    all 'au.org.ala.layers', 'au.org.ala.spatial' //, 'grails.app'
}

//
// au.org.ala.spatial.service config
//
data.dir = '/data/spatial-data'
//geoserver.url = 'http://local.ala.org.au:8080/geoserver'
geoserver.url = 'http://local.ala.org.au:8079/geoserver'
geoserver.username = ''
geoserver.password = ''
geoserver.canDeploy = true
//gdal.dir="/usr/bin/"
shpResolutions = [0.5, 0.25, 0.1, 0.05]
grdResolutions = [0.5, 0.25, 0.1, 0.05, 0.01]
//biocacheServiceUrl = 'http://biocache.ala.org.au/ws'
//biocacheServiceUrl = 'http://ala-cohen.it.csiro.au/biocache-service'
biocacheServiceUrl = 'http://biocache.ala.org.au/ws'
//biocacheServiceUrl = 'http://ala-starr.it.csiro.au:8080/biocache-service'
biocacheUrl = 'http://biocache.ala.org.au'

slave.enable = true
service.enable = true

serviceKey = ""
batch_sampling_passwords = ''
batch_sampling_points_limit = 1000000
batch_sampling_fields_limit = 1000

//
// au.org.ala.spatial.slave config
//
spatialService.url = "http://spatial-test.ala.org.au/spatial-service"
data.dir = "/data/spatial-data"
shp2pgsql.path = "/usr/bin/shp2pgsql"
gdal.dir = "/usr/bin/"
aloc.xmx = "6G"
aloc.threads = 4
maxent.mx = "1G"
maxent.threads = 4

sampling.threads = 4

slaveKey = ""
serviceKey = ""

// time between pushing status updates to the master for a task
statusTime = 3000
retryCount = 10
retryTime = 30000

/******************************************************************************\
 *  CAS SETTINGS
 *
 *  NOTE: Some of these will be ignored if default_config exists
 \******************************************************************************/
security.cas.casServerName = 'https://auth.ala.org.au'
security.cas.loginUrl = 'https://auth.ala.org.au/cas/login'
security.cas.logoutUrl = 'https://auth.ala.org.au/cas/logout'
security.cas.casServerUrlPrefix = 'https://auth.ala.org.au/cas'
security.cas.bypass = false // set to true for non-ALA deployment
security.cas.gateway = false
security.cas.casServerUrlPrefix = 'https://auth.ala.org.au/cas'
security.cas.uriExclusionFilterPattern='/images.*,/css.*,/js.*,/less.*,/tasks/status/.*'
security.cas.uriFilterPattern='/manageLayers,/manageLayers/.*,/admin,/admin/.*'
security.cas.authenticateOnlyIfLoggedInFilterPattern='/master,/master/.*,/tasks,/tasks/.*'
security.cas.disableCAS=false

auth.admin_role = "ROLE_ADMIN"
app.http.header.userId = "X-ALA-userId"

headerAndFooter.baseURL = 'https://www.ala.org.au/commonui-bs3'
ala.baseURL = 'http://www.ala.org.au'
bie.baseURL = 'http://bie.ala.org.au'
bie.searchPath = '/search'

records.url = 'http://biocache.ala.org.au/archives/exports/lat_lon_taxon.zip'

api_key = ''
lists.url = 'http://lists.ala.org.au'
collections.url = 'http://collections.ala.org.au'
sandboxHubUrl = 'http://sandbox.ala.org.au/ala-hub'
sandboxBiocacheServiceUrl = 'http://sandbox.ala.org.au/ws'
phyloServiceUrl = 'http://phylolink.ala.org.au'

spatialHubUrl = 'http://spatial.ala.org.au'

gazField = 'cl915'
userObjectsField = 'cl1083'

apiKeyCheckUrlTemplate = 'http://auth.ala.org.au/apikey/ws/check?apikey={0}'

spatialService.remote = "http://spatial-test.ala.org.au/spatial-service"

journalmap.api_key = ''
journalmap.url = 'https://www.journalmap.org/'

//For side by side installation with layers-service, analysis-service
legacy.workingdir='/data/ala/data/alaspatial/'

legacy.enabled=true

grails.spring.bean.packages = ['au.org.ala.layers']

//legacy compatability type
//"link" = link legacy files into new locations
//"copy" = copy legacy files into new locations
//"move" = move legacy files into new locations
//legacy.type="link"

legacy.ANALYSIS_LAYER_FILES_PATH='/data/ala/data/layers/analysis/'
legacy.LAYER_FILES_PATH='/data/ala/data/layers/ready'
legacy.ALASPATIAL_OUTPUT_PATH='/data/ala/runtime/output'

grails.plugin.elfinder.rootDir = '/data/spatial-service'

i18n.override.dir='/data/spatial-service/config/i81n/'


//layers-store config

//Threads created for each batch intersection and each individual shape file
layers_store.BATCH_THREAD_COUNT=3

//Set LAYER_INDEX_URL to use REMOVE layer intersections.
//layers_store.LAYER_INDEX_URL=http://spatial.ala.org.au/layers-service

//Use local layer files for sampling or the /intersect/batch service provided by LAYER_INDEX_URL
//layers_store.LOCAL_SAMPLING=false
layers_store.LOCAL_SAMPLING=true

//# Set intersect config reload time in ms
layers_store.CONFIG_RELOAD_WAIT=12000000

//Comma separated shape file fields to preload, or 'all'
//layers_store.PRELOADED_SHAPE_FILES=all
//layers_store.PRELOADED_SHAPE_FILES=cl22,cl20

// Grid intersection buffer size in bytes.  Must be multiple of 64.
// Only applies to grids > 80MB.
// layers_store.GRID_BUFFER_SIZE=4096
layers_store.GRID_BUFFER_SIZE=40960

//Number of GridCacheReader objects to open.
layers_store.GRID_CACHE_READER_COUNT=5

// layers_store ingestion
layers_store.CAN_INGEST_LAYERS=false
layers_store.CAN_UPDATE_LAYER_DISTANCES=false
layers_store.CAN_UPDATE_GRID_CACHE=false
layers_store.CAN_GENERATE_ANALYSIS_FILES=false
layers_store.CAN_INTERSECT_LAYERS=false
layers_store.CAN_GENRATE_THUMBNAILS=false

//geoserver styles with the name <fieldId>_style exist. e.g. cl21_style
layers_store.FIELD_STYLES=true

layers_store.GEONETWORK_URL='http://spatial.ala.org.au/geonetwork'

distributions.cache.dir = "/data/${appName}/mapCache/"
distributions.geoserver.image.url = "/ALA/wms?service=WMS&version=1.1.0&request=GetMap&sld=http://fish.ala.org.au/data/dist.sld&layers=ALA:aus1,ALA:Distributions&styles=&bbox=109,-47,157,-7&srs=EPSG:4326&format=image/png&width=400&height=400&viewparams=s:"