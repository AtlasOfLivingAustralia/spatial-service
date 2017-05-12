package au.org.ala.spatial.process

import au.org.ala.spatial.Util
import au.org.ala.spatial.service.TestUtil
import au.org.ala.spatial.slave.FileLockService
import au.org.ala.spatial.slave.SlaveService
import au.org.ala.spatial.slave.TaskService
import grails.test.mixin.TestMixin
import grails.test.mixin.web.ControllerUnitTestMixin
import org.apache.commons.io.FileUtils
import spock.lang.Specification

//@TestMixin(GrailsUnitTestMixin)
@TestMixin(ControllerUnitTestMixin)
class AooEooSpec extends Specification {
    def grailsApplication

    def proc = new AooEoo()

    def setup() {
        proc.taskService = Mock(TaskService)
        proc.slaveService = Mock(SlaveService)
        proc.grailsApplication = grailsApplication
        proc.fileLockService = Mock(FileLockService)
    }

    def cleanup() {

    }

    def "run AooEoo"() {
        when:

        def tmpDir = File.createTempDir()
        proc.taskService.getBasePath(_) >> tmpDir.getPath() + '/'

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

        proc.task = [spec: Mock(TaskService).getAllSpec().find { spec -> spec.name.equalsIgnoreCase('AooEoo') },
                     input: [area: "[{}]", species: "{\"q\": \"\", \"name\": \"test species\"}", resolution: 0.02]]

        proc.start()

        then:
        tmpDir.listFiles().each { file ->
            assert FileUtils.readFileToString(file) == TestUtil.getResourceAsString('output/aooeoo/' + file.name)
        }
    }

}
