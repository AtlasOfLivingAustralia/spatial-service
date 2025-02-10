package au.org.ala.spatial.process

import au.org.ala.spatial.dto.SpeciesInput
import au.org.ala.spatial.SpatialConfig
import au.org.ala.spatial.Util
import au.org.ala.spatial.LayersDistancesServiceSpec
import au.org.ala.spatial.TaskQueueService
import au.org.ala.spatial.TasksService
import au.org.ala.spatial.TestUtil
import au.org.ala.spatial.util.Records
import au.org.ala.spatial.intersect.SimpleRegion
import org.grails.spring.beans.factory.InstanceFactoryBean
import org.grails.testing.GrailsUnitTest
import spock.lang.Ignore
import spock.lang.Specification

import javax.sql.DataSource

class PointsToGridSpec extends Specification implements GrailsUnitTest {

    def gdalInstalled = false

    File tmpCsv

    TasksService tasksService
    SpatialConfig spatialConfig
    TaskQueueService taskQueueService

    @Override
    Closure doWithSpring() {
        { ->
            dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)

            this.spatialConfig = new SpatialConfig()
            this.spatialConfig.with {
                data = new SpatialConfig.DotDir()
                data.dir = new File(LayersDistancesServiceSpec.class.getResource("/resources/layers.json").getFile()).getParent()
                task = new SpatialConfig.DotTask()
                task.general = new SpatialConfig.DotThreads()
                task.general.threads = 1
                task.admin = new SpatialConfig.DotThreads()
                task.admin.threads = 1
                gdal = new SpatialConfig.DotDir()
                gdal.dir = '/opt/homebrew/bin'
                admin = new SpatialConfig.DotTimeout()
                admin.timeout = 30000

            }

            taskQueueService(TaskQueueService) {
                spatialConfig = this.spatialConfig
            }
        }
    }

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    def proc = new PointsToGrid() {
        @Override
        String streamBytes(String url, String name) {
            if (url.contains('webportal/params/details')) return '{}'
            else if (url.contains('/occurrences/search')) return '{"totalRecords": 10}'
            else if (url.contains('/occurrence/facets?facets=')) return '{"count": 10}'
            else if (url.contains('/occurrences/facets/download?facets=')) return "120,-15\n154, -25\n140, -40"
            return 'other'
        }

        @Override
        Integer occurrenceCount(SpeciesInput species, String extraFq){
            10
        }

        @Override
        def getRecords(String bs, String q, double[] bbox) {
            new RecordsMock(tmpCsv)
        }
    }

    def setup() {
        Util.metaClass.initialize()

        proc.tasksService = Mock(TasksService)
        tasksService = proc.tasksService

        taskQueueService = applicationContext.getBean('taskQueueService') as TaskQueueService

        gdalInstalled = TestUtil.GDALInstalled(spatialConfig.gdal.dir)
    }

    def cleanup() {

    }

    @Ignore
    def "run PointsToGrid"() {
        when:

        proc.spatialConfig = this.spatialConfig

        def tmpDir = null
        def replacementId = null
        if (gdalInstalled) {
            tmpDir = File.createTempDir()

            new File("${tmpDir}/layer").mkdirs()

            Util.metaClass.static.getQid = { qid -> [:] }
            Util.metaClass.static.makeQid = { query -> "qid" }

            def csvRaw = "names_and_lsid,longitude,latitude,year\n" +
                    "species1,131,-22,2000\n" +
                    "species2,121,-19,2000\n" +
                    "species3,131,-23,2000\n" +
                    "species1,141,-22,2000\n" +
                    "species1,131,-30,2000"
            tmpCsv = File.createTempFile("test", ".csv")
            tmpCsv.write(csvRaw)

            proc.taskWrapper = [
                    path: tmpDir,
                    spec : tasksService.getAllSpec().find { spec -> spec.name.equalsIgnoreCase('PointsToGrid') },
                    task: [
                            input: [
                                    [ name: "area", value: "[{\"wkt\": \"POLYGON((120 -15,154 -15,154 -40,120 -40,120 -15))\", \"bbox\": [120,-15,154,-40]}]"],
                                    [ name: "species", value: "{\"q\": \"\", \"name\": \"test species\"}"],
                                    [ name: "resolution", value: "0.02"],
                                    [ name: "gridCellSize", value : "1"],
                                    [ name: "sitesBySpecies", value : "true"],
                                    [ name: "occurrenceDensity", value : "true"],
                                    [ name: "speciesRichness", value: "true"],
                                    [ name: "movingAverage", value: "1x1"]
                            ],
                            output: []
                    ]
            ]

            proc.spatialConfig.data.dir = tmpDir.path

            proc.start()

            replacementId = new File(tmpDir.getPath() + '/layer/').listFiles().first().getName().replaceAll('_.*', '')
        }

        then:

        if (gdalInstalled) {
            def tested = []
            tested.addAll(compareDir(tmpDir, '/output/pointstogrid', replacementId))
        }
    }

    def compareDir(File tmpDir, expectedDir, String replacementId) {
        def tested = []
        tmpDir.listFiles().each { File file ->
            if (file.isDirectory()) {
                tested.addAll(compareDir(file, "${expectedDir}/${file.name}", replacementId))
            } else {
                File expect = new File(TestUtil.getResourcePath("${expectedDir}/${file.name.replace(replacementId, '*')}"))
                tested.add(expect)
                assert compareFiles(file, expect)
            }
        }
        tested
    }

    def compareFiles(File file, File file2) {
        if (file.getName().endsWith(".csv") || file.getName().endsWith(".asc") ||
                file.getName().endsWith(".wkt")                       || file.getName().endsWith(".sld")) {
            file.text == file2.text
        } else if (file.getName().endsWith(".html")) {
            //replace date/time strings, processIds
            file.text.replaceAll("\\b[0-9]{2}\\/[0-9]{2}\\/[0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2}\\b|\\b[0-9]{13}\\b", "") ==
                    file2.text.replaceAll("\\b[0-9]{2}\\/[0-9]{2}\\/[0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2}\\b|\\b[0-9]{13}\\b", "")
        } else if (file.getName().endsWith(".gri")) {
            file.bytes == file2.bytes
        } else if (file.getName().endsWith(".grd")) {
            //replace created and title values
            String remove = '(Title|Created).*\n'
            file.text.replaceAll("\r\n", "\n").replaceAll(remove, '') ==
                    file2.text.replaceAll("\r\n", "\n").replaceAll(remove, '')
        } else {
            //png, tif
            //default test of exists and !empty
            file.exists() && file2.exists() && file.size() > 0 && file2.size() > 0
        }
    }

    class RecordsMock extends Records {
        File filename

        RecordsMock(File filename) {
            super(filename.getPath())
            this.filename = filename
        }
    }
}

