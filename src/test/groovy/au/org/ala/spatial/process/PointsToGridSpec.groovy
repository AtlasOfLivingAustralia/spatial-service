package au.org.ala.spatial.process

import au.org.ala.layers.intersect.SimpleRegion
import au.org.ala.spatial.Util
import au.org.ala.spatial.analysis.layers.Records
import au.org.ala.spatial.service.TestUtil

import au.org.ala.spatial.service.TaskQueueService
import org.apache.commons.io.FileUtils
import org.grails.spring.beans.factory.InstanceFactoryBean
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

import javax.sql.DataSource

class PointsToGridSpec extends Specification implements GrailsUnitTest {

    def gdalInstalled = false

    @Override
    Closure doWithSpring() {{ ->
        dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)
    }}

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    def proc = new PointsToGrid()

    def setup() {
        proc.taskService = Mock(TaskQueueService)
        proc.slaveService = Mock(SlaveService)
        proc.grailsApplication = grailsApplication


        // gdal installation is required for 'PointsToGrid'
        grailsApplication.config.gdal.dir = '/opt/homebrew/bin'
        gdalInstalled = TestUtil.GDALInstalled(grailsApplication.config.gdal.dir)
    }

    def cleanup() {

    }

    def "run PointsToGrid"() {
        when:

        def tmpDir
        def replacementId
        if (gdalInstalled) {
            tmpDir = File.createTempDir()

            new File("${tmpDir}/layer").mkdirs()

            grailsApplication.config.data.dir = tmpDir.getPath()
            proc.taskService.getBasePath(_) >> tmpDir.getPath() + '/public/'

            System.out.println(tmpDir.getPath())

            Util.metaClass.static.getQid = { qid -> [:] }
            Util.metaClass.static.makeQid = { query -> "qid" }

            Util.metaClass.static.getUrl = { String url ->
                if (url.contains('webportal/params/details')) return '{}'
                else if (url.contains('/occurrences/search')) return '{"totalRecords": 10}'
                else if (url.contains('/occurrence/facets?facets=')) return '{"count": 10}'
                else if (url.contains('/occurrences/facets/download?facets=')) return "120,-15\n154, -25\n140, -40"
                return 'other'
            }

            def csvRaw = "names_and_lsid,longitude,latitude,year\n" +
                    "species1,131,-22,2000\n" +
                    "species2,121,-19,2000\n" +
                    "species3,131,-23,2000\n" +
                    "species1,141,-22,2000\n" +
                    "species1,131,-30,2000"
            def tmpCsv = File.createTempFile("test", ".csv")
            FileUtils.writeStringToFile(tmpCsv, csvRaw)
            proc.metaClass.getRecords = { String bs, String q, double[] bbox, String filename, SimpleRegion region ->
                return new RecordsMock(tmpCsv)
            }


            proc.task = [spec : Mock(TaskQueueService).getAllSpec().find { spec -> spec.name.equalsIgnoreCase('AooEoo') },
                         input: [area         : "[{\"wkt\": \"POLYGON((120 -15,154 -15,154 -40,120 -40,120 -15))\", \"bbox\": [120,-15,154,-40]}]",
                                 species      : "{\"q\": \"\", \"name\": \"test species\"}", resolution: 0.02,
                                 gridCellSize : 1, sitesBySpecies: true, occurrenceDensity: true, speciesRichness: true,
                                 movingAverage: "1x1"]]

            proc.start()

            replacementId = new File(tmpDir.getPath() + '/layer/').listFiles().first().getName().replaceAll('_.*', '')
        }

        then:

        if (gdalInstalled) {
            def tested = []
            tested.addAll(compareDir(tmpDir, '/output/pointstogrid', replacementId))
        }
    }

    def compareDir(tmpDir, expectedDir, replacementId) {
        def tested = []
        tmpDir.listFiles().each { file ->
            if (file.isDirectory()) {
                tested.addAll(compareDir(file, "${expectedDir}/${file.name}", replacementId))
            } else {
                def expect = new File(TestUtil.getResourcePath("${expectedDir}/${file.name.replace(replacementId, '*')}"))
                tested.add(expect)
                assert compareFiles(file, expect)
            }
        }
        tested
    }

    def compareFiles (File file, File file2) {
        if (file.getName().endsWith(".csv") || file.getName().endsWith(".asc") ||
                file.getName().endsWith(".wkt") || file.getName().endsWith(".sld")) {
            FileUtils.readFileToString(file) == FileUtils.readFileToString(file2)
        } else if (file.getName().endsWith(".html")) {
            //replace date/time strings, processIds
            FileUtils.readFileToString(file).replaceAll("\\b[0-9]{2}\\/[0-9]{2}\\/[0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2}\\b|\\b[0-9]{13}\\b", "") ==
                    FileUtils.readFileToString(file2).replaceAll("\\b[0-9]{2}\\/[0-9]{2}\\/[0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2}\\b|\\b[0-9]{13}\\b", "")
        } else if (file.getName().endsWith(".gri")) {
            FileUtils.readFileToByteArray(file) == FileUtils.readFileToByteArray(file2)
        } else if (file.getName().endsWith(".grd")) {
            //replace created and title values
            String remove = '(Title|Created).*\n'
            FileUtils.readFileToString(file).replaceAll("\r\n", "\n").replaceAll(remove, '') ==
                    FileUtils.readFileToString(file2).replaceAll("\r\n", "\n").replaceAll(remove, '')
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

