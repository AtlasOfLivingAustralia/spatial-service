grails.servlet.version = "3.0" // Change depending on target container compliance (2.5 or 3.0)
grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.work.dir = "target/work"
grails.project.target.level = 1.7
grails.project.source.level = 1.7

grails.server.port.http = 8085

grails.project.fork = [
        // configure settings for compilation JVM, note that if you alter the Groovy version forked compilation is required
        //  compile: [maxMemory: 256, minMemory: 64, debug: false, maxPerm: 256, daemon:true],

        // configure settings for the test-app JVM, uses the daemon by default
        //test: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, daemon:true, test:false, run:false],
        // configure settings for the run-app JVM
        //run: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve:false, test:false, run:false],

        // configure settings for the run-war JVM
        //war: [maxMemory: 768, minMemory: 642, debug: false, maxPerm: 256, forkReserve:false, test:false, run:false],
        // configure settings for the Console UI JVM
        //console: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, test:false, run:false]
        run: false, test: false
]

grails.project.dependency.resolver = "maven" // or ivy
grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // specify dependency exclusions here; for example, uncomment this to disable ehcache:
        // excludes 'ehcache'
    }
    log "error" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    checksums true // Whether to verify checksums on resolve
    legacyResolve true
    // whether to do a secondary resolve on plugin installation, not advised and here for backwards compatibility

    repositories {
        mavenLocal()
        mavenRepo("http://nexus.ala.org.au/content/groups/public/") {
            updatePolicy 'always'
        }
    }

    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes e.g.
        //test "org.grails:grails-datastore-test-support:1.0-grails-2.5"
        runtime "commons-httpclient:commons-httpclient:3.1",
                "org.codehaus.jackson:jackson-core-asl:1.8.6",
                "org.codehaus.jackson:jackson-mapper-asl:1.8.6"

        compile("au.org.ala:layers-store:1.3-SNAPSHOT") {
            excludes "spring-context", "spring-jdbc", "spring-orm", "spring-oxm", "ands-pid-client", "xalan"
        }

        runtime 'jfree:jfreechart:1.0.13'

        runtime 'com.thoughtworks.xstream:xstream:1.4.2'

        runtime 'commons-io:commons-io:2.4'

        //test "org.grails:grails-datastore-test-support:1.0.2-grails-2.4"

        runtime "org.geotools:gt-jts-wrapper:11.1"

        runtime "org.apache.httpcomponents:httpmime:4.3.3"
    }

    plugins {
        compile ":quartz:1.0.2"

        compile ":jsonp:0.2"
        compile ":build-info:1.2.8"

        build ":release:3.0.1"

        // plugins for the build system only
        build ":tomcat:7.0.54"

        // plugins for the compile step
        //compile ":scaffolding:2.1.2"
        compile ':cache:1.1.8'

        // plugins needed at runtime but not for compilation
        runtime ":hibernate:3.6.10.19" // or ":hibernate4:4.3.5.4"
        runtime ":database-migration:1.4.0"
        runtime ":jquery:1.11.1"
        runtime ":resources:1.2.14"
        runtime(":jquery-ui:1.10.4"){
            excludes 'jquery'
        }

        runtime ":cors:1.3.0"

        runtime ":ala-admin-plugin:1.3-SNAPSHOT"

        compile ":ala-auth:1.3.4"
        compile(":ala-bootstrap3:1.6.2") {
            excludes 'jquery'
        }

        compile ':rendering:1.0.0'
        compile ":mail:1.0.7"
    }
}
